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

for body, label in ((due, "Due-date exposure"), (plant, "Plant view")):
    for forbidden in (
        "OperationDispatchRuntime",
        "OperationQueueRuntime",
        "OperationMaintenanceRuntime",
        "OperationMaintenanceAwareDispatchRuntime",
        "OperationChangeoverRuntime",
        "RobotMission",
        "setBlock(",
        "setDeltaMovement(",
        "scheduleTick(",
        "RuntimeIntStore",
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
print(" current overdue-work exposure without fake completion timing: PASS")
print(" throughput/WIP/bottleneck/quality/reliability/due-date composition: PASS")
print(" automatic ranking or optimization: NONE")
print(" dispatch/queue/maintenance/world/robotics authority leakage: NONE")
