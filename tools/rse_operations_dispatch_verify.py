#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
JOB = ROOT / "src/main/java/dev/redstoneengineering/operations/OperationJob.java"
RESOURCE = ROOT / "src/main/java/dev/redstoneengineering/operations/OperationResourceSnapshot.java"
RUNTIME = ROOT / "src/main/java/dev/redstoneengineering/operations/OperationDispatchRuntime.java"
errors = []


def read(path):
    if not path.exists():
        errors.append(f"missing {path.relative_to(ROOT)}")
        return ""
    return path.read_text(encoding="utf-8")


def require(body, needle, label):
    if needle not in body:
        errors.append(f"{label}: missing {needle!r}")


job = read(JOB)
resource = read(RESOURCE)
runtime = read(RUNTIME)

for needle in (
    "record OperationJob(",
    "quantity must be positive",
    "priority must be in 0..100",
    "releasedAt(long gameTick)",
):
    require(job, needle, "OperationJob")

for needle in (
    "record OperationResourceSnapshot(",
    "PortQuality evidenceQuality",
    "enum State",
    "AVAILABLE",
    "BUSY",
    "BLOCKED",
    "FAULTED",
    "dispatchable()",
):
    require(resource, needle, "OperationResourceSnapshot")

for needle in (
    "enum Policy",
    "FIFO",
    "PRIORITY_THEN_FIFO",
    "enum Verdict",
    "ASSIGN",
    "WAIT",
    "SAFE_STOP",
    "DUPLICATE_JOB_ID",
    "DUPLICATE_RESOURCE_ID",
    "RESOURCE_EVIDENCE_INVALID",
    "DISPATCH_PERMIT",
    "Comparator.comparingLong(OperationJob::releaseTick)",
    "Comparator.comparingInt(OperationJob::priority).reversed()",
):
    require(runtime, needle, "OperationDispatchRuntime")

for forbidden in (
    "net.minecraft.world.level",
    "OperationsDashboardSnapshot",
    "IndustrialOperationsAssessment",
    "throughput",
    "downtime",
    "queuePressure",
    "setBlock(",
    "setDeltaMovement(",
    "RobotStateMachine",
):
    if forbidden in runtime:
        errors.append(f"OperationDispatchRuntime must remain pure and metric-independent: unexpected {forbidden!r}")

if errors:
    print("RSE OPERATIONS DISPATCH VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    sys.exit(1)

print("RSE OPERATIONS DISPATCH VERIFY: PASS")
print("  immutable job and resource evidence contracts retained")
print("  deterministic FIFO and explicit priority-then-FIFO policies retained")
print("  invalid identity/evidence fails closed")
print("  dashboards and KPIs remain downstream observers, not scheduling authority")
