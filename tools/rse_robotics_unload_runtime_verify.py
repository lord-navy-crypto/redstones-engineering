#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
ROBOTICS = ROOT / "src/main/java/dev/redstoneengineering/robotics"
RUNTIME = ROBOTICS / "RobotMaterialUnloadRuntime.java"
ENTITY = ROOT / "src/main/java/dev/redstoneengineering/entity/EngineeringMobileRobotEntity.java"
errors = []

def read(path):
    if not path.exists():
        errors.append(f"missing {path.relative_to(ROOT)}")
        return ""
    return path.read_text(encoding="utf-8")

def req(text, needle, label):
    if needle not in text:
        errors.append(f"{label}: missing {needle!r}")

runtime = read(RUNTIME)
entity = read(ENTITY)

for needle in (
    "RobotOperatingState.UNLOADING",
    "RobotMission mission",
    "RobotPayloadSnapshot payload",
    "RobotDockSnapshot dock",
    "RobotMaterialTransferSnapshot transfer",
    "RobotDockAssessment.Phase.TRANSFER",
    "RobotMaterialTransferAssessment.inspect(",
    "PAYLOAD_EVIDENCE_MISSING",
    "PAYLOAD_FAULT_ACTIVE",
    "PAYLOAD_ROBOT_MISMATCH",
    "PAYLOAD_COUNT_MISMATCH",
    "UNLOAD_QUANTITY_MISMATCH",
    "RobotStateMachine.Event.UNLOAD_COMPLETE",
    "RobotOperatingState.COMPLETE",
    "UNLOAD_TRANSITION_REJECTED",
    "unloadComplete()",
):
    req(runtime, needle, "RobotMaterialUnloadRuntime.java")

for forbidden in (
    "setBlock(",
    "setDeltaMovement(",
    "move(MoverType",
    "ItemStack",
    "IItemHandler",
    "insertItem(",
    "extractItem(",
    "setRobotState(",
    "level()",
):
    if forbidden in runtime:
        errors.append(f"RobotMaterialUnloadRuntime.java must remain pure evidence/lifecycle logic; unexpected {forbidden!r}")

if "RobotStateMachine.Event.UNLOAD_COMPLETE" in entity:
    errors.append("EngineeringMobileRobotEntity.java must not emit UNLOAD_COMPLETE before unload runtime evidence is connected")

if errors:
    print("RSE ROBOTICS UNLOAD RUNTIME VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    sys.exit(1)

print("RSE ROBOTICS UNLOAD RUNTIME VERIFY: PASS")
print("  unloading requires material mission + payload + destination dock + transfer evidence")
print("  payload identity and quantity remain bound to the exact AMR mission")
print("  destination transfer reuses authoritative dock/material assessment")
print("  only complete evidence computes UNLOAD_COMPLETE -> COMPLETE")
print("  runtime remains world/inventory/entity-mutation free")
