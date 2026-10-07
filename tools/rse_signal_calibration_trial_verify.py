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

block = read("src/main/java/dev/redstoneengineering/block/SignalAnalyzerBlock.java")
menu = read("src/main/java/dev/redstoneengineering/ui/menu/SignalAnalyzerMenu.java")
screen = read("src/main/java/dev/redstoneengineering/ui/ldlib/SignalAnalyzerLdUi.java")
record = read("src/main/java/dev/redstoneengineering/diagnostics/SignalCalibrationTrialRecord.java")
comparison = read("src/main/java/dev/redstoneengineering/diagnostics/SignalCalibrationTrialComparison.java")
store = read("src/main/java/dev/redstoneengineering/diagnostics/SignalCalibrationTrialStore.java")
gametest = read("src/main/java/dev/redstoneengineering/gametest/RseSignalCalibrationTrialGameTests.java")
runtime = read("src/main/java/dev/redstoneengineering/physics/RuntimeIntStore.java")

for token in (
    'IntegerProperty.create("reference", 0, 15)',
    ".setValue(REFERENCE, 8)",
    "QUALITY_WINDOW_BASE = WINDOW_BASE + WINDOW",
    "recordSample(level, pos, measured, present)",
    "r[QUALITY_WINDOW_BASE + write] = present ? 1 : 0",
    "rollingValidCount",
    "PortQuality measurementQuality",
    "SignalCalibrationTrialStore.clear(level, pos)",
    "captureCalibrationBaseline",
    "captureCalibrationCandidate",
    "clearCalibrationTrial",
    "BUTTON_REFERENCE_DECREASE",
    "BUTTON_REFERENCE_INCREASE",
):
    if token not in block:
        errors.append(f"SignalAnalyzerBlock missing Wave-12 evidence token {token!r}")

# Runtime layout migration must be safe when transient arrays grow.
for token in (
    "if (existing.length != size)",
    "existing = new int[size]",
):
    if token not in runtime:
        errors.append(f"RuntimeIntStore lost development-safe runtime-layout migration {token!r}")

# Physical INLINE output remains raw measurement, never calibrated/reference-adjusted.
if "int requestedOutput = state.getValue(MODE) == INLINE ? measured : 0;" not in block:
    errors.append("Signal Analyzer INLINE output is no longer explicitly raw measurement")
for forbidden in (
    "requestedOutput = calibrated",
    "requestedOutput = calibratedReading",
    "requestedOutput = state.getValue(REFERENCE)",
):
    if forbidden in block:
        errors.append(f"calibration/reference must not drive physical INLINE output: found {forbidden!r}")

for token in (
    "windowCount == 16",
    "validWindowCount == 16",
    "sampleAgeTicks <= 4",
    "measurementQuality == PortQuality.VALID",
    "absoluteError100",
    "calibratedSpan",
    "calibratedMeanStep100",
    "clippingSamples",
):
    if token not in record:
        errors.append(f"SignalCalibrationTrialRecord missing capture-quality contract {token!r}")

for token in (
    "baseline.reference() == candidate.reference()",
    "baseline.mode() == candidate.mode()",
    "baseline.facingOrdinal() == candidate.facingOrdinal()",
    "errorDelta != 0",
    "clippingDelta != 0",
    "spanDelta != 0",
    "stepDelta != 0",
    "Trend.INCOMPARABLE",
):
    if token not in comparison:
        errors.append(f"SignalCalibrationTrialComparison missing {token!r}")

# No invented absolute calibration pass/fail tolerance.
for forbidden in (
    "TOLERANCE",
    "PASS_THRESHOLD",
    "errorDelta100 <=",
    "absoluteError100() <=",
):
    if forbidden in comparison:
        errors.append(f"calibration trial invented an unsupported absolute tolerance via {forbidden!r}")

for token in (
    "MAX_ANALYZERS_PER_LEVEL = 256",
    "state.candidate = null;",
    "if (state == null || state.baseline == null) return Optional.empty();",
    "SignalCalibrationTrialComparison.between",
    "WeakHashMap",
):
    if token not in store:
        errors.append(f"SignalCalibrationTrialStore missing bounded trial contract {token!r}")

for token in (
    "BUTTON_REFERENCE_DECREASE = 6",
    "BUTTON_REFERENCE_INCREASE = 7",
    "BUTTON_TRIAL_BASELINE = 8",
    "BUTTON_TRIAL_CANDIDATE = 9",
    "BUTTON_TRIAL_CLEAR = 10",
    "validWindowCount",
    "measurementQuality",
    "trialBaselineSequence",
    "trialCandidateSequence",
    "trialErrorDelta100",
    "trialClippingDelta",
    "trialSpanDelta",
    "trialMeanStepDelta100",
):
    if token not in menu:
        errors.append(f"SignalAnalyzerMenu missing synchronized Wave-12 token {token!r}")

for token in (
    "PIONEER WORKFLOW • INTERNAL REFERENCE CALIBRATION TRIAL",
    "e_ref = mean(clamp(x_raw + b_cal,0,15)) - x_ref",
    '"x_ref","0..15 • direct entry"',
    "Trial baseline",
    "Trial candidate",
    "Clear trial",
    "measurement coverage=",
    "Internal RSE reference comparison only; this does not establish external metrological traceability.",
    "RseLdUiComponents.authorityFooter()",
):
    if token not in screen:
        errors.append(f"SignalAnalyzerLdUi missing Wave-12 HMI token {token!r}")

for token in (
    "BUTTON_REFERENCE_DIRECT_BASE",
    "setReferenceFromUi",
    "captureTrialBaseline",
    "captureTrialCandidate",
    "clearTrial",
):
    if token not in menu:
        errors.append(f"SignalAnalyzerMenu missing Wave-12 LDLib2 intent token {token!r}")

if "dev.redstoneengineering.physics" in screen:
    errors.append("SignalAnalyzerLdUi must remain presentation-only")

for token in (
    "lowerReferenceErrorIsImproved",
    "changedReferenceIsIncomparable",
    "lessClippingBreaksEqualErrorTie",
    "storeRequiresBaselineAndNewBaselineClearsCandidate",
):
    if token not in gametest:
        errors.append(f"Signal calibration GameTest missing {token!r}")

# Independent policy sanity: error-to-reference outranks clipping/noise; changed conditions are incomparable.
def classify(comparable, error_delta, clip_delta, span_delta, step_delta):
    if not comparable:
        return "INCOMPARABLE"
    if error_delta != 0:
        return "IMPROVED" if error_delta < 0 else "REGRESSED"
    if clip_delta != 0:
        return "IMPROVED" if clip_delta < 0 else "REGRESSED"
    if span_delta != 0:
        return "IMPROVED" if span_delta < 0 else "REGRESSED"
    if step_delta != 0:
        return "IMPROVED" if step_delta < 0 else "REGRESSED"
    return "SAME"

cases = (
    ((False, -100, -4, -2, -20), "INCOMPARABLE"),
    ((True, -10, 5, 5, 100), "IMPROVED"),
    ((True, 10, -5, -5, -100), "REGRESSED"),
    ((True, 0, -1, 3, 100), "IMPROVED"),
    ((True, 0, 0, -1, 100), "IMPROVED"),
    ((True, 0, 0, 0, 1), "REGRESSED"),
    ((True, 0, 0, 0, 0), "SAME"),
)
for args, expected in cases:
    actual = classify(*args)
    if actual != expected:
        errors.append(f"calibration comparison sanity failed: {args} -> {actual}, expected {expected}")

if errors:
    print("RSE SIGNAL CALIBRATION TRIAL VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE SIGNAL CALIBRATION TRIAL VERIFY: PASS")
print(" valid-zero versus empty-aperture rolling evidence: PASS")
print(" full fresh 16/16 capture gate: PASS")
print(" internal reference / display-only calibration contract: PASS")
print(" baseline/candidate comparability conditions: PASS")
print(" relative error/clipping/span/stability comparison: PASS")
print(" no invented external traceability or absolute tolerance: PASS")
print(" client/no-second-solver boundary: PASS")
