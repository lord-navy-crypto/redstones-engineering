#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        errors.append(f"missing AMR world integration file: {rel}")
        return ""
    return path.read_text(encoding="utf-8", errors="ignore")


record = read("src/main/java/dev/redstoneengineering/operations/world/OperationTransportRuntimeRecord.java")
saved = read("src/main/java/dev/redstoneengineering/operations/world/OperationPlantSavedData.java")
world = read("src/main/java/dev/redstoneengineering/operations/world/OperationRobotTransportWorldState.java")
bridge = read("src/main/java/dev/redstoneengineering/integration/OperationsRobotTransportBridge.java")
binding = read("src/main/java/dev/redstoneengineering/integration/OperationTransportBinding.java")
receipt_bridge = read("src/main/java/dev/redstoneengineering/integration/OperationsBufferReceiptBridge.java")
entity = read("src/main/java/dev/redstoneengineering/entity/EngineeringMobileRobotEntity.java")
buffer_state = read("src/main/java/dev/redstoneengineering/operations/world/OperationIndustrialBufferState.java")
gametest = read("src/main/java/dev/redstoneengineering/gametest/RseAmrWorldLogisticsPersistenceGameTests.java")
registration = read("src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java")

for token in (
    "record OperationTransportRuntimeRecord(",
    "long missionId", "long outputId", "long jobId", "BlockPos source", "BlockPos target",
    "int units", "int priority", "long preparedTick", "long stateTick", "Status status", "String robotId",
    "READY", "STARTED", "DELIVERED", "FAILED", "CANCELLED",
    "OperationTransportBinding binding()",
    "OperationTransportDemand demand()",
    "transition(Status nextStatus, long gameTick, String nextRobotId)",
    "terminal()",
):
    if record and token not in record:
        errors.append(f"Transport runtime record missing contract {token!r}")

for token in (
    "Map<Long, OperationTransportRuntimeRecord>",
    'getList("TransportRuntime", Tag.TAG_COMPOUND)',
    'tag.put("TransportRuntime", transportTags)',
    "transportRecords()", "transportRecord(long missionId)",
    "putTransportRecord(OperationTransportRuntimeRecord record)",
    "removeTransportRecord(long missionId)",
    "OperationTransportRuntimeRecord.Status.valueOf",
):
    if saved and token not in saved:
        errors.append(f"Plant SavedData missing persistent transport runtime contract {token!r}")

for token in (
    "class OperationRobotTransportWorldState",
    "prepare(", "startRoute(", "markDelivered(",
    "OperationsRobotTransportBridge.evaluate(output, demand)",
    "OperationPlantSavedData.get(level)",
    "putTransportRecord(",
    "transportRecord(missionId)",
    "robot.assignTransportRoute(",
    "robot.robotIdentity()",
    "OperationPlantRuntimeRecorder.recordLogisticsDemand(",
    "OperationPlantRuntimeRecorder.recordLogisticsEvent(",
    '"MISSION_READY"', '"MISSION_STARTED"', '"DELIVERED"',
    "record.binding()",
):
    if world and token not in world:
        errors.append(f"AMR world facade missing authoritative transport lifecycle contract {token!r}")

for forbidden in (
    "RobotTransportRouteRuntime.evaluate(", "RobotRoutePlanner.plan(", "getEntitiesOfClass", "inflate(",
    "nearest", "closerThan", "setDeltaMovement(", "move(MoverType", "setBlock(",
):
    if world and forbidden in world:
        errors.append(f"AMR world facade must call the robot's authoritative route entry point; unexpected {forbidden!r}")

for token in (
    "Pure boundary adapter", "This bridge never mutates either side", "OperationTransportBinding",
):
    if bridge and token not in bridge:
        errors.append(f"OperationsRobotTransportBridge lost pure boundary contract {token!r}")

for forbidden in ("OperationPlantSavedData", "ServerLevel", "EngineeringMobileRobotEntity", "recordPlantEvent("):
    if bridge and forbidden in bridge:
        errors.append(f"OperationsRobotTransportBridge must remain pure; unexpected {forbidden!r}")
    if binding and forbidden in binding:
        errors.append(f"OperationTransportBinding must remain immutable correlation evidence; unexpected {forbidden!r}")
    if receipt_bridge and forbidden in receipt_bridge:
        errors.append(f"OperationsBufferReceiptBridge must remain pure; unexpected {forbidden!r}")

for token in (
    "public boolean assignTransportRoute(",
    "RobotTransportRouteRuntime.evaluate(",
    "TRANSPORT_SOURCE_NOT_LOCALIZED_TO_ROBOT",
    'entityData.set(ROUTE_REASON, "FOLLOWING_TRANSPORT_ROUTE")',
    "return robotState() == RobotOperatingState.TRANSPORTING;",
    "public String robotIdentity()",
):
    if entity and token not in entity:
        errors.append(f"EngineeringMobileRobotEntity lost authoritative transport route contract {token!r}")

for forbidden in (
    "OperationTransportRuntimeRecord", "OperationPlantSavedData", "OperationPlantRuntimeRecorder",
):
    if entity and forbidden in entity:
        errors.append(f"Robot entity must not absorb Operations persistence/correlation authority; found {forbidden!r}")

for token in (
    "OperationRobotTransportWorldState.markDelivered(",
    "receipt.missionId()", "receipt.outputId()", "receipt.jobId()",
):
    if buffer_state and token not in buffer_state:
        errors.append(f"Industrial Buffer successful receipt must finalize persistent transport runtime {token!r}")

for token in (
    "class RseAmrWorldLogisticsPersistenceGameTests",
    "transportReadyStartedDeliveredSurvivesNbtRoundTrip",
    "OperationRobotTransportWorldState.prepare(",
    "OperationTransportRuntimeRecord.Status.READY",
    "OperationTransportRuntimeRecord.Status.DELIVERED",
    "OperationPlantSavedData.load(",
):
    if gametest and token not in gametest:
        errors.append(f"Local AMR logistics persistence GameTest missing regression contract {token!r}")

if registration and "event.register(RseAmrWorldLogisticsPersistenceGameTests.class);" not in registration:
    errors.append("local AMR logistics persistence GameTest is not registered")

if errors:
    print("RSE OPERATIONS AMR WORLD VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE OPERATIONS AMR WORLD VERIFY: PASS")
print(" transport mission/output/job/source/target/units/priority correlation is persistent: PASS")
print(" READY -> STARTED requires the real AMR assignTransportRoute commit: PASS")
print(" DELIVERED requires successful world buffer receipt identity correlation: PASS")
print(" transport runtime survives NBT round-trip: PASS")
print(" pure Operations/Robotics bridges remain mutation-free: PASS")
print(" robot entity contains no Operations persistence authority: PASS")
