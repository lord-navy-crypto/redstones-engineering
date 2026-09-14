#!/usr/bin/env python3
"""Static regression gate for the first RSE Robotics semantic layer."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
ROBOTICS = ROOT / "src/main/java/dev/redstoneengineering/robotics"
FILES = {
    "state": ROBOTICS / "RobotOperatingState.java",
    "mission": ROBOTICS / "RobotMission.java",
    "localization": ROBOTICS / "RobotLocalizationQuality.java",
    "safety": ROBOTICS / "RobotSafetyAssessment.java",
    "snapshot": ROBOTICS / "MobileRobotSnapshot.java",
    "machine": ROBOTICS / "RobotStateMachine.java",
}
errors = []

def read(path):
    if not path.exists():
        errors.append(f"missing {path.relative_to(ROOT)}")
        return ""
    return path.read_text(encoding="utf-8")

def req(text, needle, label):
    if needle not in text:
        errors.append(f"{label}: missing {needle!r}")

src = {name: read(path) for name, path in FILES.items()}

for needle in (
    "IDLE", "MISSION_ASSIGNED", "PLANNING", "NAVIGATING", "WAITING", "REPLANNING",
    "DOCKING", "LOADING", "TRANSPORTING", "TRANSPORT_WAITING", "TRANSPORT_REPLANNING",
    "UNLOADING", "RETURNING", "COMPLETE", "DEGRADED", "SAFE_STOP", "FAULT",
    "case NAVIGATING, REPLANNING, DOCKING, TRANSPORTING, TRANSPORT_REPLANNING, RETURNING, DEGRADED -> true;",
):
    req(src["state"], needle, "RobotOperatingState.java")

for needle in (
    "DELIVERY", "TRANSFER", "INSPECTION", "RETURN_HOME",
    "BlockPos source", "BlockPos target", "int priority", "int payloadUnits",
):
    req(src["mission"], needle, "RobotMission.java")

for needle in (
    "VALID", "DEGRADED", "LOST", "STALE",
    "case NO_SIGNAL, FAULT, DOMAIN_MISMATCH, TOPOLOGY_ERROR -> LOST;",
    "case SATURATED -> DEGRADED;",
):
    req(src["localization"], needle, "RobotLocalizationQuality.java")

for needle in (
    "E_STOP_ACTIVE", "DRIVE_NOT_READY", "LOCALIZATION_LOST", "LOCALIZATION_STALE",
    "LOCALIZATION_DEGRADED", "OBSTACLE_EVIDENCE_", "OBSTACLE_UNSAFE", "MOTION_PERMIT",
    "if (verdict != Verdict.PERMIT) motionPermit = false;",
):
    req(src["safety"], needle, "RobotSafetyAssessment.java")

for needle in (
    "RobotOperatingState state", "Optional<RobotMission> mission", "RobotLocalizationQuality localizationQuality",
    "PortQuality frontRangeQuality", "RobotSafetyAssessment.Snapshot safety",
    "return safety.motionPermit() && state.motionCapable();",
):
    req(src["snapshot"], needle, "MobileRobotSnapshot.java")

for needle in (
    "ASSIGN_MISSION", "BEGIN_PLANNING", "ROUTE_READY", "OBSTACLE_DETECTED", "ROUTE_UNAVAILABLE",
    "LOCALIZATION_LOST", "SAFETY_STOP_REQUESTED", "SENSOR_DEGRADED", "SAFE_CONDITION_RESTORED",
    "CRITICAL_FAULT", "RESET_FAULT",
    "if (event == Event.CRITICAL_FAULT) return RobotOperatingState.FAULT;",
    "event == Event.LOCALIZATION_LOST || event == Event.SAFETY_STOP_REQUESTED",
    "return RobotOperatingState.SAFE_STOP;",
    "if (event == Event.SENSOR_DEGRADED) return RobotOperatingState.DEGRADED;",
    "case IDLE -> event == Event.ASSIGN_MISSION ? RobotOperatingState.MISSION_ASSIGNED : current;",
    "case SAFE_STOP -> event == Event.SAFE_CONDITION_RESTORED ? RobotOperatingState.REPLANNING : current;",
    "case TRANSPORTING -> switch (event)",
    "case OBSTACLE_DETECTED -> RobotOperatingState.TRANSPORT_WAITING;",
    "case ROUTE_UNAVAILABLE -> RobotOperatingState.TRANSPORT_REPLANNING;",
    "case TRANSPORT_WAITING -> switch (event)",
    "case OBSTACLE_CLEARED -> RobotOperatingState.TRANSPORTING;",
    "case TRANSPORT_REPLANNING -> event == Event.REPLAN_READY ? RobotOperatingState.TRANSPORTING : current;",
):
    req(src["machine"], needle, "RobotStateMachine.java")

# Transport hold states must not collapse back to generic navigation recovery.
for forbidden in (
    "case TRANSPORTING -> switch (event) {\n                case OBSTACLE_DETECTED -> RobotOperatingState.WAITING;",
    "case TRANSPORTING -> switch (event) {\n                case ROUTE_UNAVAILABLE -> RobotOperatingState.REPLANNING;",
):
    if forbidden in src["machine"]:
        errors.append("RobotStateMachine.java: transport hold path must preserve transport lifecycle")

# Foundation is semantic only: it must not own world mutation or fabricate navigation/motion yet.
for label, text in src.items():
    for forbidden in ("setBlock(", "scheduleTick(", "RuntimeIntStore.get(", "moveTo(", "setDeltaMovement("):
        if forbidden in text:
            errors.append(f"{FILES[label].name} must remain semantic/observer-safe; unexpected {forbidden!r}")

if errors:
    print("RSE ROBOTICS FOUNDATION VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    sys.exit(1)

print("RSE ROBOTICS FOUNDATION VERIFY: PASS")
print("  AMR lifecycle: mission -> planning -> navigation -> docking/transfer -> completion")
print("  transport obstacle/reroute holds preserve TRANSPORTING parent lifecycle")
print("  abnormal lifecycle: obstacle/wait, replanning, degraded, generic safe-stop, fault")
print("  localization loss remains explicit while generic safety holds preserve their own evidence reason")
print("  localization quality preserves VALID / DEGRADED / LOST / STALE semantics")
print("  safety permit requires localization + obstacle evidence + drive ready + E-stop clear")
print("  foundation is semantic-only; world motion/entity registration intentionally deferred")
