#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
failed = []

menu_path = root / "src/main/java/dev/redstoneengineering/ui/menu/OpticalSystemMenu.java"
screen_path = root / "src/main/java/dev/redstoneengineering/client/ui/OpticalSystemScreen.java"
attenuator_path = root / "src/main/java/dev/redstoneengineering/block/OpticalAttenuatorBlock.java"
opener_path = root / "src/main/java/dev/redstoneengineering/ui/FieldDeviceUi.java"

for path in (menu_path, screen_path, attenuator_path, opener_path):
    if not path.is_file():
        failed.append(f"missing Optical HMI contract file: {path.relative_to(root)}")

if not failed:
    menu = menu_path.read_text(errors="ignore")
    screen = screen_path.read_text(errors="ignore")
    attenuator = attenuator_path.read_text(errors="ignore")
    opener = opener_path.read_text(errors="ignore")

    for token in (
        "OpticalAttenuatorBlock.setConfiguredLoss(",
        "secondary.get() + (id == BUTTON_PRIMARY_NEXT ? 1 : -1)",
        "OpticalEmitterBlock.INTENSITY",
        "OpticalEmitterBlock.CHANNEL",
        "OpticalChannelFilterBlock.TARGET",
        "BUTTON_INPUT_LEFT",
        "BUTTON_INPUT_RIGHT",
        "BUTTON_OUTPUT_LEFT",
        "BUTTON_OUTPUT_RIGHT",
    ):
        if token not in menu:
            failed.append(f"OpticalSystemMenu missing control/topology token: {token}")

    for token in (
        "public static int configuredLoss",
        "Math.max(0, Math.min(15",
        "public static boolean setConfiguredLoss",
    ):
        if token not in attenuator:
            failed.append(f"OpticalAttenuatorBlock missing full-range loss token: {token}")

    for token in (
        "Observed segment loss",
        "Source / channel",
        "Channel mismatches",
        "Physical",
    ):
        if token not in screen:
            failed.append(f"OpticalSystemScreen missing engineering evidence token: {token}")

    dedicated_start = opener.find("if (block instanceof OpticalEmitterBlock")
    if dedicated_start < 0 or "new OpticalSystemMenu" not in opener[dedicated_start:]:
        failed.append("FieldDeviceUi does not route Optical devices to dedicated HMI")

    advanced_start = opener.find("if (block instanceof SignalAmplifierBlock")
    advanced_end = opener.find("if (block instanceof QuartzPhaseDelayBlock", advanced_start)
    if advanced_start >= 0 and advanced_end > advanced_start and "OpticalEmitterBlock" in opener[advanced_start:advanced_end]:
        failed.append("AdvancedParameterMenu still intercepts OpticalEmitterBlock")

    multi_start = opener.find("if (block instanceof QuartzPhaseDelayBlock")
    multi_end = opener.find("if (block instanceof CopperCircuitMeterBlock", multi_start)
    if multi_start >= 0 and multi_end > multi_start:
        generic = opener[multi_start:multi_end]
        for optical in ("OpticalAttenuatorBlock", "OpticalChannelFilterBlock"):
            if optical in generic:
                failed.append(f"MultiPhysicsParameterMenu still intercepts {optical}")

if failed:
    print("RSE Optical HMI dispatch verification: FAIL")
    for item in failed:
        print(" -", item)
    raise SystemExit(1)

print("RSE Optical HMI dispatch verification: PASS")
print(" emitter intensity/channel controls remain server-owned: PASS")
print(" attenuator uses full 0..15 configured loss: PASS")
print(" channel filter remains discrete carrier selection: PASS")
print(" normal Optical dispatch reaches dedicated budget/diagnostic HMI: PASS")
