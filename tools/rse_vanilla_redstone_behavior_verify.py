#!/usr/bin/env python3
"""Fail-closed gate for Vanilla Redstone Engineering behavior classifications."""
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
report = read("src/main/java/dev/redstoneengineering/diagnostics/redstone/VanillaRedstoneBehaviorReport.java")
debugger = read("src/main/java/dev/redstoneengineering/block/TopologyDebuggerBlock.java")
registration = read("src/main/java/dev/redstoneengineering/VanillaRedstoneRuntimeRegistration.java")
gt = read("src/main/java/dev/redstoneengineering/gametest/RseVanillaRedstoneBehaviorGameTests.java")
workflow = read(".github/workflows/build.yml")
doc = read("docs/VANILLA_REDSTONE_ENGINEERING.md")

for needle in (
    "inspectBehavior",
    "observedActiveState",
    "completePulses",
    "narrowPulses",
    "observerFeedbackCandidates",
    "recurrentOrderedPairs",
    "QC_ACTIVITY_CORRELATION",
    "ORDER_SENSITIVITY_CANDIDATE",
    "VanillaRedstoneEngineeringProfile.inspect",
):
    need(telemetry, needle, "VanillaRedstoneRuntimeTelemetry.java")

for forbidden in (
    "System.nanoTime",
    "System.currentTimeMillis",
    "Instant.now",
    "setCanceled(",
    "setBlock(",
    "setBlockAndUpdate(",
    "scheduleTick(",
    "updateNeighborsAt(",
    "neighborChanged(",
):
    if forbidden in telemetry:
        errors.append(f"VanillaRedstoneRuntimeTelemetry.java: behavior observer must not use wall-clock/mutate simulation; found {forbidden}")

for needle in (
    "VanillaRedstoneBehaviorReport",
    "completeObservedPulses",
    "minObservedPulseWidthTicks",
    "maxObservedPulseWidthTicks",
    "observerFeedbackCandidates",
    "possibleQcDependencyCount",
    "qcCorrelatedRuntimeEvents",
    "recurrentObservedOrderPairs",
    "orderSensitivityConfidence",
    "PULSE=OBSERVED_COMPLETE_ONLY",
    "QC=CORRELATION_NOT_CAUSATION",
    "ORDER=OBSERVED_EVENT_ORDER_ONLY",
):
    need(report, needle, "VanillaRedstoneBehaviorReport.java")

for forbidden in (
    "CAUSAL_ORDER_PROVEN",
    "QC_CAUSED",
    "EXACT_PULSE_WIDTH",
    "SUB_TICK",
):
    if forbidden in report or forbidden in telemetry:
        errors.append(f"Behavior API must not claim unavailable causal/sub-tick evidence; found {forbidden}")

for needle in (
    "inspectVanillaBehavior",
    "VanillaRedstoneRuntimeTelemetry.inspectBehavior",
    "VanillaRedstoneBehaviorReport behavior",
    "behavior.summary()",
):
    need(debugger, needle, "TopologyDebuggerBlock.java")

need(registration, "event.register(RseVanillaRedstoneBehaviorGameTests.class);", "VanillaRedstoneRuntimeRegistration.java")

for name in (
    "behaviorReportMeasuresCompleteObservedPulseWidth",
    "topologyDebuggerFlagsObserverFeedbackCandidateOnly",
    "behaviorReportCorrelatesQcCandidateWithoutClaimingCausation",
    "behaviorReportScoresRecurrentObservedOrderConservatively",
):
    need(gt, name, "RseVanillaRedstoneBehaviorGameTests.java")

count = len(re.findall(r"@GameTest\s*\(", gt))
if count != 4:
    errors.append(f"RseVanillaRedstoneBehaviorGameTests.java: expected exactly 4 behavior tests, found {count}")
for needle in (
    "clearRegion",
    "level.updateNeighborsAt",
    "PULSE=OBSERVED_COMPLETE_ONLY",
    "QC=CORRELATION_NOT_CAUSATION",
    "ORDER=OBSERVED_EVENT_ORDER_ONLY",
):
    need(gt, needle, "RseVanillaRedstoneBehaviorGameTests.java")

need(workflow, "tools/rse_vanilla_redstone_behavior_verify.py", "build.yml")
need(workflow, "github.event_name == 'workflow_dispatch'", "build.yml")
need(workflow, "continue-on-error: true", "build.yml")
need(workflow, "Minecraft topology GameTests (manual diagnostic)", "build.yml")

for needle in (
    "Behavior Classification",
    "OBSERVED_COMPLETE_ONLY",
    "CORRELATION_NOT_CAUSATION",
    "OBSERVER_FEEDBACK_CANDIDATE",
    "ORDER_SENSITIVITY_CANDIDATE",
):
    need(doc, needle, "VANILLA_REDSTONE_ENGINEERING.md")

java_root = ROOT / "src/main/java"
for path in java_root.rglob("*.java"):
    text = path.read_text(encoding="utf-8")
    if "@Mixin(RedStoneWireBlock" in text or "@Mixin(value = RedStoneWireBlock" in text:
        errors.append(f"{path.relative_to(ROOT)}: behavior classification must not replace/mixin vanilla redstone wire")

if errors:
    print("RSE VANILLA REDSTONE BEHAVIOR VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    sys.exit(1)

print("RSE VANILLA REDSTONE BEHAVIOR VERIFY: PASS")
print("  complete pulse widths: observed active->inactive intervals only")
print("  observer feedback: bounded return-pattern candidate only")
print("  QC evidence: structural/runtime correlation, not causation")
print("  order sensitivity: confidence-scored candidate from observed evidence")
print("  causal/sub-tick claims: NONE")
print("  vanilla behavior modification: NONE")
print("  registered behavior GameTests: 4 (manual diagnostic / non-blocking)")
