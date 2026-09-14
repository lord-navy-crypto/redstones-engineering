#!/usr/bin/env python3
"""Static regression gate for Golden System #1 pneumatic plant commissioning."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
WITNESS = ROOT / "src/main/java/dev/redstoneengineering/diagnostics/PneumaticClosedLoopWitness.java"
COMMISSIONING = ROOT / "src/main/java/dev/redstoneengineering/diagnostics/ClosedLoopCommissioning.java"
PID = ROOT / "src/main/java/dev/redstoneengineering/block/PidControllerBlock.java"
CYLINDER = ROOT / "src/main/java/dev/redstoneengineering/block/PneumaticCylinderBlock.java"

errors = []

def read(path):
    if not path.exists():
        errors.append(f"missing {path.relative_to(ROOT)}")
        return ""
    return path.read_text(encoding="utf-8")

def req(text, needle, label):
    if needle not in text:
        errors.append(f"{label}: missing {needle!r}")

witness = read(WITNESS)
commissioning = read(COMMISSIONING)
pid = read(PID)
cylinder = read(CYLINDER)

for needle in (
    "class PneumaticClosedLoopWitness",
    "Direction processSide = controlOut.getCounterClockWise();",
    "pidPos.relative(processSide)",
    "instanceof PneumaticCylinderBlock cylinder",
    "PortKind.FEEDBACK",
    "PortDirection.OUTPUT",
    "PneumaticNetwork.actuatorPathEvidence(level, cylinderPos)",
    "PneumaticCylinderBlock.stallTicks(level, cylinderPos)",
    "PneumaticCylinderBlock.samples(level, cylinderPos)",
    "path.restrictionLoss()",
    "path.observedLoss()",
    "trackingError",
):
    req(witness, needle, "PneumaticClosedLoopWitness.java")

for needle in (
    "PneumaticClosedLoopWitness.inspect(level, pidPos)",
    "if (!plant.detected()) return controller;",
    "combineWithPneumaticPlant(controller, plant)",
    "controller.score() - plant.penalty()",
    "if (!plant.ready())",
):
    req(commissioning, needle, "ClosedLoopCommissioning.java")

# Existing generic PID and cylinder evidence contracts must remain intact.
for needle in (
    "ClosedLoopCommissioning.inspectPid(level, pos)",
    "EngineeringAcceptance.evaluate(topology, commissioning)",
):
    req(pid, needle, "PidControllerBlock.java")

for needle in (
    "public static int position(Level level, BlockPos pos)",
    "public static int target(Level level, BlockPos pos)",
    "public static int pressure(Level level, BlockPos pos)",
    "public static int stallTicks(Level level, BlockPos pos)",
    "public static int samples(Level level, BlockPos pos)",
):
    req(cylinder, needle, "PneumaticCylinderBlock.java")

# The witness must stay observational; no plant/controller mutation belongs here.
for forbidden in (
    "setBlock(",
    "RuntimeIntStore.get(",
    "PneumaticNetwork.recompute(",
    "scheduleTick(",
    "updateNeighborsAt(",
):
    if forbidden in witness:
        errors.append(f"PneumaticClosedLoopWitness.java mutates runtime via {forbidden!r}")

if errors:
    print("RSE PNEUMATIC GOLDEN SYSTEM VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    sys.exit(1)

print("RSE PNEUMATIC GOLDEN SYSTEM VERIFY: PASS")
print("  explicit PID PV -> cylinder feedback witness; no radius guessing")
print("  plant evidence: pressure / supply / losses / restriction / stall / samples / tracking")
print("  generic PID commissioning unchanged when no pneumatic witness exists")
print("  system witness remains read-only and folds into retained acceptance through inspectPid")
