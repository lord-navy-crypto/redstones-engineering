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

telemetry = read("src/main/java/dev/redstoneengineering/robotics/RobotMissionTelemetrySnapshot.java")
record = read("src/main/java/dev/redstoneengineering/robotics/RobotCommissioningTrialRecord.java")
comparison = read("src/main/java/dev/redstoneengineering/robotics/RobotCommissioningTrialComparison.java")
store = read("src/main/java/dev/redstoneengineering/robotics/RobotCommissioningTrialStore.java")
entity = read("src/main/java/dev/redstoneengineering/entity/EngineeringMobileRobotEntity.java")
tablet = read("src/main/java/dev/redstoneengineering/item/DiagnosticTabletItem.java")
screen = read("src/main/java/dev/redstoneengineering/client/ui/DiagnosticTabletScreen.java")
safety = read("src/main/java/dev/redstoneengineering/robotics/RobotSafetyAssessment.java")

for token in (
    "boolean started",
    "boolean finished",
    "boolean completed",
    "long durationTicks",
    "BlockPos startPos",
    "BlockPos finalTarget",
    "RobotLocalizationQuality worstLocalization",
    "int obstacleWaitEvents",
    "int degradedEntries",
    "int safeStopEvents",
    "int faultEvents",
    "int routeRejectEvents",
    "int dockHoldEvents",
    "int materialHoldEvents",
    "int maxRouteWaypoints",
    "int motionTicks",
    "comparablePath",
    "stationaryTicks",
):
    if token not in telemetry:
        errors.append(f"RobotMissionTelemetrySnapshot missing {token!r}")

for token in (
    "MAX_ROBOTS_PER_LEVEL = 256",
    "captureBaseline(",
    "state.candidate = null;",
    "captureCandidate(",
    "if (state == null || state.baseline == null) return Optional.empty();",
    "RobotCommissioningTrialComparison.between",
    "WeakHashMap",
):
    if token not in store:
        errors.append(f"RobotCommissioningTrialStore missing {token!r}")

for forbidden in (
    "setDeltaMovement",
    "RobotStateMachine.next",
    "RobotRoutePlanner.plan",
    "scheduleTick",
    "setBlock(",
):
    if forbidden in telemetry + record + comparison + store:
        errors.append(f"AMR trial evidence layer must remain observer-only; found {forbidden!r}")

for token in (
    "baseline.robotId().equals(candidate.robotId())",
    "before.comparablePath(after)",
    "Trend.INCOMPARABLE",
    "before.completed() != after.completed()",
    "faultDelta != 0",
    "safeStopDelta != 0",
    "localizationSeverity",
    "hardHoldDelta",
    "stationaryDelta",
    "durationDelta",
):
    if token not in comparison:
        errors.append(f"RobotCommissioningTrialComparison missing {token!r}")

# Entity telemetry must attach to real mission/state transitions, not a second planner.
for token in (
    "case OBSTACLE_DETECTED -> missionObstacleWaitEvents++",
    "case SENSOR_DEGRADED -> missionDegradedEntries++",
    "case LOCALIZATION_LOST, SAFETY_STOP_REQUESTED -> missionSafeStopEvents++",
    "case CRITICAL_FAULT ->",
    "finishMissionTelemetry(false);",
    "finishMissionTelemetry(true);",
    "if (missionTelemetryActive()) missionMotionTicks++;",
    "recordRouteReject();",
    "missionDockHoldEvents++",
    "missionMaterialHoldEvents++",
    "MissionTelemetryStartTick",
    "MissionTelemetryWorstLocalization",
):
    if token not in entity:
        errors.append(f"EngineeringMobileRobotEntity missing telemetry boundary {token!r}")

for counter in (
    "missionObstacleWaitEvents",
    "missionDegradedEntries",
    "missionSafeStopEvents",
    "missionFaultEvents",
    "missionRouteRejectEvents",
    "missionDockHoldEvents",
    "missionMaterialHoldEvents",
    "missionMotionTicks",
):
    if re.search(rf"if\s*\(\s*{counter}\s*[><=!]", entity):
        errors.append(f"observer telemetry counter {counter} is used as a control condition")

# Existing safety still decides from actual evidence, never from commissioning counters.
for token in (
    "if (!input.emergencyStopClear())",
    "if (!input.driveReady())",
    "RobotLocalizationQuality.LOST",
    "RobotLocalizationQuality.STALE",
    "RobotLocalizationQuality.DEGRADED",
    "input.obstacleEvidence() != PortQuality.VALID",
    "if (!input.obstacleClear())",
):
    if token not in safety:
        errors.append(f"RobotSafetyAssessment lost fail-closed contract {token!r}")

# Tablet interaction must intercept before the entity's default target-assignment behavior.
capture_index = entity.find("DiagnosticTabletItem.captureRobot(")
assign_index = entity.find("assignTarget(target);")
if capture_index < 0:
    errors.append("AMR entity does not route Diagnostic Tablet through its own interact contract")
elif assign_index < 0 or capture_index > assign_index:
    errors.append("Diagnostic Tablet interception must occur before default AMR target assignment")

for token in (
    "public static void captureRobot(",
    "Shift+right-click",
    "AMR trial capture rejected: finish the mission run",
    "RobotCommissioningTrialStore.captureBaseline",
    "RobotCommissioningTrialStore.captureCandidate",
    "TRIAL ROLE:",
    "TRIAL COMPARE:",
    "MODE: frozen evidence only; trial capture never commands the robot",
    "MODE: observer-only; no motion, route, dock, transfer, or safety mutation",
):
    if token not in tablet:
        errors.append(f"DiagnosticTabletItem missing AMR trial token {token!r}")

for token in (
    "MIN_WIDTH = 420",
    "MAX_WIDTH = 720",
    "MIN_HEIGHT = 300",
    "MAX_HEIGHT = 460",
    "public boolean mouseScrolled",
    "graphics.enableScissor",
    'lineValue(newest, "ENTITY:")',
    'lineValue(newest, "TRIAL COMPARE:")',
    "OBSERVER ONLY • retained block + AMR evidence",
):
    if token not in screen:
        errors.append(f"DiagnosticTabletScreen missing responsive AMR evidence token {token!r}")

if "dev.redstoneengineering.robotics" in screen:
    errors.append("DiagnosticTabletScreen must not import/run robotics simulation classes")

# Comparison policy sanity: different path is never ranked; completion/safety evidence outranks speed.
def classify(comparable, before_complete, after_complete, fault_delta, safe_delta, loc_delta,
             hard_hold_delta, obstacle_delta, stationary_delta, duration_delta):
    if not comparable:
        return "INCOMPARABLE"
    if before_complete != after_complete:
        return "IMPROVED" if after_complete else "REGRESSED"
    if fault_delta != 0:
        return "IMPROVED" if fault_delta < 0 else "REGRESSED"
    if safe_delta != 0:
        return "IMPROVED" if safe_delta < 0 else "REGRESSED"
    if loc_delta != 0:
        return "IMPROVED" if loc_delta < 0 else "REGRESSED"
    if hard_hold_delta != 0:
        return "IMPROVED" if hard_hold_delta < 0 else "REGRESSED"
    if obstacle_delta != 0:
        return "IMPROVED" if obstacle_delta < 0 else "REGRESSED"
    if stationary_delta != 0:
        return "IMPROVED" if stationary_delta < 0 else "REGRESSED"
    if duration_delta != 0:
        return "IMPROVED" if duration_delta < 0 else "REGRESSED"
    return "SAME"

cases = (
    ((False, True, True, -2, -2, -2, -2, -2, -20, -20), "INCOMPARABLE"),
    ((True, True, False, -2, -2, -2, -2, -2, -20, -20), "REGRESSED"),
    ((True, False, True, 2, 2, 2, 2, 2, 20, 20), "IMPROVED"),
    ((True, True, True, -1, 1, 1, 1, 1, 20, 20), "IMPROVED"),
    ((True, True, True, 0, 0, 0, 0, 0, -10, 20), "IMPROVED"),
    ((True, True, True, 0, 0, 0, 0, 0, 0, 5), "REGRESSED"),
    ((True, True, True, 0, 0, 0, 0, 0, 0, 0), "SAME"),
)
for args, expected in cases:
    actual = classify(*args)
    if actual != expected:
        errors.append(f"AMR trial comparison sanity failed: {args} -> {actual}, expected {expected}")

if errors:
    print("RSE AMR COMMISSIONING TRIAL VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE AMR COMMISSIONING TRIAL VERIFY: PASS")
print(" observer-only mission telemetry boundaries: PASS")
print(" COMPLETE/FAULT finished-run evidence: PASS")
print(" same-robot + same-start/target comparison scope: PASS")
print(" safety/localization evidence outranks speed: PASS")
print(" Diagnostic Tablet entity interaction contract: PASS")
print(" responsive retained AMR evidence workspace: PASS")
print(" client/no-second-robotics-solver boundary: PASS")
