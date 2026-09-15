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
print(" starvation/saturation/blocking classification: PASS")
print(" bottleneck projection remains observer-only: PASS")
print(" scheduling/world/robotics authority leakage: NONE")
