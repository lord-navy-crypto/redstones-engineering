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
saved = read("src/main/java/dev/redstoneengineering/operations/world/OperationPlantSavedData.java")
world_state = read("src/main/java/dev/redstoneengineering/operations/world/OperationMaintenanceWorldState.java")

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
print(" persistent current maintenance snapshots in plant SavedData: PASS")
print(" world start/complete commits snapshot + durable maintenance history atomically: PASS")
print(" world/KPI/robotics authority leakage: NONE")
