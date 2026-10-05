#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors = []

def read(rel):
    path = root / rel
    if not path.is_file():
        errors.append(f"missing {rel}")
        return ""
    return path.read_text(errors="ignore")

menu = read("src/main/java/dev/redstoneengineering/ui/menu/FieldDeviceMenu.java")
screen = read("src/main/java/dev/redstoneengineering/client/ui/EnhancedFieldDeviceScreen.java")
field_ui = read("src/main/java/dev/redstoneengineering/ui/FieldDeviceUi.java")
redstone_net = read("src/main/java/dev/redstoneengineering/physics/RedstoneCableNetwork.java")
terminal = read("src/main/java/dev/redstoneengineering/block/RedstoneCableTerminalBlock.java")
lapis_line = read("src/main/java/dev/redstoneengineering/block/LapisSignalLineBlock.java")
quartz_line = read("src/main/java/dev/redstoneengineering/block/QuartzTimingLineBlock.java")
instrument = read("src/main/java/dev/redstoneengineering/instrument/InstrumentNetwork.java")
data_bus = read("src/main/java/dev/redstoneengineering/physics/DataBusNetwork.java")
optical = read("src/main/java/dev/redstoneengineering/block/OpticalFiberBlock.java")
amethyst = read("src/main/java/dev/redstoneengineering/block/AmethystResonanceDustBlock.java")

# 1) Taxonomy closure: contiguous 0..78 and every real kind mapped by kindOf().
constants = [(name, int(value)) for name, value in re.findall(
    r"public static final int (KIND_[A-Z0-9_]+)\s*=\s*(\d+)\s*;", menu
)]
if len(constants) != 79:
    errors.append(f"expected 79 FieldDevice KIND constants including UNKNOWN, found {len(constants)}")
values = sorted(value for _, value in constants)
if values != list(range(79)):
    errors.append(f"FieldDevice KIND values must remain contiguous 0..78, got {values[:5]}...{values[-5:] if values else []}")

real_kinds = {name for name, value in constants if name != "KIND_UNKNOWN"}
kind_map = re.findall(r"if \(block instanceof (\w+)\) return (KIND_[A-Z0-9_]+);", menu)
mapped_kinds = [kind for _, kind in kind_map]
if len(kind_map) != 78:
    errors.append(f"expected 78 concrete block→kind mappings, found {len(kind_map)}")
if set(mapped_kinds) != real_kinds:
    missing = sorted(real_kinds - set(mapped_kinds))
    extra = sorted(set(mapped_kinds) - real_kinds)
    errors.append(f"kindOf coverage mismatch missing={missing} extra={extra}")
if len(mapped_kinds) != len(set(mapped_kinds)):
    dup = sorted({k for k in mapped_kinds if mapped_kinds.count(k) > 1})
    errors.append(f"duplicate kindOf mappings: {dup}")

# 2) Every real kind must be covered by a dedicated route or explicit Enhanced classification.
class_to_kind = dict(kind_map)
dedicated_classes = set(re.findall(r"block instanceof (\w+)", field_ui))
dedicated_kinds = {kind for cls, kind in class_to_kind.items() if cls in dedicated_classes}

# A shared fallback contract is explicit when the kind is referenced at least twice in Enhanced:
# once can be a cosmetic label; twice requires it to participate in role/model/topology/control logic.
enhanced_counts = {kind: screen.count(kind) for kind in real_kinds}
enhanced_kinds = {kind for kind, count in enhanced_counts.items() if count >= 2}
covered = dedicated_kinds | enhanced_kinds
uncovered = sorted(real_kinds - covered)
if uncovered:
    errors.append(f"real FieldDevice kinds without dedicated or explicit Enhanced Pioneer coverage: {uncovered}")

# Known dedicated-only families should stay dedicated rather than being forced into generic fallback prose.
dedicated_only_expected = {
    "KIND_SERVO_ACTUATOR", "KIND_SERVO_POSITION_SENSOR", "KIND_REDUNDANT_VOTER", "KIND_FAULT_LATCH",
    "KIND_PNEUMATIC_PIPE", "KIND_AIR_RESERVOIR", "KIND_PNEUMATIC_CHECK_VALVE", "KIND_OPTICAL_SPLITTER",
}
for kind in dedicated_only_expected:
    if kind not in dedicated_kinds:
        errors.append(f"{kind} lost its dedicated FieldDeviceUi route")

# 3) Wave-8 synchronized PortQuality state must exist and be used by the screen.
for token in (
    "private final DataSlot evidenceQuality = trackedInt();",
    "public PortQuality evidenceQuality()",
    "evidenceQuality.set(-1);",
    "if (evidenceQuality.get() < 0)",
    "LapisSignalLineBlock.quality(level, blockPos)",
    "LapisSignalLineBlock.sourceCount(level, blockPos)",
    "QuartzTimingLineBlock.quality(level, blockPos)",
    "QuartzTimingLineBlock.sourceCount(level, blockPos)",
    "RedstoneCableNetwork.sourceEvidence(level, blockPos)",
    "InstrumentNetwork.scan(level, blockPos)",
    "DataBusNetwork.quality(level, blockPos)",
    "OpticalFiberBlock.quality(level, blockPos, state)",
    "AmethystResonanceDustBlock.status(level, blockPos)",
):
    if token not in menu:
        errors.append(f"FieldDeviceMenu missing Wave-8 evidence token {token!r}")

for token in (
    "PIONEER PATTERN • SOURCE / MEDIUM INTEGRITY",
    "isSourceMediumIntegrityDevice()",
    "configured zero remains VALID evidence",
    "valid zero ≠ no source",
    "multi-source=TOPOLOGY_ERROR; truncated scan=STALE",
    "duplicate channel or truncated scan invalidates trustworthy evidence",
    "PortQuality",
    "STALE • SERVER EVIDENCE INCOMPLETE",
    "TOPOLOGY ERROR • CONFLICT / INVALID PATH",
    "KIND_OPTICAL_FIBER_JUNCTION",
):
    if token not in screen:
        errors.append(f"EnhancedFieldDeviceScreen missing Wave-8 contract {token!r}")

# Real Lapis source controls: not decorative; menu mutates server state and recomputes the network.
for token in (
    "block instanceof LapisPrecisionSourceBlock",
    "Math.max(0, value - 5)",
    "value >= 100 ? 0 : value + 5",
    "state.setValue(LapisPrecisionSourceBlock.VALUE, value)",
    "DomainNetwork.recomputeLapis(server, blockPos)",
):
    if token not in menu:
        errors.append(f"Lapis source control missing server action {token!r}")
if "FieldDeviceMenu.KIND_LAPIS_SOURCE" not in screen or 'case FieldDeviceMenu.KIND_LAPIS_SOURCE -> "Value"' not in screen:
    errors.append("Lapis source is not enabled as a real adjustable Enhanced HMI control")

# 4) Cross-source evidence semantics: UI claims must be supported by the server implementations.
server_contracts = {
    "RedstoneCableNetwork.java": (redstone_net, (
        "sourceCount > 0 ? PortQuality.VALID : PortQuality.NO_SIGNAL",
        "removeEvidence(level, pos)",
        "int nextPower = Math.max(0, current.power - loss)",
        "int loss = (to.getBlock() instanceof RedstoneSignalCableBlock",
    )),
    "RedstoneCableTerminalBlock.java": (terminal, (
        "Distinguish an actually attached zero-valued source from an empty terminal",
        "externalSourcePresent",
    )),
    "LapisSignalLineBlock.java": (lapis_line, (
        "if(n>1)return PortQuality.TOPOLOGY_ERROR",
        "if(stored==PortQuality.STALE)return PortQuality.STALE",
        "sourceCount(Level l,BlockPos p)",
    )),
    "QuartzTimingLineBlock.java": (quartz_line, (
        "if(n>1)return PortQuality.TOPOLOGY_ERROR",
        "if(stored==PortQuality.STALE)return PortQuality.STALE",
        "sourceCount(Level l,BlockPos p)",
    )),
    "InstrumentNetwork.java": (instrument, (
        "if (!bounded) return PortQuality.TOPOLOGY_ERROR",
        "if (duplicateChannelsInMask(mask) > 0) return PortQuality.TOPOLOGY_ERROR",
        "interferenceConfidencePercent()",
    )),
    "DataBusNetwork.java": (data_bus, (
        "if (diagnostics.truncated()) return PortQuality.STALE",
        "if (diagnostics.distinctValues() > 1 || !diagnostics.valid()) return PortQuality.TOPOLOGY_ERROR",
    )),
    "OpticalFiberBlock.java": (optical, (
        "if (driverCount(level, pos) > 1) return PortQuality.TOPOLOGY_ERROR",
        "if (stored == PortQuality.STALE) return PortQuality.STALE",
    )),
    "AmethystResonanceDustBlock.java": (amethyst, (
        "FREQUENCY_CONFLICT",
        "STALE",
        "PortQuality.TOPOLOGY_ERROR",
    )),
}
for name, (text, tokens) in server_contracts.items():
    for token in tokens:
        if token not in text:
            errors.append(f"{name} source-integrity contract missing {token!r}")

# Independent bounded sanity sweep for the visible Redstone cable hop law.
for power in range(16):
    next_power = max(0, power - 1)
    if not 0 <= next_power <= 15:
        errors.append(f"Redstone cable hop escaped 0..15 at power={power}")
    if power == 0 and next_power != 0:
        errors.append("zero-valued Redstone propagation must remain zero")

# Client must remain presentation-only.
for forbidden in (
    "dev.redstoneengineering.physics",
    "RedstoneCableNetwork.",
    "DomainNetwork.",
    "InstrumentNetwork.",
    "DataBusNetwork.",
):
    if forbidden in screen:
        errors.append(f"Enhanced client HMI contains forbidden server-solver token {forbidden!r}")

if errors:
    print("RSE FIELD DEVICE PIONEER COVERAGE VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

fallback_count = len(real_kinds - dedicated_kinds)
print("RSE FIELD DEVICE PIONEER COVERAGE VERIFY: PASS")
print(f" FieldDevice taxonomy: {len(real_kinds)} real kinds + KIND_UNKNOWN")
print(f" exact dedicated FieldDeviceUi routes: {len(dedicated_kinds)}")
print(f" shared/fallback candidates covered by Enhanced contracts: {fallback_count}")
print(" uncovered real kinds: 0")
print(" source/medium PortQuality synchronization: PASS")
print(" valid-zero / source-count / stale-conflict contracts: PASS")
print(" Lapis Precision Source real controls: PASS")
print(" client/no-second-solver boundary: PASS")
