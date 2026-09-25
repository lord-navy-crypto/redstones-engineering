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
    "src/main/java/dev/redstoneengineering/signal/OpticalPassiveLogic.java",
    "MIN_INTENSITY = 0",
    "MAX_INTENSITY = 15",
    "MIN_CONFIGURED_LOSS = 0",
    "MAX_CONFIGURED_LOSS = 15",
    "MIN_LEGACY_LOSS = 0",
    "MAX_LEGACY_LOSS = 8",
    "DEFAULT_LEGACY_LOSS = 2",
    "MIN_CHANNEL = 0",
    "MAX_CHANNEL = 15",
    "FILTER_INSERTION_LOSS = 1",
    "TRANSFER_TICK_TICKS = 2",
    "CONFIGURATION_RECHECK_TICKS = 1",
    "attenuatedIntensity",
    "fullyAttenuated",
    "channelMatched",
    "filteredIntensity",
)
require(
    "src/main/java/dev/redstoneengineering/block/OpticalAttenuatorBlock.java",
    "OpticalPassiveLogic.boundedConfiguredLoss",
    "OpticalPassiveLogic.boundedLegacyLoss",
    "OpticalPassiveLogic.attenuatedIntensity",
    "OpticalPassiveLogic.fullyAttenuated",
)
require(
    "src/main/java/dev/redstoneengineering/block/OpticalChannelFilterBlock.java",
    "OpticalPassiveLogic.boundedChannel",
    "OpticalPassiveLogic.channelMatched",
    "OpticalPassiveLogic.filteredIntensity",
    "OpticalPassiveLogic.FILTER_INSERTION_LOSS",
)
require(
    "src/main/java/dev/redstoneengineering/ui/menu/OpticalSystemMenu.java",
    "legacyParameter.set(state.getValue(OpticalAttenuatorBlock.LOSS))",
    "routeRigidDomain(id)",
    "DirectionalDomainBlock.rotateRigidSeriesAxis",
    "public boolean rigidSeriesRoute()",
    "public Direction inputDirection()",
)
optical_screen = require(
    "src/main/java/dev/redstoneengineering/client/ui/OpticalSystemScreen.java",
    "OpticalPassiveLogic.MIN_CONFIGURED_LOSS",
    "OpticalPassiveLogic.MAX_CONFIGURED_LOSS",
    "OpticalPassiveLogic.MIN_LEGACY_LOSS",
    "OpticalPassiveLogic.MAX_LEGACY_LOSS",
    "OpticalPassiveLogic.FILTER_INSERTION_LOSS",
    "menu.inputDirection()",
    "Rigid optical axis",
)
if "dev.redstoneengineering.physics" in optical_screen:
    errors.append("Optical client HMI crossed the persistence/physics authority boundary")

require(
    "src/main/java/dev/redstoneengineering/signal/RedstoneCopperDriverLogic.java",
    "MIN_VOLTAGE = 0",
    "MAX_VOLTAGE = 15",
    "MIN_SLEW = 1",
    "MAX_SLEW = 15",
    "MIN_LEGACY_SLEW_MODE = 0",
    "MAX_LEGACY_SLEW_MODE = 2",
    "DEFAULT_LEGACY_SLEW_MODE = 1",
    "LEGACY_SLEW_SLOW = 1",
    "LEGACY_SLEW_NORMAL = 2",
    "LEGACY_SLEW_FAST = 4",
    "CONTROL_TICK_TICKS = 1",
    "boundedVoltage",
    "boundedSlew",
    "slewForLegacyMode",
    "moveToward",
    "trackingError",
    "fullScaleRampTicks",
)
require(
    "src/main/java/dev/redstoneengineering/block/RedstoneCopperDriverBlock.java",
    "RedstoneCopperDriverLogic.boundedSlew",
    "RedstoneCopperDriverLogic.moveToward",
    "RedstoneCopperDriverLogic.trackingError",
    "RedstoneCopperDriverLogic.CONTROL_TICK_TICKS",
)
require(
    "src/main/java/dev/redstoneengineering/ui/menu/ProcessParameterMenu.java",
    "p2.set(state.getValue(RedstoneCopperDriverBlock.SLEW))",
    "RedstoneCopperDriverBlock.trackingError",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/ProcessParameterNotebookScreen.java",
    "RedstoneCopperDriverLogic.MIN_SLEW",
    "RedstoneCopperDriverLogic.MAX_SLEW",
    "RedstoneCopperDriverLogic.fullScaleRampTicks",
    "RedstoneCopperDriverLogic.slewForLegacyMode",
)

require(
    "src/main/java/dev/redstoneengineering/signal/PwmCarrierLogic.java",
    "MIN_COMMAND = 0",
    "MAX_COMMAND = 15",
    "MIN_CONFIGURED_PERIOD_TICKS = 2",
    "MAX_CONFIGURED_PERIOD_TICKS = 64",
    "boundedCommand",
    "boundedConfiguredPeriod",
    "dutyQuantumPermille",
)
require(
    "src/main/java/dev/redstoneengineering/block/PwmControllerBlock.java",
    "MIN_LEGACY_PERIOD_MODE = 0",
    "MAX_LEGACY_PERIOD_MODE = 3",
    "DEFAULT_LEGACY_PERIOD_MODE = 2",
    "LEGACY_PERIOD_FAST = 4",
    "LEGACY_PERIOD_MEDIUM = 8",
    "LEGACY_PERIOD_DEFAULT = 16",
    "LEGACY_PERIOD_SLOW = 32",
    "PwmCarrierLogic.boundedConfiguredPeriod",
    "CARRIER_TICK_TICKS = 1",
)
require(
    "src/main/java/dev/redstoneengineering/ui/menu/ProcessParameterMenu.java",
    "p2.set(state.getValue(PwmControllerBlock.PERIOD_MODE))",
    "PwmControllerBlock.setConfiguredPeriod",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/ProcessParameterNotebookScreen.java",
    "PwmCarrierLogic.MIN_CONFIGURED_PERIOD_TICKS",
    "PwmCarrierLogic.MAX_CONFIGURED_PERIOD_TICKS",
    "PwmCarrierLogic.dutyQuantumPermille",
    "PwmControllerBlock.periodFor(menu.p2())",
)

require(
    "src/main/java/dev/redstoneengineering/signal/LapisPrecisionRangeSensorLogic.java",
    "MIN_RANGE_BLOCKS = 1",
    "MAX_RANGE_BLOCKS = 128",
    "MIN_LEGACY_RANGE_INDEX = 0",
    "MAX_LEGACY_RANGE_INDEX = 3",
    "DEFAULT_LEGACY_RANGE_INDEX = 1",
    "LEGACY_RANGE_SHORT = 8",
    "LEGACY_RANGE_MEDIUM = 16",
    "LEGACY_RANGE_LONG = 32",
    "LEGACY_RANGE_EXTENDED = 64",
    "MIN_NORMALIZED_OUTPUT = 0",
    "MAX_NORMALIZED_OUTPUT = 100",
    "boundedRange",
    "rangeForLegacyIndex",
    "normalizedDistance",
)
require(
    "src/main/java/dev/redstoneengineering/block/LapisPrecisionRangeSensorBlock.java",
    "LapisPrecisionRangeSensorLogic.boundedRange",
    "LapisPrecisionRangeSensorLogic.rangeForLegacyIndex",
    "LapisPrecisionRangeSensorLogic.normalizedDistance",
)
require(
    "src/main/java/dev/redstoneengineering/ui/menu/AdvancedParameterMenu.java",
    "p1.set(state.getValue(LapisPrecisionRangeSensorBlock.RANGE_INDEX))",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/AdvancedParameterNotebookScreen.java",
    "LapisPrecisionRangeSensorLogic.MIN_RANGE_BLOCKS",
    "LapisPrecisionRangeSensorLogic.MAX_RANGE_BLOCKS",
    "LapisPrecisionRangeSensorLogic.LEGACY_RANGE_SHORT",
    "LapisPrecisionRangeSensorLogic.LEGACY_RANGE_MEDIUM",
    "LapisPrecisionRangeSensorLogic.LEGACY_RANGE_LONG",
    "LapisPrecisionRangeSensorLogic.LEGACY_RANGE_EXTENDED",
    "LapisPrecisionRangeSensorLogic.MAX_NORMALIZED_OUTPUT",
    "Current legacy preset=",
)

require(
    "src/main/java/dev/redstoneengineering/signal/SignalAmplifierLogic.java",
    "MIN_SIGNAL = 0",
    "MAX_SIGNAL = 15",
    "MIN_GAIN = 1",
    "MAX_GAIN = 8",
    "MIN_LEGACY_GAIN_MODE = 0",
    "MAX_LEGACY_GAIN_MODE = 3",
    "DEFAULT_LEGACY_GAIN_MODE = 1",
    "gainForLegacyMode",
    "boundedGain",
    "rawOutput",
    "output",
    "clipping",
)
require(
    "src/main/java/dev/redstoneengineering/block/SignalAmplifierBlock.java",
    "SignalAmplifierLogic.MIN_LEGACY_GAIN_MODE",
    "SignalAmplifierLogic.MAX_LEGACY_GAIN_MODE",
    "SignalAmplifierLogic.DEFAULT_LEGACY_GAIN_MODE",
    "SignalAmplifierLogic.boundedGain",
    "SignalAmplifierLogic.rawOutput",
    "SignalAmplifierLogic.clipping",
    "SignalAmplifierLogic.output",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/AdvancedParameterNotebookScreen.java",
    "SignalAmplifierLogic.MIN_GAIN",
    "SignalAmplifierLogic.MAX_GAIN",
    "SignalAmplifierLogic.MIN_SIGNAL",
    "SignalAmplifierLogic.MAX_SIGNAL",
    "Shift-click compatibility presets cover ×1..×4",
)

require(
    "src/main/java/dev/redstoneengineering/signal/LapisNoiseSourceLogic.java",
    "MIN_BASELINE = 0",
    "MAX_BASELINE = 100",
    "MIN_NOISE_AMPLITUDE = 0",
    "MAX_NOISE_AMPLITUDE = 50",
    "MIN_SAMPLE_PERIOD_TICKS = 1",
    "MAX_SAMPLE_PERIOD_TICKS = 64",
    "DEFAULT_SAMPLE_PERIOD_TICKS = 4",
    "boundedBaseline",
    "boundedNoiseAmplitude",
    "boundedSamplePeriod",
    "baselineForLegacyIndex",
    "noiseForLegacyIndex",
    "samplePeriodForLegacyRate",
)
require(
    "src/main/java/dev/redstoneengineering/block/LapisNoiseSourceBlock.java",
    "LapisNoiseSourceLogic.boundedBaseline(stored.a())",
    "LapisNoiseSourceLogic.boundedNoiseAmplitude(stored.b())",
    "LapisNoiseSourceLogic.boundedSamplePeriod(stored.c())",
    "configurationChanged",
    "Route changes topology only. Never rewrite or advance the deterministic sample.",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/AdvancedParameterNotebookScreen.java",
    "LapisNoiseSourceLogic.MIN_BASELINE",
    "LapisNoiseSourceLogic.MAX_BASELINE",
    "LapisNoiseSourceLogic.MIN_NOISE_AMPLITUDE",
    "LapisNoiseSourceLogic.MAX_NOISE_AMPLITUDE",
    "LapisNoiseSourceLogic.MIN_SAMPLE_PERIOD_TICKS",
    "LapisNoiseSourceLogic.MAX_SAMPLE_PERIOD_TICKS",
    "Legacy quick presets map into these same exact parameters",
    "Rotating the LAPIS output changes topology only",
)

require(
    "src/main/java/dev/redstoneengineering/signal/HoneyVibrationDamperLogic.java",
    "MIN_ATTENUATION = 1",
    "MAX_ATTENUATION = 15",
    "DEFAULT_ATTENUATION = 4",
    "PACKET_TTL_TICKS = 4",
    "INITIAL_ENVELOPE_QUALITY = 80",
    "QUALITY_DECAY_PER_STEP = 20",
    "boundedAttenuation",
    "attenuatedAmplitude",
    "degradedQuality",
)
require(
    "src/main/java/dev/redstoneengineering/block/HoneyVibrationDamperBlock.java",
    "HoneyVibrationDamperLogic.DEFAULT_ATTENUATION",
    "HoneyVibrationDamperLogic.boundedAttenuation",
    "HoneyVibrationDamperLogic.attenuatedAmplitude",
    "HoneyVibrationDamperLogic.degradedQuality",
)
require(
    "src/main/java/dev/redstoneengineering/physics/VibrationNetwork.java",
    "HoneyVibrationDamperBlock.configuredAttenuation(level, node.pos)",
    "HoneyVibrationDamperLogic.INITIAL_ENVELOPE_QUALITY",
    "HoneyVibrationDamperLogic.PACKET_TTL_TICKS",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/ProcessParameterNotebookScreen.java",
    "HoneyVibrationDamperLogic.MIN_ATTENUATION",
    "HoneyVibrationDamperLogic.MAX_ATTENUATION",
    "HoneyVibrationDamperLogic.INITIAL_ENVELOPE_QUALITY",
    "HoneyVibrationDamperLogic.QUALITY_DECAY_PER_STEP",
    "HoneyVibrationDamperLogic.PACKET_TTL_TICKS",
    "same server-owned D",
)

require(
    "src/main/java/dev/redstoneengineering/block/FaultInjectorBlock.java",
    "MIN_MODE = 0",
    "MAX_MODE = 3",
    "DEFAULT_MODE = 0",
    "MODE_STUCK_LOW = 0",
    "MODE_STUCK_HIGH = 1",
    "MODE_BIAS_PLUS = 2",
    "MODE_BIAS_MINUS = 3",
    "BIAS_STEP = 4",
    "MAX_SIGNAL = 15",
    "boundedMode",
    "boundedSignal",
    "applyFault",
    "transferLawText",
    "effectCount",
    "Clears retained fault statistics while preserving live ARM state and the latest I/O evidence",
)
require(
    "src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java",
    "FaultInjectorBlock.signalQuality",
    "FaultInjectorBlock.armQuality",
    "FaultInjectorBlock.lastInput",
    "FaultInjectorBlock.lastOutput",
    "FaultInjectorBlock.effectCount",
    "Math.min(0x1FFFF",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/UniversalFieldDeviceScreen.java",
    "FaultInjectorBlock.transferLawText",
    "Last input → output",
    "Activations / effective transforms",
    "Reset fault statistics clears activation/effect counters only",
)

require(
    "src/main/java/dev/redstoneengineering/block/WatchdogBlock.java",
    "MIN_TIMEOUT_INDEX = 0",
    "MAX_TIMEOUT_INDEX = 3",
    "DEFAULT_TIMEOUT_INDEX = 1",
    "TIMEOUT_SHORT_TICKS = 20",
    "TIMEOUT_MEDIUM_TICKS = 40",
    "TIMEOUT_LONG_TICKS = 80",
    "TIMEOUT_EXTENDED_TICKS = 160",
    "SAMPLE_TICKS = 2",
    "MAX_AGE_TICKS = 12000",
    "boundedTimeoutIndex",
    "timeoutChoicesText",
)
require(
    "src/main/java/dev/redstoneengineering/block/RedundantVoterBlock.java",
    "MIN_TOLERANCE_INDEX = 0",
    "MAX_TOLERANCE_INDEX = 3",
    "DEFAULT_TOLERANCE_INDEX = 1",
    "TOLERANCE_EXACT = 0",
    "TOLERANCE_TIGHT = 1",
    "TOLERANCE_NORMAL = 2",
    "TOLERANCE_RELAXED = 4",
    "MIN_VALID_INPUTS = 2",
    "NOMINAL_INPUTS = 3",
    "boundedToleranceIndex",
    "toleranceChoicesText",
)
require(
    "src/main/java/dev/redstoneengineering/ui/menu/ReliabilitySystemMenu.java",
    "WatchdogBlock.stepTimeout",
    "RedundantVoterBlock.stepTolerance",
    "FaultLatchBlock.stepThreshold",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/ReliabilitySystemScreen.java",
    "WatchdogBlock.timeoutChoicesText()",
    "WatchdogBlock.SAMPLE_TICKS",
    "RedundantVoterBlock.toleranceChoicesText()",
    "RedundantVoterBlock.MIN_VALID_INPUTS",
    "RedundantVoterBlock.NOMINAL_INPUTS",
    "only a VALID observed transition resets age",
    "quality remains FAULT",
)

require(
    "src/main/java/dev/redstoneengineering/block/FaultLatchBlock.java",
    "MIN_THRESHOLD_INDEX = 0",
    "MAX_THRESHOLD_INDEX = 3",
    "DEFAULT_THRESHOLD_INDEX = 0",
    "THRESHOLD_LEVEL_LOW = 1",
    "THRESHOLD_LEVEL_MEDIUM = 4",
    "THRESHOLD_LEVEL_HIGH = 8",
    "THRESHOLD_LEVEL_CRITICAL = 12",
    "MAX_ALARM_OUTPUT = 15",
    "boundedThresholdIndex",
    "thresholdChoicesText",
    "boolean resetEvidenceBad = !resetObservation.valid()",
    "boolean faultActive = !faultObservation.valid()",
)
require(
    "src/main/java/dev/redstoneengineering/ui/menu/ReliabilitySystemMenu.java",
    "FaultLatchBlock.stepThreshold",
    "FaultLatchBlock.faultInputQuality",
    "FaultLatchBlock.resetInputQuality",
    "FaultLatchBlock.operationalEvidenceQuality",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/ReliabilitySystemScreen.java",
    "FaultLatchBlock.thresholdChoicesText()",
    "FAULT evidence missing/invalid OR value ≥ T → LATCH",
    "rising RESET + VALID fault value < T → CLEAR",
    "NO_SIGNAL on FAULT IN is missing evidence, not a measured zero",
    "FRONT alarm output remains authoritative VALID state",
)

require(
    "src/main/java/dev/redstoneengineering/signal/MechanicalExciterLogic.java",
    "MIN_AMPLITUDE = 0",
    "MAX_AMPLITUDE = 15",
    "MIN_CONFIGURED_FREQUENCY = 1",
    "MAX_CONFIGURED_FREQUENCY = 15",
    "DEFAULT_CONFIGURED_FREQUENCY = 8",
    "MIN_RATE = 1",
    "MAX_RATE = 15",
    "DEFAULT_AMPLITUDE_RISE = 2",
    "DEFAULT_AMPLITUDE_FALL = 1",
    "DEFAULT_FREQUENCY_SLEW = 1",
    "CONTROL_TICK_TICKS = 1",
    "boundedConfiguredFrequency",
    "boundedRate",
    "fullScaleRampTicks",
)
require(
    "src/main/java/dev/redstoneengineering/block/MechanicalExciterBlock.java",
    "MechanicalExciterLogic.boundedRate(stored.a())",
    "MechanicalExciterLogic.boundedRate(stored.b())",
    "MechanicalExciterLogic.boundedRate(stored.c())",
    "MechanicalExciterLogic.boundedRate(rise)",
    "MechanicalExciterLogic.boundedRate(fall)",
    "MechanicalExciterLogic.boundedRate(frequencySlew)",
    "setConfiguredFrequency",
)
require(
    "src/main/java/dev/redstoneengineering/ui/menu/ProcessParameterMenu.java",
    "MechanicalExciterBlock.setConfiguredFrequency",
    "MechanicalExciterBlock.setConfiguredDynamics",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/ProcessParameterNotebookScreen.java",
    "MechanicalExciterLogic.MIN_CONFIGURED_FREQUENCY",
    "MechanicalExciterLogic.MAX_CONFIGURED_FREQUENCY",
    "MechanicalExciterLogic.MIN_RATE",
    "MechanicalExciterLogic.MAX_RATE",
    "MechanicalExciterLogic.fullScaleRampTicks",
)

require(
    "src/main/java/dev/redstoneengineering/block/PermanentMagnetBlock.java",
    "MIN_STRENGTH = 1",
    "MAX_STRENGTH = 15",
    "DEFAULT_STRENGTH = 8",
    "STRENGTH_STEP = 1",
    "boundedStrength",
    "stepStrength",
)
require(
    "src/main/java/dev/redstoneengineering/ui/menu/MagneticSystemMenu.java",
    "PermanentMagnetBlock.stepStrength",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/MagneticSystemScreen.java",
    "PermanentMagnetBlock.MIN_STRENGTH",
    "PermanentMagnetBlock.MAX_STRENGTH",
    "PermanentMagnetBlock.DEFAULT_STRENGTH",
    "PermanentMagnetBlock.STRENGTH_STEP",
    "STATIC SCALAR FREE-SPACE SOURCE",
)

require(
    "src/main/java/dev/redstoneengineering/block/InductionCoilBlock.java",
    "MIN_LEGACY_TURNS = 1",
    "MAX_LEGACY_TURNS = 4",
    "DEFAULT_LEGACY_TURNS = 2",
    "MIN_CONFIGURED_TURNS = 1",
    "MAX_CONFIGURED_TURNS = 16",
    "FIELD_RADIUS = 6",
    "SAMPLE_TICKS = 2",
    "MAX_EMF = 15",
    "boundedConfiguredTurns",
    "boundedLegacyTurns",
)
require(
    "src/main/java/dev/redstoneengineering/ui/menu/MagneticSystemMenu.java",
    "InductionCoilBlock.configuredTurns",
    "engineeringB.set(state.getValue(InductionCoilBlock.TURNS))",
    "DirectionalDomainBlock.rotateRigidSeriesAxis",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/MagneticSystemScreen.java",
    "InductionCoilBlock.MIN_CONFIGURED_TURNS",
    "InductionCoilBlock.MAX_CONFIGURED_TURNS",
    "InductionCoilBlock.MIN_LEGACY_TURNS",
    "InductionCoilBlock.MAX_LEGACY_TURNS",
    "InductionCoilBlock.FIELD_RADIUS",
    "InductionCoilBlock.SAMPLE_TICKS",
    "InductionCoilBlock.MAX_EMF",
    "Exact turns N",
    "Legacy preset",
)

require(
    "src/main/java/dev/redstoneengineering/signal/ElectromagnetLogic.java",
    "MIN_FIELD = 0",
    "MAX_FIELD = 15",
    "MIN_RESPONSE_RATE = 1",
    "MAX_RESPONSE_RATE = 15",
    "DEFAULT_RISE_RATE = 2",
    "DEFAULT_FALL_RATE = 3",
    "MIN_COOLING_RATE = 1",
    "MAX_COOLING_RATE = 40",
    "DEFAULT_COOLING_RATE = 20",
    "MAX_THERMAL_LOAD = 1000",
    "WARM_DERATE_THRESHOLD = 700",
    "HOT_DERATE_THRESHOLD = 850",
    "WARM_FIELD_CAP = 10",
    "HOT_FIELD_CAP = 6",
    "boundedResponseRate",
    "boundedCoolingRate",
    "boundedThermalLoad",
)
require(
    "src/main/java/dev/redstoneengineering/block/ElectromagnetBlock.java",
    "ElectromagnetLogic.boundedResponseRate",
    "ElectromagnetLogic.boundedCoolingRate",
    "ElectromagnetLogic.DEFAULT_RISE_RATE",
    "ElectromagnetLogic.DEFAULT_FALL_RATE",
    "ElectromagnetLogic.DEFAULT_COOLING_RATE",
)
magnetic_screen = require(
    "src/main/java/dev/redstoneengineering/client/ui/MagneticSystemScreen.java",
    "ElectromagnetLogic.MIN_RESPONSE_RATE",
    "ElectromagnetLogic.MAX_RESPONSE_RATE",
    "ElectromagnetLogic.MIN_COOLING_RATE",
    "ElectromagnetLogic.MAX_COOLING_RATE",
    "ElectromagnetLogic.MAX_THERMAL_LOAD",
    "ElectromagnetLogic.WARM_DERATE_THRESHOLD",
    "ElectromagnetLogic.HOT_DERATE_THRESHOLD",
)
if "dev.redstoneengineering.physics" in magnetic_screen:
    errors.append("Magnetic client HMI crossed the persistence/physics authority boundary")

require(
    "src/main/java/dev/redstoneengineering/signal/CopperFuseLogic.java",
    "MIN_RATING = 1",
    "MAX_RATING = 15",
    "DEFAULT_RATING = 4",
    "MIN_TIME_CURRENT_CLASS = 0",
    "MAX_TIME_CURRENT_CLASS = 2",
    "FAST_CLASS = 0",
    "NORMAL_CLASS = 1",
    "SLOW_CLASS = 2",
    "TRIP_THRESHOLD = 1000",
    "boundedRating",
    "boundedTimeCurrentClass",
    "timeCurrentClassFactor",
)
require(
    "src/main/java/dev/redstoneengineering/block/CopperFuseBlock.java",
    "CopperFuseLogic.MIN_RATING",
    "CopperFuseLogic.MAX_RATING",
    "CopperFuseLogic.DEFAULT_RATING",
    "CopperFuseLogic.boundedRating",
    "CopperFuseLogic.boundedTimeCurrentClass",
    "CopperFuseLogic.DEFAULT_TIME_CURRENT_CLASS",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/ProcessParameterNotebookScreen.java",
    "CopperFuseLogic.MIN_RATING",
    "CopperFuseLogic.MAX_RATING",
    "CopperFuseLogic.MIN_TIME_CURRENT_CLASS",
    "CopperFuseLogic.MAX_TIME_CURRENT_CLASS",
    "CopperFuseLogic.TRIP_THRESHOLD",
    "CopperFuseLogic.timeCurrentClassFactor",
)

require(
    "src/main/java/dev/redstoneengineering/signal/CopperCapacitorLogic.java",
    "MIN_CAPACITANCE_INDEX = 0",
    "MAX_CAPACITANCE_INDEX = 3",
    "DEFAULT_CAPACITANCE_INDEX = 1",
    "MIN_BASE_TAU = 1",
    "MAX_BASE_TAU = 64",
    "MIN_LEAKAGE_FACTOR = 2",
    "MAX_LEAKAGE_FACTOR = 16",
    "DEFAULT_LEAKAGE_FACTOR = 8",
    "boundedBaseTau",
    "boundedLeakageFactor",
)
require(
    "src/main/java/dev/redstoneengineering/block/CopperCapacitorBlock.java",
    "CopperCapacitorLogic.boundedBaseTau",
    "CopperCapacitorLogic.boundedLeakageFactor",
    "CopperCapacitorLogic.DEFAULT_LEAKAGE_FACTOR",
)
require(
    "src/main/java/dev/redstoneengineering/ui/menu/ProcessParameterMenu.java",
    "DirectionalDomainBlock.rotateRigidSeriesAxis",
    "rigidSeriesRoute",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/ProcessParameterNotebookScreen.java",
    "CopperCapacitorLogic.MIN_BASE_TAU",
    "CopperCapacitorLogic.MAX_BASE_TAU",
    "CopperCapacitorLogic.MIN_LEAKAGE_FACTOR",
    "CopperCapacitorLogic.MAX_LEAKAGE_FACTOR",
    "τopen=τbase×leakage",
    "endpoints cannot be bent independently",
)

require(
    "src/main/java/dev/redstoneengineering/block/EdgeDetectorBlock.java",
    "MIN_PULSE_WIDTH = 1",
    "MAX_PULSE_WIDTH = 20",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/SignalProcessorScreen.java",
    "EdgeDetectorBlock.MIN_PULSE_WIDTH",
    "EdgeDetectorBlock.MAX_PULSE_WIDTH",
)

require(
    "src/main/java/dev/redstoneengineering/signal/PressureRegulatorLogic.java",
    "MIN_PRESSURE = 0",
    "MAX_PRESSURE = 100",
    "MIN_CONFIGURED_SETPOINT = 1",
    "MAX_CONFIGURED_SETPOINT = 100",
    "MIN_RESPONSE_RATE = 1",
    "MAX_RESPONSE_RATE = 100",
    "boundedConfiguredSetpoint",
    "boundedResponseRate",
)
require(
    "src/main/java/dev/redstoneengineering/block/PressureRegulatorBlock.java",
    "PressureRegulatorLogic.boundedConfiguredSetpoint",
    "PressureRegulatorLogic.boundedResponseRate",
)
require(
    "src/main/java/dev/redstoneengineering/signal/PneumaticProportionalValveLogic.java",
    "MIN_OPENING = 0",
    "MAX_OPENING = 15",
    "MIN_RESPONSE_RATE = 1",
    "MAX_RESPONSE_RATE = 15",
    "boundedOpening",
    "boundedResponseRate",
)
require(
    "src/main/java/dev/redstoneengineering/block/PneumaticProportionalValveBlock.java",
    "PneumaticProportionalValveLogic.boundedResponseRate",
    "PneumaticProportionalValveLogic.boundedOpening",
)
pneumatic_screen = require(
    "src/main/java/dev/redstoneengineering/client/ui/PneumaticSystemScreen.java",
    "PressureRegulatorLogic.MIN_CONFIGURED_SETPOINT",
    "PressureRegulatorLogic.MAX_CONFIGURED_SETPOINT",
    "PressureRegulatorLogic.MIN_RESPONSE_RATE",
    "PressureRegulatorLogic.MAX_RESPONSE_RATE",
    "PneumaticProportionalValveLogic.MIN_RESPONSE_RATE",
    "PneumaticProportionalValveLogic.MAX_RESPONSE_RATE",
)
if "dev.redstoneengineering.physics" in pneumatic_screen:
    errors.append("Pneumatic client HMI crossed the persistence/physics authority boundary")

require(
    "src/main/java/dev/redstoneengineering/block/PneumaticReceiverBlock.java",
    "MIN_RANGE_MODE = 0",
    "MAX_RANGE_MODE = 2",
    "DEFAULT_RANGE_MODE = 2",
    "LOW_FULL_SCALE_PRESSURE = 25",
    "MID_FULL_SCALE_PRESSURE = 50",
    "HIGH_FULL_SCALE_PRESSURE = 100",
    "REDSTONE_FULL_SCALE = 15",
    "boundedRangeMode",
    "boundedPressureForScale",
    "redstoneLevelsPerPressure",
    "pressurePerRedstoneLevel",
)
require(
    "src/main/java/dev/redstoneengineering/ui/menu/PneumaticSystemMenu.java",
    "PneumaticReceiverBlock.stepRange",
    "changed = rotateRigidDirectional(id)",
    "DirectionalSignalBlock.rotateRigidSeriesAxis",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/PneumaticSystemScreen.java",
    "PneumaticReceiverBlock.LOW_FULL_SCALE_PRESSURE",
    "PneumaticReceiverBlock.MID_FULL_SCALE_PRESSURE",
    "PneumaticReceiverBlock.HIGH_FULL_SCALE_PRESSURE",
    "PneumaticReceiverBlock.REDSTONE_FULL_SCALE",
    "PneumaticReceiverBlock.redstoneLevelsPerPressure",
    "PneumaticReceiverBlock.pressurePerRedstoneLevel",
)

require(
    "src/main/java/dev/redstoneengineering/block/QuartzLabOscillatorBlock.java",
    "MIN_PERIOD_INDEX = 0",
    "MAX_PERIOD_INDEX = 4",
    "DEFAULT_PERIOD_INDEX = 2",
    "MIN_JITTER_TICKS = 0",
    "MAX_JITTER_TICKS = 3",
    "DEFAULT_JITTER_TICKS = 1",
    "stepPeriodIndex",
    "stepJitter",
    "resetTimingConfiguration",
    "currentEpoch",
)
require(
    "src/main/java/dev/redstoneengineering/ui/menu/QuartzTimingMenu.java",
    "KIND_LAB_OSCILLATOR = 4",
    "QuartzLabOscillatorBlock.timingEvidence",
    "QuartzLabOscillatorBlock.stepPeriodIndex",
    "QuartzLabOscillatorBlock.stepJitter",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/QuartzTimingScreen.java",
    "QuartzLabOscillatorBlock.MIN_JITTER_TICKS",
    "QuartzLabOscillatorBlock.MAX_JITTER_TICKS",
    "STALE CONFIG EPOCH",
    "CURRENT-CONFIG REALIZED INTERVAL",
)

require(
    "src/main/java/dev/redstoneengineering/signal/AirCompressorLogic.java",
    "MIN_PRESSURE = 0",
    "MAX_PRESSURE = 100",
    "MIN_RAMP_RATE = 1",
    "MAX_RAMP_RATE = 100",
    "boundedPressure",
    "boundedRampRate",
)
require(
    "src/main/java/dev/redstoneengineering/block/AirCompressorBlock.java",
    "AirCompressorLogic.boundedRampRate(stored.a())",
    "AirCompressorLogic.boundedRampRate(stored.b())",
    "AirCompressorLogic.boundedPressure",
)
require(
    "src/main/java/dev/redstoneengineering/signal/PneumaticReliefValveLogic.java",
    "MIN_CONFIGURED_SETPOINT = 1",
    "MAX_CONFIGURED_SETPOINT = 100",
    "MIN_BLOWDOWN = 1",
    "MAX_BLOWDOWN = 25",
    "DEFAULT_BLOWDOWN = 5",
    "boundedConfiguredSetpoint",
    "boundedConfiguredBlowdown",
)
require(
    "src/main/java/dev/redstoneengineering/block/PneumaticReliefValveBlock.java",
    "PneumaticReliefValveLogic.DEFAULT_BLOWDOWN",
    "PneumaticReliefValveLogic.boundedConfiguredSetpoint",
    "PneumaticReliefValveLogic.boundedConfiguredBlowdown",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/PneumaticSystemScreen.java",
    "AirCompressorLogic.MIN_RAMP_RATE",
    "AirCompressorLogic.MAX_RAMP_RATE",
    "PneumaticReliefValveLogic.MIN_CONFIGURED_SETPOINT",
    "PneumaticReliefValveLogic.MAX_CONFIGURED_SETPOINT",
    "PneumaticReliefValveLogic.MIN_BLOWDOWN",
    "PneumaticReliefValveLogic.MAX_BLOWDOWN",
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
print(" Optical attenuator/filter exact transfer + rigid-route authority: PASS")
print(" Redstone-Copper exact slew/legacy preset dynamics authority: PASS")
print(" PWM exact-period/legacy-preset carrier authority: PASS")
print(" Lapis Precision Range exact/legacy range + normalization authority: PASS")
print(" Signal Amplifier exact gain/headroom + legacy preset authority: PASS")
print(" Lapis Noise Source stored/effective + legacy preset authority: PASS")
print(" Honey Vibration Damper configurable through-loss/local-decay authority: PASS")
print(" Fault Injector mode transfer + retained evidence authority: PASS")
print(" Watchdog discrete timeout + heartbeat baseline authority: PASS")
print(" Redundant Voter tolerance + degraded quorum authority: PASS")
print(" Fault Latch threshold + fail-safe missing-evidence authority: PASS")
print(" Mechanical Exciter frequency/rate stored-effective authority: PASS")
print(" Permanent Magnet single scalar-strength parameter authority: PASS")
print(" Induction Coil exact/legacy turns + sampled EMF authority: PASS")
print(" Electromagnet response/cooling/thermal pure parameter authority: PASS")
print(" Copper Fuse rating/class/I2t parameter authority: PASS")
print(" Copper Capacitor baseTau/leakage + rigid axial route authority: PASS")
print(" Edge Detector exact 1..20 pulse-width authority: PASS")
print(" Pressure Regulator setpoint/response-rate pure authority: PASS")
print(" Proportional Valve opening/response-rate pure authority: PASS")
print(" Air Compressor bounded ramp-rate persistence authority: PASS")
print(" Relief Valve setpoint/blowdown pure authority: PASS")
print(" Pneumatic Receiver 25/50/100 FS calibration + rigid converter authority: PASS")
print(" Servo persistent bounds + 2-tick control cycle + client boundary: PASS")
print(" PID persistent bounds + deadband/integral/cycle + client boundary: PASS")
print(" Lapis LPF synchronized alpha + 2-tick sample/derived response contract: PASS")
print(" Quartz oscillator/divider/delay bounds + edge-safe timing contract: PASS")
print(" Quartz Lab period/jitter epoch-scoped realized-evidence contract: PASS")
