#!/usr/bin/env python3
"""Lossless interaction and 14-device commissioning test gate (source-level)."""
from pathlib import Path
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
blocks = root / "src/main/java/dev/redstoneengineering/block"
names = [
    "EngineeringCompass", "AnalogIndicator", "HoneyVibrationDamper", "IronCore",
    "LapisNoiseSource", "MolecularCloudReceiver", "PhononConduit",
    "SculkVibrationInterface", "SoulFluxInjector", "SoulFluxMeter",
    "SoulSandReservoir", "SoulSoilConduit",
    "ThermalPulseEncoder", "ThermalPulseReceiver",
]
assert len(set(names)) == 14
errors = []
helper_path = blocks / "NicheDeviceCommissioning.java"
helper = helper_path.read_text(encoding="utf-8")
if "public static Inspection inspect(Level level, BlockPos pos, BlockState state)" not in helper:
    errors.append("missing common read-only device inspection")
if "player.displayClientMessage(Component.literal(inspect(" not in helper:
    errors.append("missing full-length player chat reporting")
for mutation in (
    "setBlock(", "scheduleTick(", "InformationRuntime.write(",
    "RuntimeIntStore.get(", "SoulFluxNetwork.inject(",
    "demagnetize(", "resetHistory(", "adjustNoise(",
):
    if mutation in helper:
        errors.append("unexpected commissioning-world mutation: " + mutation)
for name in names:
    code = (blocks / (name + "Block.java")).read_text(encoding="utf-8")
    if code.count("NicheDeviceCommissioning.send(serverPlayer, pos, state)") != 1:
        errors.append(name + ": inspector not mounted exactly once")
    if "useWithoutItem(" not in code:
        errors.append(name + ": no user interaction route")
    if "level.isClientSide" not in code:
        errors.append(name + ": inspector may be invoked outside server")
    if "instanceof " + name + "Block" not in helper:
        errors.append(name + ": lacks unique device-specific inspection")
    if "FieldDeviceUi.open" not in code and name != "EngineeringCompass":
        errors.append(name + ": existing HMI no longer reachable")
for name in ("IronCore", "MolecularCloudReceiver", "SculkVibrationInterface"):
    code = (blocks / (name + "Block.java")).read_text(encoding="utf-8")
    if "hit.getDirection() == Direction.DOWN" not in code:
        errors.append(name + ": no read-only face to avoid destructive Shift action")
for name in ("HoneyVibrationDamper", "PhononConduit", "ThermalPulseEncoder"):
    code = (blocks / (name + "Block.java")).read_text(encoding="utf-8")
    if "hit.getDirection() == Direction.UP" not in code:
        errors.append(name + ": missing Shift-top site survey")
for name, token in (
    ("IronCore", "demagnetize(level, pos)"),
    ("MolecularCloudReceiver", "resetHistory(level, pos)"),
    ("SculkVibrationInterface", "RuntimeIntStore.remove(level, KEY, pos)"),
    ("LapisNoiseSource", "adjustNoise(level, pos, 1)"),
):
    code = (blocks / (name + "Block.java")).read_text(encoding="utf-8")
    if token not in code:
        errors.append(name + ": previous configuration/reset shortcut lost")
for marker in (
    "adjacent engineering blocks=", "threshold margin=", "configured envelope=[",
    "coverage complete=", "transitions=", "adjacent recipients=",
    "ideal quantized output=", "no-recharge empty estimate=",
    "packet age=", "FRONT output=", "local next decay=", "decay=1 unit per",
):
    if marker not in helper:
        errors.append("missing device-specific diagnostic evidence: " + marker)
for marker in ("a declared port is not proof", "load the full aperture", "zero can be valid",
               "sender-side pulse is not guaranteed", "last displayed value may be retained"):
    if marker not in helper:
        errors.append("missing physical validity caveat: " + marker)
if errors:
    print("RSE Niche14 commissioning verifier FAILED")
    for error in errors:
        print(" -", error)
    sys.exit(1)
print("RSE Niche14 commissioning verifier PASSED")
print("14/14 registry devices have unique read-only commissioning observations")
print("Existing GUIs, save identities, and safety actions have source-level guards")
print("NOTE: real-client interaction and multiplayer/GameTests remain separately required")
