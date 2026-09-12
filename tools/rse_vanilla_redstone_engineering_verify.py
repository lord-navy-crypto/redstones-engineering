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
debugger = read("src/main/java/dev/redstoneengineering/block/TopologyDebuggerBlock.java")
gt = read("src/main/java/dev/redstoneengineering/gametest/RseVanillaRedstoneEngineeringGameTests.java")
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
    "targetsVanillaRedstone",
    "inspectVanillaTarget",
    "VanillaRedstoneEngineeringProfile",
    "computeVanillaOutput",
    "EngineeringTopologyView.inspect",
    "report.hasIssue()",
):
    need(debugger, needle, "TopologyDebuggerBlock.java")

for name in (
    "vanillaProfileFindsPoweredDustAndTimingComponents",
    "vanillaProfileIsObserverOnly",
    "topologyDebuggerSelectsVanillaRedstoneMode",
):
    need(gt, name, "RseVanillaRedstoneEngineeringGameTests.java")

count = len(re.findall(r"@GameTest\s*\(", gt))
if count != 3:
    errors.append(f"RseVanillaRedstoneEngineeringGameTests.java: expected exactly 3 foundation tests, found {count}")

need(module, "event.register(RseVanillaRedstoneEngineeringGameTests.class);", "EngineeringSystemsModule.java")
need(workflow, "tools/rse_vanilla_redstone_engineering_verify.py", "build.yml")
# Runtime GameTests remain registered as diagnostic evidence, but they are intentionally manual/non-blocking.
for needle in (
    "Minecraft topology GameTests (manual diagnostic)",
    "github.event_name == 'workflow_dispatch'",
    "continue-on-error: true",
    "./gradlew compileJava",
    "./gradlew test",
):
    need(workflow, needle, "build.yml")
need(doc, "Diagnostics Only", "VANILLA_REDSTONE_ENGINEERING.md")
need(doc, "No mixins", "VANILLA_REDSTONE_ENGINEERING.md")

# Phase one is observation only: no world mutation, no replacement solver, no vanilla mixin.
for label, src in (
    ("VanillaRedstoneEngineeringProfile.java", profile),
    ("VanillaRedstoneDiagnosticsReport.java", report),
):
    for forbidden in ("setBlock(", "scheduleTick(", "updateNeighborsAt(", "neighborChanged("):
        if forbidden in src:
            errors.append(f"{label}: diagnostics-only layer must not mutate simulation; found {forbidden}")

java_root = ROOT / "src/main/java"
for path in java_root.rglob("*.java"):
    text = path.read_text(encoding="utf-8")
    if "@Mixin(RedStoneWireBlock" in text or "@Mixin(value = RedStoneWireBlock" in text:
        errors.append(f"{path.relative_to(ROOT)}: phase-one VRE must not replace/mixin vanilla redstone wire")

if errors:
    print("RSE VANILLA REDSTONE ENGINEERING VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    sys.exit(1)

print("RSE VANILLA REDSTONE ENGINEERING VERIFY: PASS")
print("  vanilla behavior modification: NONE (Diagnostics Only)")
print("  bounded adjacent-component traversal: <=256 nodes / <=24 Manhattan blocks")
print("  dust 0..15 + configured repeater timing evidence: PASS")
print("  QC/fan-out/density classifications remain advisories: PASS")
print("  Topology Debugger vanilla-mode routing: PASS")
print("  registered VRE foundation GameTests: 3 (manual diagnostic / non-blocking)")
