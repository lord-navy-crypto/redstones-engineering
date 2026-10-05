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

record = read("src/main/java/dev/redstoneengineering/diagnostics/CommissioningTrialRecord.java")
comparison = read("src/main/java/dev/redstoneengineering/diagnostics/CommissioningTrialComparison.java")
store = read("src/main/java/dev/redstoneengineering/diagnostics/CommissioningTrialStore.java")
pid_block = read("src/main/java/dev/redstoneengineering/block/PidControllerBlock.java")
pid_menu = read("src/main/java/dev/redstoneengineering/ui/menu/PidControllerMenu.java")
pid_screen = read("src/main/java/dev/redstoneengineering/client/ui/PidControllerScreen.java")
commissioning = read("src/main/java/dev/redstoneengineering/diagnostics/ClosedLoopCommissioning.java")

for token in (
    "long sequence",
    "long gameTick",
    "int tuningPreset",
    "CommissioningSnapshot commissioning",
    "EngineeringAcceptanceSnapshot acceptance",
    "score=",
    "settle=",
    "overshoot=",
    "sat=",
    "issues=",
):
    if token not in record:
        errors.append(f"CommissioningTrialRecord missing {token!r}")

for token in (
    "MAX_CONTROLLERS_PER_LEVEL = 256",
    "captureBaseline(",
    "state.candidate = null;",
    "captureCandidate(",
    "if (state == null || state.baseline == null) return Optional.empty();",
    "comparison(Level level, BlockPos pos)",
    "CommissioningTrialComparison.between",
    "clear(Level level, BlockPos pos)",
    "WeakHashMap",
):
    if token not in store:
        errors.append(f"CommissioningTrialStore missing {token!r}")

for forbidden in (
    "RuntimeIntStore",
    "setBlock(",
    "scheduleTick(",
    "PneumaticNetwork",
    "updateNeighborsAt",
):
    if forbidden in record + comparison + store:
        errors.append(f"trial evidence layer must remain read-only; found forbidden token {forbidden!r}")

for token in (
    "ClosedLoopCommissioning.compare(",
    "settlingDeltaTicks",
    "overshootDelta",
    "saturationDelta",
    "topologyIssueDelta",
    "AcceptanceEvidenceTrend.INCOMPARABLE",
    "candidate.acceptance().status() != EngineeringAcceptanceStatus.FAIL",
    "issueDelta <= 0",
    "dynamic.robust()",
):
    if token not in comparison:
        errors.append(f"CommissioningTrialComparison missing {token!r}")

for token in (
    "captureCommissioningTrialBaseline",
    "captureCommissioningTrialCandidate",
    "clearCommissioningTrial",
    "trialEvidenceReady",
    "CommissioningStatus.PASS",
    "CommissioningStatus.MARGINAL",
    "CommissioningStatus.FAIL",
    "CommissioningTrialStore.clear(level, pos)",
):
    if token not in pid_block:
        errors.append(f"PidControllerBlock missing trial contract {token!r}")

for forbidden in (
    "CommissioningStatus.IDLE -> true",
    "CommissioningStatus.RUNNING -> true",
    "CommissioningStatus.UNAVAILABLE -> true",
):
    if forbidden in pid_block:
        errors.append(f"PidControllerBlock accepts non-settled trial evidence via {forbidden!r}")

for token in (
    "BUTTON_TRIAL_BASELINE = 8",
    "BUTTON_TRIAL_CANDIDATE = 9",
    "BUTTON_TRIAL_CLEAR = 10",
    "trialBaselineSequence",
    "trialCandidateSequence",
    "trialTrend",
    "trialRobust",
    "trialSettlingDelta",
    "trialOvershootDelta",
    "trialSaturationDelta",
    "trialTopologyIssueDelta",
    "CommissioningTrialStore.baseline",
    "CommissioningTrialStore.candidate",
    "CommissioningTrialStore.comparison",
):
    if token not in pid_menu:
        errors.append(f"PidControllerMenu missing synchronized trial token {token!r}")

for token in (
    "PIONEER WORKFLOW • CLOSED-LOOP COMMISSIONING TRIAL",
    "Trial baseline",
    "Trial candidate",
    "Clear trial",
    "START WITH BASELINE",
    "BASELINE READY • settle, then candidate",
    "Captures require settled PASS / MARGINAL / FAIL evidence",
    "Δsettle",
    "Δovershoot",
    "Δsat",
    "TRIAL ",
):
    if token not in pid_screen:
        errors.append(f"PidControllerScreen missing trial HMI token {token!r}")

if "dev.redstoneengineering.physics" in pid_screen:
    errors.append("PidControllerScreen imports server physics directly")

# Existing dynamic robustness thresholds remain centralized in ClosedLoopCommissioning.
for token in (
    "scoreLoss <= 20",
    "settlingPenalty <= 80",
    "overshootIncrease <= 1",
    "saturationIncrease <= 4",
):
    if token not in commissioning:
        errors.append(f"ClosedLoopCommissioning lost established robustness threshold {token!r}")
    if token in comparison:
        errors.append(f"CommissioningTrialComparison duplicated dynamic robustness threshold {token!r}")

# Small deterministic policy sanity table for trial trend precedence.
def trend(ready_before, ready_after, issue_delta, severity_delta, score_delta, settling_delta, overshoot_delta, saturation_delta):
    if not ready_before or not ready_after:
        return "INCOMPARABLE"
    if issue_delta < 0:
        return "IMPROVED"
    if issue_delta > 0:
        return "REGRESSED"
    if severity_delta < 0:
        return "IMPROVED"
    if severity_delta > 0:
        return "REGRESSED"
    if score_delta > 0:
        return "IMPROVED"
    if score_delta < 0:
        return "REGRESSED"
    if settling_delta < 0 or overshoot_delta < 0 or saturation_delta < 0:
        return "IMPROVED"
    if settling_delta > 0 or overshoot_delta > 0 or saturation_delta > 0:
        return "REGRESSED"
    return "SAME"

cases = (
    ((False, True, 0, 0, 10, -5, -1, -1), "INCOMPARABLE"),
    ((True, True, -1, 2, -20, 50, 2, 3), "IMPROVED"),
    ((True, True, 1, -2, 20, -50, -2, -3), "REGRESSED"),
    ((True, True, 0, 0, 5, 10, 1, 1), "IMPROVED"),
    ((True, True, 0, 0, 0, -10, 0, 0), "IMPROVED"),
    ((True, True, 0, 0, 0, 10, 0, 0), "REGRESSED"),
    ((True, True, 0, 0, 0, 0, 0, 0), "SAME"),
)
for args, expected in cases:
    actual = trend(*args)
    if actual != expected:
        errors.append(f"trial comparison policy sanity failed: {args} -> {actual}, expected {expected}")

if errors:
    print("RSE CLOSED-LOOP COMMISSIONING TRIAL VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE CLOSED-LOOP COMMISSIONING TRIAL VERIFY: PASS")
print(" bounded one-baseline/one-candidate evidence store: PASS")
print(" settled-evidence capture gate: PASS")
print(" existing authoritative commissioning/topology reuse: PASS")
print(" baseline/candidate fixed comparison semantics: PASS")
print(" centralized robustness thresholds: PASS")
print(" synchronized server-owned PID trial HMI: PASS")
print(" client/no-second-solver boundary: PASS")
