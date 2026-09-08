#!/usr/bin/env python3
"""Fail-closed gate that keeps RSE's Vanilla Redstone Engineering layer observer-only."""
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


preservation = read("src/main/java/dev/redstoneengineering/gametest/RseVanillaRedstonePreservationGameTests.java")
registration = read("src/main/java/dev/redstoneengineering/VanillaRedstoneRuntimeRegistration.java")
workflow = read(".github/workflows/build.yml")
status_doc = read("docs/VANILLA_REDSTONE_IMMUTABILITY.md")

required_tests = (
    "vanillaDustAttenuationRemainsFifteenToTwelve",
    "vanillaRepeaterDelayFourDoesNotFireEarly",
    "vanillaComparatorCompareAndSubtractRemainDistinct",
    "vanillaObserverPulseStillReturnsLow",
    "vanillaPistonDirectPowerStillExtendsAndRetracts",
)
for name in required_tests:
    need(preservation, name, "RseVanillaRedstonePreservationGameTests.java")
if len(re.findall(r"@GameTest\s*\(", preservation)) != len(required_tests):
    errors.append("RseVanillaRedstonePreservationGameTests.java: expected exactly 5 preservation tests")
need(registration, "event.register(RseVanillaRedstonePreservationGameTests.class);", "VanillaRedstoneRuntimeRegistration.java")
need(workflow, "tools/rse_vanilla_redstone_immutability_verify.py", "build.yml")
minimum_match = re.search(r"test_count < ([0-9]+)", workflow)
if minimum_match is None or int(minimum_match.group(1)) < 317:
    errors.append("build.yml: GameTest floor must be at least 317 after vanilla immutability closure")

for phrase in (
    "Vanilla Immutability Regression Gate",
    "Phase 5 optimized runtime is not active",
    "explicitly optional",
    "fail closed to vanilla semantics",
    "replacement redstone solver",
):
    need(status_doc, phrase, "VANILLA_REDSTONE_IMMUTABILITY.md")

# Hot-path/query production surfaces must stay observation-only. Tests are intentionally excluded:
# GameTest fixtures are allowed to manipulate vanilla blocks to prove their normal behavior.
production_files = [
    "src/main/java/dev/redstoneengineering/VanillaRedstoneRuntimeRegistration.java",
    "src/main/java/dev/redstoneengineering/diagnostics/redstone/VanillaRedstoneEngineeringProfile.java",
    "src/main/java/dev/redstoneengineering/diagnostics/redstone/VanillaRedstoneRuntimeTelemetry.java",
    "src/main/java/dev/redstoneengineering/diagnostics/redstone/VanillaRedstoneDiagnosticsReport.java",
    "src/main/java/dev/redstoneengineering/diagnostics/redstone/VanillaRedstoneRuntimeReport.java",
    "src/main/java/dev/redstoneengineering/diagnostics/redstone/VanillaRedstoneTimingReport.java",
    "src/main/java/dev/redstoneengineering/diagnostics/redstone/VanillaRedstoneBehaviorReport.java",
]
for rel in production_files:
    src = read(rel)
    for forbidden in (
        "setCanceled(",
        "setBlock(",
        "setBlockAndUpdate(",
        "removeBlock(",
        "destroyBlock(",
        "scheduleTick(",
        "updateNeighborsAt(",
        "updateNeighbourForOutputSignal(",
        "neighborChanged(",
    ):
        if forbidden in src:
            errors.append(f"{rel}: vanilla observer surface must not mutate simulation; found {forbidden}")

# No bytecode injection into the vanilla redstone engine/components. This is broader than the older
# dust-only guard and deliberately covers the components exercised by the preservation suite.
redstone_targets = (
    "RedStoneWireBlock",
    "RepeaterBlock",
    "ComparatorBlock",
    "ObserverBlock",
    "PistonBaseBlock",
)
java_root = ROOT / "src/main/java"
for path in java_root.rglob("*.java"):
    text = path.read_text(encoding="utf-8")
    if "@Mixin" not in text:
        continue
    for target in redstone_targets:
        if target in text:
            errors.append(f"{path.relative_to(ROOT)}: must not mixin/inject vanilla redstone target {target}")

# No hidden mixin/coremod/access-transformer resource entry may be introduced as an alternate path.
resource_root = ROOT / "src/main/resources"
for path in resource_root.rglob("*"):
    if not path.is_file():
        continue
    lower = path.name.lower()
    if "mixin" in lower or "accesstransformer" in lower or lower.endswith("coremods.json"):
        errors.append(f"{path.relative_to(ROOT)}: unexpected bytecode/coremod hook in vanilla-first build")

# The NeighborNotify listener itself must remain bounded bookkeeping, not query-time structural work.
telemetry = read("src/main/java/dev/redstoneengineering/diagnostics/redstone/VanillaRedstoneRuntimeTelemetry.java")
on_notify = telemetry.split("public static void onNeighborNotify", 1)
if len(on_notify) != 2:
    errors.append("VanillaRedstoneRuntimeTelemetry.java: missing onNeighborNotify listener")
else:
    listener_body = on_notify[1].split("public static VanillaRedstoneRuntimeReport inspect", 1)[0]
    for forbidden in (
        "VanillaRedstoneEngineeringProfile.inspect(",
        "inspectTiming(",
        "inspectBehavior(",
    ):
        if forbidden in listener_body:
            errors.append(f"VanillaRedstoneRuntimeTelemetry.java: hot listener must not run query/scan work; found {forbidden}")
need(telemetry, "MAX_EVENTS_PER_LEVEL = 4096", "VanillaRedstoneRuntimeTelemetry.java")
need(telemetry, "MAX_STATE_POSITIONS = 1024", "VanillaRedstoneRuntimeTelemetry.java")

if errors:
    print("RSE VANILLA REDSTONE IMMUTABILITY VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    sys.exit(1)

print("RSE VANILLA REDSTONE IMMUTABILITY VERIFY: PASS")
print("  current optimized/replacement vanilla runtime: NOT ACTIVE")
print("  VRE production mutation APIs: NONE")
print("  redstone mixin/coremod/access-transformer hooks: NONE")
print("  NeighborNotify hot path: bounded observer bookkeeping only")
print("  preservation fixtures: dust / repeater / comparator / observer / piston")
print("  required GameTest floor: >=317")
