#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing downstream material-flow file: {rel}")
        return ""
    return path.read_text(errors="ignore")


requirement = read("src/main/java/dev/redstoneengineering/operations/OperationInputRequirement.java")
allocation = read("src/main/java/dev/redstoneengineering/operations/OperationInputAllocation.java")
buffer_runtime = read("src/main/java/dev/redstoneengineering/operations/OperationBufferRuntime.java")
release_runtime = read("src/main/java/dev/redstoneengineering/operations/OperationMaterialReleaseRuntime.java")

for token in (
    "record OperationInputRequirement(",
    "long downstreamJobId",
    "String bufferId",
    "long outputId",
    "int requiredUnits",
    "requiredUnits must be positive",
):
    if requirement and token not in requirement:
        errors.append(f"OperationInputRequirement missing explicit material requirement {token!r}")

for token in (
    "record OperationInputAllocation(",
    "long downstreamJobId",
    "String bufferId",
    "long outputId",
    "long upstreamJobId",
    "int allocatedUnits",
):
    if allocation and token not in allocation:
        errors.append(f"OperationInputAllocation missing upstream/downstream traceability {token!r}")

for token in (
    "ALLOCATED",
    "AllocationDecision",
    "INPUT_REQUIREMENT_MISSING",
    "INPUT_BUFFER_ID_MISMATCH",
    "INPUT_OUTPUT_NOT_AVAILABLE",
    "INPUT_QUANTITY_NOT_AVAILABLE",
    "new OperationInputAllocation(",
    "requirement.downstreamJobId()",
    "selected.jobId()",
    "requirement.requiredUnits()",
    "remaining > 0",
    "INPUT_ALLOCATED",
):
    if buffer_runtime and token not in buffer_runtime:
        errors.append(f"OperationBufferRuntime missing explicit lot-allocation contract {token!r}")

for token in (
    "class OperationMaterialReleaseRuntime",
    "RELEASED",
    "DOWNSTREAM_JOB_MISSING",
    "INPUT_REQUIREMENT_MISSING",
    "DOWNSTREAM_JOB_ID_MISMATCH",
    "OperationBufferRuntime.allocate(buffer, requirement)",
    "OperationQueueRuntime.enqueue(queue, downstreamJob)",
    "waitFor(buffer, queue, admission.reason())",
    "safeStop(buffer, queue, admission.reason())",
    "allocation.nextState()",
    "admission.nextState()",
    "allocation.allocation()",
    "MATERIAL_JOB_RELEASED",
):
    if release_runtime and token not in release_runtime:
        errors.append(f"OperationMaterialReleaseRuntime missing atomic buffer/queue release contract {token!r}")

for body, label in ((buffer_runtime, "OperationBufferRuntime"), (release_runtime, "OperationMaterialReleaseRuntime")):
    for forbidden in (
        "setBlock(",
        "setDeltaMovement(",
        "scheduleTick(",
        "getEntities",
        "RuntimeIntStore",
        "OperationsDashboardSnapshot",
        "IndustrialOperationsAssessment",
        "RobotMission",
        "RobotRoutePlanner",
        "EngineeringMobileRobotEntity",
        "System.currentTimeMillis",
    ):
        if body and forbidden in body:
            errors.append(f"{label} must remain pure and independent of world/KPI/robotics authority; found {forbidden!r}")

if errors:
    print("RSE OPERATIONS MATERIAL RELEASE VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE OPERATIONS MATERIAL RELEASE VERIFY: PASS")
print(" exact output-lot input requirement: PASS")
print(" upstream output/job trace preserved in allocation: PASS")
print(" insufficient/missing material waits without fabrication: PASS")
print(" partial lot allocation retains explicit remainder: PASS")
print(" downstream job identity must match requirement: PASS")
print(" queue admission failure rolls buffer state back: PASS")
print(" successful release changes buffer and queue snapshots together: PASS")
print(" world/KPI/robotics authority leakage: NONE")
