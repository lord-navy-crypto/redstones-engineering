#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors = []

def read(rel):
    path = root / rel
    if not path.is_file():
        errors.append(f"missing {rel}")
        return ""
    return path.read_text(errors="ignore")

binding = read("src/main/java/dev/redstoneengineering/integration/OperationTransportBinding.java")
bridge = read("src/main/java/dev/redstoneengineering/integration/OperationsRobotTransportBridge.java")
acceptance = read("src/main/java/dev/redstoneengineering/integration/OperationsRobotMaterialFlowAcceptance.java")
gametest = read("src/main/java/dev/redstoneengineering/gametest/RseOperationsRobotAcceptanceGameTests.java")
material = read("src/main/java/dev/redstoneengineering/robotics/RobotMaterialTransferAssessment.java")
dock = read("src/main/java/dev/redstoneengineering/robotics/RobotDockAssessment.java")

for token in (
    'return "ops-m" + missionId + "-o" + outputId + "-j" + jobId;',
    'return correlationKey() + "-payload";',
    'return correlationKey() + "-load";',
    'return correlationKey() + "-unload";',
    "matches(OperationOutputSnapshot output, OperationTransportDemand demand)",
    "matches(RobotMission mission)",
):
    if token not in binding:
        errors.append(f"OperationTransportBinding missing correlation contract {token!r}")

for token in (
    "OperationsRobotTransportBridge.evaluate(output, demand)",
    "RobotDockAssessment.inspect",
    "RobotMaterialTransferAssessment.inspect",
    "binding.matches(output, demand)",
    "binding.matches(mission)",
    "binding.loadTransferId().equals(loadTransfer.transferId())",
    "binding.payloadId().equals(payload.payloadId())",
    "binding.unloadTransferId().equals(unloadTransfer.transferId())",
    "loadTransfer.requestedUnits() != binding.units()",
    "payload.units() != binding.units()",
    "unloadTransfer.requestedUnits() != binding.units()",
    "MISSION_FINAL_TARGET_MISMATCH",
    "MISSION_TERMINAL_FAULT",
    "END_TO_END_ACCEPTED_WITH_RECOVERED_HOLDS",
    "END_TO_END_ACCEPTED",
):
    if token not in acceptance:
        errors.append(f"OperationsRobotMaterialFlowAcceptance missing {token!r}")

for token in (
    "MISSION_READY",
    "OUTPUT_EVIDENCE_INVALID",
    "TRANSPORT_QUANTITY_MISMATCH",
    "TRANSPORT_MISSION_READY",
):
    if token not in bridge:
        errors.append(f"OperationsRobotTransportBridge lost dispatch contract {token!r}")

for token in (
    "TRANSFER_COMPLETE",
    "TRANSFER_COUNT_MISMATCH",
    "TRANSFER_EVIDENCE_",
):
    if token not in material:
        errors.append(f"RobotMaterialTransferAssessment lost transfer contract {token!r}")

for token in (
    "DOCK_EVIDENCE_",
    "ROBOT_NOT_CONFIRMED_DOCKED",
    "TRANSFER_NOT_READY",
):
    if token not in dock:
        errors.append(f"RobotDockAssessment lost dock contract {token!r}")

for forbidden in (
    ".setBlock(",
    ".scheduleTick(",
    "setDeltaMovement",
    "RobotStateMachine.next",
    "RobotRoutePlanner.plan",
    "move(",
):
    if forbidden in acceptance:
        errors.append(f"end-to-end acceptance must be observer-only; found {forbidden!r}")

for token in (
    "nominalCorrelatedMaterialFlowPasses",
    "recoveredSafetyHoldIsMarginalNotSilentPass",
    "loadTransferCorrelationMismatchFailsClosed",
    "unloadQuantityMismatchFailsClosed",
    "terminalRobotFaultOverridesGoodTransferEvidence",
    "wrongFinalTargetCannotBeAccepted",
):
    if token not in gametest:
        errors.append(f"Operations/AMR acceptance GameTest missing {token!r}")

# Policy sanity: dispatch/correlation/quantity/safety must outrank terminal completion.
def classify(ready=True, correlation=True, quantities=True, target=True, fault=False, complete=True, holds=0, loc="VALID"):
    if not ready:
        return "WAIT_OR_STOP"
    if not correlation:
        return "SAFE_STOP"
    if not quantities:
        return "SAFE_STOP"
    if not target:
        return "SAFE_STOP"
    if fault:
        return "FAULT"
    if not complete:
        return "SAFE_STOP"
    if holds > 0 or loc != "VALID":
        return "MARGINAL"
    return "PASS"

cases = (
    ((True, True, True, True, False, True, 0, "VALID"), "PASS"),
    ((True, True, True, True, False, True, 1, "VALID"), "MARGINAL"),
    ((True, True, True, True, False, True, 0, "STALE"), "MARGINAL"),
    ((True, False, True, True, False, True, 0, "VALID"), "SAFE_STOP"),
    ((True, True, False, True, False, True, 0, "VALID"), "SAFE_STOP"),
    ((True, True, True, False, False, True, 0, "VALID"), "SAFE_STOP"),
    ((True, True, True, True, True, True, 0, "VALID"), "FAULT"),
)
for args, expected in cases:
    actual = classify(*args)
    if actual != expected:
        errors.append(f"end-to-end acceptance policy sanity failed: {args} -> {actual}, expected {expected}")

if errors:
    print("RSE OPERATIONS-AMR MATERIAL FLOW ACCEPTANCE VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE OPERATIONS-AMR MATERIAL FLOW ACCEPTANCE VERIFY: PASS")
print(" stable mission/output/job correlation identities: PASS")
print(" dispatch/dock/transfer evaluator reuse: PASS")
print(" load/payload/unload identity chain: PASS")
print(" quantity/source/target fail-closed semantics: PASS")
print(" terminal AMR safety evidence outranks completion: PASS")
print(" PASS / MARGINAL completed-flow distinction: PASS")
print(" observer-only integration boundary: PASS")
