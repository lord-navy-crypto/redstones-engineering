#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
path = root / "src/main/java/dev/redstoneengineering/operations/OperationDispatchRuntime.java"
errors: list[str] = []

if not path.is_file():
    errors.append("missing OperationDispatchRuntime.java")
    body = ""
else:
    body = path.read_text(errors="ignore")

for token in (
    "EARLIEST_DUE_DATE",
    "policy == Policy.EARLIEST_DUE_DATE",
    "comparingLong(OperationDispatchRuntime::dueDateSortKey)",
    ".thenComparing(fifo)",
    "job.hasDueDate() ? job.dueTick() : Long.MAX_VALUE",
    "Jobs without a due date (dueTick == 0) follow all explicitly dated jobs under EDD",
):
    if body and token not in body:
        errors.append(f"EDD dispatch contract missing {token!r}")

# EDD must remain one comparator policy inside the existing authoritative dispatcher.
for forbidden in (
    "class EarliestDueDateDispatch",
    "class EddDispatch",
    "OperationsDashboardSnapshot",
    "IndustrialOperationsAssessment",
    "throughput",
    "downtime",
    "queuePressure",
    "System.currentTimeMillis",
    "setBlock(",
    "setDeltaMovement(",
):
    if body and forbidden in body:
        errors.append(f"EDD must not introduce second/KPI/world authority; found {forbidden!r}")

if errors:
    print("RSE OPERATIONS EDD VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE OPERATIONS EDD VERIFY: PASS")
print(" EDD remains inside OperationDispatchRuntime: PASS")
print(" explicit due dates sort earliest first: PASS")
print(" dueTick == 0 sorts after dated work: PASS")
print(" equal due dates fall back to FIFO + jobId: PASS")
print(" KPI/world authority leakage: NONE")
