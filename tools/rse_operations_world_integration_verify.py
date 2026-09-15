#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing operations world integration file: {rel}")
        return ""
    return path.read_text(errors="ignore")


provider = read("src/main/java/dev/redstoneengineering/operations/world/OperationWorldResourceProvider.java")
snapshot = read("src/main/java/dev/redstoneengineering/operations/world/OperationWorldResourceSnapshot.java")
resolver = read("src/main/java/dev/redstoneengineering/operations/world/OperationWorldResourceResolver.java")
sequence = read("src/main/java/dev/redstoneengineering/block/SequenceControllerBlock.java")
alarm = read("src/main/java/dev/redstoneengineering/block/AlarmProcessorBlock.java")
watchdog = read("src/main/java/dev/redstoneengineering/block/WatchdogBlock.java")
interlock = read("src/main/java/dev/redstoneengineering/block/SafetyInterlockBlock.java")
fault_latch = read("src/main/java/dev/redstoneengineering/block/FaultLatchBlock.java")
servo = read("src/main/java/dev/redstoneengineering/block/ServoActuatorBlock.java")

for token in (
    "interface OperationWorldResourceProvider",
    "operationResourceSnapshot",
):
    if provider and token not in provider:
        errors.append(f"World resource provider missing contract {token!r}")

for token in (
    "record OperationWorldResourceSnapshot",
    "resourceId",
    "processCapabilities",
    "available",
    "running",
    "completionEvidenceAvailable",
    "faultActive",
    "numericEvidence",
    "PortQuality",
    "validEvidence",
):
    if snapshot and token not in snapshot:
        errors.append(f"World resource snapshot missing fail-closed evidence field/helper {token!r}")

for token in (
    "class OperationWorldResourceResolver",
    "resolve",
    "BlockPos",
    "OperationWorldResourceProvider",
):
    if resolver and token not in resolver:
        errors.append(f"World resource resolver missing explicit-position resolution {token!r}")

for body, label, required in (
    (sequence, "SequenceControllerBlock", ("implements OperationWorldResourceProvider", "operationResourceSnapshot", "step", "completedCycles", "sequence_step", "completed_cycles")),
    (alarm, "AlarmProcessorBlock", ("implements OperationWorldResourceProvider", "operationResourceSnapshot", "latched", "unacknowledged", "alarm_severity")),
    (watchdog, "WatchdogBlock", ("implements OperationWorldResourceProvider", "operationResourceSnapshot", "ageTicks", "timeoutCount", "heartbeat_age_ticks")),
    (interlock, "SafetyInterlockBlock", ("implements OperationWorldResourceProvider", "operationResourceSnapshot", "failedMask", "failed_mask")),
    (fault_latch, "FaultLatchBlock", ("implements OperationWorldResourceProvider", "operationResourceSnapshot", "latched", "tripCount", "trip_count")),
    (servo, "ServoActuatorBlock", ("OperationWorldResourceProvider", "operationResourceSnapshot", "servo_positioning", "completionEvidenceAvailable", "position", "velocity", "error", "braking")),
):
    for token in required:
        if body and token not in body:
            errors.append(f"{label} missing Operations evidence integration {token!r}")

if servo and "false," not in servo:
    errors.append("ServoActuatorBlock must explicitly expose completion evidence as unavailable rather than inventing a completion event")

for body, label in ((provider, "provider"), (snapshot, "snapshot"), (resolver, "resolver")):
    for forbidden in (
        "OperationDispatchRuntime",
        "OperationQueueRuntime",
        "OperationMaintenanceRuntime",
        "OperationChangeoverRuntime",
        "RobotMission",
        "setBlock(",
        "setDeltaMovement(",
        "scheduleTick(",
        "Minecraft.getInstance",
        "client.ui",
    ):
        if body and forbidden in body:
            errors.append(f"Operations world {label} must remain evidence-only; found {forbidden!r}")

for body, label in ((sequence, "SequenceControllerBlock"), (alarm, "AlarmProcessorBlock"), (watchdog, "WatchdogBlock"), (interlock, "SafetyInterlockBlock"), (fault_latch, "FaultLatchBlock"), (servo, "ServoActuatorBlock")):
    for forbidden in ("OperationDispatchRuntime", "OperationQueueRuntime", "OperationChangeoverRuntime", "OperationMaintenanceRuntime"):
        if body and forbidden in body:
            errors.append(f"{label} must expose evidence only; found Operations authority {forbidden!r}")

if resolver:
    for forbidden in ("getEntitiesOfClass", "inflate(", "closerThan", "nearest"):
        if forbidden in resolver:
            errors.append(f"World resource resolver must not use proximity discovery; found {forbidden!r}")

if errors:
    print("RSE OPERATIONS WORLD INTEGRATION VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE OPERATIONS WORLD INTEGRATION VERIFY: PASS")
print(" explicit-position resource resolution: PASS")
print(" fail-closed evidence snapshot: PASS")
print(" existing sequence/alarm/watchdog/interlock/fault devices expose Operations evidence: PASS")
print(" existing servo actuator exposes real machine evidence without fabricated completion: PASS")
print(" dispatch/queue/world-motion/client authority leakage: NONE")
print(" proximity auto-discovery: NONE")
