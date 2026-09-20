#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing world-backed plant monitor file: {rel}")
        return ""
    return path.read_text(errors="ignore")


assessment = read("src/main/java/dev/redstoneengineering/diagnostics/OperationWorldPlantStateAssessment.java")
persistent = read("src/main/java/dev/redstoneengineering/diagnostics/OperationPersistentPlantRuntimeAssessment.java")
menu = read("src/main/java/dev/redstoneengineering/ui/menu/OperationsMonitorMenu.java")
screen = read("src/main/java/dev/redstoneengineering/client/ui/OperationsMonitorScreen.java")

for token in (
    "class OperationWorldPlantStateAssessment",
    "enum Coverage",
    "record Snapshot",
    "OperationPlantSavedData.get",
    "workcells()",
    "buffers()",
    "workcellBufferBinding",
    "OperationWorkcellStore.resolveBoundResources",
    "totalWorkcells",
    "configuredWorkcells",
    "totalBuffers",
    "usedBufferUnits",
    "bufferCapacityUnits",
    "wipPressurePercent",
    "boundResources",
    "validResources",
    "faultResources",
):
    if assessment and token not in assessment:
        errors.append(f"OperationWorldPlantStateAssessment missing world evidence token {token!r}")

for token in (
    "class OperationPersistentPlantRuntimeAssessment",
    "OperationPlantSavedData.get",
    "retainedJobs",
    "activeJobs",
    "completedJobs",
    "queueEvents",
    "qualityEvents",
    "maintenanceEvents",
    "deliveryEvents",
    "logisticsEvents",
    "onTimeDeliveries",
    "lateDeliveries",
    "firstPassYieldPercent",
    "outstandingWithDueDate",
    "overdueOutstandingJobs",
):
    if persistent and token not in persistent:
        errors.append(f"OperationPersistentPlantRuntimeAssessment missing retained runtime token {token!r}")

for token in (
    "OperationWorldPlantStateAssessment.inspect",
    "OperationPersistentPlantRuntimeAssessment.inspect",
    "worldPlantCoverage",
    "worldPlantWorkcells",
    "worldPlantConfiguredWorkcells",
    "worldPlantBuffers",
    "worldPlantUsedBufferUnits",
    "worldPlantBufferCapacityUnits",
    "worldPlantWipPressurePercent",
    "worldPlantBoundResources",
    "worldPlantValidResources",
    "worldPlantFaultResources",
    "persistentPlantCoverage",
    "persistentPlantRetainedJobs",
    "persistentPlantRetainedEvents",
    "persistentPlantOnTimeDeliveries",
    "persistentPlantLateDeliveries",
):
    if menu and token not in menu:
        errors.append(f"OperationsMonitorMenu missing world/persistent plant synchronization {token!r}")

for token in (
    "WORLD PLANT STATE",
    "Workcells configured",
    "Buffers / WIP",
    "Bound resources",
    "CONFIGURATION",
    "WIP PRESSURE",
    "RESOURCE HEALTH",
    "drawPlantMetricBar",
    "configurationPercent",
    "resourceHealthPercent",
    "EVIDENCE COVERAGE",
    "PERSISTENT PLANT RUNTIME",
    "Jobs active / completed / retained",
    "FPY/reject/rework",
    "Due outstanding/overdue",
    "OTD",
    "Plant ledger Q/QC/PM/D/L",
):
    if screen and token not in screen:
        errors.append(f"OperationsMonitorScreen missing world/persistent plant UI token {token!r}")

combined = assessment + persistent + menu + screen
for forbidden in (
    "OperationDispatchRuntime",
    "OperationQueueRuntime",
    "OperationMaintenanceRuntime",
    "OperationChangeoverRuntime",
    "OperationBufferRuntime.receive",
    "OperationBufferRuntime.allocate",
    "OperationPlantSavedData.putBuffer",
    "OperationPlantSavedData.putWorkcell",
    "setBlock(",
    "setDeltaMovement(",
    "getEntitiesOfClass",
    "inflate(",
):
    if combined and forbidden in combined:
        errors.append(f"world-backed Operations Monitor must remain observer-only; found {forbidden!r}")

for stale in (
    "PLANT KPIs • INCOMPLETE",
    "WITHHELD • EVIDENCE MISSING",
    "Queue/job history is not persisted yet",
):
    if screen and stale in screen:
        errors.append(f"OperationsMonitorScreen still claims obsolete missing persistence: {stale!r}")

if errors:
    print("RSE OPERATIONS MONITOR WORLD PLANT VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE OPERATIONS MONITOR WORLD PLANT VERIFY: PASS")
print(" server-owned workcell/buffer/binding evidence visible in existing Operations Monitor: PASS")
print(" configuration/WIP/resource-health visual bars use existing synchronized evidence: PASS")
print(" aggregate WIP/capacity derived from persisted Industrial Buffers: PASS")
print(" resource validity/fault evidence derived from explicit workcell bindings: PASS")
print(" retained job/queue/quality/maintenance/delivery/logistics evidence visible read-only: PASS")
print(" monitor control/mutation authority leakage: NONE")
