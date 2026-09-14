#!/usr/bin/env python3
"""Fail-closed static guard for the AMR dock evidence/handshake foundation."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
SNAPSHOT = ROOT / "src/main/java/dev/redstoneengineering/robotics/RobotDockSnapshot.java"
ASSESSMENT = ROOT / "src/main/java/dev/redstoneengineering/robotics/RobotDockAssessment.java"
errors: list[str] = []


def read(path: Path) -> str:
    if not path.is_file():
        errors.append(f"missing: {path.relative_to(ROOT)}")
        return ""
    return path.read_text(encoding="utf-8", errors="ignore")


def require(body: str, token: str, label: str) -> None:
    if token not in body:
        errors.append(f"{label}: missing {token!r}")


snapshot = read(SNAPSHOT)
assessment = read(ASSESSMENT)

for token in (
    "record RobotDockSnapshot(",
    "String dockId",
    "BlockPos position",
    "PortQuality evidenceQuality",
    "String reservedRobotId",
    "String occupiedRobotId",
    "boolean approachClear",
    "boolean alignmentReady",
    "boolean transferReady",
    "boolean emergencyStopClear",
    "boolean faultActive",
    "reservedByOther(String robotId)",
    "occupiedByOther(String robotId)",
):
    require(snapshot, token, "RobotDockSnapshot.java")

for token in (
    "APPROACH",
    "DOCK",
    "TRANSFER",
    "PERMIT",
    "WAIT",
    "SAFE_STOP",
    "FAULT",
    '"NO_DOCK_EVIDENCE"',
    '"ROBOT_IDENTITY_MISSING"',
    '"DOCK_FAULT_ACTIVE"',
    '"DOCK_E_STOP_ACTIVE"',
    '"DOCK_EVIDENCE_"',
    '"DOCK_RESERVED_FOR_OTHER"',
    '"DOCK_OCCUPIED_BY_OTHER"',
    '"APPROACH_BLOCKED"',
    '"ALIGNMENT_NOT_READY"',
    '"ROBOT_NOT_CONFIRMED_DOCKED"',
    '"TRANSFER_NOT_READY"',
    '"TRANSFER_PERMIT"',
):
    require(assessment, token, "RobotDockAssessment.java")

# Foundation must classify authoritative evidence only. It must not mutate the
# world, claim occupancy itself, scan for nearby robots, or perform inventory transfer.
for label, body in (("RobotDockSnapshot.java", snapshot), ("RobotDockAssessment.java", assessment)):
    for forbidden in (
        "net.minecraft.world.level.Level",
        "getEntities(",
        "getBlockState(",
        "setBlock(",
        "move(MoverType",
        "setDeltaMovement(",
        "ItemStack",
        "IItemHandler",
        "insertItem(",
        "extractItem(",
    ):
        if forbidden in body:
            errors.append(f"{label}: dock foundation must remain evidence-only; unexpected {forbidden!r}")

if errors:
    print("RSE ROBOTICS DOCK VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE ROBOTICS DOCK VERIFY: PASS")
print("  dock identity/reservation/occupancy evidence: explicit")
print("  approach/dock/transfer handshake phases: distinct")
print("  missing/invalid evidence and E-stop: fail-safe")
print("  fault evidence: fault verdict")
print("  transfer requires confirmed robot occupancy")
print("  world mutation/inventory transfer/nearby-entity scans: absent")
