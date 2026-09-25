#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors = []

def read(rel):
    path = root / rel
    if not path.is_file():
        errors.append(f"missing UI dispatch contract file: {rel}")
        return ""
    return path.read_text(errors="ignore")

field = read("src/main/java/dev/redstoneengineering/ui/FieldDeviceUi.java")
client = read("src/main/java/dev/redstoneengineering/client/ui/EngineeringUiClientRegistration.java")
multi_menu = read("src/main/java/dev/redstoneengineering/ui/menu/MultiPhysicsParameterMenu.java")
multi_screen = read("src/main/java/dev/redstoneengineering/client/ui/MultiPhysicsParameterNotebookScreen.java")

# Dedicated HMIs must win normal gameplay dispatch.
dedicated = {
    "SignalConditionerBlock": "new SignalConditionerMenu",
    "RangeSensorBlock": "new RangeSensorMenu",
    "PrecisionFilterBlock || block instanceof EdgeDetectorBlock || block instanceof PulseShaperBlock": "new SignalProcessorMenu",
    "QuartzOscillatorBlock || block instanceof QuartzLabOscillatorBlock": "new QuartzTimingMenu",
    "RadioTransmitterBlock || block instanceof RadioReceiverBlock": "new RadioLinkMenu",
    "RedstoneByteEncoderBlock || block instanceof ByteToRedstoneDecoderBlock || block instanceof SerializerBlock": "new DigitalCommunicationMenu",
    "AirCompressorBlock || block instanceof PneumaticPipeBlock || block instanceof AirReservoirBlock": "new PneumaticSystemMenu",
    "OpticalEmitterBlock || block instanceof OpticalReceiverBlock || block instanceof OpticalPowerMeterBlock": "new OpticalSystemMenu",
    "AmethystResonatorBlock || block instanceof AmethystFrequencyFilterBlock": "new AmethystSystemMenu",
    "ElectromagnetBlock || block instanceof PermanentMagnetBlock || block instanceof InductionCoilBlock": "new MagneticSystemMenu",
    "ServoActuatorBlock": "new ServoActuatorMenu",
    "WatchdogBlock || block instanceof ServoPositionSensorBlock": "new ReliabilitySystemMenu",
}
for family, opener in dedicated.items():
    if family not in field:
        errors.append(f"FieldDeviceUi missing dedicated family dispatch: {family}")
    if opener not in field:
        errors.append(f"FieldDeviceUi missing dedicated menu opener: {opener}")

# Process Notebook is an active generic HMI only for this bounded set.
for token in (
    "block instanceof PwmControllerBlock",
    "block instanceof RedstoneCopperDriverBlock",
    "block instanceof CopperCapacitorBlock",
    "block instanceof CopperFuseBlock",
    "block instanceof CopperVoltageSourceBlock",
    "block instanceof HoneyVibrationDamperBlock",
    "block instanceof MechanicalExciterBlock",
    "block instanceof LapisPrecisionSourceBlock",
    "new ProcessParameterMenu",
):
    if token not in field:
        errors.append(f"FieldDeviceUi missing active Process dispatch token: {token}")

# Advanced Notebook is an active generic HMI only for these three devices.
for token in (
    "block instanceof SignalAmplifierBlock",
    "block instanceof LapisNoiseSourceBlock",
    "block instanceof LapisPrecisionRangeSensorBlock",
    "new AdvancedParameterMenu",
):
    if token not in field:
        errors.append(f"FieldDeviceUi missing active Advanced dispatch token: {token}")

# The old MultiPhysics transport remains registered for compatibility, but must never steal
# normal right-click dispatch from the dedicated family HMIs.
for forbidden in (
    "MultiPhysicsParameterMenu",
    "new MultiPhysicsParameterMenu",
):
    if forbidden in field:
        errors.append(f"FieldDeviceUi reintroduced legacy MultiPhysics dispatch: {forbidden}")

for token in (
    "Legacy compatibility parameter transport",
    "Normal gameplay dispatch now prefers each family's dedicated HMI",
    "without becoming the primary right-click path",
):
    if token not in multi_menu:
        errors.append(f"MultiPhysicsParameterMenu missing compatibility-only contract: {token}")

for token in (
    "Legacy compatibility notebook",
    "normal gameplay uses the corresponding dedicated family HMI",
):
    if token not in multi_screen:
        errors.append(f"MultiPhysicsParameterNotebookScreen missing compatibility-only contract: {token}")

# Client screen registration must reflect the current real screens, not legacy visual classes.
for token in (
    "SignalConditionerScreen::new",
    "PidEngineeringNotebookScreen::new",
    "ServoActuatorNotebookScreen::new",
    "ProcessParameterNotebookScreen::new",
    "AdvancedParameterNotebookScreen::new",
    "MultiPhysicsParameterNotebookScreen::new",
    "RangeSensorScreen::new",
    "SignalProcessorScreen::new",
    "QuartzTimingScreen::new",
    "PneumaticSystemScreen::new",
    "OpticalSystemScreen::new",
    "AmethystSystemScreen::new",
    "MagneticSystemScreen::new",
):
    if token not in client:
        errors.append(f"EngineeringUiClientRegistration missing current screen mapping: {token}")

if "PidControllerScreen::new" in client:
    errors.append("EngineeringUiClientRegistration restored obsolete PID visual screen")

# Keep the Signal Conditioner fix locked: it has its own dedicated route-capable HMI and may not
# drift back into ProcessParameterMenu normal gameplay dispatch.
process_start = field.find("if (block instanceof PwmControllerBlock")
process_end = field.find("if (block instanceof SignalAmplifierBlock", process_start)
if process_start >= 0 and process_end > process_start and "SignalConditionerBlock" in field[process_start:process_end]:
    errors.append("Signal Conditioner drifted back into ProcessParameterMenu normal dispatch")

if errors:
    print("RSE UI dispatch authority verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE UI dispatch authority verification: PASS")
print(" dedicated family HMIs win normal gameplay dispatch: PASS")
print(" Signal Conditioner dedicated HMI cannot be shadowed by Process notebook: PASS")
print(" Process notebook active generic device set is explicit: PASS")
print(" Advanced notebook active generic device set is explicit: PASS")
print(" MultiPhysics notebook is compatibility-only and cannot reclaim normal right-click dispatch: PASS")
print(" current client screen registrations are guarded: PASS")
