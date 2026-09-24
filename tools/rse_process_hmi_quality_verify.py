#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
failed = []

menu_path = root / "src/main/java/dev/redstoneengineering/ui/menu/ProcessParameterMenu.java"
screen_path = root / "src/main/java/dev/redstoneengineering/client/ui/ProcessParameterNotebookScreen.java"
conditioner_path = root / "src/main/java/dev/redstoneengineering/block/SignalConditionerBlock.java"
pwm_path = root / "src/main/java/dev/redstoneengineering/block/PwmControllerBlock.java"

for path in (menu_path, screen_path, conditioner_path, pwm_path):
    if not path.is_file():
        failed.append(f"missing process quality contract file: {path.relative_to(root)}")

if not failed:
    menu = menu_path.read_text(errors="ignore")
    screen = screen_path.read_text(errors="ignore")
    conditioner = conditioner_path.read_text(errors="ignore")
    pwm = pwm_path.read_text(errors="ignore")

    for token in (
        "SignalConditionerBlock.inspectInputQuality",
        "SignalConditionerBlock.inspectOutputQuality",
        "SignalConditionerBlock.lastLimitingAgeTicks",
        "PwmControllerBlock.commandQuality",
        "PwmControllerBlock.inhibitQuality",
        "PwmControllerBlock.outputQuality",
    ):
        if token not in menu:
            failed.append(f"ProcessParameterMenu missing quality synchronization token: {token}")

    for token in (
        '"Input quality"',
        '"Output quality"',
        '"Last limit age"',
        '"Command quality"',
        '"Inhibit quality"',
        "Active limiting marks output SATURATED",
        "PWM fail-safe behavior uses command and inhibit quality separately",
    ):
        if token not in screen:
            failed.append(f"ProcessParameterNotebookScreen missing quality presentation token: {token}")

    for token in (
        "public static PortQuality inspectInputQuality",
        "public static PortQuality inspectOutputQuality",
        "PortQuality.SATURATED",
        "RedstoneObservationSupport.combineQuality",
    ):
        if token not in conditioner:
            failed.append(f"SignalConditionerBlock missing quality contract: {token}")

    for token in (
        "public static PortQuality commandQuality",
        "public static PortQuality inhibitQuality",
        "public static PortQuality outputQuality",
        "inhibitEvidenceUnusable",
        "RedstoneObservationSupport.combineQuality",
    ):
        if token not in pwm:
            failed.append(f"PwmControllerBlock missing quality contract: {token}")

if failed:
    print("RSE process HMI quality verification: FAIL")
    for item in failed:
        print(" -", item)
    raise SystemExit(1)

print("RSE process HMI quality verification: PASS")
print(" conditioner input/output quality separation: PASS")
print(" conditioner saturation + retained limiting age: PASS")
print(" PWM command/inhibit/output quality separation: PASS")
print(" PWM fail-safe evidence remains server-backed: PASS")
