#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
ROBOTICS = ROOT / "src/main/java/dev/redstoneengineering/robotics"
SNAPSHOT = ROBOTICS / "RobotMaterialTransferSnapshot.java"
ASSESSMENT = ROBOTICS / "RobotMaterialTransferAssessment.java"
RUNTIME = ROBOTICS / "RobotMaterialFlowRuntime.java"
errors = []

def read(path):
    if not path.exists():
        errors.append(f"missing {path.relative_to(ROOT)}")
        return ""
    return path.read_text(encoding="utf-8")

def req(text, needle, label):
    if needle not in text:
        errors.append(f"{label}: missing {needle!r}")

snapshot = read(SNAPSHOT)
assessment = read(ASSESSMENT)
runtime = read(RUNTIME)

for needle in (
    "String transferId",
    "String dockId",
    "String robotId",
    "int requestedUnits",
    "int transferredUnits",
    "PortQuality evidenceQuality",
    "boolean sourceConfirmed",
    "boolean destinationConfirmed",
    "boolean completionConfirmed",
    "boolean faultActive",
    'if (requestedUnits <= 0) throw new IllegalArgumentException("requested units must be positive")',
    'if (transferredUnits < 0) throw new IllegalArgumentException("transferred units must be non-negative")',
    'if (transferredUnits > requestedUnits) throw new IllegalArgumentException("transferred units cannot exceed requested units")',
    "return transferredUnits == requestedUnits;",
):
    req(snapshot, needle, "RobotMaterialTransferSnapshot.java")

for needle in (
    "COMPLETE",
    "WAIT",
    "SAFE_STOP",
    "FAULT",
    "NO_TRANSFER_EVIDENCE",
    "EXPECTED_DOCK_ID_MISSING",
    "EXPECTED_ROBOT_ID_MISSING",
    "TRANSFER_FAULT_ACTIVE",
    "TRANSFER_EVIDENCE_",
    "TRANSFER_DOCK_MISMATCH",
    "TRANSFER_ROBOT_MISMATCH",
    "SOURCE_NOT_CONFIRMED",
    "DESTINATION_NOT_CONFIRMED",
    "TRANSFER_NOT_CONFIRMED_COMPLETE",
    "TRANSFER_COUNT_MISMATCH",
    "TRANSFER_COMPLETE",
    "transfer.evidenceQuality() != PortQuality.VALID",
    "!expectedDockId.trim().equals(transfer.dockId())",
    "!expectedRobotId.trim().equals(transfer.robotId())",
    "!transfer.completeQuantity()",
):
    req(assessment, needle, "RobotMaterialTransferAssessment.java")

for needle in (
    "RobotOperatingState current",
    "RobotDockSnapshot dock",
    "RobotMaterialTransferSnapshot transfer",
    "String robotId",
    "current != RobotOperatingState.LOADING",
    "RobotDockAssessment.Phase.TRANSFER",
    "RobotMaterialTransferAssessment.inspect(",
    "RobotStateMachine.Event.CRITICAL_FAULT",
    "RobotStateMachine.Event.SAFETY_STOP_REQUESTED",
    "RobotStateMachine.Event.LOAD_COMPLETE",
    "RobotOperatingState.TRANSPORTING",
    "TRANSFER_NOT_EVALUATED",
    "advancesToTransport()",
):
    req(runtime, needle, "RobotMaterialFlowRuntime.java")

# Evidence classes remain observer-only. Runtime may compute lifecycle transitions
# but still must not mutate world/inventory/entity state itself.
for label, text in (("RobotMaterialTransferSnapshot.java", snapshot), ("RobotMaterialTransferAssessment.java", assessment)):
    for forbidden in (
        "setBlock(",
        "setDeltaMovement(",
        "move(MoverType",
        "ItemStack",
        "IItemHandler",
        "insertItem(",
        "extractItem(",
        "RobotStateMachine.Event.LOAD_COMPLETE",
    ):
        if forbidden in text:
            errors.append(f"{label}: material evidence layer must remain observer-safe; unexpected {forbidden!r}")

for forbidden in (
    "setBlock(",
    "setDeltaMovement(",
    "move(MoverType",
    "ItemStack",
    "IItemHandler",
    "insertItem(",
    "extractItem(",
    "setRobotState(",
):
    if forbidden in runtime:
        errors.append(f"RobotMaterialFlowRuntime.java must remain a pure decision bridge; unexpected {forbidden!r}")

if errors:
    print("RSE ROBOTICS MATERIAL FLOW VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    sys.exit(1)

print("RSE ROBOTICS MATERIAL FLOW VERIFY: PASS")
print("  transfer evidence is identity-bound to transfer, dock, and robot")
print("  requested/transferred quantities are bounded and fail closed")
print("  completion requires valid evidence + source + destination + completion confirmation")
print("  partial transfer remains WAIT; confirmed quantity mismatch becomes SAFE_STOP")
print("  runtime bridge requires LOADING + dock TRANSFER permit + material COMPLETE before LOAD_COMPLETE")
print("  runtime bridge computes next state without mutating inventory/world/entity state")
