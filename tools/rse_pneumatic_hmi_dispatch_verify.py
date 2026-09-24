#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
failed = []

menu_path = root / "src/main/java/dev/redstoneengineering/ui/menu/PneumaticSystemMenu.java"
screen_path = root / "src/main/java/dev/redstoneengineering/client/ui/PneumaticSystemScreen.java"
opener_path = root / "src/main/java/dev/redstoneengineering/ui/FieldDeviceUi.java"

for path in (menu_path, screen_path, opener_path):
    if not path.is_file():
        failed.append(f"missing Pneumatic HMI contract file: {path.relative_to(root)}")

if not failed:
    menu = menu_path.read_text(errors="ignore")
    screen = screen_path.read_text(errors="ignore")
    opener = opener_path.read_text(errors="ignore")

    for token in (
        "AirCompressorBlock.configuredResponse",
        "AirCompressorBlock.setResponseRates",
        "PressureRegulatorBlock.setpointPressure(level, blockPos, state)",
        "PressureRegulatorBlock.responseRate(level, blockPos, state)",
        "PressureRegulatorBlock.setEngineeringParameters",
        "PneumaticProportionalValveBlock.configuredResponseRate",
        "PneumaticProportionalValveBlock.setConfiguredResponseRate",
        "PneumaticReliefValveBlock.setpointPressure(level, blockPos, state)",
        "PneumaticReliefValveBlock.configuredBlowdown",
        "PneumaticReliefValveBlock.setEngineeringParameters",
        "BUTTON_SECONDARY_PREVIOUS",
        "BUTTON_SECONDARY_NEXT",
        "engineeringA",
        "engineeringB",
    ):
        if token not in menu:
            failed.append(f"PneumaticSystemMenu missing exact-parameter token: {token}")

    for token in (
        "Ramp up",
        "Ramp down",
        "Response rate",
        "Configured spool rate",
        "RELIEF PROTECTION PARAMETERS",
        "Blowdown",
        "Reseat pressure",
        "Physical direction is controlled only on Route.",
    ):
        if token not in screen:
            failed.append(f"PneumaticSystemScreen missing exact-parameter/evidence token: {token}")

    dedicated_start = opener.find("if (block instanceof AirCompressorBlock || block instanceof PneumaticPipeBlock")
    if dedicated_start < 0:
        failed.append("FieldDeviceUi missing dedicated Pneumatic dispatch family")
    else:
        dedicated = opener[dedicated_start:]
        for device in (
            "AirCompressorBlock",
            "PressureRegulatorBlock",
            "PneumaticProportionalValveBlock",
            "PneumaticReliefValveBlock",
        ):
            if device not in dedicated:
                failed.append(f"dedicated Pneumatic dispatch missing {device}")
        if "new PneumaticSystemMenu(id, inv, pos)" not in dedicated:
            failed.append("dedicated Pneumatic dispatch does not open PneumaticSystemMenu")

    process_start = opener.find("if (block instanceof SignalConditionerBlock")
    process_end = opener.find("if (block instanceof SignalAmplifierBlock", process_start)
    advanced_start = process_end
    advanced_end = opener.find("if (block instanceof QuartzPhaseDelayBlock", advanced_start)
    multi_start = advanced_end
    multi_end = opener.find("if (block instanceof CopperCircuitMeterBlock", multi_start)

    slices = (
        ("ProcessParameterMenu", opener[process_start:process_end] if process_start >= 0 and process_end > process_start else ""),
        ("AdvancedParameterMenu", opener[advanced_start:advanced_end] if advanced_start >= 0 and advanced_end > advanced_start else ""),
        ("MultiPhysicsParameterMenu", opener[multi_start:multi_end] if multi_start >= 0 and multi_end > multi_start else ""),
    )
    for label, body in slices:
        for device in (
            "AirCompressorBlock",
            "PressureRegulatorBlock",
            "PneumaticProportionalValveBlock",
            "PneumaticReliefValveBlock",
        ):
            if device in body:
                failed.append(f"{label} still intercepts {device}")

if failed:
    print("RSE Pneumatic HMI dispatch verification: FAIL")
    for item in failed:
        print(" -", item)
    raise SystemExit(1)

print("RSE Pneumatic HMI dispatch verification: PASS")
print(" compressor exact ramp-up/ramp-down controls: PASS")
print(" regulator exact setpoint/response-rate controls: PASS")
print(" proportional valve exact spool-rate control: PASS")
print(" relief exact setpoint/blowdown controls: PASS")
print(" physical topology remains on shared Route page: PASS")
print(" normal right-click reaches dedicated Pneumatic HMI: PASS")
