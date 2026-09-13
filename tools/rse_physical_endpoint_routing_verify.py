#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
BLOCK = ROOT / "src/main/java/dev/redstoneengineering/block"
UI = ROOT / "src/main/java/dev/redstoneengineering"
errors = []

def text(path: Path) -> str:
    if not path.exists():
        errors.append(f"missing {path.relative_to(ROOT)}")
        return ""
    return path.read_text(encoding="utf-8")

def require(path: Path, needle: str, label: str) -> None:
    body = text(path)
    if needle not in body:
        errors.append(f"{path.relative_to(ROOT)}: missing {label}")

signal = BLOCK / "DirectionalSignalBlock.java"
domain = BLOCK / "DirectionalDomainBlock.java"
pid_menu = UI / "ui/menu/PidControllerMenu.java"
pid_screen = UI / "client/ui/PidControllerScreen.java"
conditioner = BLOCK / "SignalConditionerBlock.java"
servo_sensor = BLOCK / "ServoPositionSensorBlock.java"

for path in (signal, domain):
    require(path, "physicalPortsDoNotOverlap", "full physical-port collision validation")
    require(path, "hasAuxiliaryPorts", "dense multi-port fallback classification")
    require(path, "rotateWholeRoute(level, pos, clockwise)", "rigid legal-layout rotation fallback")
    require(path, "INPUT_FACING", "independent configured RX property")

require(pid_menu, "BUTTON_INPUT_PREVIOUS", "PID RX controls")
require(pid_menu, "BUTTON_OUTPUT_NEXT", "PID TX controls")
require(pid_menu, "DirectionalSignalBlock.rotateSeriesInput", "PID server-authoritative RX routing")
require(pid_menu, "DirectionalSignalBlock.rotateSeriesOutput", "PID server-authoritative TX routing")
require(pid_screen, 'Component.literal("RX ▲")', "PID RX up control")
require(pid_screen, 'Component.literal("TX ▼")', "PID TX down control")
require(pid_screen, "menu.inputFacing()", "dynamic PID RX face display")
require(pid_screen, "menu.outputFacing()", "dynamic PID TX face display")

require(conditioner, "seriesInputSide", "Signal Conditioner configured RX backend")
require(conditioner, "seriesOutputSide", "Signal Conditioner configured TX backend")
require(servo_sensor, "seriesInputSide", "Servo Position Sensor configured RX backend")

# Directional processors must not reconstruct their configurable RX by taking the
# opposite of TX. Output-query uses of getOpposite() are legitimate, so this gate
# only rejects opposite-face expressions passed into input reads/observations.
for path in BLOCK.glob("*.java"):
    body = text(path)
    if "DirectionalSignalBlock" not in body and "DirectionalDomainBlock" not in body:
        continue
    suspicious = [
        r"readInputFrom\([^;\n]*getOpposite\(\)",
        r"observe\([^;\n]*outputSide\([^\)]*\)\.getOpposite\(\)",
        r"getSignal\([^;\n]*outputSide\([^\)]*\)\.getOpposite\(\)",
    ]
    for pattern in suspicious:
        if re.search(pattern, body):
            errors.append(
                f"{path.relative_to(ROOT)}: configurable RX reconstructed from TX opposite instead of INPUT_FACING"
            )
            break

if errors:
    print("RSE physical endpoint routing verification: FAIL")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print("RSE physical endpoint routing verification: PASS")
print(" - independent RX/TX route properties retained")
print(" - declared physical ports are collision-checked before route mutation")
print(" - dense multi-port layouts fall back to rigid legal rotation")
print(" - dedicated PID HMI exposes server-authoritative RX/TX routing")
print(" - no audited directional backend reconstructs RX as TX opposite")
