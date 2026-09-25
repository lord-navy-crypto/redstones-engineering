#!/usr/bin/env python3
"""Cross-layer regression gate for server-owned engineering parameter contracts."""
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing parameter-contract file: {rel}")
        return ""
    return path.read_text(errors="ignore")


def require(rel: str, *tokens: str) -> str:
    text = read(rel)
    for token in tokens:
        if text and token not in text:
            errors.append(f"{rel} missing parameter-contract token: {token}")
    return text


def forbid(rel: str, *tokens: str) -> None:
    text = read(rel)
    for token in tokens:
        if text and token in text:
            errors.append(f"{rel} reintroduced stale/fake parameter contract: {token}")


precision_logic = require(
    "src/main/java/dev/redstoneengineering/signal/PrecisionFilterLogic.java",
    "MIN_RATE = 1",
    "MAX_RATE = 4",
    "boundedRate",
)
require(
    "src/main/java/dev/redstoneengineering/block/PrecisionFilterBlock.java",
    'IntegerProperty.create("rate", 1, 4)',
    "PrecisionFilterLogic.boundedRate",
)
require(
    "src/main/java/dev/redstoneengineering/blockentity/PrecisionFilterBlockEntity.java",
    "PrecisionFilterLogic.boundedRate",
    "PrecisionFilterLogic.MAX_RATE",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/SignalProcessorScreen.java",
    "PrecisionFilterLogic.MIN_RATE",
    "PrecisionFilterLogic.MAX_RATE",
)
forbid(
    "src/main/java/dev/redstoneengineering/block/PrecisionFilterBlock.java",
    "Math.min(15, value)",
    "rate >= 15",
)

pulse_logic = require(
    "src/main/java/dev/redstoneengineering/signal/PulseShaperLogic.java",
    "MIN_THRESHOLD = 1",
    "MAX_THRESHOLD = 15",
    "MIN_HYSTERESIS = 1",
    "MAX_HYSTERESIS = 4",
    "MIN_WIDTH = 1",
    "MAX_WIDTH = 8",
    "boundedThreshold",
    "boundedHysteresis",
    "boundedWidth",
)
require(
    "src/main/java/dev/redstoneengineering/block/PulseShaperBlock.java",
    "PulseShaperLogic.boundedWidth",
    "PulseShaperLogic.MAX_WIDTH",
)
require(
    "src/main/java/dev/redstoneengineering/blockentity/PulseShaperBlockEntity.java",
    "PulseShaperLogic.boundedThreshold",
    "PulseShaperLogic.boundedHysteresis",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/SignalProcessorScreen.java",
    "PulseShaperLogic.MIN_WIDTH",
    "PulseShaperLogic.MAX_WIDTH",
    "PulseShaperLogic.MIN_HYSTERESIS",
    "PulseShaperLogic.MAX_HYSTERESIS",
)
forbid(
    "src/main/java/dev/redstoneengineering/block/PulseShaperBlock.java",
    "Math.min(32",
)

conditioner_logic = require(
    "src/main/java/dev/redstoneengineering/signal/SignalConditionerLogic.java",
    "MODE_SCALE = 0",
    "MODE_OFFSET = 1",
    "MODE_CLAMP = 2",
    "MODE_THRESHOLD = 3",
    "MODE_DEADBAND = 4",
    "MODE_ATTENUATE = 5",
    "minParameter",
    "maxParameter",
    "boundedParameter",
    "cycleParameter",
    "public static int apply",
    "public static boolean limiting",
)
require(
    "src/main/java/dev/redstoneengineering/block/SignalConditionerBlock.java",
    "SignalConditionerLogic.apply",
    "SignalConditionerLogic.limiting",
    "SignalConditionerLogic.defaultParameter",
    "SignalConditionerLogic.cycleParameter",
    "rotateRigidSeriesAxis",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/SignalConditionerScreen.java",
    "SignalConditionerLogic.minParameter",
    "SignalConditionerLogic.maxParameter",
    "SignalConditionerLogic.boundedParameter",
    "Stored → effective",
)
forbid(
    "src/main/java/dev/redstoneengineering/block/SignalConditionerBlock.java",
    "private static int calculate(",
    "independently configurable RX/TX faces",
)

parameters = require(
    "src/main/java/dev/redstoneengineering/physics/EngineeringDeviceParameters.java",
    "MIN_SPEED_LIMIT = 1",
    "MAX_SPEED_LIMIT = 8",
    "MIN_ACCELERATION_PERIOD = 1",
    "MAX_ACCELERATION_PERIOD = 12",
    "MIN_ACCELERATION_STEP = 1",
    "MAX_ACCELERATION_STEP = 4",
    "MIN_KP = 0",
    "MAX_KP = 12",
    "MIN_KI_DIVISOR = 0",
    "MAX_KI_DIVISOR = 64",
    "MIN_KD = 0",
    "MAX_KD = 12",
    "MIN_DERIVATIVE_SMOOTHING = 1",
    "MAX_DERIVATIVE_SMOOTHING = 16",
    "MIN_RISE_LIMIT = 1",
    "MAX_RISE_LIMIT = 15",
    "MIN_FALL_LIMIT = 1",
    "MAX_FALL_LIMIT = 15",
)

require(
    "src/main/java/dev/redstoneengineering/block/ServoActuatorBlock.java",
    "CONTROL_CYCLE_TICKS = 2",
    "MIN_SPEED_LIMIT = EngineeringDeviceParameters.ServoParameters.MIN_SPEED_LIMIT",
    "MAX_SPEED_LIMIT = EngineeringDeviceParameters.ServoParameters.MAX_SPEED_LIMIT",
    "MIN_ACCELERATION_PERIOD = EngineeringDeviceParameters.ServoParameters.MIN_ACCELERATION_PERIOD",
    "MAX_ACCELERATION_PERIOD = EngineeringDeviceParameters.ServoParameters.MAX_ACCELERATION_PERIOD",
    "MIN_ACCELERATION_STEP = EngineeringDeviceParameters.ServoParameters.MIN_ACCELERATION_STEP",
    "MAX_ACCELERATION_STEP = EngineeringDeviceParameters.ServoParameters.MAX_ACCELERATION_STEP",
)
servo_screen = require(
    "src/main/java/dev/redstoneengineering/client/ui/ServoActuatorNotebookScreen.java",
    "ServoActuatorBlock.CONTROL_CYCLE_TICKS",
    "ServoActuatorBlock.MIN_SPEED_LIMIT",
    "ServoActuatorBlock.MAX_SPEED_LIMIT",
    "ServoActuatorBlock.MIN_ACCELERATION_PERIOD",
    "ServoActuatorBlock.MAX_ACCELERATION_PERIOD",
    "ServoActuatorBlock.MIN_ACCELERATION_STEP",
    "ServoActuatorBlock.MAX_ACCELERATION_STEP",
    "theoreticalRampTicks()",
)
if "dev.redstoneengineering.physics" in servo_screen:
    errors.append("Servo client HMI crossed the persistence/physics authority boundary")

require(
    "src/main/java/dev/redstoneengineering/block/PidControllerBlock.java",
    "CONTROL_CYCLE_TICKS = 2",
    "DEADBAND_LEVELS = 1",
    "INTEGRAL_MIN = -180",
    "INTEGRAL_MAX = 180",
    "MIN_KP = EngineeringDeviceParameters.PidParameters.MIN_KP",
    "MAX_KI_DIVISOR = EngineeringDeviceParameters.PidParameters.MAX_KI_DIVISOR",
    "MAX_DERIVATIVE_SMOOTHING = EngineeringDeviceParameters.PidParameters.MAX_DERIVATIVE_SMOOTHING",
    "MAX_RISE_LIMIT = EngineeringDeviceParameters.PidParameters.MAX_RISE_LIMIT",
    "MAX_FALL_LIMIT = EngineeringDeviceParameters.PidParameters.MAX_FALL_LIMIT",
)
pid_screen = require(
    "src/main/java/dev/redstoneengineering/client/ui/PidEngineeringNotebookScreen.java",
    "PidControllerBlock.CONTROL_CYCLE_TICKS",
    "PidControllerBlock.MIN_KP",
    "PidControllerBlock.MAX_KI_DIVISOR",
    "PidControllerBlock.MAX_DERIVATIVE_SMOOTHING",
    "PidControllerBlock.MAX_RISE_LIMIT",
    "PidControllerBlock.MAX_FALL_LIMIT",
    "fullScaleRiseTicks()",
    "fullScaleFallTicks()",
)
if "dev.redstoneengineering.physics" in pid_screen:
    errors.append("PID client HMI crossed the persistence/physics authority boundary")

require(
    "src/main/java/dev/redstoneengineering/block/LapisLowPassFilterBlock.java",
    "FILTER_SAMPLE_TICKS = 2",
    "scheduleTick(pos, this, FILTER_SAMPLE_TICKS)",
)
require(
    "src/main/java/dev/redstoneengineering/ui/menu/LapisLowPassFilterMenu.java",
    "LapisLowPassFilterBlock.FILTER_SAMPLE_TICKS",
    "samplePeriodSeconds",
    "pole()",
    "step90Samples",
    "step90Ticks",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/LapisLowPassFilterScreen.java",
    "menu.sampleTicks()",
    "menu.samplePeriodSeconds()",
    "menu.pole()",
    "menu.step90Samples()",
    "menu.step90Ticks()",
)

require(
    "src/main/java/dev/redstoneengineering/block/QuartzOscillatorBlock.java",
    "MIN_PERIOD_TICKS = 2",
    "MAX_PERIOD_TICKS = 200",
    "DEFAULT_PERIOD_TICKS = 8",
    "FINE_STEP_TICKS = 1",
    "COARSE_STEP_TICKS = 5",
    "setter must never inject an early edge",
)
require(
    "src/main/java/dev/redstoneengineering/block/QuartzClockDividerBlock.java",
    "MIN_DIVISION = 2",
    "MAX_DIVISION = 32",
    "PARAMETER_STEP = 1",
    "MAX_OUTPUT_PERIOD_TICKS = 4096",
    "Math.min(MAX_OUTPUT_PERIOD_TICKS",
)
require(
    "src/main/java/dev/redstoneengineering/block/QuartzPhaseDelayBlock.java",
    "MIN_DELAY_TICKS = 1",
    "MAX_DELAY_TICKS = 32",
    "PARAMETER_STEP_TICKS = 1",
    "QUEUE_CAPACITY = 8",
)
require(
    "src/main/java/dev/redstoneengineering/ui/menu/QuartzTimingMenu.java",
    "QuartzClockDividerBlock.PARAMETER_STEP",
    "QuartzPhaseDelayBlock.PARAMETER_STEP_TICKS",
)
quartz_screen = require(
    "src/main/java/dev/redstoneengineering/client/ui/QuartzTimingScreen.java",
    "QuartzClockDividerBlock.MIN_DIVISION",
    "QuartzClockDividerBlock.MAX_DIVISION",
    "QuartzClockDividerBlock.MAX_OUTPUT_PERIOD_TICKS",
    "QuartzPhaseDelayBlock.MIN_DELAY_TICKS",
    "QuartzPhaseDelayBlock.MAX_DELAY_TICKS",
    "QuartzPhaseDelayBlock.QUEUE_CAPACITY",
)
if "dev.redstoneengineering.physics" in quartz_screen:
    errors.append("Quartz client HMI crossed the persistence/physics authority boundary")

if errors:
    print("RSE parameter-contract consistency verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE parameter-contract consistency verification: PASS")
print(" Precision Filter effective 1..4 slew authority: PASS")
print(" Pulse Shaper threshold/hysteresis/width authority: PASS")
print(" Signal Conditioner pure transfer/range authority: PASS")
print(" Servo persistent bounds + 2-tick control cycle + client boundary: PASS")
print(" PID persistent bounds + deadband/integral/cycle + client boundary: PASS")
print(" Lapis LPF synchronized alpha + 2-tick sample/derived response contract: PASS")
print(" Quartz oscillator/divider/delay bounds + edge-safe timing contract: PASS")
