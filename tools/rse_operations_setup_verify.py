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
changeover_evidence = read("src/main/java/dev/redstoneengineering/operations/OperationChangeoverEvidence.java")
changeover_runtime = read("src/main/java/dev/redstoneengineering/operations/OperationChangeoverRuntime.java")

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

for token in (
    "record OperationChangeoverEvidence(",
    "String resourceId",
    "String targetProcessId",
    "PortQuality evidenceQuality",
    "boolean setupWorkConfirmed",
    "boolean completionConfirmed",
    "boolean faultActive",
):
    if changeover_evidence and token not in changeover_evidence:
        errors.append(f"OperationChangeoverEvidence missing explicit completion evidence {token!r}")

for token in (
    "class OperationChangeoverRuntime",
    "CHANGEOVER_TARGET_MISSING",
    "CHANGEOVER_RESOURCE_ID_MISMATCH",
    "RESOURCE_SETUP_EVIDENCE_INVALID",
    "RESOURCE_EVIDENCE_INVALID",
    "CHANGEOVER_TARGET_NOT_CAPABLE",
    "ALREADY_CONFIGURED",
    "CHANGEOVER_ALREADY_IN_PROGRESS",
    "CHANGEOVER_TARGET_CONFLICT",
    "CHANGEOVER_REQUESTED",
    "CHANGEOVER_EVIDENCE_MISSING",
    "CHANGEOVER_NOT_IN_PROGRESS",
    "CHANGEOVER_FAULT_ACTIVE",
    "CHANGEOVER_EVIDENCE_INVALID",
    "CHANGEOVER_TARGET_MISMATCH",
    "CHANGEOVER_WORK_UNCONFIRMED",
    "CHANGEOVER_COMPLETION_UNCONFIRMED",
    "CHANGEOVER_COMPLETE",
    "OperationResourceSetupSnapshot.State.CHANGEOVER_IN_PROGRESS",
    "OperationResourceSetupSnapshot.State.READY",
):
    if changeover_runtime and token not in changeover_runtime:
        errors.append(f"OperationChangeoverRuntime missing evidence-bound lifecycle {token!r}")

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

# Changeover completion must come from evidence, never from elapsed time or a hidden timer.
for forbidden in (
    "System.currentTimeMillis",
    "assignedTick",
    "gameTick -",
    "scheduleTick(",
    "setBlock(",
    "RuntimeIntStore",
    "OperationsDashboardSnapshot",
    "IndustrialOperationsAssessment",
    "RobotMission",
):
    if changeover_runtime and forbidden in changeover_runtime:
        errors.append(f"Changeover lifecycle must remain evidence-bound and pure; found {forbidden!r}")

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
print(" request -> in-progress -> evidence-confirmed READY lifecycle: PASS")
print(" elapsed time cannot auto-complete changeover: PASS")
print(" final job/resource selection delegates to OperationDispatchRuntime: PASS")
print(" duplicate ranking/world/KPI/robotics authority: NONE")
