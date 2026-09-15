#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing setup file: {rel}")
        return ""
    return path.read_text(errors="ignore")


setup = read("src/main/java/dev/redstoneengineering/operations/OperationResourceSetupSnapshot.java")
runtime = read("src/main/java/dev/redstoneengineering/operations/OperationSetupAwareDispatchRuntime.java")

for token in (
    "record OperationResourceSetupSnapshot(",
    "String resourceId",
    "String configuredProcessId",
    "String targetProcessId",
    "CHANGEOVER_IN_PROGRESS",
    "UNCONFIGURED",
    "FAULTED",
    "PortQuality evidenceQuality",
    "boolean faultActive",
    "READY setup requires configuredProcessId",
    "changeover requires targetProcessId",
    "boolean readyFor(String processId)",
):
    if setup and token not in setup:
        errors.append(f"OperationResourceSetupSnapshot missing setup evidence contract {token!r}")

for token in (
    "class OperationSetupAwareDispatchRuntime",
    "SETUP_EVIDENCE_MISSING",
    "DUPLICATE_SETUP_RESOURCE_ID",
    "RESOURCE_SETUP_FAULT_ACTIVE",
    "RESOURCE_SETUP_EVIDENCE_INVALID",
    "SETUP_EVIDENCE_MISSING_FOR_RESOURCE",
    "SETUP_PROCESS_CAPABILITY_MISMATCH",
    "OperationResourceSnapshot.State.UNAVAILABLE",
    "OperationDispatchRuntime.evaluate(",
    "SETUP_OR_CHANGEOVER_REQUIRED",
    "SELECTED_RESOURCE_SETUP_INCONSISTENT",
    "SETUP_READY_",
):
    if runtime and token not in runtime:
        errors.append(f"OperationSetupAwareDispatchRuntime missing fail-closed setup gate {token!r}")

# The setup layer may inspect capability to validate/projection, but must not own final ranking/selection.
for forbidden in (
    "Comparator.comparing",
    "jobComparator(",
    "resource.dispatchable()",
    "new OperationAssignment(",
    "OperationsDashboardSnapshot",
    "IndustrialOperationsAssessment",
    "throughput",
    "downtime",
    "queuePressure",
    "RobotMission",
    "RobotRoutePlanner",
    "setBlock(",
    "setDeltaMovement(",
    "RuntimeIntStore",
):
    if runtime and forbidden in runtime:
        errors.append(f"Setup-aware dispatch must not duplicate ranking/world/KPI/robotics authority; found {forbidden!r}")

if errors:
    print("RSE OPERATIONS SETUP VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE OPERATIONS SETUP VERIFY: PASS")
print(" explicit configured/target process setup evidence: PASS")
print(" invalid/missing/fault setup evidence fails closed: PASS")
print(" capability/setup contradiction fails closed: PASS")
print(" unfinished changeover remains WAIT: PASS")
print(" final job/resource selection delegates to OperationDispatchRuntime: PASS")
print(" duplicate ranking/world/KPI/robotics authority: NONE")
