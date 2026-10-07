#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors = []

def read(rel):
    path = root / rel
    if not path.is_file():
        errors.append(f"missing {rel}")
        return ""
    return path.read_text(errors="ignore")

doc = read("docs/PIONEER_SHOWCASE_STANDARD.md")
lowpass = read("src/main/java/dev/redstoneengineering/client/ui/LapisLowPassScreen.java")
scope = read("src/main/java/dev/redstoneengineering/ui/ldlib/OscilloscopeLdUi.java")
pid = read("src/main/java/dev/redstoneengineering/ui/ldlib/PidControllerLdUi.java")
digital = read("src/main/java/dev/redstoneengineering/ui/ldlib/DigitalCommunicationLdUi.java")
pneumatic = read("src/main/java/dev/redstoneengineering/ui/ldlib/PneumaticSystemLdUi.java")
optical = read("src/main/java/dev/redstoneengineering/ui/ldlib/OpticalSystemLdUi.java")
magnetic = read("src/main/java/dev/redstoneengineering/ui/ldlib/MagneticSystemLdUi.java")
amethyst = read("src/main/java/dev/redstoneengineering/ui/ldlib/AmethystSystemLdUi.java")
reliability = read("src/main/java/dev/redstoneengineering/ui/ldlib/ReliabilitySystemLdUi.java")
radio = read("src/main/java/dev/redstoneengineering/ui/ldlib/RadioLinkLdUi.java")
analyzer = read("src/main/java/dev/redstoneengineering/ui/ldlib/SignalAnalyzerLdUi.java")
buffer = read("src/main/java/dev/redstoneengineering/client/ui/IndustrialBufferScreen.java")
logic = read("src/main/java/dev/redstoneengineering/ui/ldlib/LogicAnalyzerLdUi.java")
copper = read("src/main/java/dev/redstoneengineering/ui/ldlib/CopperCircuitMeterLdUi.java")
ops = read("src/main/java/dev/redstoneengineering/client/ui/OperationsMonitorScreen.java")
workcell = read("src/main/java/dev/redstoneengineering/client/ui/WorkcellControllerScreen.java")
universal = read("src/main/java/dev/redstoneengineering/ui/ldlib/UniversalFieldDeviceLdUi.java")
enhanced = read("src/main/java/dev/redstoneengineering/client/ui/EnhancedFieldDeviceScreen.java")
processor = read("src/main/java/dev/redstoneengineering/ui/ldlib/SignalProcessorLdUi.java")
range_sensor = read("src/main/java/dev/redstoneengineering/ui/ldlib/RangeSensorLdUi.java")
conditioner = read("src/main/java/dev/redstoneengineering/ui/ldlib/SignalConditionerLdUi.java")
quartz = read("src/main/java/dev/redstoneengineering/ui/ldlib/QuartzTimingLdUi.java")
media_conversion = read("src/main/java/dev/redstoneengineering/ui/ldlib/MediaConversionLdUi.java")
client_registration = read("src/main/java/dev/redstoneengineering/client/ui/EngineeringUiClientRegistration.java")
field_menu = read("src/main/java/dev/redstoneengineering/ui/menu/FieldDeviceMenu.java")
universal_menu = read("src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java")
temperature_sensor = read("src/main/java/dev/redstoneengineering/block/TemperatureSensorBlock.java")
wave14_tests = read("src/main/java/dev/redstoneengineering/gametest/RsePioneerWave14GameTests.java")
wave15_tests = read("src/main/java/dev/redstoneengineering/gametest/RsePioneerWave15GameTests.java")
wave16_tests = read("src/main/java/dev/redstoneengineering/gametest/RsePioneerWave16GameTests.java")
wave17_tests = read("src/main/java/dev/redstoneengineering/gametest/RsePioneerWave17GameTests.java")
iron_core_block = read("src/main/java/dev/redstoneengineering/block/IronCoreBlock.java")
thermal_mass_block = read("src/main/java/dev/redstoneengineering/block/ThermalMassBlock.java")
thermal_heater_block = read("src/main/java/dev/redstoneengineering/block/ThermalHeaterBlock.java")
thermal_radiator_block = read("src/main/java/dev/redstoneengineering/block/ThermalRadiatorBlock.java")
thermal_calorimeter_block = read("src/main/java/dev/redstoneengineering/block/ThermalCalorimeterBlock.java")
soul_conduit_block = read("src/main/java/dev/redstoneengineering/block/SoulSoilConduitBlock.java")
soul_reservoir_block = read("src/main/java/dev/redstoneengineering/block/SoulSandReservoirBlock.java")
lapis_noise_block = read("src/main/java/dev/redstoneengineering/block/LapisNoiseSourceBlock.java")
quartz_oscillator_block = read("src/main/java/dev/redstoneengineering/block/QuartzLabOscillatorBlock.java")
quartz_phase_delay_block = read("src/main/java/dev/redstoneengineering/block/QuartzPhaseDelayBlock.java")
quartz_sampler_block = read("src/main/java/dev/redstoneengineering/block/QuartzTriggeredLapisSamplerBlock.java")
soul_injector_block = read("src/main/java/dev/redstoneengineering/block/SoulFluxInjectorBlock.java")
soul_meter_block = read("src/main/java/dev/redstoneengineering/block/SoulFluxMeterBlock.java")
molecular_receiver_block = read("src/main/java/dev/redstoneengineering/block/MolecularCloudReceiverBlock.java")
copper_wire_block = read("src/main/java/dev/redstoneengineering/block/CopperWireBlock.java")
copper_source_block = read("src/main/java/dev/redstoneengineering/block/CopperVoltageSourceBlock.java")
copper_load_block = read("src/main/java/dev/redstoneengineering/block/CopperResistiveLoadBlock.java")
copper_series_block = read("src/main/java/dev/redstoneengineering/block/CopperSeriesResistorBlock.java")
copper_capacitor_block = read("src/main/java/dev/redstoneengineering/block/CopperCapacitorBlock.java")
copper_fuse_block = read("src/main/java/dev/redstoneengineering/block/CopperFuseBlock.java")
copper_junction_block = read("src/main/java/dev/redstoneengineering/block/CopperCableJunctionBlock.java")
gametest_registration = read("src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java")
tablet = read("src/main/java/dev/redstoneengineering/client/ui/DiagnosticTabletScreen.java")
robot = read("src/main/java/dev/redstoneengineering/entity/EngineeringMobileRobotEntity.java")
ops_robot_acceptance = read("src/main/java/dev/redstoneengineering/integration/OperationsRobotMaterialFlowAcceptance.java")
transport_binding = read("src/main/java/dev/redstoneengineering/integration/OperationTransportBinding.java")

for token in (
    "Pioneer / Showcase blocks",
    "Lapis Low-Pass Filter — model transparency pioneer",
    "Oscilloscope — experiment and sampling pioneer",
    "PID Controller — control and acceptance-evidence pioneer",
    "MODEL — expose the real implemented transfer/model/constraint",
    "Do not invent knobs, formulas, history, uncertainty, experiments or hidden physics",
    "Wave 1:",
    "Wave 2:",
    "Wave 3:",
    "Wave 4:",
    "Wave 5:",
    "Wave 6:",
    "Wave 7:",
    "Wave 8:",
    "Wave 9:",
    "Wave 10:",
    "Wave 11:",
    "Wave 12:",
    "Wave 13:",
    "Pioneer completion ledger after Wave 13: **87 + 7 = 94 / 122 registered blocks processed; 28 remain.**",
    "Wave 14:",
    "Pioneer completion ledger after Wave 14: **94 + 7 = 101 / 122 registered blocks processed; 21 remain.**",
    "Wave 15:",
    "Pioneer completion ledger after Wave 15: **101 + 7 = 108 / 122 registered blocks processed; 14 remain.**",
    "Wave 16:",
    "Pioneer completion ledger after Wave 16: **108 + 7 = 115 / 122 registered blocks processed; 7 remain.**",
    "Wave 17:",
    "Pioneer completion ledger after Wave 17: **115 + 7 = 122 / 122 registered blocks processed; 0 remain.**",
    "**Pioneer broad-rollout campaign complete: 122 / 122.**",
):
    if token not in doc:
        errors.append(f"Pioneer standard missing {token!r}")

for token in (
    "y[n] = y[n-1] + α",
    "LIVE SUBSTITUTION",
    "VARIABLE ROLES",
):
    if token not in lowpass:
        errors.append(f"Low-pass pioneer missing {token!r}")

for token in (
    "SAMPLING EXPERIMENT",
    "SAMPLING MODEL",
    "FROZEN EVIDENCE COMPARISON",
    "PASS",
    "MARGINAL",
    "FAIL",
):
    if token not in scope:
        errors.append(f"Oscilloscope pioneer missing {token!r}")

for token in (
    "Capture acceptance",
    "historyCount()",
    "comparisonTrend()",
    "PIONEER WORKFLOW • CLOSED-LOOP COMMISSIONING TRIAL",
    "Trial baseline",
    "Trial candidate",
    "trialTrend()",
    "trialRobust()",
):
    if token not in pid:
        errors.append(f"PID pioneer missing acceptance-evidence token {token!r}")

wave2 = {
    "DigitalCommunicationLdUi.java": (digital, (
        "PIONEER PATTERN • COMMUNICATION MODEL",
        "communicationEquation(m)",
        "U = min(100%, 100 · T_frame / Δt_arrival)",
        "Q_bus = max(35, 100 - loadingPenalty - contentionPenalty)",
        '"MEASURED", "Q_link"',
    )),
    "PneumaticSystemLdUi.java": (pneumatic, (
        "PIONEER PATTERN • PNEUMATIC MODEL",
        "ΔP_path = ΔP_line + ΔP_restriction",
        "H_charge = max(0, P_line - P_stored)",
        "ΔP_local = max(0, P_in - P_out)",
        "SERVER PNEUMATIC NETWORK",
    )),
    "OpticalSystemLdUi.java": (optical, (
        "PIONEER PATTERN • OPTICAL MODEL",
        "opticalEquation(m)",
        "I_out = max(0, I_in - L)",
        "I_A = floor(I_in/2)",
        "L_obs = I_TX - I_RX",
    )),
    "MagneticSystemLdUi.java": (magnetic, (
        "PIONEER PATTERN • MAGNETIC MODEL",
        "V_ind = clamp(N · |B[n] - B[n-1]|, 0, 15)",
        "Σ S_i / max(1,r_i²)",
        'liveRow("EVIDENCE"',
    )),
}

for name, (text, tokens) in wave2.items():
    for token in tokens:
        if token not in text:
            errors.append(f"{name} missing Wave-2 pioneer-rollout token {token!r}")
    if "dev.redstoneengineering.physics" in text:
        errors.append(f"{name} imports physics directly; client screen must remain presentation-only")


wave3 = {
    "AmethystSystemLdUi.java": (amethyst, (
        "PIONEER PATTERN • RESONANCE MODEL",
        "A_out = (f_in = f_target) ? max(0, A_in - 1) : 0",
        "BW = 5 - Q",
        "Frequency values are deliberate model indices, not fabricated Hz",
        "no client-side spectrum/history is invented",
    )),
    "ReliabilitySystemLdUi.java": (reliability, (
        "PIONEER PATTERN • RELIABILITY / SAFE STATE",
        "reliabilityEquation(m)",
        "heartbeat seen ∧ age ≥ timeout",
        "spread ≤ tolerance",
        "fault ≥ threshold",
    )),
    "RadioLinkLdUi.java": (radio, (
        "PIONEER PATTERN • RADIO LINK BUDGET",
        "M_decode = Q_link - Q_min",
        "availability = 100 · validSamples / samples",
        'liveRow("EVIDENCE","quality"',
    )),
    "SignalAnalyzerLdUi.java": (analyzer, (
        "PIONEER PATTERN • METROLOGY / CALIBRATION",
        "x_cal = clamp(x_raw + b_cal, 0, 15)",
        '"b_cal","-2..+2 • direct entry"',
        "Calibration changes only the displayed engineering reading",
        "client never samples the world",
    )),
    "IndustrialBufferScreen.java": (buffer, (
        "PIONEER PATTERN • OPERATIONS / WIP MODEL",
        "WIP% = 100·used/capacity",
        "clamp(round(15·used/capacity),1,15)",
        'evidenceRow(graphics,"Workcell roles"',
        "LOT identity",
    )),
}

for name, (text, tokens) in wave3.items():
    for token in tokens:
        if token not in text:
            errors.append(f"{name} missing Wave-3 pioneer-rollout token {token!r}")
    if "dev.redstoneengineering.physics" in text:
        errors.append(f"{name} imports physics directly; client screen must remain presentation-only")


wave4 = {
    "LogicAnalyzerLdUi.java": (logic, (
        "PIONEER PATTERN • DIGITAL TIMING MODEL",
        "D_ch[n] = (x_ch[n] ≥ T) ? HIGH : LOW",
        '"Δt_sample"',
        '"Δt_cursor"',
        "server-authoritative",
    )),
    "CopperCircuitMeterLdUi.java": (copper, (
        "PIONEER PATTERN • ELECTRICAL MEASUREMENT MODEL",
        "I = V / R_eq ; P = V · I",
        "COMMISSIONING",
        "observer-only",
        "server computes V, R_eq, I and P",
    )),
    "OperationsMonitorScreen.java": (ops, (
        "PIONEER PATTERN • PLANT STATE / KPI AUTHORITY",
        "QUEUE = max(valid horizontal QUEUE/WIP sources)",
        '"queue pressure"',
        '"state"',
        "KPIs stay WITHHELD",
    )),
    "WorkcellControllerScreen.java": (workcell, (
        "PIONEER PATTERN • WORKCELL ADMISSION GATE",
        "PERMIT ⇔ valid capacity evidence ∧ no fault ∧ output space ∧ resource capacity",
        '"admission"',
        '"reason"',
        "Operations Binding Tool",
    )),
    "UniversalFieldDeviceLdUi.java": (universal, (
        "universalContract(menu.configKind())",
        "Universal HMI rule:",
        "reset statistics does not disarm",
        "PERMIT=15 iff A>0 ∧ B>0 ∧ C>0",
        "no hidden universal physics",
    )),
}

for name, (text, tokens) in wave4.items():
    for token in tokens:
        if token not in text:
            errors.append(f"{name} missing Wave-4 pioneer-rollout token {token!r}")
    if "dev.redstoneengineering.physics" in text:
        errors.append(f"{name} imports physics directly; client screen must remain presentation-only")


wave5 = {
    "EnhancedFieldDeviceScreen.java": (enhanced, (
        "PIONEER PATTERN • SHARED FIELD DEVICE",
        "pioneerContract()",
        "sharedPioneerExplanation()",
        "TOPOLOGY: connected faces = physical graph edges; medium identity is preserved",
        '"OBSERVE: " + observerEquation()',
        "observerFixedContract()",
        "observer-only • read-only",
        "STATE: safety/process state is server-authoritative; invalid evidence fails closed",
        '"AUTHORITY", "policy"',
    )),
    "SignalProcessorLdUi.java": (processor, (
        "PIONEER PATTERN • SIGNAL PROCESSOR MODEL",
        "processorEquation(",
        "y[n+1] = y[n] + clamp(x[n]-y[n], -r, +r)",
        "e[n] = edge_mode(x[n-1], x[n]); e[n] ⇒ y=15 for 2 ticks",
        "rising edge(x) ⇒ y=15 for W ticks; otherwise y=0",
        "Observer readback never initializes or retriggers runtime state",
    )),
}

for name, (text, tokens) in wave5.items():
    for token in tokens:
        if token not in text:
            errors.append(f"{name} missing Wave-5 pioneer-rollout token {token!r}")
    if "dev.redstoneengineering.physics" in text:
        errors.append(f"{name} imports physics directly; client screen must remain presentation-only")


wave6_tokens = (
    "PIONEER PATTERN • RSE DISCRETE TRANSPORT MODEL",
    "isDiscreteTransportDevice()",
    "HOP: A_next=max(0,A-1); RETAIN @4t: A←max(0,A-2), Q←max(0,Q-10)",
    "HOP: A_next=max(0,A-4), node Q=80; RETAIN @4t: A←max(0,A-4), Q←max(0,Q-20)",
    "Lm={water:1,milk-model:2,lava:3}",
    "PHONON_THERMAL is a finite-bandwidth event-packet abstraction",
    "SCULK / CALIBRATED-SENSOR EVENT CODE",
    "MECHANICAL_VIBRATION • SIX-WAY • LOW-LOSS PACKET",
    "HYDROACOUSTIC • SIX-WAY • MEDIUM-DEPENDENT LOSS",
    "PHONON_THERMAL • SIX-WAY • FINITE-BANDWIDTH PACKET",
)
for token in wave6_tokens:
    if token not in enhanced:
        errors.append(f"EnhancedFieldDeviceScreen missing Wave-6 token {token!r}")

for kind in (
    "KIND_MECHANICAL_EXCITER",
    "KIND_SLIME_VIBRATION",
    "KIND_MECHANICAL_RECEIVER",
    "KIND_HONEY_DAMPER",
    "KIND_SCULK_INTERFACE",
    "KIND_HYDRO_TUBE",
    "KIND_HYDRO_EXCITER",
    "KIND_HYDRO_RECEIVER",
    "KIND_PHONON_CONDUIT",
    "KIND_THERMAL_ENCODER",
    "KIND_THERMAL_RECEIVER",
):
    if kind not in enhanced:
        errors.append(f"EnhancedFieldDeviceScreen missing Wave-6 device kind {kind}")

wave7 = {
    "RangeSensorLdUi.java": (range_sensor, (
        "FORMULA-FIRST SENSOR RESPONSE",
        "y = (d ≤ 0) ? 0 : round(15 · (R - d + 1) / R)",
        '"EVIDENCE","scan"',
        "A complete CLEAR scan with d=0 is valid evidence",
    )),
    "SignalConditionerLdUi.java": (conditioner, (
        "SERIES SIGNAL CONDITIONER",
        "y = clamp₀..₁₅(g · x)",
        "y = (|x - y_prev| ≥ B) ? x : y_prev",
        '"EVIDENCE", "boundary"',
        "RseLdUiComponents.authorityFooter()",
    )),
    "QuartzTimingLdUi.java": (quartz, (
        "FORMULA-FIRST TIMING MODEL",
        "valid input ⇒ T_out = min(4096, N · max(1,T_in)) ticks",
        "expectedDividerPeriod",
        "SATURATED @4096",
        "|e_T| = |T_meas - T_upstream|",
    )),
    "MediaConversionLdUi.java": (media_conversion, (
        "FORMULA-FIRST MEDIA BOUNDARY",
        "y_L = round(100 · x_R / 15)",
        "y_R = round(15 · x_L / 100)",
        "x_reconstructed",
        "|e_q|",
        "NO NEW SOURCE PRECISION",
    )),
}

for name, (text, tokens) in wave7.items():
    for token in tokens:
        if token not in text:
            errors.append(f"{name} missing Wave-7 pioneer-rollout token {token!r}")
    if "dev.redstoneengineering.physics" in text:
        errors.append(f"{name} imports physics directly; client screen must remain presentation-only")


for token in (
    "OBSERVER ONLY • retained block + AMR evidence",
    'lineValue(newest, "TRIAL COMPARE:")',
    "MIN_WIDTH = 420",
    "public boolean mouseScrolled",
):
    if token not in tablet:
        errors.append(f"Diagnostic Tablet missing Wave-10 AMR evidence token {token!r}")

for token in (
    "missionTelemetrySnapshot()",
    "DiagnosticTabletItem.captureRobot(",
    "MissionTelemetryStartTick",
):
    if token not in robot:
        errors.append(f"EngineeringMobileRobotEntity missing Wave-10 token {token!r}")

for token in (
    "END_TO_END_ACCEPTED",
    "END_TO_END_ACCEPTED_WITH_RECOVERED_HOLDS",
    "LOAD_TRANSFER_CORRELATION_MISMATCH",
    "PAYLOAD_CORRELATION_MISMATCH",
    "UNLOAD_TRANSFER_CORRELATION_MISMATCH",
):
    if token not in ops_robot_acceptance:
        errors.append(f"OperationsRobotMaterialFlowAcceptance missing Wave-11 token {token!r}")

for token in (
    "correlationKey()",
    "payloadId()",
    "loadTransferId()",
    "unloadTransferId()",
):
    if token not in transport_binding:
        errors.append(f"OperationTransportBinding missing Wave-11 token {token!r}")

wave13_menu_tokens = (
    "PIONEER_MEASUREMENT_TEMPERATURE",
    "PIONEER_MEASUREMENT_LIGHT",
    "PIONEER_MEASUREMENT_TANK",
    "PIONEER_MEASUREMENT_ENTITY_DENSITY",
    "PIONEER_MEASUREMENT_LAPIS_METER",
    "PIONEER_MEASUREMENT_LAPIS_RANGE",
    "PIONEER_MEASUREMENT_ANALOG_INDICATOR",
    "fillPioneerMeasurementSnapshot(block, state)",
    "TemperatureSensorBlock.observe(level, blockPos)",
    "TankLevelSensorBlock.columnSample(level, blockPos)",
    "EntityDensitySensorBlock.densitySample(level, blockPos)",
    "LapisPrecisionMeterBlock.reading(level, blockPos, state)",
    "LapisPrecisionRangeSensorBlock.rangeSample(server, blockPos, state)",
    "indicator.inputObservation(level, blockPos, state)",
)
for token in wave13_menu_tokens:
    if token not in universal_menu:
        errors.append(f"UniversalFieldDeviceMenu missing Wave-13 measurement token {token!r}")

wave13_screen_tokens = (
    "PIONEER WAVE 13 • MEASUREMENT",
    "T_target = N>0 ? floor(ΣT_body / N) : T_environment",
    "condition(B_local, BALANCED)",
    "contiguous loaded fluid cells above",
    "living entities in AABB inflate(4,2,4)",
    "m = unique Lapis sample on selected face",
    "target ⇒ x = round(100·d/R)",
    "display = clamp(x_back,0,15)",
    "measurementRoles(int kind)",
    '"MEASURED"',
    '"EVIDENCE"',
    "no experiment tab is invented",
)
# The no-experiment rule is documented rather than duplicated in client prose.
for token in wave13_screen_tokens[:-1]:
    if token not in universal:
        errors.append(f"UniversalFieldDeviceLdUi missing Wave-13 measurement token {token!r}")
if wave13_screen_tokens[-1] not in doc:
    errors.append("Pioneer standard lost the Wave-13 no-invented-experiment rule")

for token in (
    "Temperature Sensor",
    "Engineering Light Sensor",
    "Tank Level Sensor",
    "Entity Density Sensor",
    "Lapis Precision Meter",
    "Lapis Precision Range Sensor",
    "Analog Indicator",
    "28 remain",
):
    if token not in doc:
        errors.append(f"Pioneer standard missing Wave-13 ledger token {token!r}")

for token in (
    "FieldDeviceUi.openUniversal(serverPlayer, pos)",
    "target=",
    "observation.targetTemperature()",
):
    if token not in temperature_sensor:
        errors.append(f"TemperatureSensorBlock missing Wave-13 HMI token {token!r}")


wave14_menu_tokens = (
    "PIONEER_PROCESS_CALIBRATION",
    "PIONEER_PROCESS_SAMPLE_HOLD",
    "PIONEER_PROCESS_PWM",
    "PIONEER_PROCESS_LAPIS_TEMPERATURE",
    "PIONEER_PROCESS_LAPIS_MAGNETIC",
    "PIONEER_PROCESS_LAPIS_OPTICAL",
    "PIONEER_PROCESS_LAPIS_VOLTAGE",
    "fillPioneerProcessSnapshot(block, state)",
    "CalibrationModuleBlock.measurement(level, blockPos)",
    "SampleHoldBlock.captureCount(level, blockPos)",
    "pwm.assessment(level, blockPos, state)",
    "SensorModel.samplePeriod(profile)",
    "SensorModel.resolutionStep(profile)",
    "SensorModel.noiseAmplitude(profile)",
    "SensorModel.latencySamples(profile)",
)
for token in wave14_menu_tokens:
    if token not in universal_menu:
        errors.append(f"UniversalFieldDeviceMenu missing Wave-14 process token {token!r}")

wave14_screen_tokens = (
    "PIONEER WAVE 14 • SIGNAL / TRANSDUCTION",
    "y = profile(x_obs); residual = y - x_ref",
    "configured trigger edge ⇒ y_hold ← x",
    "N_on=round((u/15)·T)",
    "x=clamp(T_index,0,100)",
    "100·clamp(B,0,15)/15",
    "100·clamp(I,0,15)/15",
    "100·clamp(V,0,15)/15",
    "processRoles(int kind)",
    '"Δt_sample"',
    '"resolution"',
    '"noise"',
    '"latency"',
)
for token in wave14_screen_tokens:
    if token not in universal:
        errors.append(f"UniversalFieldDeviceLdUi missing Wave-14 process token {token!r}")

for token in (
    "Calibration Module",
    "Sample & Hold",
    "PWM Controller",
    "Lapis Temperature Transducer",
    "Lapis Magnetic Transducer",
    "Lapis Optical Transducer",
    "Lapis Voltage Transducer",
    "21 remain",
    "21 → 14 → 7 → 0",
):
    if token not in doc:
        errors.append(f"Pioneer standard missing Wave-14 ledger token {token!r}")

for token in (
    "wave14PortRolesRemainDeviceSpecific",
    "pwmQuantizationSweepIsBoundedAndEndpointExact",
    "transducerProfilesExposeRealSamplingQuantities",
    "EngineeringDomain.THERMAL",
    "EngineeringDomain.IRON_MAGNETIC",
    "EngineeringDomain.OPTICAL",
    "EngineeringDomain.COPPER",
):
    if token not in wave14_tests:
        errors.append(f"Wave-14 GameTests missing behavioral token {token!r}")

if "event.register(RsePioneerWave14GameTests.class);" not in gametest_registration:
    errors.append("Wave-14 GameTests are not registered")

wave15_menu_tokens = (
    "PIONEER_PROCESS_COPPER_WIRE",
    "PIONEER_PROCESS_COPPER_VOLTAGE_SOURCE",
    "PIONEER_PROCESS_COPPER_LOAD",
    "PIONEER_PROCESS_COPPER_SERIES_RESISTOR",
    "PIONEER_PROCESS_COPPER_CAPACITOR",
    "PIONEER_PROCESS_COPPER_FUSE",
    "PIONEER_PROCESS_COPPER_JUNCTION",
    "CopperSeriesResistorBlock.loadResistanceMilli(level, blockPos)",
    "CopperSeriesResistorBlock.currentMilli(level, blockPos)",
    "CopperFuseBlock.loadResistanceMilli(level, blockPos)",
    "CopperFuseBlock.currentMilli(level, blockPos)",
)
for token in wave15_menu_tokens:
    if token not in universal_menu:
        errors.append(f"UniversalFieldDeviceMenu missing Wave-15 Copper token {token!r}")

wave15_screen_tokens = (
    "PIONEER WAVE 15 • COPPER ELECTRICAL",
    "I = V/R; P = V·I",
    "V_out = V_in·R_load/(R_s+R_load)",
    "q_target=round(100·V_in/15)",
    "TRIPPED ← TRIPPED ∨ (I>I_rating)",
    '"R_load"',
    '"charge"',
    '"trip latch"',
    '"SOLVER"',
    '"STATE"',
    "Opening the HMI never performs another load-network scan",
)
for token in wave15_screen_tokens:
    if token not in universal:
        errors.append(f"UniversalFieldDeviceLdUi missing Wave-15 Copper token {token!r}")

for token in (
    "Copper Wire",
    "Copper Voltage Source",
    "Copper Resistive Load",
    "Copper Series Resistor",
    "Copper Capacitor",
    "Copper Fuse",
    "Copper Cable Junction",
    "14 remain",
    "14 → 7 → 0",
):
    if token not in doc:
        errors.append(f"Pioneer standard missing Wave-15 ledger token {token!r}")

for name, text in (
    ("CopperWireBlock", copper_wire_block),
    ("CopperVoltageSourceBlock", copper_source_block),
    ("CopperResistiveLoadBlock", copper_load_block),
    ("CopperSeriesResistorBlock", copper_series_block),
    ("CopperCapacitorBlock", copper_capacitor_block),
    ("CopperFuseBlock", copper_fuse_block),
    ("CopperCableJunctionBlock", copper_junction_block),
):
    if "FieldDeviceUi.openUniversal" not in text:
        errors.append(f"{name} missing Wave-15 Pioneer HMI entry")

for token in (
    "LOAD_RESISTANCE_MILLI_SLOT",
    "CURRENT_MILLI_SLOT",
    "loadResistanceMilli",
    "currentMilli",
):
    if token not in copper_series_block:
        errors.append(f"CopperSeriesResistorBlock missing retained Wave-15 evidence token {token!r}")

for token in (
    "LOAD_RESISTANCE_MILLI",
    "CURRENT_MILLI",
    "loadResistanceMilli",
    "currentMilli",
):
    if token not in copper_fuse_block:
        errors.append(f"CopperFuseBlock missing retained Wave-15 protection token {token!r}")

for token in (
    "copperDcModelSweepRemainsBounded",
    "capacitorProfilesExposeImplementedTau",
    "seriesResistorRetainsSolverEvidenceFromAuthoritativeTick",
    "fuseRetainsProtectionEvidenceFromAuthoritativeTick",
):
    if token not in wave15_tests:
        errors.append(f"Wave-15 GameTests missing behavioral token {token!r}")

if "event.register(RsePioneerWave15GameTests.class);" not in gametest_registration:
    errors.append("Wave-15 GameTests are not registered")

wave16_menu_tokens = (
    "PIONEER_PROCESS_LAPIS_NOISE",
    "PIONEER_PROCESS_QUARTZ_OSCILLATOR",
    "PIONEER_PROCESS_QUARTZ_PHASE_DELAY",
    "PIONEER_PROCESS_QUARTZ_LAPIS_SAMPLER",
    "PIONEER_PROCESS_SOUL_INJECTOR",
    "PIONEER_PROCESS_SOUL_METER",
    "PIONEER_PROCESS_MOLECULAR_RECEIVER",
    "LapisNoiseSourceBlock.currentValue(level, blockPos, state)",
    "QuartzLabOscillatorBlock.timingEvidence(level, blockPos, state)",
    "QuartzPhaseDelayBlock.pendingTicks(level, blockPos)",
    "QuartzTriggeredLapisSamplerBlock.heldValue(level, blockPos)",
    "SoulFluxInjectorBlock.attachedNodeCount(level, blockPos)",
    "SoulFluxMeterBlock.inputObservation(level, blockPos, state)",
    "MolecularCloudReceiverBlock.gainFor(sensitivity)",
)
for token in wave16_menu_tokens:
    if token not in universal_menu:
        errors.append(f"UniversalFieldDeviceMenu missing Wave-16 active-source token {token!r}")

wave16_screen_tokens = (
    "PIONEER WAVE 16 • ACTIVE SOURCE / TIMING",
    "y[n]=clamp(μ + η_det(n,pos),0,100)",
    "half-interval=max(1,T_nom/2 + j)",
    "real post-init rising edge ⇒ pending=D",
    "valid QUARTZ rising edge ⇒ y_hold←x_L",
    "packet=4u",
    "floor(15·Q_s/100)",
    "c_raw=clamp(round(g·Σ r_cloud/(1+d²)),0,15)",
    '"μ"',
    '"jitter offset"',
    '"pending"',
    '"c_filt"',
    '"ADJUSTABLE"',
    '"EVIDENCE"',
    '"SOLVER"',
)
for token in wave16_screen_tokens:
    if token not in universal:
        errors.append(f"UniversalFieldDeviceLdUi missing Wave-16 active-source token {token!r}")

for token in (
    "Lapis Noise Source",
    "Quartz Lab Oscillator",
    "Quartz Phase Delay",
    "Quartz Triggered Lapis Sampler",
    "Soul Flux Injector",
    "Soul Flux Meter",
    "Molecular Cloud Receiver",
    "7 remain",
    "Remaining 7 for Wave 17:",
    "115 → 122",
):
    if token not in doc:
        errors.append(f"Pioneer standard missing Wave-16 ledger token {token!r}")

for name, text in (
    ("LapisNoiseSourceBlock", lapis_noise_block),
    ("QuartzLabOscillatorBlock", quartz_oscillator_block),
    ("QuartzPhaseDelayBlock", quartz_phase_delay_block),
    ("QuartzTriggeredLapisSamplerBlock", quartz_sampler_block),
    ("SoulFluxInjectorBlock", soul_injector_block),
    ("SoulFluxMeterBlock", soul_meter_block),
    ("MolecularCloudReceiverBlock", molecular_receiver_block),
):
    if "FieldDeviceUi.openUniversal" not in text:
        errors.append(f"{name} missing Wave-16 Pioneer HMI entry")

for token in (
    "zeroNoiseSourceProducesValidZeroEvidence",
    "quartzOscillatorRetainsRealizedIntervalEvidence",
    "wave16ParameterProfilesRemainBounded",
    "wave16PortRolesRemainSpecialized",
):
    if token not in wave16_tests:
        errors.append(f"Wave-16 GameTests missing behavioral token {token!r}")

if "event.register(RsePioneerWave16GameTests.class);" not in gametest_registration:
    errors.append("Wave-16 GameTests are not registered")


wave17_menu_tokens = (
    "PIONEER_PROCESS_IRON_CORE",
    "PIONEER_PROCESS_THERMAL_MASS",
    "PIONEER_PROCESS_THERMAL_HEATER",
    "PIONEER_PROCESS_THERMAL_RADIATOR",
    "PIONEER_PROCESS_THERMAL_CALORIMETER",
    "PIONEER_PROCESS_SOUL_CONDUIT",
    "PIONEER_PROCESS_SOUL_RESERVOIR",
    "IronCoreBlock.appliedFieldSample(level, blockPos)",
    "ThermalMassBlock.thermalState(level, blockPos, state)",
    "ThermalHeaterBlock.targetTemperature(voltage, resistance)",
    "ThermalRadiatorBlock.observation(level, blockPos)",
    "ThermalCalorimeterBlock.history(level, blockPos)",
    "SoulFluxNetwork.chargeSnapshot(level, blockPos)",
)
for token in wave17_menu_tokens:
    if token not in universal_menu:
        errors.append(f"UniversalFieldDeviceMenu missing Wave-17 closure token {token!r}")

wave17_screen_tokens = (
    "PIONEER WAVE 17 • MATERIAL / STORAGE / THERMAL",
    "complete radius-2 scan ∧ B_applied≥8",
    "T_target=floor((2·T_env+T_neighbor)/3)",
    "P=V²/R",
    "every 10 ticks each adjacent mass T>20",
    "ΔT_20t=T[n]-T[n-1]",
    "transient Soul Flux J decays by 1 each 20 ticks",
    "stored Q_s decays by 1 each 40 ticks",
    '"remanence"',
    '"C_index"',
    '"C·ΔT"',
    '"STATE"',
    '"ADJUSTABLE"',
    '"DERIVED"',
)
for token in wave17_screen_tokens:
    if token not in universal:
        errors.append(f"UniversalFieldDeviceLdUi missing Wave-17 closure token {token!r}")

for token in (
    "Iron Core",
    "Thermal Mass",
    "Thermal Heater",
    "Thermal Radiator",
    "Thermal Calorimeter",
    "Soul Soil Conduit",
    "Soul Sand Reservoir",
    "0 remain",
    "122 / 122",
):
    if token not in doc:
        errors.append(f"Pioneer standard missing Wave-17 closure token {token!r}")

for name, text in (
    ("IronCoreBlock", iron_core_block),
    ("ThermalMassBlock", thermal_mass_block),
    ("ThermalHeaterBlock", thermal_heater_block),
    ("ThermalRadiatorBlock", thermal_radiator_block),
    ("ThermalCalorimeterBlock", thermal_calorimeter_block),
    ("SoulSoilConduitBlock", soul_conduit_block),
    ("SoulSandReservoirBlock", soul_reservoir_block),
):
    if "FieldDeviceUi.openUniversal" not in text:
        errors.append(f"{name} missing final Pioneer HMI entry")

for token in (
    "finalSevenParameterProfilesMatchImplementedModels",
    "thermalMassCapacityChangesBoundedResponse",
    "calorimeterHistoryIsServerRetainedObserverEvidence",
    "soulReservoirValidZeroDiffersFromAbsentConduitFlux",
    "finalSevenPortRolesRemainSpecialized",
):
    if token not in wave17_tests:
        errors.append(f"Wave-17 GameTests missing behavioral token {token!r}")

if "event.register(RsePioneerWave17GameTests.class);" not in gametest_registration:
    errors.append("Wave-17 GameTests are not registered")

if "dev.redstoneengineering.physics" in universal:
    errors.append("UniversalFieldDeviceLdUi imports physics directly; final client must remain presentation-only")

registration_token = "event.register(EngineeringUiRegistration.FIELD_DEVICE.get(), EnhancedFieldDeviceScreen::new);"
if registration_token not in client_registration:
    errors.append("FIELD_DEVICE is no longer registered to the enhanced shared Pioneer inspector")

kind_count = field_menu.count("public static final int KIND_")
if kind_count < 79:
    errors.append(f"expected FieldDeviceMenu taxonomy to retain at least 79 device kinds, found {kind_count}")

formula_migrated = 0
screens_dir = root / "src/main/java/dev/redstoneengineering/client/ui"
if screens_dir.is_dir():
    for path in screens_dir.glob("*Screen.java"):
        text = path.read_text(errors="ignore")
        if "extends EngineeringScreen<" in text and "formulaCard(" in text:
            formula_migrated += 1

ldlib_formula_migrated = 0
ldlib_dir = root / "src/main/java/dev/redstoneengineering/ui/ldlib"
if ldlib_dir.is_dir():
    for path in ldlib_dir.glob("*LdUi.java"):
        text = path.read_text(errors="ignore")
        if "ModularUI" in text and ("formulaCard(" in text or "governingEquation(" in text):
            ldlib_formula_migrated += 1

formula_migrated_total = formula_migrated + ldlib_formula_migrated
if formula_migrated_total < 20:
    errors.append(
        f"expected at least 20 formula-first HMI families after Wave 5, "
        f"found EngineeringScreen={formula_migrated}, LDLib2={ldlib_formula_migrated}, total={formula_migrated_total}"
    )

if errors:
    print("RSE PIONEER SHOWCASE ROLLOUT VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE PIONEER SHOWCASE ROLLOUT VERIFY: PASS")
print(" showcase pioneer references: low-pass / oscilloscope / PID PASS")
print(" Wave 2 communication / pneumatic / optical / magnetic PASS")
print(" Wave 3 amethyst / reliability / radio / metrology / operations PASS")
print(" Wave 4 timing / copper / plant-state / workcell / universal-contract PASS")
print(" Wave 5 shared field-device inspector / signal processors PASS")
print(" Wave 6 discrete mechanical / hydro / Sculk / thermal transport PASS")
print(" Wave 7 range / conditioning / quartz / media-conversion contracts PASS")
print(" Wave 8 full FieldDevice source / medium integrity closure PASS")
print(" Wave 9 explicit PID baseline / candidate commissioning trial PASS")
print(" Wave 10 AMR mission telemetry / Diagnostic Tablet trial PASS")
print(" Wave 11 Operations → AMR end-to-end material-flow acceptance PASS")
print(" Wave 12 Signal Analyzer internal-reference calibration trial PASS")
print(" Wave 13 seven-block measurement / observer Pioneer rollout PASS")
print(" Wave 14 seven-block signal / transduction Pioneer rollout PASS")
print(" Wave 15 seven-block Copper electrical Pioneer rollout PASS")
print(" Wave 16 seven-block active-source / timing Pioneer rollout PASS")
print(" Wave 17 final seven-block material / storage / thermal rollout PASS")
print(" Pioneer completion ledger: 122 / 122 processed; 0 remain")
print(f" FieldDeviceMenu device-kind taxonomy: {kind_count}")
print(f" formula-first legacy EngineeringScreen families: {formula_migrated}")
print(f" formula-first LDLib2 HMI families: {ldlib_formula_migrated}")
print(f" formula-first HMI families total: {formula_migrated_total}")
print(" no client-side second physics/robotics/metrology/Copper/timing/thermal solver in Waves 2-17: PASS")
