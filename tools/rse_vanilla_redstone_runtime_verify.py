#!/usr/bin/env python3
"""Fail-closed gate for Vanilla Redstone Engineering Phase 2 runtime telemetry."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.exists():
        errors.append(f"missing {rel}")
        return ""
    return path.read_text(encoding="utf-8")


def need(src: str, needle: str, label: str) -> None:
    if needle not in src:
        errors.append(f"{label}: missing {needle!r}")


telemetry = read("src/main/java/dev/redstoneengineering/diagnostics/redstone/VanillaRedstoneRuntimeTelemetry.java")
report = read("src/main/java/dev/redstoneengineering/diagnostics/redstone/VanillaRedstoneRuntimeReport.java")
registration = read("src/main/java/dev/redstoneengineering/VanillaRedstoneRuntimeRegistration.java")
debugger = read("src/main/java/dev/redstoneengineering/block/TopologyDebuggerBlock.java")
gt = read("src/main/java/dev/redstoneengineering/gametest/RseVanillaRedstoneRuntimeGameTests.java")
workflow = read(".github/workflows/build.yml")
doc = read("docs/VANILLA_REDSTONE_ENGINEERING.md")

for needle in (
    "MAX_EVENTS_PER_LEVEL = 4096",
    "MAX_STATE_POSITIONS = 1024",
    "DEFAULT_RADIUS_BLOCKS = 16",
    "MAX_RADIUS_BLOCKS = 64",
    "DEFAULT_WINDOW_TICKS = 100L",
    "MAX_WINDOW_TICKS = 1200L",
    "WeakHashMap",
    "BlockEvent.NeighborNotifyEvent",
    "event.getNotifiedSides().size()",
    "event.getForceRedstoneUpdate()",
    "observedStateTransition",
    "RedStoneWireBlock.POWER",
    "BlockStateProperties.POWERED",
    "BlockStateProperties.LIT",
    "BlockStateProperties.EXTENDED",
):
    need(telemetry, needle, "VanillaRedstoneRuntimeTelemetry.java")

if re.search(r"public\s+static\s+(?:synchronized\s+)?void\s+clear\s*\(ServerLevel\s+level\)", telemetry) is None:
    errors.append("VanillaRedstoneRuntimeTelemetry.java: missing public static clear(ServerLevel) lifecycle reset")

for forbidden in (
    "setCanceled(",
    "setBlock(",
    "setBlockAndUpdate(",
    "scheduleTick(",
    "updateNeighborsAt(",
    "neighborChanged(",
):
    if forbidden in telemetry:
        errors.append(f"VanillaRedstoneRuntimeTelemetry.java: observer-only telemetry must not mutate simulation; found {forbidden}")

for needle in (
    "neighborNotificationEvents",
    "notifiedSideTotal",
    "forceRedstoneUpdateEvents",
    "observedStateTransitions",
    "uniqueSourcePositions",
    "hotspotEventCount",
    "NeighborNotifyEvent observations=0",
):
    need(report, needle, "VanillaRedstoneRuntimeReport.java")

if "eventsPerSecond()" in report:
    errors.append("VanillaRedstoneRuntimeReport.java: do not expose a per-second rate before observation-window coverage is measured")

for needle in (
    "NeoForge.EVENT_BUS.addListener(VanillaRedstoneRuntimeTelemetry::onNeighborNotify)",
    "modBus.addListener(VanillaRedstoneRuntimeRegistration::registerGameTests)",
    "event.register(RseVanillaRedstoneRuntimeGameTests.class);",
):
    need(registration, needle, "VanillaRedstoneRuntimeRegistration.java")

if "@EventBusSubscriber" in registration:
    errors.append("VanillaRedstoneRuntimeRegistration.java: use explicit event-bus registration, not automatic subscriber scanning")

for needle in (
    "inspectVanillaRuntime",
    "vanillaDiagnosticSummary",
    "VanillaRedstoneRuntimeTelemetry.inspect",
    "RUNTIME |",
):
    need(debugger, needle, "TopologyDebuggerBlock.java")

for name in (
    "runtimeTelemetryRecordsRealNeighborNotifications",
    "runtimeTelemetrySeparatesObservedStateTransitions",
    "topologyDebuggerIncludesVanillaRuntimeEvidence",
):
    need(gt, name, "RseVanillaRedstoneRuntimeGameTests.java")

count = len(re.findall(r"@GameTest\s*\(", gt))
if count != 3:
    errors.append(f"RseVanillaRedstoneRuntimeGameTests.java: expected exactly 3 runtime tests, found {count}")
need(gt, "level.updateNeighborsAt", "RseVanillaRedstoneRuntimeGameTests.java")
need(gt, "VanillaRedstoneRuntimeTelemetry.clear", "RseVanillaRedstoneRuntimeGameTests.java")

need(workflow, "tools/rse_vanilla_redstone_runtime_verify.py", "build.yml")
minimum_match = re.search(r"test_count < ([0-9]+)", workflow)
if minimum_match is None or int(minimum_match.group(1)) < 180:
    errors.append("build.yml: GameTest floor must remain at least 180 after VRE runtime telemetry")
need(doc, "Phase 2 — Runtime Update Profiling", "VANILLA_REDSTONE_ENGINEERING.md")
need(doc, "Neighbor Notification Events", "VANILLA_REDSTONE_ENGINEERING.md")
need(doc, "Observed State Transitions", "VANILLA_REDSTONE_ENGINEERING.md")
need(doc, "not solver-evaluation counts", "VANILLA_REDSTONE_ENGINEERING.md")

java_root = ROOT / "src/main/java"
for path in java_root.rglob("*.java"):
    text = path.read_text(encoding="utf-8")
    if "@Mixin(RedStoneWireBlock" in text or "@Mixin(value = RedStoneWireBlock" in text:
        errors.append(f"{path.relative_to(ROOT)}: VRE runtime telemetry must not replace/mixin vanilla redstone wire")

if errors:
    print("RSE VANILLA REDSTONE RUNTIME VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    sys.exit(1)

print("RSE VANILLA REDSTONE RUNTIME VERIFY: PASS")
print("  source: server-side NeoForge NeighborNotifyEvent")
print("  semantics: Neighbor Notification Events != internal solver evaluations")
print("  observed state transitions: separate metric")
print("  storage: weak level keys + bounded 4096-event / 1024-state retention")
print("  query: <=64-block radius / <=1200-tick rolling window")
print("  vanilla behavior modification: NONE")
print("  executable Phase-2 GameTests: 3")
