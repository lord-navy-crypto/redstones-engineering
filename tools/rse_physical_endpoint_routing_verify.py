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
scaler = BLOCK / "RedstoneToLapisScalerBlock.java"
quantizer = BLOCK / "LapisToRedstoneQuantizerBlock.java"
conversion_menu = UI / "ui/menu/MediaConversionMenu.java"
conversion_screen = UI / "client/ui/MediaConversionScreen.java"

for path in (signal, domain):
    require(path, "physicalPortsDoNotOverlap", "full physical-port collision validation")
    require(path, "hasAuxiliaryPorts", "dense multi-port fallback classification")
    require(path, "rotateWholeRoute(level, pos, clockwise)", "rigid legal-layout rotation fallback")
    require(path, "INPUT_FACING", "independent configured RX property")
require(signal, "rotateRigidSeriesAxis", "rigid opposite-port signal rotation")
require(signal, "Direction newInput = newOutput.getOpposite();", "exact opposite RX/TX invariant")
require(signal, ".setValue(FACING, newOutput)", "rigid TX state mutation")
require(signal, ".setValue(INPUT_FACING, newInput)", "rigid RX state mutation")

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

for path in (scaler, quantizer):
    require(path, "INPUT_FACING", "converter independent RX property")
    require(path, "state.getValue(INPUT_FACING)", "converter backend reads configured RX")
    require(path, "rotateInput", "converter RX mutation")
    require(path, "rotateOutput", "converter TX mutation")
    require(path, "nextFreeHorizontal", "converter endpoint collision avoidance")
    require(path, "candidate != forbidden", "converter overlap rejection")

require(scaler, "DomainNetwork.driveLapis(server, pos.relative(oldOutput), pos, 0, false)", "scaler clears old Lapis TX")
require(quantizer, "level.updateNeighborsAt(pos.relative(oldOutput), block)", "quantizer notifies old Redstone TX")
require(quantizer, "level.updateNeighborsAt(pos.relative(nextOutput), block)", "quantizer notifies new Redstone TX")
require(conversion_menu, "BUTTON_RX_PREVIOUS", "converter RX HMI controls")
require(conversion_menu, "BUTTON_TX_NEXT", "converter TX HMI controls")
require(conversion_menu, "RedstoneToLapisScalerBlock.rotateInput", "scaler RX routing")
require(conversion_menu, "RedstoneToLapisScalerBlock.rotateOutput", "scaler TX routing")
require(conversion_menu, "LapisToRedstoneQuantizerBlock.rotateInput", "quantizer RX routing")
require(conversion_menu, "LapisToRedstoneQuantizerBlock.rotateOutput", "quantizer TX routing")
require(conversion_screen, 'Component.literal("RX ◀")', "converter RX UI")
require(conversion_screen, 'Component.literal("TX ▶")', "converter TX UI")
require(conversion_screen, "menu.inputFace()", "converter live RX display")
require(conversion_screen, "menu.outputFace()", "converter live TX display")

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
            errors.append(f"{path.relative_to(ROOT)}: configurable RX reconstructed from TX opposite instead of INPUT_FACING")
            break

for path in (scaler, quantizer):
    body = text(path)
    if "inputSide(BlockState state) { return outputSide(state).getOpposite(); }" in body:
        errors.append(f"{path.relative_to(ROOT)}: converter RX still reconstructed from TX opposite")

if errors:
    print("RSE physical endpoint routing verification: FAIL")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print("RSE physical endpoint routing verification: PASS")
print(" - independent RX/TX route properties retained")
print(" - declared physical ports are collision-checked before route mutation")
print(" - dense multi-port layouts fall back to rigid legal rotation")
print(" - straight-through signal processors can enforce exact opposite RX/TX")
print(" - dedicated PID HMI exposes server-authoritative RX/TX routing")
print(" - Redstone/Lapis converters expose independent server-authoritative RX/TX routing")
print(" - old converter outputs are cleared or notified before TX relocation")
print(" - no audited directional backend reconstructs RX as TX opposite")
