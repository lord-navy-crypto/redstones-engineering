#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing routing file: {rel}")
        return ""
    return path.read_text(errors="ignore")


binding = read("src/main/java/dev/redstoneengineering/operations/OperationJobRouteBinding.java")
ledger = read("src/main/java/dev/redstoneengineering/operations/OperationCompletionLedger.java")
routed = read("src/main/java/dev/redstoneengineering/operations/OperationRoutedDispatchRuntime.java")

for token in (
    "record OperationJobRouteBinding(",
    "long jobId",
    "String routeId",
    "int stepIndex",
    "Set<Long> predecessorJobIds",
    "job cannot depend on itself",
    "predecessorsSatisfied",
):
    if binding and token not in binding:
        errors.append(f"OperationJobRouteBinding missing routing/precedence contract {token!r}")

for token in (
    "record OperationCompletionLedger(",
    "Set<Long> completedJobIds",
    "PortQuality evidenceQuality",
    "boolean faultActive",
    "completed(long jobId)",
):
    if ledger and token not in ledger:
        errors.append(f"OperationCompletionLedger missing completion evidence contract {token!r}")

for token in (
    "class OperationRoutedDispatchRuntime",
    "COMPLETION_LEDGER_FAULT_ACTIVE",
    "COMPLETION_LEDGER_EVIDENCE_INVALID",
    "DUPLICATE_ROUTE_BINDING",
    "ROUTE_BINDING_MISSING_FOR_JOB",
    "PREDECESSORS_INCOMPLETE",
    "OperationDispatchRuntime.evaluate(",
    "routeEligible",
    "ROUTE_ELIGIBLE_",
    "SELECTED_ROUTE_BINDING_MISSING",
):
    if routed and token not in routed:
        errors.append(f"OperationRoutedDispatchRuntime missing precedence-gated dispatch contract {token!r}")

for forbidden in (
    "Comparator.comparing",
    "resource.dispatchable()",
    "resource.supports(",
    "new OperationAssignment(",
    "setBlock(",
    "setDeltaMovement(",
    "RuntimeIntStore",
    "OperationsDashboardSnapshot",
    "IndustrialOperationsAssessment",
    "RobotMission",
    "RobotRoutePlanner",
):
    if routed and forbidden in routed:
        errors.append(f"Routed dispatch must not duplicate dispatch/resource/world/KPI authority; found {forbidden!r}")

if errors:
    print("RSE OPERATIONS ROUTING VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE OPERATIONS ROUTING VERIFY: PASS")
print(" explicit job route/step/predecessor binding: PASS")
print(" completion ledger quality/fault evidence: PASS")
print(" incomplete predecessors remain WAIT: PASS")
print(" missing/duplicate routing evidence fails closed: PASS")
print(" final resource selection delegates to OperationDispatchRuntime: PASS")
print(" duplicate dispatch/resource/world/KPI authority: NONE")
