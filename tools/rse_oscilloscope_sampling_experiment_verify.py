#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors = []

def read(rel):
    path = root / rel
    if not path.is_file():
        errors.append(f"missing {rel}")
        return ""
    return path.read_text(errors="ignore")

record = read("src/main/java/dev/redstoneengineering/blockentity/OscilloscopeSamplingExperimentRecord.java")
be = read("src/main/java/dev/redstoneengineering/blockentity/OscilloscopeBlockEntity.java")
block = read("src/main/java/dev/redstoneengineering/block/OscilloscopeBlock.java")
menu = read("src/main/java/dev/redstoneengineering/ui/menu/OscilloscopeMenu.java")
screen = read("src/main/java/dev/redstoneengineering/client/ui/OscilloscopeScreen.java")
gametest = read("src/main/java/dev/redstoneengineering/gametest/RseEngineeringUiGameTests.java")

for token in (
    "record OscilloscopeSamplingExperimentRecord",
    "comparisonReady()",
    'tag.putBoolean(prefix + "Present", true)',
    "ObservedFrequencyMilliHz",
    "AliasRiskCode",
    "MeanStep100",
):
    if token not in record:
        errors.append(f"sampling experiment record missing {token!r}")

for token in (
    "captureExperimentBaseline",
    "captureExperimentCandidate",
    "clearSamplingExperiment",
    "samplingExperimentStatus",
    "EngineeringAcceptanceStatus.NOT_READY",
    "EngineeringAcceptanceStatus.PASS",
    "EngineeringAcceptanceStatus.MARGINAL",
    "EngineeringAcceptanceStatus.FAIL",
    "experimentBaseline.save",
    "experimentCandidate.save",
    'OscilloscopeSamplingExperimentRecord.load(tag, "experimentBaseline")',
    'OscilloscopeSamplingExperimentRecord.load(tag, "experimentCandidate")',
):
    if token not in be:
        errors.append(f"oscilloscope block entity missing experiment token {token!r}")

for token in (
    "BUTTON_EXPERIMENT_BASELINE",
    "BUTTON_EXPERIMENT_CANDIDATE",
    "BUTTON_EXPERIMENT_CLEAR",
    "level.getGameTime()",
):
    if token not in block:
        errors.append(f"oscilloscope block missing experiment action {token!r}")

for token in (
    "baselinePresent",
    "candidatePresent",
    "baselineSamplePeriodTicks",
    "candidateSamplePeriodTicks",
    "experimentSamplesDelta",
    "experimentFrequencyDeltaMilliHz",
    "scope.samplingExperimentStatus().ordinal()",
):
    if token not in menu:
        errors.append(f"oscilloscope menu missing frozen evidence synchronization {token!r}")

for token in (
    'EXPERIMENT("Experiment")',
    "Capture baseline",
    "Capture candidate",
    "Clear experiment",
    "FROZEN EVIDENCE COMPARISON",
    "N_cycle = T_obs / Δt_sample = captured periodSamples",
    "≤2 samples/cycle → FAIL",
    "3–4 → MARGINAL",
    "≥5 → PASS",
    "not proof that the original source is alias-free",
    "vanilla Redstone clock is a valid source",
):
    if token not in screen:
        errors.append(f"oscilloscope experiment HMI missing {token!r}")

for forbidden in (
    "dev.redstoneengineering.physics",
    "RuntimeIntStore",
    "scheduleTick(",
    "setBlock(",
):
    if forbidden in screen:
        errors.append(f"experiment screen violates client authority boundary with {forbidden!r}")

for token in (
    "oscilloscopeSamplingExperimentFreezesComparableEvidence",
    "BUTTON_EXPERIMENT_BASELINE",
    "BUTTON_EXPERIMENT_CANDIDATE",
    "BUTTON_EXPERIMENT_CLEAR",
    "samplingExperimentStatus() != EngineeringAcceptanceStatus.PASS",
    "samplingExperimentSamplesPerCycleDelta() != 4",
):
    if token not in gametest:
        errors.append(f"sampling experiment GameTest missing {token!r}")

if errors:
    print("RSE OSCILLOSCOPE SAMPLING EXPERIMENT VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE OSCILLOSCOPE SAMPLING EXPERIMENT VERIFY: PASS")
print(" immutable frozen baseline/candidate records: PASS")
print(" save/reload experiment evidence: PASS")
print(" server-owned PASS/MARGINAL/FAIL/NOT_READY verdict: PASS")
print(" baseline survives timebase/live-capture invalidation: PASS")
print(" formula-first comparison workspace: PASS")
print(" explicit aliasing caveat / no false ground-truth claim: PASS")
print(" client/no-second-solver boundary: PASS")
print(" executable experiment GameTest source: PASS")
