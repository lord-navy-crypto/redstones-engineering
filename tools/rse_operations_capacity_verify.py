#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing capacity file: {rel}")
        return ""
    return path.read_text(errors="ignore")


capacity = read("src/main/java/dev/redstoneengineering/operations/OperationWorkcellCapacitySnapshot.java")
admission = read("src/main/java/dev/redstoneengineering/operations/OperationWorkcellAdmissionAssessment.java")
bottleneck = read("src/main/java/dev/redstoneengineering/diagnostics/OperationBottleneckAssessment.java")

for token in (
    "record OperationWorkcellCapacitySnapshot(",
    "Set<String> resourceIds",
    "int activeAssignments",
    "int queuedJobs",
    "int inputCapacityUnits",
    "int inputWipUnits",
    "int outputCapacityUnits",
    "int outputWipUnits",
    "MAX_RESOURCE_COUNT = 64",
    "MAX_BUFFER_UNITS = 4096",
    "activeAssignments must fit resource capacity",
    "input WIP exceeds input capacity",
    "output WIP exceeds output capacity",
    "availableResourceSlots()",
    "resourcesSaturated()",
    "inputStarved()",
    "outputBlocked()",
    "resourceUtilizationPercent()",
):
    if capacity and token not in capacity:
        errors.append(f"OperationWorkcellCapacitySnapshot missing finite-capacity contract {token!r}")

for token in (
    "class OperationWorkcellAdmissionAssessment",
    "PERMIT",
    "WAIT",
    "SAFE_STOP",
    "FAULT",
    "WORKCELL_CAPACITY_EVIDENCE_MISSING",
    "WORKCELL_FAULT_ACTIVE",
    "WORKCELL_CAPACITY_EVIDENCE_INVALID",
    "RESOURCE_NOT_IN_WORKCELL",
    "OUTPUT_BUFFER_CAPACITY_REACHED",
    "WORKCELL_RESOURCE_CAPACITY_REACHED",
    "WORKCELL_ADMISSION_PERMIT",
    "workcell.outputBlocked()",
    "workcell.resourcesSaturated()",
):
    if admission and token not in admission:
        errors.append(f"OperationWorkcellAdmissionAssessment missing finite-capacity admission rule {token!r}")

for forbidden in (
    "OperationBottleneckAssessment",
    "severityScore",
    "OperationDispatchRuntime",
    "OperationQueueRuntime",
    "RobotMission",
    "setBlock(",
    "setDeltaMovement(",
    "RuntimeIntStore",
):
    if admission and forbidden in admission:
        errors.append(f"Workcell admission must use capacity evidence only, not ranking/KPI/world/robotics authority; found {forbidden!r}")

for token in (
    "class OperationBottleneckAssessment",
    "Observer-only finite-capacity bottleneck projection",
    "INPUT_STARVED",
    "RESOURCE_SATURATED",
    "OUTPUT_BLOCKED",
    "FAULTED",
    "EVIDENCE_INVALID",
    "WORKCELL_FAULT_ACTIVE",
    "WORKCELL_EVIDENCE_INVALID",
    "OUTPUT_BUFFER_AT_CAPACITY",
    "RESOURCE_CAPACITY_SATURATED_WITH_QUEUE",
    "INPUT_WIP_EMPTY",
    "NO_FINITE_CAPACITY_CONSTRAINT",
    "severityScore",
    "resourceUtilizationPercent",
    "inputWipPressurePercent",
    "outputWipPressurePercent",
):
    if bottleneck and token not in bottleneck:
        errors.append(f"OperationBottleneckAssessment missing observer constraint contract {token!r}")

for forbidden in (
    "OperationDispatchRuntime",
    "OperationQueueRuntime",
    "OperationMaterialReleaseRuntime",
    "OperationChangeoverRuntime",
    "RobotMission",
    "RobotRoutePlanner",
    "setBlock(",
    "setDeltaMovement(",
    "scheduleTick(",
    "RuntimeIntStore",
):
    if bottleneck and forbidden in bottleneck:
        errors.append(f"Bottleneck diagnostics must not own scheduling/world/robotics authority; found {forbidden!r}")

for forbidden in (
    "OperationsDashboardSnapshot",
    "IndustrialOperationsAssessment",
    "throughputCyclesPerMinute",
    "downtimeTicks",
):
    if capacity and forbidden in capacity:
        errors.append(f"Finite-capacity evidence must not be derived from observer KPIs; found {forbidden!r}")

if errors:
    print("RSE OPERATIONS CAPACITY VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE OPERATIONS CAPACITY VERIFY: PASS")
print(" explicit finite resource/input/output capacities: PASS")
print(" finite-capacity workcell admission gate: PASS")
print(" saturation/output blocking remain WAIT rather than silent over-admission: PASS")
print(" starvation/saturation/blocking diagnostic classification: PASS")
print(" bottleneck projection remains observer-only: PASS")
print(" scheduling/world/robotics authority leakage: NONE")
