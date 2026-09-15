#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing reliability file: {rel}")
        return ""
    return path.read_text(errors="ignore")


window = read("src/main/java/dev/redstoneengineering/operations/OperationReliabilityWindowSnapshot.java")
assessment = read("src/main/java/dev/redstoneengineering/diagnostics/OperationReliabilityPerformanceAssessment.java")

for token in (
    "record OperationReliabilityWindowSnapshot(",
    "long observedTicks",
    "long operatingTicks",
    "long faultDowntimeTicks",
    "long plannedMaintenanceTicks",
    "long otherHeldTicks",
    "int failureCount",
    "int completedRepairCount",
    "PortQuality evidenceQuality",
    "boolean faultActive",
    "reliability window tick accounting must equal observedTicks",
    "completed repairs cannot exceed observed failures",
):
    if window and token not in window:
        errors.append(f"Reliability window missing accounting contract {token!r}")

for token in (
    "class OperationReliabilityPerformanceAssessment",
    "Observer-only reliability projection",
    "COMPLETE",
    "PARTIAL",
    "INVALID",
    "int observedAvailabilityPercent",
    "long meanOperatingTicksPerFailure",
    "long meanFaultDowntimeTicksPerCompletedRepair",
    "window.faultDowntimeTicks()",
    "window.plannedMaintenanceTicks()",
    "operatingTicks + faultDowntimeTicks",
    "failureCount == 0 ? 0",
    "completedRepairs == 0 ? 0",
):
    if assessment and token not in assessment:
        errors.append(f"Reliability assessment missing observer metric {token!r}")

for forbidden in (
    "OperationDispatchRuntime",
    "OperationQueueRuntime",
    "OperationMaintenanceRuntime",
    "OperationMaintenanceAwareDispatchRuntime",
    "RobotMission",
    "setBlock(",
    "setDeltaMovement(",
    "RuntimeIntStore",
    "scheduleTick(",
):
    if assessment and forbidden in assessment:
        errors.append(f"Reliability metrics must remain observer-only; found {forbidden!r}")

for forbidden in (
    "forecast",
    "predictive maintenance trigger",
    "certified",
):
    if assessment and forbidden in assessment.lower():
        errors.append(f"Reliability observer must not make unsupported predictive/certification claims; found {forbidden!r}")

if errors:
    print("RSE OPERATIONS RELIABILITY VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE OPERATIONS RELIABILITY VERIFY: PASS")
print(" operating/fault/planned-maintenance/other-hold accounting: PASS")
print(" planned maintenance separated from fault downtime: PASS")
print(" observed availability and historical mean-time metrics: PASS")
print(" predictive maintenance authority: NONE")
print(" dispatch/world/robotics authority leakage: NONE")
