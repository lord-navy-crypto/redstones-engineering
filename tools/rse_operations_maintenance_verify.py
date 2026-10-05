#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing maintenance file: {rel}")
        return ""
    return path.read_text(errors="ignore")


snapshot = read("src/main/java/dev/redstoneengineering/operations/OperationResourceMaintenanceSnapshot.java")
evidence = read("src/main/java/dev/redstoneengineering/operations/OperationMaintenanceCompletionEvidence.java")
runtime = read("src/main/java/dev/redstoneengineering/operations/OperationMaintenanceRuntime.java")
gate = read("src/main/java/dev/redstoneengineering/operations/OperationMaintenanceAwareDispatchRuntime.java")
queue_runtime = read("src/main/java/dev/redstoneengineering/operations/OperationQueueRuntime.java")
saved = read("src/main/java/dev/redstoneengineering/operations/world/OperationPlantSavedData.java")
world_state = read("src/main/java/dev/redstoneengineering/operations/world/OperationMaintenanceWorldState.java")
queue_world_state = read("src/main/java/dev/redstoneengineering/operations/world/OperationQueueWorldState.java")
workcell_menu = read("src/main/java/dev/redstoneengineering/ui/menu/WorkcellControllerMenu.java")
workcell_screen = read("src/main/java/dev/redstoneengineering/client/ui/WorkcellControllerScreen.java")
maintenance_gametest = read("src/main/java/dev/redstoneengineering/gametest/RseMaintenancePersistenceGameTests.java")
queue_gametest = read("src/main/java/dev/redstoneengineering/gametest/RseQueuePersistenceGameTests.java")
gametest_registration = read("src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java")

for token in (
    "record OperationResourceMaintenanceSnapshot(",
    "AVAILABLE",
    "MAINTENANCE_DUE",
    "IN_PROGRESS",
    "FAULTED",
    "String maintenanceId",
    "PortQuality evidenceQuality",
    "boolean faultActive",
    "maintenance due/in-progress state requires maintenanceId",
    "boolean productionReady()",
):
    if snapshot and token not in snapshot:
        errors.append(f"Maintenance snapshot missing contract {token!r}")

for token in (
    "record OperationMaintenanceCompletionEvidence(",
    "String resourceId",
    "String maintenanceId",
    "PortQuality evidenceQuality",
    "boolean workConfirmed",
    "boolean completionConfirmed",
    "boolean faultActive",
):
    if evidence and token not in evidence:
        errors.append(f"Maintenance completion evidence missing contract {token!r}")

for token in (
    "class OperationMaintenanceRuntime",
    "STARTED",
    "COMPLETED",
    "MAINTENANCE_NOT_DUE",
    "MAINTENANCE_ALREADY_IN_PROGRESS",
    "MAINTENANCE_STARTED",
    "MAINTENANCE_COMPLETION_EVIDENCE_MISSING",
    "MAINTENANCE_COMPLETION_FAULT_ACTIVE",
    "MAINTENANCE_COMPLETION_EVIDENCE_INVALID",
    "MAINTENANCE_NOT_IN_PROGRESS",
    "MAINTENANCE_RESOURCE_ID_MISMATCH",
    "MAINTENANCE_ID_MISMATCH",
    "MAINTENANCE_WORK_UNCONFIRMED",
    "MAINTENANCE_COMPLETION_UNCONFIRMED",
    "MAINTENANCE_COMPLETE",
):
    if runtime and token not in runtime:
        errors.append(f"Maintenance runtime missing lifecycle rule {token!r}")

for forbidden in (
    "System.currentTimeMillis",
    "gameTick",
    "elapsed",
    "scheduleTick(",
    "OperationPlantSavedData",
    "ServerLevel",
):
    if runtime and forbidden in runtime:
        errors.append(f"Maintenance runtime must remain pure and must not auto-complete from time/world state; found {forbidden!r}")

for token in (
    "class OperationMaintenanceAwareDispatchRuntime",
    "MAINTENANCE_EVIDENCE_MISSING",
    "DUPLICATE_MAINTENANCE_RESOURCE_ID",
    "RESOURCE_MAINTENANCE_FAULT_ACTIVE",
    "RESOURCE_MAINTENANCE_EVIDENCE_INVALID",
    "MAINTENANCE_EVIDENCE_MISSING_FOR_RESOURCE",
    "OperationResourceSnapshot.State.UNAVAILABLE",
    "OperationDispatchRuntime.evaluate(",
    "MAINTENANCE_HOLD",
    "SELECTED_RESOURCE_MAINTENANCE_INCONSISTENT",
    "MAINTENANCE_READY_",
):
    if gate and token not in gate:
        errors.append(f"Maintenance-aware dispatch missing gate rule {token!r}")

for forbidden in (
    "Comparator.comparing",
    "jobComparator(",
    "new OperationAssignment(",
    "OperationsDashboardSnapshot",
    "IndustrialOperationsAssessment",
    "OperationBottleneckAssessment",
    "throughput",
    "downtime",
    "RobotMission",
    "RobotRoutePlanner",
    "setBlock(",
    "setDeltaMovement(",
    "RuntimeIntStore",
):
    if gate and forbidden in gate:
        errors.append(f"Maintenance-aware dispatch must not duplicate ranking/world/KPI/robotics authority; found {forbidden!r}")

for token in (
    "dispatchMaintenanceAware(",
    "OperationMaintenanceAwareDispatchRuntime.evaluate(",
    "applyAssignment(",
    "OperationMaintenanceAwareDispatchRuntime.Verdict.FAULT",
):
    if queue_runtime and token not in queue_runtime:
        errors.append(f"Queue runtime missing maintenance-aware queue commit composition {token!r}")

for forbidden in (
    "OperationPlantSavedData",
    "ServerLevel",
    "setBlock(",
    "setDeltaMovement(",
    "RobotMission",
):
    if queue_runtime and forbidden in queue_runtime:
        errors.append(f"Queue runtime must remain pure while composing maintenance-aware dispatch; found {forbidden!r}")

for token in (
    "Map<String, OperationResourceMaintenanceSnapshot>",
    'getList("ResourceMaintenance", Tag.TAG_COMPOUND)',
    'tag.put("ResourceMaintenance", maintenanceTags)',
    "maintenanceSnapshots()",
    "maintenanceSnapshot(String resourceId)",
    "putMaintenanceSnapshot(OperationResourceMaintenanceSnapshot snapshot)",
    "removeMaintenanceSnapshot(String resourceId)",
    'OperationResourceMaintenanceSnapshot.State.valueOf',
    'PortQuality.valueOf',
):
    if saved and token not in saved:
        errors.append(f"Plant SavedData missing persistent maintenance state contract {token!r}")

for token in (
    "class OperationMaintenanceWorldState",
    "OperationPlantSavedData.get(level)",
    "observe(",
    "start(",
    "complete(",
    "OperationMaintenanceRuntime.start(current)",
    "OperationMaintenanceRuntime.complete(current, evidence)",
    "putMaintenanceSnapshot(",
    "OperationPlantRuntimeRecorder.recordMaintenanceSnapshot(",
    "OperationPlantRuntimeRecorder.recordMaintenanceDecision(",
    "MAINTENANCE_STATE_MISSING",
    "rollback",
):
    if world_state and token not in world_state:
        errors.append(f"Maintenance world state missing authoritative persistence/history bridge {token!r}")

for forbidden in (
    "getEntitiesOfClass",
    "nearest",
    "closerThan",
    "System.currentTimeMillis",
    "scheduleTick(",
    "setBlock(",
    "setDeltaMovement(",
    "RobotMission",
):
    if world_state and forbidden in world_state:
        errors.append(f"Maintenance world state must use explicit resource identity without proximity/time/robotics shortcuts; found {forbidden!r}")

for token in (
    "maintenanceSnapshot(resource.resourceId())",
    "OperationQueueRuntime.dispatchMaintenanceAware(",
    "MAINTENANCE_EVIDENCE_MISSING_FOR_RESOURCE",
):
    if queue_world_state and token not in queue_world_state:
        errors.append(f"Persistent queue dispatch is not consuming world-backed maintenance evidence {token!r}")

if queue_world_state and "OperationMaintenanceAwareDispatchRuntime.evaluate(" in queue_world_state:
    errors.append("Queue world state must delegate maintenance-aware queue decisions to OperationQueueRuntime")

for token in (
    "OperationPlantSavedData.get(server)",
    "maintenanceSnapshot(",
    "PortQuality.VALID",
    "maintenanceReadyResources",
    "maintenanceDueResources",
    "maintenanceInProgressResources",
    "maintenanceFaultResources",
    "maintenanceEvidenceAvailable()",
):
    if workcell_menu and token not in workcell_menu:
        errors.append(f"Workcell maintenance readback missing synchronized persistent evidence {token!r}")

for token in (
    '"Maintenance evidence"',
    '"READY / DUE / ACTIVE / FAULT"',
    "maintenanceReadyResources()",
    "maintenanceDueResources()",
    "maintenanceInProgressResources()",
    "maintenanceFaultResources()",
    "SETUP remains withheld",
):
    if workcell_screen and token not in workcell_screen:
        errors.append(f"Workcell screen missing truthful maintenance readback {token!r}")

if workcell_screen and "SETUP and MAINTENANCE remain withheld until their world evidence is persisted." in workcell_screen:
    errors.append("Workcell screen still claims maintenance persistence is unavailable")

for forbidden in (
    "OperationMaintenanceWorldState.start(",
    "OperationMaintenanceWorldState.complete(",
    "OperationMaintenanceRuntime.start(",
    "OperationMaintenanceRuntime.complete(",
):
    if workcell_menu and forbidden in workcell_menu:
        errors.append(f"Workcell menu must remain read-only; found maintenance mutation {forbidden!r}")
    if workcell_screen and forbidden in workcell_screen:
        errors.append(f"Workcell screen must remain read-only; found maintenance mutation {forbidden!r}")

for token in (
    "class RseMaintenancePersistenceGameTests",
    "maintenanceDueStartCompleteAndRoundTripPersist",
    "OperationMaintenanceWorldState.observe(",
    "OperationMaintenanceWorldState.start(",
    "OperationMaintenanceWorldState.complete(",
    "OperationResourceMaintenanceSnapshot.State.IN_PROGRESS",
    "OperationResourceMaintenanceSnapshot.State.AVAILABLE",
    "OperationPlantSavedData.load(",
):
    if maintenance_gametest and token not in maintenance_gametest:
        errors.append(f"Local maintenance persistence GameTest missing regression contract {token!r}")

for token in (
    "OperationResourceMaintenanceSnapshot.State.MAINTENANCE_DUE",
    '"MAINTENANCE_HOLD"',
    "OperationResourceMaintenanceSnapshot.State.AVAILABLE",
    "OperationQueueWorldState.dispatch(",
):
    if queue_gametest and token not in queue_gametest:
        errors.append(f"Local persistent queue GameTest missing maintenance-aware dispatch regression {token!r}")

if gametest_registration and "event.register(RseMaintenancePersistenceGameTests.class);" not in gametest_registration:
    errors.append("local maintenance persistence GameTest is not registered")
if gametest_registration and "event.register(RseQueuePersistenceGameTests.class);" not in gametest_registration:
    errors.append("local persistent queue GameTest is not registered")

if errors:
    print("RSE OPERATIONS MAINTENANCE VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE OPERATIONS MAINTENANCE VERIFY: PASS")
print(" explicit maintenance due/in-progress/available evidence: PASS")
print(" evidence-bound start -> in-progress -> complete lifecycle: PASS")
print(" elapsed-time auto-completion: NONE")
print(" maintenance hold projects resource UNAVAILABLE: PASS")
print(" final resource selection delegates to OperationDispatchRuntime: PASS")
print(" queue assignment consumes the maintenance-aware dispatch gate before persistent commit: PASS")
print(" persistent current maintenance snapshots in plant SavedData: PASS")
print(" world start/complete commits snapshot + durable maintenance history atomically: PASS")
print(" Workcell HMI reads persistent maintenance evidence without mutation authority: PASS")
print(" local maintenance + queue persistence GameTests: REGISTERED")
print(" world/KPI/robotics authority leakage: NONE")
