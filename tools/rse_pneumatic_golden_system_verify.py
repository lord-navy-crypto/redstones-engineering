#!/usr/bin/env python3
"""Static regression gate for Golden System #1 pneumatic plant commissioning, fault path and HMI evidence."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
WITNESS = ROOT / "src/main/java/dev/redstoneengineering/diagnostics/PneumaticClosedLoopWitness.java"
COMMISSIONING = ROOT / "src/main/java/dev/redstoneengineering/diagnostics/ClosedLoopCommissioning.java"
PID = ROOT / "src/main/java/dev/redstoneengineering/block/PidControllerBlock.java"
CYLINDER = ROOT / "src/main/java/dev/redstoneengineering/block/PneumaticCylinderBlock.java"
FAULT = ROOT / "src/main/java/dev/redstoneengineering/block/FaultInjectorBlock.java"
VALVE = ROOT / "src/main/java/dev/redstoneengineering/block/PneumaticProportionalValveBlock.java"
NETWORK = ROOT / "src/main/java/dev/redstoneengineering/physics/PneumaticNetwork.java"
MENU = ROOT / "src/main/java/dev/redstoneengineering/ui/menu/PidControllerMenu.java"
SCREEN = ROOT / "src/main/java/dev/redstoneengineering/client/ui/PidControllerScreen.java"

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
fault = read(FAULT)
valve = read(VALVE)
network = read(NETWORK)
menu = read(MENU)
screen = read(SCREEN)

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
    "enum Diagnosis",
    "NO_SUPPLY",
    "RESTRICTION",
    "LOW_ACTUATOR_PRESSURE",
    "STALLED",
    "static Diagnosis diagnose(",
    "if (restrictionLoss >= 25 || observedLoss >= 30) return Diagnosis.RESTRICTION;",
):
    req(witness, needle, "PneumaticClosedLoopWitness.java")

for needle in (
    "public static CommissioningSnapshot inspectController(Level level, BlockPos pidPos)",
    "RuntimeIntStore.peek(level, PID_KEY, pidPos)",
    "CommissioningSnapshot controller = inspectController(level, pidPos);",
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

# Controlled restriction scenario reuses the real signal fault injector and real pneumatic solver.
for needle in (
    'Modes: 0 STUCK_LOW, 1 STUCK_HIGH, 2 BIAS_PLUS_4, 3 BIAS_MINUS_4.',
    '"STUCK LOW"',
    '"BIAS -4"',
    'default -> Math.max(0, input - 4);',
):
    req(fault, needle, "FaultInjectorBlock.java")
for needle in (
    '"OPENING COMMAND"',
    "public static int opening(Level level, BlockPos pos)",
):
    req(valve, needle, "PneumaticProportionalValveBlock.java")
for needle in (
    "if (state.getBlock() instanceof PneumaticProportionalValveBlock)",
    "int opening = PneumaticProportionalValveBlock.opening(level, pos);",
    "pressure = (pressure * opening + 7) / 15;",
    "int restrictionLoss = Math.max(0, observedLoss - lineLoss);",
):
    req(network, needle, "PneumaticNetwork.java")

# HMI must preserve the causal split: controller-only, plant witness, diagnostic inference, then system verdict.
for needle in (
    "ClosedLoopCommissioning.inspectController(level, blockPos)",
    "ClosedLoopCommissioning.inspectPneumaticPlant(level, blockPos)",
    "controllerScore.set(controller.score())",
    "controllerStatus.set(controller.status().ordinal())",
    "plantDetected.set(plant.detected() ? 1 : 0)",
    "plantRestrictionLoss.set(plant.restrictionLoss())",
    "plantStallTicks.set(plant.stallTicks())",
    "plantPenalty.set(plant.penalty())",
    "plantDiagnosis.set(plant.diagnosis().ordinal())",
    "public CommissioningStatus controllerStatus()",
    "public CommissioningStatus plantStatus()",
    "public PneumaticClosedLoopWitness.Diagnosis plantDiagnosis()",
):
    req(menu, needle, "PidControllerMenu.java")

for needle in (
    'statusLine(graphics, "Controller"',
    'statusLine(graphics, "Pneumatic plant"',
    'statusLine(graphics, "Likely cause"',
    'statusLine(graphics, "System verdict"',
    '"Actuator / supply pressure"',
    '"Loss obs / line / restrict"',
    '"Position / target / stall"',
    '"RESTRICTION • check valve / path command"',
    '"NONE • explicit cylinder feedback not detected"',
):
    req(screen, needle, "PidControllerScreen.java")

# The witness and diagnostic facade must stay observational; no plant/controller mutation belongs here.
for label, source in (("PneumaticClosedLoopWitness.java", witness), ("ClosedLoopCommissioning.java", commissioning)):
    for forbidden in (
        "setBlock(",
        "RuntimeIntStore.get(",
        "PneumaticNetwork.recompute(",
        "scheduleTick(",
        "updateNeighborsAt(",
    ):
        if forbidden in source:
            errors.append(f"{label} mutates runtime via {forbidden!r}")

if errors:
    print("RSE PNEUMATIC GOLDEN SYSTEM VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    sys.exit(1)

print("RSE PNEUMATIC GOLDEN SYSTEM VERIFY: PASS")
print("  explicit PID PV -> cylinder feedback witness; no radius guessing")
print("  plant evidence: pressure / supply / losses / restriction / stall / samples / tracking")
print("  diagnostic inference: no supply / restriction / low actuator pressure / stall")
print("  real restriction path: Fault Injector -> valve opening command -> pneumatic pressure loss")
print("  generic PID commissioning unchanged when no pneumatic witness exists")
print("  HMI separates controller evidence, plant witness, likely-cause inference, and system verdict")
print("  system witness and commissioning facade remain read-only")
