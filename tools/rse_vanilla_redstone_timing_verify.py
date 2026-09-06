#!/usr/bin/env python3
"""Fail-closed gate for Vanilla Redstone Engineering Phase 3 timing/order evidence."""
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
timing = read("src/main/java/dev/redstoneengineering/diagnostics/redstone/VanillaRedstoneTimingReport.java")
debugger = read("src/main/java/dev/redstoneengineering/block/TopologyDebuggerBlock.java")
registration = read("src/main/java/dev/redstoneengineering/VanillaRedstoneRuntimeRegistration.java")
gt = read("src/main/java/dev/redstoneengineering/gametest/RseVanillaRedstoneTimingGameTests.java")
workflow = read(".github/workflows/build.yml")
doc = read("docs/VANILLA_REDSTONE_ENGINEERING.md")

for needle in (
    "nextObservationSequence",
    "observationSequence",
    "inspectTiming",
    "clearRegion",
    "sameTickOrderedPairs",
    "sameTickDistinctSourcePairs",
    "minInterObservation",
    "maxInterObservation",
    "minInterTransition",
    "maxInterTransition",
    "level.getGameTime()",
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
        errors.append(f"VanillaRedstoneRuntimeTelemetry.java: timing observer must not use wall-clock/mutate simulation; found {forbidden}")

for needle in (
    "VanillaRedstoneTimingReport",
    "hasTimingEvidence()",
    "hasTransitionIntervalEvidence()",
    "hasSameTickOrderEvidence()",
    "ORDER=OBSERVED_EVENT_ORDER_ONLY",
    "interObs=",
    "interTransition=",
):
    need(timing, needle, "VanillaRedstoneTimingReport.java")

for forbidden in ("exactUpdateOrder", "causalUpdateOrder", "subTickTime", "solverOrder"):
    if forbidden in timing or forbidden in telemetry:
        errors.append(f"Phase-3 API must not claim unobserved timing/order semantics; found {forbidden}")

for needle in (
    "inspectVanillaTiming",
    "VanillaRedstoneRuntimeTelemetry.inspectTiming",
    "VanillaRedstoneTimingReport timing",
    "timing.summary()",
):
    need(debugger, needle, "TopologyDebuggerBlock.java")

need(registration, "event.register(RseVanillaRedstoneTimingGameTests.class);", "VanillaRedstoneRuntimeRegistration.java")

for name in (
    "timingReportCapturesCrossTickSpacing",
    "timingReportPreservesSameTickObservationOrder",
    "topologyDebuggerIncludesTimingEvidence",
):
    need(gt, name, "RseVanillaRedstoneTimingGameTests.java")

count = len(re.findall(r"@GameTest\s*\(", gt))
if count != 3:
    errors.append(f"RseVanillaRedstoneTimingGameTests.java: expected exactly 3 timing tests, found {count}")
for needle in (
    "clearRegion",
    "level.updateNeighborsAt",
    "ORDER=OBSERVED_EVENT_ORDER_ONLY",
    "delayCfg=6gt",
):
    need(gt, needle, "RseVanillaRedstoneTimingGameTests.java")

need(workflow, "tools/rse_vanilla_redstone_timing_verify.py", "build.yml")
minimum_match = re.search(r"test_count < ([0-9]+)", workflow)
if minimum_match is None or int(minimum_match.group(1)) < 183:
    errors.append("build.yml: GameTest floor must be at least 183 after Phase-3 timing/order analysis")

for needle in (
    "Phase 3 — Timing and Order Analysis",
    "server game ticks",
    "OBSERVED_EVENT_ORDER_ONLY",
    "not causal update order",
    "not sub-tick time",
):
    need(doc, needle, "VANILLA_REDSTONE_ENGINEERING.md")

java_root = ROOT / "src/main/java"
for path in java_root.rglob("*.java"):
    text = path.read_text(encoding="utf-8")
    if "@Mixin(RedStoneWireBlock" in text or "@Mixin(value = RedStoneWireBlock" in text:
        errors.append(f"{path.relative_to(ROOT)}: Phase 3 must not replace/mixin vanilla redstone wire")

if errors:
    print("RSE VANILLA REDSTONE TIMING VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    sys.exit(1)

print("RSE VANILLA REDSTONE TIMING VERIFY: PASS")
print("  timebase: server gameTime / game ticks only")
print("  inter-observation + same-source transition spacing: bounded evidence")
print("  same-tick order: listener observation sequence only")
print("  causal update order / scheduler priority / sub-tick time claims: NONE")
print("  vanilla behavior modification: NONE")
print("  executable Phase-3 GameTests: 3")
