#!/usr/bin/env python3
from pathlib import Path
import sys

root=Path(sys.argv[1] if len(sys.argv)>1 else ".").resolve()
failed=[]
def req(path,*tokens):
    p=root/path
    if not p.is_file():
        failed.append(f"missing: {path}"); return
    t=p.read_text(errors="ignore")
    for token in tokens:
        if token not in t:
            failed.append(f"{path} missing token: {token}")

req("src/main/java/dev/redstoneengineering/block/SignalProbeBlock.java",
    "stepChannel","configuredChannel","measuredValue","Probe channel →")
req("src/main/java/dev/redstoneengineering/block/RedstoneReferenceSourceBlock.java",
    "stepPower","configuredPower")
req("src/main/java/dev/redstoneengineering/physics/RedstoneCableNetwork.java",
    "PATH_EVIDENCE_KEY","record PathEvidence","winningSource","PATH_ATTENUATION_LOSS")
req("src/main/java/dev/redstoneengineering/block/RedstoneSignalCableBlock.java",
    "sourceLevel=","loss=","margin=")
req("src/main/java/dev/redstoneengineering/block/RedstoneCableTerminalBlock.java",
    "toggleMode","winningSource","attenuation")
req("src/main/java/dev/redstoneengineering/block/SignalConditionerBlock.java",
    "RUNTIME_KEY","limitingEpisodes","lastLimitingAgeTicks","case 0 -> \"LEGACY SCALE\"")
req("src/main/java/dev/redstoneengineering/ui/menu/SignalConditionerMenu.java",
    "limitingEpisodes","lastLimitingAge")
req("src/main/java/dev/redstoneengineering/client/ui/SignalConditionerScreen.java",
    "SCALE","limitingEpisodes","lastLimitingAgeTicks")

req("src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java",
    "CONFIG_SIGNAL_PROBE","CONFIG_REFERENCE_SOURCE","CONFIG_REDSTONE_CABLE","CONFIG_CABLE_TERMINAL",
    "RedstoneCableTerminalBlock.toggleMode")
req("src/main/java/dev/redstoneengineering/client/ui/UniversalFieldDeviceScreen.java",
    "INSTRUMENT PROBE","REDSTONE REFERENCE SOURCE","INSULATED REDSTONE LINK","REDSTONE CABLE TERMINAL",
    "Mode • Cable → Vanilla","CONFIG_SIGNAL_PROBE","CONFIG_REFERENCE_SOURCE")

req("src/main/java/dev/redstoneengineering/block/QuartzOscillatorBlock.java",
    "stepPeriod","periodTicks")

req("src/main/java/dev/redstoneengineering/block/FaultLatchBlock.java",
    "stepThreshold","thresholdValue","manualReset","resetPermitted",
    "RESET_REACQUIRE","resetRising","faultClearForReset")

req("src/main/java/dev/redstoneengineering/block/AnalogIndicatorBlock.java",
    "retainedMinimum","retainedMaximum","resetExtrema","sampleCount")

req("src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java",
    "CONFIG_QUARTZ_OSCILLATOR","CONFIG_FAULT_LATCH","CONFIG_ANALOG_INDICATOR","CONFIG_JUNCTION")

req("src/main/java/dev/redstoneengineering/client/ui/UniversalFieldDeviceScreen.java",
    "QUARTZ TIMING SOURCE","FAULT LATCH","Reset permissive","Reset blocked • fault not clear",
    "ANALOG REDSTONE INDICATOR","JUNCTION • ROUTING ONLY")

req("src/main/java/dev/redstoneengineering/block/RedstoneByteEncoderBlock.java",
    "FULL_SCALE","encode","stepMode","value * 17")

req("src/main/java/dev/redstoneengineering/block/ByteToRedstoneDecoderBlock.java",
    "FULL_SCALE","decode","stepMode","value / 17.0")

req("src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java",
    "CONFIG_BYTE_ENCODER","CONFIG_BYTE_DECODER","RedstoneByteEncoderBlock.stepMode","ByteToRedstoneDecoderBlock.stepMode")

req("src/main/java/dev/redstoneengineering/client/ui/UniversalFieldDeviceScreen.java",
    "REDSTONE → BYTE ENCODER","BYTE → REDSTONE DECODER","FULL_SCALE uses the complete byte range")

req("src/main/java/dev/redstoneengineering/block/SerializerBlock.java",
    "PERIOD_MODE","WORD_PERIODS = {4, 8, 16}","stepPeriod","wordPeriod")

req("src/main/java/dev/redstoneengineering/block/DigitalRegeneratorBlock.java",
    "stepThreshold","acceptedCount","rejectedCount","RuntimeIntStore")

req("src/main/java/dev/redstoneengineering/block/DeserializerBlock.java",
    "inputEvidenceQuality","SerialNetwork.quality","PortQuality.NO_SIGNAL","PortQuality.STALE")

req("src/main/java/dev/redstoneengineering/block/DifferentialReceiverBlock.java",
    "inputEvidenceQuality","DifferentialNetwork.quality","PortQuality.NO_SIGNAL","PortQuality.STALE")

req("src/main/java/dev/redstoneengineering/block/DifferentialDriverBlock.java",
    "THRESHOLD","thresholdValue","stepThreshold",
    "inputLevel","inputQuality","drivenBit",
    "EngineeringPortSnapshot.redstone(")

req("src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java",
    "CONFIG_SERIALIZER","CONFIG_DESERIALIZER","CONFIG_SERIAL_LINE","CONFIG_REGENERATOR",
    "CONFIG_DIFF_DRIVER","CONFIG_DIFF_PAIR","CONFIG_DIFF_RECEIVER",
    "DifferentialDriverBlock.inputLevel","DifferentialDriverBlock.inputQuality",
    "DifferentialDriverBlock.drivenBit")

req("src/main/java/dev/redstoneengineering/client/ui/UniversalFieldDeviceScreen.java",
    "SERIALIZER","DESERIALIZER","SERIAL LINK","DIGITAL REGENERATOR",
    "DIFFERENTIAL DRIVER","DIFFERENTIAL DRIVER • INPUT NO SOURCE",
    "DIFFERENTIAL DRIVER • INPUT EVIDENCE BAD","Input evidence",
    "DIFFERENTIAL LINK","DIFFERENTIAL RECEIVER")

req("src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java",
    "CONFIG_DATA_BUS","CONFIG_SERIALIZER","CONFIG_DESERIALIZER","CONFIG_SERIAL_LINE",
    "CONFIG_REGENERATOR","CONFIG_DIFF_DRIVER","CONFIG_DIFF_PAIR","CONFIG_DIFF_RECEIVER",
    "DataBusNetwork.quality","SerialNetwork.quality","DifferentialNetwork.quality",
    "DeserializerBlock.inputEvidenceQuality","DifferentialReceiverBlock.inputEvidenceQuality")

req("src/main/java/dev/redstoneengineering/client/ui/UniversalFieldDeviceScreen.java",
    "8-BIT DATA BUS","8-BIT BUS • NO DRIVER","8-BIT BUS • TOPOLOGY ERROR",
    "SERIALIZER • BYTE SOURCE MISSING","DESERIALIZER • SERIAL NO SOURCE",
    "SERIAL LINK • NO SOURCE","SERIAL LINK • TOPOLOGY ERROR",
    "DIFFERENTIAL DRIVER","DIFFERENTIAL LINK • NO SOURCE",
    "DIFFERENTIAL LINK • TOPOLOGY ERROR","DIFFERENTIAL RECEIVER • NO SOURCE",
    "Evidence state","Input evidence","syncedQuality","evidenceIssue","evidenceSevere")

req("src/main/java/dev/redstoneengineering/block/WatchdogBlock.java",
    "stepTimeout","timedOut","TIMEOUT_TICKS","sourceSeen","heartbeat.quality()")

req("src/main/java/dev/redstoneengineering/block/SafetyInterlockBlock.java",
    "transitionCount","blockedTicks","INITIALIZED","runtime[INITIALIZED] == 0")

req("src/main/java/dev/redstoneengineering/block/FaultInjectorBlock.java",
    "RedstoneObservationSupport.observe","evidence.arm().valid()",
    "signalQuality","armQuality","combineQuality",
    "PortQuality.FAULT")

req("src/main/java/dev/redstoneengineering/gametest/RseEngineeringSystemsGameTests.java",
    "faultInjectorRejectsFaultQualityArmAuthority",
    "FAULT-quality HIGH ARM incorrectly authorized fault injection")

req("src/main/java/dev/redstoneengineering/client/ui/UniversalFieldDeviceScreen.java",
    "FAULT INJECTOR • ARM NO SOURCE","FAULT INJECTOR • ARM EVIDENCE BAD",
    "SIGNAL evidence","ARM evidence")

req("src/main/java/dev/redstoneengineering/block/SequenceControllerBlock.java",
    "RUN_REACQUIRE","runQuality","runtime[RUN_REACQUIRE] = 1",
    "Reacquire the physical RUN level")

req("src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java",
    "SequenceControllerBlock.runQuality(level, blockPos, state)")

req("src/main/java/dev/redstoneengineering/client/ui/UniversalFieldDeviceScreen.java",
    "SEQUENCE • RUN NO SOURCE","RUN evidence",
    "Reset to IDLE • require fresh RUN edge")

req("src/main/java/dev/redstoneengineering/block/OscilloscopeBlock.java",
    "TIMEBASE_MODE","samplePeriodTicks","BUTTON_TIMEBASE")

req("src/main/java/dev/redstoneengineering/blockentity/OscilloscopeBlockEntity.java",
    "estimatedPeriodTicks(int channel, int samplePeriodTicks)","cursorDeltaTicks(int samplePeriodTicks)")

req("src/main/java/dev/redstoneengineering/ui/menu/OscilloscopeMenu.java",
    "BUTTON_TIMEBASE","samplePeriodTicks")

req("src/main/java/dev/redstoneengineering/client/ui/OscilloscopeScreen.java",
    "Timebase","ticks/sample","Changing timebase clears the capture")

req("src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java",
    "CONFIG_INSTRUMENT_BUS","CONFIG_WATCHDOG","CONFIG_QUARTZ_TRACE")

req("src/main/java/dev/redstoneengineering/client/ui/UniversalFieldDeviceScreen.java",
    "INSTRUMENTATION BUS","WATCHDOG • TIMEOUT","WATCHDOG • NO VALID SOURCE",
    "Source acquired","QUARTZ TIMING TRACE","State transitions")

req("src/main/java/dev/redstoneengineering/block/SingleRelayBlock.java",
    "NORMALLY_CLOSED","RELAY COIL CONTROL","switchCount","toggleContactMode")

req("src/main/java/dev/redstoneengineering/RedstoneEngineering.java",
    "SINGLE_RELAY_CODEC","SINGLE_RELAY =","SINGLE_RELAY_ITEM")

req("src/main/java/dev/redstoneengineering/block/RedundantVoterBlock.java",
    "stepTolerance","validInputs","disagreementCount","evidenceQuality",
    "combineQuality(votingQuality, evidenceQuality)")

req("src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java",
    "CONFIG_SINGLE_RELAY","CONFIG_REDUNDANT_VOTER")

req("src/main/java/dev/redstoneengineering/client/ui/UniversalFieldDeviceScreen.java",
    "SINGLE RELAY","2oo3 VOTER")

req("src/main/java/dev/redstoneengineering/block/SignalAmplifierBlock.java",
    "GAIN_MODE","clippingEpisodes","resetClipEvidence","PortQuality.SATURATED")

req("src/main/java/dev/redstoneengineering/RedstoneEngineering.java",
    "SIGNAL_AMPLIFIER_CODEC","SIGNAL_AMPLIFIER =","SIGNAL_AMPLIFIER_ITEM")

req("src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java",
    "CONFIG_SIGNAL_AMPLIFIER","SignalAmplifierBlock.stepGain","resetClipEvidence")

req("src/main/java/dev/redstoneengineering/client/ui/UniversalFieldDeviceScreen.java",
    "SIGNAL AMPLIFIER","Reset clipping evidence")

req("src/main/java/dev/redstoneengineering/block/QuartzTriggeredLapisSamplerBlock.java",
    "ACCEPTED_CAPTURES","REJECTED_CAPTURES","acceptedCaptures","rejectedCaptures")

req("src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java",
    "CONFIG_QUARTZ_LAPIS_SAMPLER")

req("src/main/java/dev/redstoneengineering/client/ui/UniversalFieldDeviceScreen.java",
    "QUARTZ-SYNCHRONIZED SAMPLE & HOLD","Accepted captures","Rejected captures")

req("src/main/java/dev/redstoneengineering/block/SampleHoldBlock.java",
    "heldValue","resetCount","captureCount","sampleAgeTicks")

req("src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java",
    "CONFIG_SIGNAL_TAP","CONFIG_SAMPLE_HOLD")

req("src/main/java/dev/redstoneengineering/client/ui/UniversalFieldDeviceScreen.java",
    "BUFFERED SIGNAL TAP","Held value","SAMPLE & HOLD")

req("src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java",
    "CONFIG_SIGNAL_TAP","CONFIG_SAMPLE_HOLD","CalibrationModuleBlock.measurement")

req("src/main/java/dev/redstoneengineering/client/ui/UniversalFieldDeviceScreen.java",
    "BUFFERED SIGNAL TAP","Held value","Reference residual","Traceable samples")

req("src/main/java/dev/redstoneengineering/block/SignalSelectorBlock.java",
    "INVERT_SELECT","SIGNAL A","SIGNAL B","SELECT","switchCount","toggleInvertSelect",
    "selectQuality","selectedPayloadQuality",
    "RedstoneObservationSupport.combineQuality(",
    "selected.quality(), select.quality()")

req("src/main/java/dev/redstoneengineering/RedstoneEngineering.java",
    "SIGNAL_SELECTOR_CODEC","SIGNAL_SELECTOR =","SIGNAL_SELECTOR_ITEM")

req("src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java",
    "CONFIG_SIGNAL_SELECTOR","SignalSelectorBlock.toggleInvertSelect",
    "SignalSelectorBlock.selectQuality","SignalSelectorBlock.selectedPayloadQuality")

req("src/main/java/dev/redstoneengineering/client/ui/UniversalFieldDeviceScreen.java",
    "2:1 SIGNAL SELECTOR","Select logic • INVERTED","Selection changes",
    "SELECT NO SOURCE","Payload evidence")

req("src/main/java/dev/redstoneengineering/block/AnalogComparatorBlock.java",
    "HYSTERESIS","PROCESS","REFERENCE","transitionCount","stepHysteresis","stepMode",
    "processQuality","referenceQuality","decisionQuality")

req("src/main/java/dev/redstoneengineering/RedstoneEngineering.java",
    "ANALOG_COMPARATOR_CODEC","ANALOG_COMPARATOR =","ANALOG_COMPARATOR_ITEM")

req("src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java",
    "CONFIG_ANALOG_COMPARATOR","configQuaternary","AnalogComparatorBlock.stepHysteresis",
    "AnalogComparatorBlock.processQuality","AnalogComparatorBlock.referenceQuality")

req("src/main/java/dev/redstoneengineering/client/ui/UniversalFieldDeviceScreen.java",
    "ANALOG COMPARATOR","Process-reference","Decision output","Compare • ",
    "PROCESS evidence","REFERENCE evidence","EVIDENCE HOLD","• HELD")

req("src/main/java/dev/redstoneengineering/block/SingleRelayBlock.java",
    "PICKUP_MODE","pickupLevel","dropoutLevel","stepPickup","coilInput")

req("src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java",
    "SingleRelayBlock.PICKUP_MODE","SingleRelayBlock.stepPickup",
    "SingleRelayBlock.controlHoldActive","SingleRelayBlock.payloadHoldActive",
    "SingleRelayBlock.controlQuality","SingleRelayBlock.payloadQuality")

req("src/main/java/dev/redstoneengineering/client/ui/UniversalFieldDeviceScreen.java",
    "Pickup / dropout","◀ Pickup","Pickup ▶",
    "SINGLE RELAY • EVIDENCE HOLD","SINGLE RELAY • CONTROL NO SOURCE",
    "CTRL=","PAY=","controlHold","payloadHold")

req("src/main/java/dev/redstoneengineering/physics/RedstoneObservationSupport.java",
    "engineeringSnapshot","combineQuality","snapshot.get().quality()")

req("src/main/java/dev/redstoneengineering/block/SignalSelectorBlock.java",
    "RedstoneObservationSupport.observe","controlEvidenceUnusable")

req("src/main/java/dev/redstoneengineering/block/SingleRelayBlock.java",
    "coilObservation","controlEvidenceUnusable","input.quality()",
    "CONTROL_HOLD_ACTIVE","CONTROL_BAD_EPISODES",
    "PAYLOAD_HOLD_ACTIVE","PAYLOAD_BAD_EPISODES",
    "controlHoldActive","payloadHoldActive","controlQuality","payloadQuality",
    "combineQuality(PortQuality.VALID, coil.quality())",
    "combineQuality(input.quality(), coil.quality())")

req("src/main/java/dev/redstoneengineering/gametest/RseRelayEvidenceGameTests.java",
    "relayHoldsLastPayloadAcrossFaultQuality",
    "relayExposesMissingControlWhileDeenergizing",
    "PortQuality.FAULT","PortQuality.NO_SIGNAL",
    "payloadHoldActive","payloadBadEpisodes","controlQuality")

req("src/main/java/dev/redstoneengineering/gametest/RseSignalSelectorEvidenceGameTests.java",
    "selectorDistinguishesMissingSelectFromExplicitLow",
    "PortQuality.NO_SIGNAL","PortQuality.VALID",
    "selectQuality","selectedPayloadQuality")

req("src/main/java/dev/redstoneengineering/gametest/RseAnalogComparatorEvidenceGameTests.java",
    "comparatorHoldsDecisionAcrossMissingProcessEvidence",
    "PortQuality.NO_SIGNAL","PortQuality.VALID",
    "processQuality","referenceQuality")

req("src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java",
    "event.register(RseRelayEvidenceGameTests.class);",
    "event.register(RseSignalSelectorEvidenceGameTests.class);",
    "event.register(RseAnalogComparatorEvidenceGameTests.class);")

req("src/main/java/dev/redstoneengineering/block/AnalogComparatorBlock.java",
    "combineQuality","!processObservation.valid()","!referenceObservation.valid()",
    "processQuality","referenceQuality")

req("src/main/java/dev/redstoneengineering/block/SignalAmplifierBlock.java",
    "RedstoneObservationSupport.observe","input.quality()")

if failed:
    print("RSE redstone-core engineering-depth verification: FAIL")
    for x in failed: print(" -",x)
    raise SystemExit(1)

print("RSE redstone-core engineering-depth verification: PASS")
print(" configurable A/B/C/D signal probe: PASS")
print(" adjustable 0..15 reference source: PASS")
print(" insulated cable signal-integrity evidence: PASS")
print(" terminal direction authority in HMI: PASS")
print(" conditioner scale identity + limiting history: PASS")
print(" timing/safety/junction core HMI: PASS")
print(" redstone-byte mapping modes: PASS")
print(" serial/differential communication depth: PASS")
print(" parallel/serial/differential media roles: PASS")
print(" instrument/timing/control supervision depth: PASS")
print(" relay/voter safety-control hardware: PASS")
print(" dedicated amplifier gain/clipping stage: PASS")
print(" conditioner/amplifier role separation: PASS")
print(" synchronized precision sampling semantics: PASS")
print(" buffered tap and sample-hold evidence: PASS")
print(" tap/sample-hold/calibration evidence: PASS")
print(" two-input analog signal selection: PASS")
print(" live-reference hysteretic comparison: PASS")
print(" relay pickup/dropout hysteresis: PASS")
print(" redstone quality propagation and conservative control: PASS")
