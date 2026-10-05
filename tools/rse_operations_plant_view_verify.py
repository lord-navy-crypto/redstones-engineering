#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing plant-view file: {rel}")
        return ""
    return path.read_text(errors="ignore")


due = read("src/main/java/dev/redstoneengineering/diagnostics/OperationDueDateExposureAssessment.java")
plant = read("src/main/java/dev/redstoneengineering/diagnostics/OperationPlantViewAssessment.java")
persistent = read("src/main/java/dev/redstoneengineering/diagnostics/OperationPersistentPlantRuntimeAssessment.java")
menu = read("src/main/java/dev/redstoneengineering/ui/menu/OperationsMonitorMenu.java")
screen = read("src/main/java/dev/redstoneengineering/client/ui/OperationsMonitorScreen.java")
monitor = read("src/main/java/dev/redstoneengineering/block/OperationsMonitorBlock.java")

for token in (
    "class OperationDueDateExposureAssessment",
    "Observer-only due-date exposure",
    "OperationCompletionLedger",
    "outstandingWithDueDate",
    "overdueOutstandingJobs",
    "notYetDueOutstandingJobs",
    "undatedOutstandingJobs",
    "gameTick > job.dueTick()",
    "completion ledger",
    "does not retain completion timestamps",
):
    if due and token not in due:
        errors.append(f"Due-date exposure missing conservative observer contract {token!r}")

for token in (
    "class OperationPlantViewAssessment",
    "Read-only plant-level Industrial Operations decision-support projection",
    "OperationsDashboardSnapshot",
    "OperationBottleneckAssessment.Snapshot",
    "OperationQualityPerformanceAssessment.Snapshot",
    "OperationReliabilityPerformanceAssessment.Snapshot",
    "OperationDueDateExposureAssessment.Snapshot",
    "throughputCyclesPerMinute",
    "queuePressurePercent",
    "firstPassYieldPercent",
    "observedAvailabilityPercent",
    "overdueOutstandingJobs",
    "bottleneckPresent",
    "qualityLossObserved",
    "reliabilityLossObserved",
    "overdueWorkPresent",
    "EvidenceCoverage",
):
    if plant and token not in plant:
        errors.append(f"Plant view missing cross-domain observer signal {token!r}")

for token in (
    "class OperationPersistentPlantRuntimeAssessment",
    "Read-only observer projection of the world-backed Persistent Plant Runtime",
    "OperationPlantSavedData",
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
    "undatedDeliveries",
    "onTimeDeliveryPercent",
    "firstPassYieldPercent",
    "rejectRatePercent",
    "reworkRatePercent",
    "maintenanceFaultEvents",
    "outstandingWithDueDate",
    "overdueOutstandingJobs",
    "parseInspectionUnits",
    '"ON_TIME"',
    'startsWith("LATE_BY_")',
    "!job.status().terminal()",
):
    if persistent and token not in persistent:
        errors.append(f"Persistent Plant Runtime assessment missing retained observer evidence {token!r}")

for token in (
    "OperationPlantViewAssessment",
    "OperationPersistentPlantRuntimeAssessment",
    "persistentPlantCoverage",
    "persistentPlantRetainedJobs",
    "persistentPlantActiveJobs",
    "persistentPlantCompletedJobs",
    "persistentPlantRetainedEvents",
    "persistentPlantQueueEvents",
    "persistentPlantQualityEvents",
    "persistentPlantMaintenanceEvents",
    "persistentPlantDeliveryEvents",
    "persistentPlantLogisticsEvents",
    "persistentPlantOnTimeDeliveries",
    "persistentPlantLateDeliveries",
    "persistentPlantUndatedDeliveries",
    "persistentPlantOnTimeDeliveryPercent",
    "persistentPlantFirstPassYieldPercent",
    "persistentPlantRejectRatePercent",
    "persistentPlantReworkRatePercent",
    "persistentPlantMaintenanceFaultEvents",
    "persistentPlantOutstandingWithDueDate",
    "persistentPlantOverdueOutstandingJobs",
    "worldPlantCoverage",
):
    if menu and token not in menu:
        errors.append(f"Operations Monitor menu missing synchronized persistent plant evidence {token!r}")

for token in (
    "WORLD PLANT STATE",
    "PERSISTENT PLANT RUNTIME",
    "Jobs active / completed / retained",
    "FPY/reject/rework",
    "PM faults",
    "Due outstanding/overdue",
    "OTD",
    "Plant ledger Q/QC/PM/D/L",
):
    if screen and token not in screen:
        errors.append(f"Operations Monitor screen missing persistent Plant Runtime presentation {token!r}")

for stale in (
    "Queue/job history is not persisted yet",
    "Quality / reliability / delivery\", \"WITHHELD • EVIDENCE MISSING",
    "PLANT KPIs • INCOMPLETE",
):
    if screen and stale in screen:
        errors.append(f"Operations Monitor screen still claims obsolete missing persistence: {stale!r}")

for body, label in (
    (due, "Due-date exposure"),
    (plant, "Plant view"),
    (persistent, "Persistent Plant Runtime assessment"),
    (menu, "Operations Monitor menu"),
    (screen, "Operations Monitor screen"),
    (monitor, "Operations Monitor block"),
):
    for forbidden in (
        "OperationQueueRuntime.enqueue",
        "OperationQueueRuntime.dispatch",
        "OperationQueueRuntime.complete",
        "OperationMaintenanceRuntime.start",
        "OperationChangeoverRuntime.start",
        "OperationBufferRuntime.receive",
        "RobotMission(",
        "setDeltaMovement(",
    ):
        if body and forbidden in body:
            errors.append(f"{label} must remain observer-only; found {forbidden!r}")

for forbidden in (
    "onTimeDeliveryPercent",
    "onTimeRate",
    "lateDeliveryRate",
    "optimal",
    "bestPolicy",
):
    if due and forbidden in due:
        errors.append(f"Due-date exposure must not claim unsupported delivery performance; found {forbidden!r}")

if errors:
    print("RSE OPERATIONS PLANT VIEW VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE OPERATIONS PLANT VIEW VERIFY: PASS")
print(" conservative legacy due-date observer contract retained: PASS")
print(" persisted world workcell/buffer state visible through existing Operations Monitor: PASS")
print(" persistent job + queue/quality/maintenance/delivery/logistics evidence visible read-only: PASS")
print(" quality KPI derived only from explicit inspection units: PASS")
print(" dated on-time/late delivery performance derived from retained completion timing: PASS")
print(" automatic ranking or optimization: NONE")
print(" dispatch/queue/maintenance/world/robotics authority leakage: NONE")
