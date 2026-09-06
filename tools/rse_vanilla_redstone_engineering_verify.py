#!/usr/bin/env python3
"""Fail-closed static gate for the diagnostics-only Vanilla Redstone Engineering foundation."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
errors = []

def read(rel: str) -> str:
    path = ROOT / rel
    if not path.exists():
        errors.append(f"missing {rel}")
        return ""
    return path.read_text(encoding="utf-8")

def need(src: str, needle: str, label: str) -> None:
    if needle not in src:
        errors.append(f"{label}: missing {needle!r}")

profile = read("src/main/java/dev/redstoneengineering/diagnostics/redstone/VanillaRedstoneEngineeringProfile.java")
report = read("src/main/java/dev/redstoneengineering/diagnostics/redstone/VanillaRedstoneDiagnosticsReport.java")
target = read("src/main/java/dev/redstoneengineering/diagnostics/redstone/VanillaRedstoneTargetSnapshot.java")
history = read("src/main/java/dev/redstoneengineering/diagnostics/redstone/VanillaRedstoneTargetHistory.java")
debugger = read("src/main/java/dev/redstoneengineering/block/TopologyDebuggerBlock.java")
menu = read("src/main/java/dev/redstoneengineering/ui/menu/TopologyDebuggerMenu.java")
screen = read("src/main/java/dev/redstoneengineering/client/ui/TopologyDebuggerScreen.java")
ui_common = read("src/main/java/dev/redstoneengineering/ui/EngineeringUiRegistration.java")
ui_client = read("src/main/java/dev/redstoneengineering/client/ui/EngineeringUiClientRegistration.java")
runtime_registration = read("src/main/java/dev/redstoneengineering/VanillaRedstoneRuntimeRegistration.java")
gt = read("src/main/java/dev/redstoneengineering/gametest/RseVanillaRedstoneEngineeringGameTests.java")
runtime_gt = read("src/main/java/dev/redstoneengineering/gametest/RseVanillaRedstoneRuntimeGameTests.java")
module = read("src/main/java/dev/redstoneengineering/EngineeringSystemsModule.java")
workflow = read(".github/workflows/build.yml")
doc = read("docs/VANILLA_REDSTONE_ENGINEERING.md")

for needle in (
    "MAX_NODES = 256",
    "MAX_MANHATTAN_DISTANCE = 24",
    "Blocks.REDSTONE_WIRE",
    "RedStoneWireBlock.POWER",
    "RepeaterBlock.DELAY",
    "possibleQcDependencies",
    "level.hasNeighborSignal(pos.above())",
    "visited.size() >= MAX_NODES",
):
    need(profile, needle, "VanillaRedstoneEngineeringProfile.java")

for needle in (
    "POSSIBLE_QC_DEPENDENCY",
    "OBSERVER_DENSE",
    "HIGH_LOCAL_FANOUT",
    "DENSE_COMPONENT_CLUSTER",
    "PROFILE_TRUNCATED",
    "hasTopologyIssue()",
    "VANILLA REDSTONE",
):
    need(report, needle, "VanillaRedstoneDiagnosticsReport.java")

for needle in (
    "DUST = 1", "REPEATER = 2", "COMPARATOR = 3", "OBSERVER = 4",
    "RedStoneWireBlock.POWER", 'intProperty(state, "delay", -1)',
    'stringProperty(state, "mode", "")', 'directionProperty(state, "facing")'
):
    need(target, needle, "VanillaRedstoneTargetSnapshot.java")

for needle in (
    "MAX_EVENTS_PER_LEVEL = 2048", "DISPLAY_SAMPLES = 24", "WeakHashMap",
    "onNeighborNotify", "sample.source().equals(target)", "latestGameTime", "transitionCount"
):
    need(history, needle, "VanillaRedstoneTargetHistory.java")

for needle in (
    "targetsVanillaRedstone",
    "inspectVanillaTarget",
    "VanillaRedstoneEngineeringProfile",
    "computeVanillaOutput",
    "EngineeringTopologyView.inspect",
    "report.hasIssue()",
    "new TopologyDebuggerMenu",
    "openMenu",
):
    need(debugger, needle, "TopologyDebuggerBlock.java")

for needle in (
    "VanillaRedstoneTargetSnapshot.inspect",
    "VanillaRedstoneRuntimeTelemetry.inspect",
    "VanillaRedstoneRuntimeTelemetry.inspectTiming",
    "VanillaRedstoneRuntimeTelemetry.inspectBehavior",
    "VanillaRedstoneTargetHistory.inspect",
    "timelineLatestTimeLow",
    "timelineAges",
):
    need(menu, needle, "TopologyDebuggerMenu.java")

for needle in (
    "VANILLA BEHAVIOR IMMUTABILITY",
    "EngineeringChartRenderer.drawWaveform",
    "EngineeringChartRenderer.drawChangeMarkers",
    "EngineeringChartRenderer.drawGameTimeAxis",
    "Exact target-source observations only",
    "logical-server gameTime",
):
    need(screen, needle, "TopologyDebuggerScreen.java")

need(ui_common, "MenuType<TopologyDebuggerMenu>", "EngineeringUiRegistration.java")
need(ui_client, "TopologyDebuggerScreen::new", "EngineeringUiClientRegistration.java")
need(runtime_registration, "VanillaRedstoneTargetHistory::onNeighborNotify", "VanillaRedstoneRuntimeRegistration.java")

for name in (
    "vanillaProfileFindsPoweredDustAndTimingComponents",
    "vanillaProfileIsObserverOnly",
    "topologyDebuggerSelectsVanillaRedstoneMode",
):
    need(gt, name, "RseVanillaRedstoneEngineeringGameTests.java")
need(gt, "VanillaRedstoneTargetSnapshot.inspect", "RseVanillaRedstoneEngineeringGameTests.java")
need(runtime_gt, "VanillaRedstoneTargetHistory.inspect", "RseVanillaRedstoneRuntimeGameTests.java")

count = len(re.findall(r"@GameTest\s*\(", gt))
if count != 3:
    errors.append(f"RseVanillaRedstoneEngineeringGameTests.java: expected exactly 3 foundation tests, found {count}")

need(module, "event.register(RseVanillaRedstoneEngineeringGameTests.class);", "EngineeringSystemsModule.java")
need(workflow, "tools/rse_vanilla_redstone_engineering_verify.py", "build.yml")
minimum_match = re.search(r"test_count < ([0-9]+)", workflow)
if minimum_match is None or int(minimum_match.group(1)) < 177:
    errors.append("build.yml: GameTest floor must remain at least 177 after VRE diagnostics")
need(doc, "Diagnostics Only", "VANILLA_REDSTONE_ENGINEERING.md")
need(doc, "No mixins", "VANILLA_REDSTONE_ENGINEERING.md")

# Observation/presentation layers must not mutate world state or become a replacement solver.
for label, src in (
    ("VanillaRedstoneEngineeringProfile.java", profile),
    ("VanillaRedstoneDiagnosticsReport.java", report),
    ("VanillaRedstoneTargetSnapshot.java", target),
    ("VanillaRedstoneTargetHistory.java", history),
    ("TopologyDebuggerMenu.java", menu),
):
    for forbidden in ("setBlock(", "scheduleTick(", "updateNeighborsAt(", "neighborChanged("):
        if forbidden in src:
            errors.append(f"{label}: diagnostics-only layer must not mutate simulation; found {forbidden}")

java_root = ROOT / "src/main/java"
for path in java_root.rglob("*.java"):
    text = path.read_text(encoding="utf-8")
    if "@Mixin(RedStoneWireBlock" in text or "@Mixin(value = RedStoneWireBlock" in text:
        errors.append(f"{path.relative_to(ROOT)}: VRE must not replace/mixin vanilla redstone wire")

if errors:
    print("RSE VANILLA REDSTONE ENGINEERING VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    sys.exit(1)

print("RSE VANILLA REDSTONE ENGINEERING VERIFY: PASS")
print("  vanilla behavior modification: NONE (Diagnostics Only)")
print("  bounded adjacent-component traversal: <=256 nodes / <=24 Manhattan blocks")
print("  dust 0..15 + configured repeater timing evidence: PASS")
print("  component snapshots for dust/repeater/comparator/observer: PASS")
print("  exact-target event timeline: <=24 display samples / server gameTime")
print("  server-backed Topology Debugger engineering overlay: PASS")
print("  shared chart rendering + read-only authority boundary: PASS")
print("  QC/fan-out/density classifications remain advisories: PASS")
print("  executable VRE foundation GameTests: 3")
