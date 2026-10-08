#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing Engineering UI file: {rel}")
        return ""
    return path.read_text(errors="ignore")


def require(rel: str, *tokens: str) -> None:
    body = read(rel)
    for token in tokens:
        if body and token not in body:
            errors.append(f"{rel}: missing UI safety token {token!r}")


require("src/main/java/dev/redstoneengineering/client/ui/EngineeringScreen.java",
        "OVERVIEW", "PORTS", "CONFIGURE", "DIAGNOSTICS", "HISTORY",
        'Component.literal("Route")', "routePage", "setRoutePage", "routeActionId(boolean clockwise)", "routeSupported",
        "routeNext", 'Component.literal("Cycle direction ▶")',
        "routeInputNext", "routeOutputNext",
        'Component.literal("Cycle RX ▶")',
        'Component.literal("Cycle TX ▶")',
        "One-button route control",
        "routeInputActionId(boolean clockwise)", "routeOutputActionId(boolean clockwise)",
        "hasRouteInputEndpoint()", "hasRouteOutputEndpoint()",
        "DigitalCommunicationMenu.BUTTON_INPUT_LEFT", "DigitalCommunicationMenu.BUTTON_INPUT_RIGHT",
        "DigitalCommunicationMenu.BUTTON_OUTPUT_LEFT", "DigitalCommunicationMenu.BUTTON_OUTPUT_RIGHT",
        "PneumaticSystemMenu.BUTTON_INPUT_LEFT", "PneumaticSystemMenu.BUTTON_INPUT_RIGHT",
        "PneumaticSystemMenu.BUTTON_OUTPUT_LEFT", "PneumaticSystemMenu.BUTTON_OUTPUT_RIGHT",
        "SignalProcessorMenu.BUTTON_INPUT_LEFT", "SignalProcessorMenu.BUTTON_OUTPUT_RIGHT",
        "SignalConditionerMenu.BUTTON_INPUT_LEFT", "SignalConditionerMenu.BUTTON_OUTPUT_RIGHT",
        "QuartzTimingMenu.BUTTON_INPUT_LEFT", "QuartzTimingMenu.BUTTON_OUTPUT_RIGHT",
        "AmethystSystemMenu.BUTTON_INPUT_LEFT", "AmethystSystemMenu.BUTTON_OUTPUT_RIGHT",
        "OpticalSystemMenu.BUTTON_INPUT_LEFT", "OpticalSystemMenu.BUTTON_OUTPUT_RIGHT",
        "MagneticSystemMenu.BUTTON_INPUT_LEFT", "MagneticSystemMenu.BUTTON_OUTPUT_RIGHT",
        "ReliabilitySystemMenu.BUTTON_INPUT_LEFT", "ReliabilitySystemMenu.BUTTON_OUTPUT_RIGHT",
        "FieldDeviceMenu.BUTTON_INPUT_PREVIOUS", "FieldDeviceMenu.BUTTON_OUTPUT_NEXT",
        'DIAGNOSTICS("Observe"', "ROLE • ", "HEALTH • ", "LIVE STATE • HEALTH ", "I/O • ", "menu.portRouteLabel()",
        "routeControlY()", "footerTop()", "FOOTER_HEIGHT = 66", "renderPersistentLiveStateStrip", "fitForWidth", "safeText",
        "isConfigureSection()", "showsPortVisualization", "canvasRight() - canvasValueX()",
        "MIN_WORKSPACE_WIDTH = 440", "MAX_WORKSPACE_WIDTH = 780",
        "MIN_WORKSPACE_HEIGHT = 320", "MAX_WORKSPACE_HEIGHT = 520",
        "mouseScrolled", "DEFAULT_CANVAS_WIDTH = 1020", "DEFAULT_CANVAS_HEIGHT = 1820",
        "CONTENT_TOP = 112", "NAV_COLUMNS = 3", "NAV_GAP_X = 8", "NAV_GAP_Y = 6",
        "NAV_HEIGHT = 20", "NAV_TOP = 34", "navX", "navY", "pageLabelY = CONTENT_TOP - 18",
        "renderGlobalEngineeringContract", "ENGINEERING CONTRACT • MODEL / VARIABLES / EVIDENCE",
        "renderMechanismFlow", "MECHANISM FLOW • LIVE SERVER STRUCTURE",
        "menu.receivePortFacesLabel()", "menu.transmitPortFacesLabel()",
    "SHARED_APPENDIX_TOP = 960",
    "CONFIGURE_APPENDIX_TOP = 1080",
    "SHARED_APPENDIX_GAP = 44",
    "sharedAppendixTop",
    "engineeringContractTop",
    "renderAppendixDivider",
    "SHARED ENGINEERING APPENDIX • BELOW DEVICE-SPECIFIC CONTENT",
        "MODEL AUTHORITY", "FORMULA POLICY", "EVIDENCE CONTRACT",
        "renderScrollIndicators", "virtualContentWidth", "virtualContentHeight",
        "formulaCard", "variableRole", "evidenceRow", "wrappedText",
        '"Parameters, modes and actions"', '"Direct RX / TX direction control"',
        "SignalAnalyzerMenu.BUTTON_ROTATE_LEFT", "SignalAnalyzerMenu.BUTTON_ROTATE_RIGHT",
        "if (menu instanceof SignalAnalyzerMenu) return true;")
require("src/main/java/dev/redstoneengineering/client/ui/EngineeringIoCompassOverlay.java",
        "engineeringScreen.showsPortVisualization()", '"I/O COMPASS"',
        "boolean rightFits", "boolean leftFits", "if (!rightFits && !leftFits) return;",
        "screen.height - margin - panelHeight", "connectionMask(menu)", "linkEvidenceKnown",
        '"DECLARED"', '"AIR PATH"', '"LOS PATH"', '"LINKED"', '"OPEN"')
require("src/main/java/dev/redstoneengineering/client/ui/PneumaticSystemScreen.java",
        "extends LdlibEngineeringHostScreen<PneumaticSystemMenu>")
require("src/main/java/dev/redstoneengineering/ui/ldlib/PneumaticSystemLdUi.java",
        "DataBindingBuilder.string", "P ∈ {25,50,75,100}",
        "Toggle valve", "Cycle direction ▶", "Cycle RX ▶", "Cycle TX ▶",
        "ΔP_path = ΔP_line + ΔP_restriction",
        "COMMISSIONING", "cylinderSupply()", "cylinderRestrictionLoss()",
        "RseLdUiComponents.authorityFooter()")
require("src/main/java/dev/redstoneengineering/ui/menu/PneumaticSystemMenu.java",
        "BUTTON_SETPOINT_DIRECT_BASE", "pressure % 25",
        "PneumaticSystemLdUi.create(this, inventory.player)",
        "setSetpointFromUi", "toggleValve",
        "cycleWholeRouteForward", "cycleInputForward", "cycleOutputForward")

require("src/main/java/dev/redstoneengineering/client/ui/MagneticSystemScreen.java",
        "extends LdlibEngineeringHostScreen<MagneticSystemMenu>")
require("src/main/java/dev/redstoneengineering/ui/ldlib/MagneticSystemLdUi.java",
        "DataBindingBuilder.string", "V_ind = clamp", "1..15", "1..4",
        "Cycle N marker ▶", "Cycle RX ▶", "Cycle TX ▶",
        "RseLdUiComponents.authorityFooter()")
require("src/main/java/dev/redstoneengineering/ui/menu/MagneticSystemMenu.java",
        "BUTTON_PRIMARY_DIRECT_BASE", "PermanentMagnetBlock.STRENGTH", "InductionCoilBlock.TURNS",
        "MagneticSystemLdUi.create(this, inventory.player)",
        "setPrimaryFromUi", "cycleOrientationForward", "cycleInputForward", "cycleOutputForward")

require("src/main/java/dev/redstoneengineering/ui/ldlib/EnhancedFieldDeviceLdUi.java",
        "KIND_MECHANICAL_EXCITER", "KIND_HYDRO_EXCITER",
        "1..15 frequency index", '-> "f"',
        "discreteExciterAdjustable(int k)", "exact server-backed frequency",
        "read-only implemented model", "TTL=8t; retain A−2, Q−10",
        "event packet; source clears after 1t",
        "8-bit payload • 0..255",
        "byte-frame transport • fixed link timing",
        "1-bit balanced logic",
        "read-only transport contract",
        "hop loss = 1; strongest-source resolution",
        "0..100 lossless line • single-source topology",
        "clock period evidence • single-source topology",
        "frequency 1..15 + amplitude packet • conflict-aware",
        "SERVICE_OPEN = hard isolation",
        "y_byte = x_R ∈ [0,15]  (no rescale)",
        "y_R = valid ? min(15, x_byte) : 0",
        "serial_byte = bus_byte ; frame = 8 t/word",
        "bus_byte = serial_byte ; watchdog = 16 t",
        "b = (x_R > 0) ? 1 : 0",
        "y_R = (valid ∧ b=1) ? 15 : 0",
        "y_through = y_tap = x_in ; tap never back-drives input",
        "y_R = round(condition_PRECISION(x_servo))",
        "y_flow = condition_PRECISION(flow_proxy(P_in,P_out,path))",
        "ΔB_axis = B(+axis,r=6) − B(−axis,r=6)",
        "E[f]=ΣA_i(f), f_dom=argmax(E[f])",
        "radius 6 • 10t scan • bands 1..15",
        "observer-only • read-only")
require("src/main/java/dev/redstoneengineering/block/MechanicalExciterBlock.java",
        "setFrequency(Level level, BlockPos pos, int frequency)")
require("src/main/java/dev/redstoneengineering/block/HydroacousticExciterBlock.java",
        "setFrequency(Level level, BlockPos pos, int frequency)")
require("src/main/java/dev/redstoneengineering/ui/menu/FieldDeviceMenu.java",
        "MechanicalExciterBlock.setFrequency", "HydroacousticExciterBlock.setFrequency")

require("src/main/java/dev/redstoneengineering/ui/ldlib/UniversalFieldDeviceLdUi.java",
        "{6, 9, 12, 16} gain", "{8, 16, 32, 64} blocks", "1..3 severity",
        "{2, 4, 8, 16} ticks", 'CONFIG_COPPER_CAPACITOR -> "τ"',
        "τ is the visible discrete response constant",
        "SampleHoldBlock.modeName(menu.configPrimary())",
        "CalibrationModuleBlock.profileName(menu.configPrimary())",
        "FaultInjectorBlock.modeLabelFor(menu.configPrimary())",
        "SensorModel.condition(x, profile)",
        "primaryCycleKind", "hasExplicitAction", "hasToggle",
        "READ-ONLY HMI • no fake control",
        "numeric values use exact entry",
        "DataBindingBuilder.string", "applyPrimary(menu, value)", "applySecondary(menu, value)")
require("src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java",
        "MolecularCloudReceiverBlock.SENSITIVITY",
        "LapisPrecisionRangeSensorBlock.RANGE_INDEX",
        "AlarmProcessorBlock.SEVERITY")

require("src/main/java/dev/redstoneengineering/ui/ldlib/UniversalFieldDeviceLdUi.java",
        '"g"', "gain map is FIXED",
        '"B_threshold"', '"scan radius"',
        '"T_floor"', "read-only decay law",
        "server-supported {6,9,12,16} set",
        "packet = 4·u_R • read-only law",
        "y_R=floor(15·Q_s/100) • read-only",
        '"trigger"', "QUARTZ rising edge",
        '"ΔT_20t"', "20 ticks • read-only retained interval",
        "read-only storage law",
        "aperture is FIXED: 6 adjacent faces",
        "Visible τ selects the implemented discrete response profile",
        "condition↑ latches severity",
        "ARM ? fault_mode(x) : x",
        "RESET∨¬RUN⇒step=0",
        "PERMIT=15 iff A>0 ∧ B>0 ∧ C>0",
        "alarm=15 iff topology report hasIssue()",
        '"V_node","drivers","ports"',
        "explicit splice • >1 driver = TOPOLOGY_ERROR",
        "read-only topology contract")

require("src/main/java/dev/redstoneengineering/ui/ldlib/IndustrialBufferLdUi.java",
        'fixedRow("capacity"', "server-owned buffer capacity",
        "SOUTH=15 iff free capacity>0", "NORTH=15 iff free capacity=0")
require("src/main/java/dev/redstoneengineering/ui/ldlib/WorkcellControllerLdUi.java",
        'fixedRow("binding authority"', "Operations Binding Tool",
        "Binding is external server authority")
require("src/main/java/dev/redstoneengineering/ui/ldlib/OperationsMonitorLdUi.java",
        'fixedRow("KPI window"', "1200 ticks / 60 s",
        "queue≥13 OVERLOADED", "queue≥9 CONGESTED",
        "stopped+queued 600t ⇒ FAILED")

require("src/main/java/dev/redstoneengineering/client/ui/PidControllerScreen.java",
        "extends LdlibEngineeringHostScreen<PidControllerMenu>")
require("src/main/java/dev/redstoneengineering/ui/ldlib/PidControllerLdUi.java",
        "PIONEER PATTERN • CONTROL / ACCEPTANCE MODEL",
        "e[n]=SP[n]-PV[n]", "saturation may hold integral",
        "PidTrendPlotElement", "Cycle tuning preset ▶", "Cycle RX ▶", "Cycle TX ▶",
        "Capture acceptance", "Trial baseline", "Trial candidate",
        "PIONEER WORKFLOW • CLOSED-LOOP COMMISSIONING TRIAL",
        "RseLdUiComponents.authorityFooter()")
require("src/main/java/dev/redstoneengineering/ui/menu/PidControllerMenu.java",
        "PidControllerLdUi.create(this, inventory.player)",
        "cycleTuningForward", "cycleInputForward", "cycleOutputForward",
        "captureAcceptance", "resetRuntimeTrend",
        "captureTrialBaseline", "captureTrialCandidate", "clearTrial")

require("src/main/java/dev/redstoneengineering/client/ui/CopperCircuitMeterScreen.java",
        "extends LdlibEngineeringHostScreen<CopperCircuitMeterMenu>")
require("src/main/java/dev/redstoneengineering/ui/ldlib/CopperCircuitMeterLdUi.java",
        "I = V / R_eq", "COMMISSIONING", "OBSERVER ONLY",
        "Cycle measurement face ▶", "RseLdUiComponents.authorityFooter()")
require("src/main/java/dev/redstoneengineering/ui/menu/CopperCircuitMeterMenu.java",
        "CopperCircuitMeterLdUi.create(this, inventory.player)", "cycleFaceForward")
require("src/main/java/dev/redstoneengineering/client/ui/MediaConversionScreen.java",
        "extends LdlibEngineeringHostScreen<MediaConversionMenu>")
require("src/main/java/dev/redstoneengineering/ui/ldlib/MediaConversionLdUi.java",
        "Cycle RX ▶", "Cycle TX ▶",
        "menu.inputFace().getName().toUpperCase()",
        "menu.outputFace().getName().toUpperCase()",
        "NO NEW SOURCE PRECISION",
        "RseLdUiComponents.authorityFooter()")
require("src/main/java/dev/redstoneengineering/ui/menu/MediaConversionMenu.java",
        "MediaConversionLdUi.create(this, inventory.player)",
        "cycleRxForward", "cycleTxForward")

require("src/main/java/dev/redstoneengineering/client/ui/LogicAnalyzerScreen.java",
        "extends LdlibEngineeringHostScreen<LogicAnalyzerMenu>")
require("src/main/java/dev/redstoneengineering/ui/ldlib/LogicAnalyzerLdUi.java",
        "ModularUI", "DataBindingBuilder.string", "LogicAnalyzerPlotElement",
        "PIONEER PATTERN • DIGITAL TIMING MODEL", "D_ch[n] = (x_ch[n] ≥ T) ? HIGH : LOW",
        "Bus interference", "shield exposed instrument segments", "RseLdUiComponents.authorityFooter()")
require("src/main/java/dev/redstoneengineering/ui/menu/LogicAnalyzerMenu.java",
        "BUTTON_THRESHOLD_DIRECT_BASE = 17000", "BUTTON_CURSOR_A_DIRECT_BASE = 17100",
        "BUTTON_CURSOR_B_DIRECT_BASE = 17200", "setCursorA", "setCursorB",
        "LogicAnalyzerLdUi.create(this, inventory.player)",
        "setThresholdFromUi", "setCursorAFromUi", "setCursorBFromUi",
        "cycleTriggerChannel", "cycleTriggerEdge")
require("src/main/java/dev/redstoneengineering/blockentity/LogicAnalyzerBlockEntity.java",
        "setCursorA(int slot)", "setCursorB(int slot)")

require("src/main/java/dev/redstoneengineering/client/ui/SignalAnalyzerScreen.java",
        "extends LdlibEngineeringHostScreen<SignalAnalyzerMenu>")
require("src/main/java/dev/redstoneengineering/ui/ldlib/SignalAnalyzerLdUi.java",
        "ModularUI", "DataBindingBuilder.string", "SignalAnalyzerPlotElement",
        "-2..+2 • direct entry", "0..15 • direct entry",
        "PIONEER WORKFLOW • INTERNAL REFERENCE CALIBRATION TRIAL",
        "RseLdUiComponents.authorityFooter()")
require("src/main/java/dev/redstoneengineering/ui/menu/SignalAnalyzerMenu.java",
        "BUTTON_CALIBRATION_DIRECT_BASE = 18000", "BUTTON_REFERENCE_DIRECT_BASE = 18100",
        "setCalibrationOffset", "setReference",
        "SignalAnalyzerLdUi.create(this, inventory.player)",
        "setCalibrationFromUi", "setReferenceFromUi",
        "captureTrialBaseline", "captureTrialCandidate", "clearTrial")
require("src/main/java/dev/redstoneengineering/block/SignalAnalyzerBlock.java",
        "setCalibrationOffset(Level level, BlockPos pos, int offset)",
        "setReference(Level level, BlockPos pos, int reference)")

require("src/main/java/dev/redstoneengineering/client/ui/RangeSensorScreen.java",
        "extends LdlibEngineeringHostScreen<RangeSensorMenu>")
require("src/main/java/dev/redstoneengineering/ui/ldlib/RangeSensorLdUi.java",
        "TextField", "DataBindingBuilder.string",
        "{4,8,15}", "Cycle detect ▶", "Cycle response ▶",
        "FORMULA-FIRST SENSOR RESPONSE",
        "A complete CLEAR scan with d=0 is valid evidence",
        "RseLdUiComponents.authorityFooter()")
require("src/main/java/dev/redstoneengineering/ui/menu/RangeSensorMenu.java",
        "BUTTON_RANGE_DIRECT_BASE = 16000",
        "range == 4 ? 0", "range == 8 ? 1", "range == 15 ? 2",
        "RangeSensorLdUi.create(this, inventory.player)",
        "setRangeFromUi", "cycleDetectForward", "cycleResponseForward")

require("src/main/java/dev/redstoneengineering/client/ui/AmethystSystemScreen.java",
        "extends LdlibEngineeringHostScreen<AmethystSystemMenu>")
require("src/main/java/dev/redstoneengineering/ui/ldlib/AmethystSystemLdUi.java",
        "DataBindingBuilder.string", "f_target", "Q_idx",
        "A_out = (f_in = f_target) ? max(0, A_in - 1) : 0",
        "BW = 5 - Q", "Frequency values are deliberate model indices, not fabricated Hz",
        "Pulse", "Cycle direction ▶", "Cycle RX ▶", "Cycle TX ▶",
        "RseLdUiComponents.authorityFooter()")
require("src/main/java/dev/redstoneengineering/ui/menu/AmethystSystemMenu.java",
        "BUTTON_PRIMARY_DIRECT_BASE = 15000", "BUTTON_SECONDARY_DIRECT_BASE = 15100",
        "AmethystResonatorBlock.FREQUENCY", "AmethystResonatorBlock.AMPLITUDE",
        "AmethystFrequencyFilterBlock.TARGET", "AmethystTunedResonatorBlock.NATURAL",
        "AmethystTunedResonatorBlock.Q_INDEX",
        "AmethystSystemLdUi.create(this, inventory.player)",
        "setPrimaryFromUi", "setSecondaryFromUi", "pulse",
        "cycleWholeRouteForward", "cycleInputForward", "cycleOutputForward")

require("src/main/java/dev/redstoneengineering/client/ui/RadioLinkScreen.java",
        "extends LdlibEngineeringHostScreen<RadioLinkMenu>")
require("src/main/java/dev/redstoneengineering/ui/ldlib/RadioLinkLdUi.java",
        "DataBindingBuilder.string", "ADJUSTABLE", "0..3",
        "availability", "decode", "Cycle output direction ▶",
        "RseLdUiComponents.authorityFooter()")
require("src/main/java/dev/redstoneengineering/ui/menu/RadioLinkMenu.java",
        "BUTTON_CHANNEL_DIRECT_BASE = 14000",
        "RadioTransmitterBlock.CHANNEL", "RadioReceiverBlock.CHANNEL",
        "RadioLinkLdUi.create(this, inventory.player)",
        "setChannelFromUi", "cycleOutputForward")

require("src/main/java/dev/redstoneengineering/client/ui/DigitalCommunicationScreen.java",
        "extends LdlibEngineeringHostScreen<DigitalCommunicationMenu>")
require("src/main/java/dev/redstoneengineering/ui/ldlib/DigitalCommunicationLdUi.java",
        "ModularUI", "DataBindingBuilder.string", "{20,40,60}% • direct entry",
        "Cycle direction ▶", "Cycle RX ▶", "Cycle TX ▶",
        "RseLdUiComponents.authorityFooter()")
require("src/main/java/dev/redstoneengineering/ui/menu/DigitalCommunicationMenu.java",
        "BUTTON_PARAMETER_DIRECT_BASE = 13000", "percent == 20 ? 0",
        "percent == 40 ? 1", "percent == 60 ? 2",
        "DigitalCommunicationLdUi.create(this, inventory.player)",
        "setRegeneratorThresholdFromUi", "cycleWholeRouteForward",
        "cycleRxForward", "cycleTxForward")

require("src/main/java/dev/redstoneengineering/ui/ldlib/PneumaticSystemLdUi.java",
        "P_out = round(100 · u_R / 15)",
        "y_R = min(15, floor(15 · P_in / 100))",
        "OPEN ⇒ BACK ↔ FRONT ; CLOSED ⇒ isolated",
        "permitted flow = BACK → FRONT only ; reverse blocked",
        "H_charge = max(0, P_line - P_stored)",
        "ΔP_local = max(0, P_in - P_out)")

require("src/main/java/dev/redstoneengineering/client/ui/OpticalSystemScreen.java",
        "extends LdlibEngineeringHostScreen<OpticalSystemMenu>")
require("src/main/java/dev/redstoneengineering/ui/ldlib/OpticalSystemLdUi.java",
        "ModularUI", "DataBindingBuilder.string",
        "Cycle direction ▶", "Cycle RX ▶", "Cycle TX ▶",
        "RseLdUiComponents.authorityFooter()")
require("src/main/java/dev/redstoneengineering/ui/menu/OpticalSystemMenu.java",
        "BUTTON_PRIMARY_DIRECT_BASE", "BUTTON_SECONDARY_DIRECT_BASE",
        "OpticalSystemLdUi.create(this, inventory.player)",
        "applyPrimaryFromUi", "applySecondaryFromUi",
        "cycleWholeRouteForward", "cycleInputForward", "cycleOutputForward")

require("src/main/java/dev/redstoneengineering/client/ui/QuartzTimingScreen.java",
        "extends LdlibEngineeringHostScreen<QuartzTimingMenu>")
require("src/main/java/dev/redstoneengineering/ui/ldlib/QuartzTimingLdUi.java",
        "DataBindingBuilder.string", "DIRECT T", "DIRECT N",
        "Reset measurement", "Cycle RX ▶",
        "f_nom = 20 / T Hz", "|e_T| = |T_meas - T_upstream|",
        "RseLdUiComponents.authorityFooter()")
require("src/main/java/dev/redstoneengineering/ui/menu/QuartzTimingMenu.java",
        "BUTTON_PARAMETER_DIRECT_BASE", "QuartzClockDividerBlock.cycleDivision",
        "QuartzTimingLdUi.create(this, inventory.player)",
        "setTimingParameterFromUi", "resetMeasurement",
        "cycleInputForward", "cycleOutputForward", "cycleWholeRouteForward")

require("src/main/java/dev/redstoneengineering/client/ui/ReliabilitySystemScreen.java",
        "extends LdlibEngineeringHostScreen<ReliabilitySystemMenu>")
require("src/main/java/dev/redstoneengineering/ui/ldlib/ReliabilitySystemLdUi.java",
        "ModularUI", "DataBindingBuilder.string",
        "{20,40,80,160} ticks", "{0,1,2,4} spread", "{1,4,8,12} redstone",
        "Maintenance action", "Cycle direction ▶", "Cycle RX ▶", "Cycle TX ▶",
        "RseLdUiComponents.authorityFooter()")
require("src/main/java/dev/redstoneengineering/ui/menu/ReliabilitySystemMenu.java",
        "BUTTON_PARAMETER_DIRECT_BASE", "value == 160", "value == 12",
        "ReliabilitySystemLdUi.create(this, inventory.player)",
        "applyParameterFromUi", "runMaintenance",
        "cycleWholeRouteForward", "cycleInputForward", "cycleOutputForward")

require("src/main/java/dev/redstoneengineering/ui/ldlib/OpticalSystemLdUi.java",
        "I_set", "CH_target", "exact server-backed value",
        "0..8", "0..3")
require("src/main/java/dev/redstoneengineering/ui/menu/OpticalSystemMenu.java",
        "BUTTON_PRIMARY_DIRECT_BASE = 9000", "BUTTON_SECONDARY_DIRECT_BASE = 9100",
        "OpticalEmitterBlock.INTENSITY", "OpticalChannelFilterBlock.TARGET",
        "OpticalAttenuatorBlock.LOSS", "FreeSpaceOpticalTransmitterBlock.CHANNEL",
        "FreeSpaceOpticalReceiverBlock.CHANNEL")

require("src/main/java/dev/redstoneengineering/ui/ldlib/ReliabilitySystemLdUi.java",
        "{20,40,80,160} ticks", "{0,1,2,4} spread", "{1,4,8,12} redstone",
        "direct entry")
require("src/main/java/dev/redstoneengineering/ui/menu/ReliabilitySystemMenu.java",
        "BUTTON_PARAMETER_DIRECT_BASE = 10000", "value == 160 ? 3",
        "RedundantVoterBlock.TOLERANCE", "FaultLatchBlock.THRESHOLD",
        "validVisibleParameter")

require("src/main/java/dev/redstoneengineering/ui/ldlib/QuartzTimingLdUi.java",
        "DIRECT T", "DIRECT N", "DataBindingBuilder.string")
require("src/main/java/dev/redstoneengineering/ui/menu/QuartzTimingMenu.java",
        "BUTTON_PARAMETER_DIRECT_BASE = 11000", "QuartzClockDividerBlock.setDivision",
        "setTimingParameterFromUi")
require("src/main/java/dev/redstoneengineering/block/QuartzClockDividerBlock.java",
        "setDivision(ServerLevel level, BlockPos pos, int divisor)")

require("src/main/java/dev/redstoneengineering/client/ui/SignalProcessorScreen.java",
        "extends LdlibEngineeringHostScreen<SignalProcessorMenu>")
require("src/main/java/dev/redstoneengineering/ui/ldlib/SignalProcessorLdUi.java",
        "TextField", "DataBindingBuilder.string",
        "r ∈ 1..4", "W ∈ 1..8 ticks",
        "Cycle edge mode ▶", "Cycle RX ▶", "Cycle TX ▶",
        "RseLdUiComponents.authorityFooter()")
require("src/main/java/dev/redstoneengineering/ui/menu/SignalProcessorMenu.java",
        "BUTTON_PARAMETER_DIRECT_BASE", "PrecisionFilterBlock.setRate", "PulseShaperBlock.setWidth",
        "SignalProcessorLdUi.create(this, inventory.player)",
        "setParameterFromUi", "cycleParameterForward")
require("src/main/java/dev/redstoneengineering/block/PrecisionFilterBlock.java",
        "setRate(Level level, BlockPos pos, int rate)")
require("src/main/java/dev/redstoneengineering/block/PulseShaperBlock.java",
        "setWidth(Level level, BlockPos pos, int width)")

# LDLib2 MIGRATION CONTRACT: shared host + shared components + per-device UI + server intent facade.
require("src/main/java/dev/redstoneengineering/ui/ldlib/RseLdUiComponents.java",
        "formulaCard(", "liveRow(", "serverAction(", "authorityFooter()")
require("src/main/java/dev/redstoneengineering/client/ui/ldlib/LdlibEngineeringHostScreen.java",
        "IModularUIHolderMenu", "AbstractContainerScreen<M>",
        "getModularUI()", "LDLib2 owns the engineering HMI canvas")
require("src/main/java/dev/redstoneengineering/client/ui/SignalConditionerScreen.java",
        "extends LdlibEngineeringHostScreen<SignalConditionerMenu>")
require("src/main/java/dev/redstoneengineering/ui/ldlib/SignalConditionerLdUi.java",
        "ModularUI", "DataBindingBuilder.string",
        "setNumbersOnlyInt(-5, 15)",
        "Cycle mode ▶", "Cycle RX ▶", "Cycle TX ▶",
        "RseLdUiComponents.authorityFooter()", "governingEquation(menu.mode())")
require("src/main/java/dev/redstoneengineering/ui/ldlib/RseLdUiComponents.java",
        "authorityFooter()", "SERVER AUTHORITY",
        "presentation + validated operator intent")
require("src/main/java/dev/redstoneengineering/block/SignalConditionerBlock.java",
        "setFormulaParameter(Level level, BlockPos pos, int formulaValue)",
        "formulaValue + 5")
require("src/main/java/dev/redstoneengineering/ui/menu/SignalConditionerMenu.java",
        "BUTTON_PARAM_DIRECT_BASE", "setFormulaParameter(level, blockPos",
        "SignalConditionerLdUi.create(this, inventory.player)",
        "applyVisibleFormulaParameter", "cycleModeForward",
        "cycleInputForward", "cycleOutputForward")

require("src/main/java/dev/redstoneengineering/client/ui/OscilloscopeScreen.java",
        "extends LdlibEngineeringHostScreen<OscilloscopeMenu>")
require("src/main/java/dev/redstoneengineering/ui/ldlib/OscilloscopeLdUi.java",
        "ModularUI", "Cycle Δt ▶", "SAMPLING EXPERIMENT",
        "setTriggerLevelFromUi", "setCursorAFromUi", "setCursorBFromUi")
require("src/main/java/dev/redstoneengineering/ui/menu/OscilloscopeMenu.java",
        "OscilloscopeLdUi.create(this, inventory.player)",
        "setSamplePeriodFromUi", "setTriggerLevelFromUi",
        "setCursorAFromUi", "setCursorBFromUi")

require("src/main/java/dev/redstoneengineering/client/ui/UniversalFieldDeviceScreen.java",
        "extends LdlibEngineeringHostScreen<UniversalFieldDeviceMenu>")
require("src/main/java/dev/redstoneengineering/ui/ldlib/UniversalFieldDeviceLdUi.java",
        "ModularUI", "FORMULA PARAMETER WORKBENCH",
        "DECLARED ENGINEERING PORTS", "SYSTEM / OPERATOR STATE",
        "PIONEER WAVE 13 • MEASUREMENT", "SERVER-SYNCHRONIZED DEVICE STATE",
        "RseLdUiComponents.authorityFooter()")
require("src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java",
        "UniversalFieldDeviceLdUi.create(this, inventory.player)",
        "applyPrimaryRawTargetFromUi", "applySecondaryRawTargetFromUi",
        "cycleWholeRouteForward", "cycleInputForward", "cycleOutputForward",
        "runConfigAction", "toggleConfiguration")

require("src/main/java/dev/redstoneengineering/client/ui/EnhancedFieldDeviceScreen.java",
        "extends LdlibEngineeringHostScreen<FieldDeviceMenu>")
require("src/main/java/dev/redstoneengineering/ui/ldlib/EnhancedFieldDeviceLdUi.java",
        "ModularUI", "engineeringHint(", "diagnosticHint(",
        "Exact engineering value", "directEntryKind(", "directRangeLabel(",
        "DataBindingBuilder.string", "PIONEER PATTERN • SHARED FIELD DEVICE",
        "PIONEER PATTERN • SOURCE / MEDIUM INTEGRITY",
        "Cycle direction ▶", "Cycle RX ▶", "Cycle TX ▶",
        "READ-ONLY HMI • no fake control", "RseLdUiComponents.authorityFooter()")

# Legacy FieldDevice authority must include endpoint-aware RX/TX controls while preserving
# standalone physical measurement/interface rotation.
require("src/main/java/dev/redstoneengineering/ui/menu/FieldDeviceMenu.java",
        "DirectionalRedstoneEndpointBlock", "SignalProbeBlock", "RedstoneCableTerminalBlock",
        "BUTTON_PRIMARY_DIRECT_BASE", "applyDirectPrimaryEngineeringValue",
        "BUTTON_INPUT_PREVIOUS", "BUTTON_INPUT_NEXT", "BUTTON_OUTPUT_PREVIOUS", "BUTTON_OUTPUT_NEXT",
        "rotateEndpoint(block, input, clockwise)",
        "DirectionalSignalBlock.rotateSeriesInput(level, blockPos, clockwise)",
        "DirectionalSignalBlock.rotateSeriesOutput(level, blockPos, clockwise)",
        "DirectionalDomainBlock.rotateSeriesInput(level, blockPos, clockwise)",
        "DirectionalDomainBlock.rotateSeriesOutput(level, blockPos, clockwise)",
        "DirectionalRedstoneEndpointBlock.rotateOutput(level, blockPos, clockwise)",
        "SignalProbeBlock.rotateMeasurementAxis(level, blockPos, clockwise)",
        "RedstoneCableTerminalBlock.rotateInterface(level, blockPos, clockwise)",
        "EnhancedFieldDeviceLdUi.create(this, inventory.player)",
        "applyPrimaryEngineeringValueFromUi", "toggleFromUi", "presetFromUi",
        "cycleDirectionForward", "cycleInputForward", "cycleOutputForward")
require("src/main/java/dev/redstoneengineering/block/SignalProbeBlock.java",
        "rotateMeasurementAxis(Level level, BlockPos pos, boolean clockwise)",
        "ROUTE_CYCLE", "state.setValue(FACING, next)")
require("src/main/java/dev/redstoneengineering/block/RedstoneCableTerminalBlock.java",
        "rotateInterface(Level level, BlockPos pos, boolean clockwise)",
        "state.setValue(FACING, nextFacing)", "RedstoneCableNetwork.recompute(server, pos)")
require("src/main/java/dev/redstoneengineering/block/RedstoneReferenceSourceBlock.java",
        "extends DirectionalRedstoneEndpointBlock")
require("src/main/java/dev/redstoneengineering/block/DirectionalRedstoneSensorBlock.java",
        "extends DirectionalRedstoneEndpointBlock")

# Universal fallback must derive endpoint visibility from declared ports and keep multi-port layouts legal.
require("src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java",
        "DirectionalRedstoneEndpointBlock", "SignalProbeBlock", "RedstoneCableTerminalBlock",
        "isRotatable(block)", "hasInputEndpoint()", "hasOutputEndpoint()",
        "routeKind(block) == ROUTE_MULTI_PORT_LAYOUT",
        "DirectionalRedstoneEndpointBlock.rotateOutput(level, blockPos, clockwise)",
        "SignalProbeBlock.rotateMeasurementAxis(level, blockPos, clockwise)",
        "RedstoneCableTerminalBlock.rotateInterface(level, blockPos, clockwise)")

# Migrated endpoint devices must have a reachable Engineering UI. Shift preserves compact diagnostics.
for rel in (
    "src/main/java/dev/redstoneengineering/block/EngineeringLightSensorBlock.java",
    "src/main/java/dev/redstoneengineering/block/TankLevelSensorBlock.java",
    "src/main/java/dev/redstoneengineering/block/EntityDensitySensorBlock.java",
    "src/main/java/dev/redstoneengineering/block/AnalogIndicatorBlock.java",
):
    require(rel, "player.isShiftKeyDown()", "FieldDeviceUi.open(serverPlayer, pos)")

# Dedicated pneumatic HMI must track both declared endpoint faces, not infer RX as TX.opposite().
require("src/main/java/dev/redstoneengineering/ui/menu/PneumaticSystemMenu.java",
        "block instanceof PressureRegulatorBlock",
        "facing.set(DirectionalDomainBlock.seriesOutputSide(state).ordinal())",
        "inputFacing.set(DirectionalDomainBlock.seriesInputSide(state).ordinal())",
        "DirectionalDomainBlock.seriesInputSide(state)",
        "DirectionalSignalBlock.seriesInputSide(state)",
        "DirectionalDomainBlock.rotateSeriesInput(level, blockPos, clockwise)",
        "DirectionalDomainBlock.rotateSeriesOutput(level, blockPos, clockwise)",
        "changed = rotateDirectional(block, id)")

# Digital communication HMI likewise tracks independent RX/TX authority.
require("src/main/java/dev/redstoneengineering/ui/menu/DigitalCommunicationMenu.java",
        "inputFacing", "BUTTON_INPUT_LEFT", "BUTTON_INPUT_RIGHT", "BUTTON_OUTPUT_LEFT", "BUTTON_OUTPUT_RIGHT",
        "DirectionalDomainBlock.seriesInputSide(state)", "DirectionalSignalBlock.seriesInputSide(state)",
        "DirectionalDomainBlock.rotateSeriesInput(level, blockPos, clockwise)",
        "DirectionalSignalBlock.rotateSeriesOutput(level, blockPos, clockwise)")

# Newly audited specialized device HMIs must expose true endpoint authority where physically valid.
require("src/main/java/dev/redstoneengineering/ui/menu/SignalProcessorMenu.java",
        "BUTTON_INPUT_LEFT", "BUTTON_OUTPUT_RIGHT", "DirectionalSignalBlock.rotateSeriesInput", "DirectionalSignalBlock.rotateSeriesOutput")
require("src/main/java/dev/redstoneengineering/ui/menu/SignalConditionerMenu.java",
        "BUTTON_INPUT_LEFT", "BUTTON_OUTPUT_RIGHT", "DirectionalSignalBlock.rotateSeriesInput", "DirectionalSignalBlock.rotateSeriesOutput")
require("src/main/java/dev/redstoneengineering/ui/menu/QuartzTimingMenu.java",
        "BUTTON_INPUT_LEFT", "BUTTON_OUTPUT_RIGHT", "DirectionalDomainBlock.rotateSeriesInput", "DirectionalDomainBlock.rotateSeriesOutput")
require("src/main/java/dev/redstoneengineering/ui/menu/AmethystSystemMenu.java",
        "BUTTON_INPUT_LEFT", "BUTTON_OUTPUT_RIGHT", "DirectionalDomainBlock.rotateSeriesInput", "DirectionalDomainBlock.rotateSeriesOutput")
require("src/main/java/dev/redstoneengineering/ui/menu/MagneticSystemMenu.java",
        "BUTTON_INPUT_LEFT", "BUTTON_OUTPUT_RIGHT", "DirectionalDomainBlock.rotateSeriesInput", "DirectionalDomainBlock.rotateSeriesOutput")
require("src/main/java/dev/redstoneengineering/ui/menu/ReliabilitySystemMenu.java",
        "BUTTON_INPUT_LEFT", "BUTTON_OUTPUT_RIGHT", "routeEndpoint", "routeFaultLatch")

# Range sensor rotation owns stale-output invalidation at the block layer, not ad-hoc menu mutation.
require("src/main/java/dev/redstoneengineering/block/RangeSensorBlock.java",
        "rotateSensingAxis(Level level, BlockPos pos, boolean clockwise)",
        "Direction oldOutput = outputSide(state)", "Direction newOutput = outputSide(next)",
        "level.updateNeighborsAt(pos.relative(oldOutput), sensor)",
        "level.updateNeighborsAt(pos.relative(newOutput), sensor)")
require("src/main/java/dev/redstoneengineering/ui/menu/RangeSensorMenu.java",
        "RangeSensorBlock.rotateSensingAxis(level, blockPos, id == BUTTON_ROTATE_RIGHT)")

# Signal Analyzer owns a real six-face TEST/INLINE axis. Rotation must clear history from the old
# measurement face, withdraw the stale INLINE output, and remain server-authoritative through Route.
require("src/main/java/dev/redstoneengineering/block/SignalAnalyzerBlock.java",
        "rotateMeasurementAxis(Level level, BlockPos pos, boolean clockwise)",
        "ROUTE_CYCLE", "state.setValue(FACING, nextFacing).setValue(OUTPUT, 0)",
        "RuntimeIntStore.remove(level, KEY, pos)",
        "level.updateNeighborsAt(pos.relative(oldOutput), analyzer)",
        "level.updateNeighborsAt(pos.relative(newOutput), analyzer)")
require("src/main/java/dev/redstoneengineering/ui/menu/SignalAnalyzerMenu.java",
        "BUTTON_ROTATE_LEFT", "BUTTON_ROTATE_RIGHT",
        "SignalAnalyzerBlock.rotateMeasurementAxis(level, blockPos, id == BUTTON_ROTATE_RIGHT)")

# Free-space optical channel selection belongs in Configure; independent RX/TX authority belongs on Route.
require("src/main/java/dev/redstoneengineering/ui/FieldDeviceUi.java",
        "FreeSpaceOpticalTransmitterBlock", "FreeSpaceOpticalReceiverBlock", "new OpticalSystemMenu")
require("src/main/java/dev/redstoneengineering/ui/menu/OpticalSystemMenu.java",
        "KIND_FREE_SPACE_TX", "KIND_FREE_SPACE_RX",
        "FreeSpaceOpticalTransmitterBlock.CHANNEL", "FreeSpaceOpticalReceiverBlock.CHANNEL",
        "BUTTON_SECONDARY_PREVIOUS", "BUTTON_SECONDARY_NEXT",
        "BUTTON_INPUT_LEFT", "BUTTON_INPUT_RIGHT", "BUTTON_OUTPUT_LEFT", "BUTTON_OUTPUT_RIGHT",
        "DirectionalSignalBlock.rotateSeriesInput(level, blockPos, false)",
        "DirectionalSignalBlock.rotateSeriesOutput(level, blockPos, false)",
        "DirectionalDomainBlock.rotateSeriesInput(level, blockPos, false)",
        "DirectionalDomainBlock.rotateSeriesOutput(level, blockPos, false)",
        "routeSplitter(id)")
require("src/main/java/dev/redstoneengineering/ui/ldlib/OpticalSystemLdUi.java",
        "KIND_FREE_SPACE_TX", "KIND_FREE_SPACE_RX",
        "secondaryControl", "Direction and physical interface orientation are controlled only on Route.")

# Specialized Configure pages may not recreate a second direction/orientation authority.
route_capable_screens = (
    "SignalConditionerScreen.java", "RangeSensorScreen.java", "SignalAnalyzerScreen.java",
    "SignalProcessorScreen.java", "QuartzTimingScreen.java", "RadioLinkScreen.java",
    "DigitalCommunicationScreen.java", "PneumaticSystemScreen.java", "OpticalSystemScreen.java",
    "AmethystSystemScreen.java", "MagneticSystemScreen.java", "ReliabilitySystemScreen.java",
)
for name in route_capable_screens:
    body = read("src/main/java/dev/redstoneengineering/client/ui/" + name)
    for forbidden in ("directionCycle", "orientationCycle", "BUTTON_ROTATE_LEFT", "BUTTON_ROTATE_RIGHT", "BUTTON_OUTPUT_LEFT", "BUTTON_OUTPUT_RIGHT"):
        if forbidden in body:
            errors.append(f"{name}: duplicates physical Route authority on Configure via {forbidden!r}")

for name in (
    "EnhancedFieldDeviceScreen.java", "SignalConditionerScreen.java", "PidControllerScreen.java",
    "OscilloscopeScreen.java", "LogicAnalyzerScreen.java", "SignalAnalyzerScreen.java",
    "UniversalFieldDeviceScreen.java", "RangeSensorScreen.java", "SignalProcessorScreen.java",
    "QuartzTimingScreen.java", "RadioLinkScreen.java", "DigitalCommunicationScreen.java",
    "PneumaticSystemScreen.java", "OpticalSystemScreen.java", "AmethystSystemScreen.java",
    "MagneticSystemScreen.java", "ReliabilitySystemScreen.java", "OperationsMonitorScreen.java",
):
    body = read("src/main/java/dev/redstoneengineering/client/ui/" + name)
    if not body:
        continue
    suspicious = (
        'drawString(font, "This ', 'drawString(font,"This ',
        'drawString(font, "The ', 'drawString(font,"The ',
        'drawString(font, "Observer ', 'drawString(font,"Observer ',
        'drawString(font, "Buttons ', 'drawString(font,"Buttons ',
    )
    for token in suspicious:
        if token in body:
            errors.append(f"{name}: long explanatory text bypasses safeText via {token!r}")

screen = read("src/main/java/dev/redstoneengineering/client/ui/EngineeringScreen.java")
for forbidden in ("sharedRotateCcw", "sharedRotateCw", '"SIGNAL ROUTE"', "drawFaceMatrix(", "ROUTE_CONTROL_Y = 160"):
    if forbidden in screen:
        errors.append(f"EngineeringScreen restored crowded/duplicated layout element {forbidden!r}")

# Both direction-cycle actions and endpoint-cycle actions must be present where applicable.
for token in (
    "FieldDeviceMenu.BUTTON_ROTATE_CCW", "FieldDeviceMenu.BUTTON_ROTATE_CW",
    "UniversalFieldDeviceMenu.BUTTON_ROTATE_LEFT", "UniversalFieldDeviceMenu.BUTTON_ROTATE_RIGHT",
    "RangeSensorMenu.BUTTON_ROTATE_LEFT", "RangeSensorMenu.BUTTON_ROTATE_RIGHT",
    "SignalAnalyzerMenu.BUTTON_ROTATE_LEFT", "SignalAnalyzerMenu.BUTTON_ROTATE_RIGHT",
    "SignalProcessorMenu.BUTTON_ROTATE_LEFT", "SignalProcessorMenu.BUTTON_ROTATE_RIGHT",
    "SignalConditionerMenu.BUTTON_ROTATE_LEFT", "SignalConditionerMenu.BUTTON_ROTATE_RIGHT",
    "QuartzTimingMenu.BUTTON_ROTATE_LEFT", "QuartzTimingMenu.BUTTON_ROTATE_RIGHT",
    "RadioLinkMenu.BUTTON_OUTPUT_LEFT", "RadioLinkMenu.BUTTON_OUTPUT_RIGHT",
    "DigitalCommunicationMenu.BUTTON_ROTATE_LEFT", "DigitalCommunicationMenu.BUTTON_ROTATE_RIGHT",
    "PneumaticSystemMenu.BUTTON_ROTATE_LEFT", "PneumaticSystemMenu.BUTTON_ROTATE_RIGHT",
    "OpticalSystemMenu.BUTTON_ROTATE_LEFT", "OpticalSystemMenu.BUTTON_ROTATE_RIGHT",
    "AmethystSystemMenu.BUTTON_ROTATE_LEFT", "AmethystSystemMenu.BUTTON_ROTATE_RIGHT",
    "MagneticSystemMenu.BUTTON_ROTATE_LEFT", "MagneticSystemMenu.BUTTON_ROTATE_RIGHT",
    "ReliabilitySystemMenu.BUTTON_ROTATE_LEFT", "ReliabilitySystemMenu.BUTTON_ROTATE_RIGHT",
):
    if token not in screen:
        errors.append(f"EngineeringScreen Route page missing preserved direction action {token!r}")

# Operations Monitor is intentionally a fixed multi-face observer contract, not a fake rotatable output.
require("src/main/java/dev/redstoneengineering/block/OperationsMonitorBlock.java",
        '"MACHINE RUNNING", Direction.DOWN', '"CYCLE PULSE", Direction.UP',
        '"QUEUE / WIP", side')

client_dir = root / "src/main/java/dev/redstoneengineering/client/ui"
if client_dir.is_dir():
    forbidden_authority = ("dev.redstoneengineering.physics", "RuntimeIntStore", "scheduleTick(", "setBlock(", "updateNeighborsAt(", "EngineeringAcceptance.evaluate")
    for source in sorted(client_dir.glob("*.java")):
        body = source.read_text(errors="ignore")
        for token in forbidden_authority:
            if token in body:
                errors.append(f"client UI authority violation in {source.name}: contains {token!r}")

require("src/main/java/dev/redstoneengineering/client/ui/EngineeringUiClientRegistration.java",
        "EnhancedFieldDeviceScreen::new", "UniversalFieldDeviceScreen::new", "AmethystSystemScreen::new",
        "ReliabilitySystemScreen::new", "OperationsMonitorScreen::new", "EngineeringIoCompassOverlay::render")
require("src/main/java/dev/redstoneengineering/gametest/RseEngineeringUiGameTests.java",
        "conditionerUiActionsDriveAuthoritativeWorldState", "pidUiActionChangesOnlyBoundedTuningPreset")

if errors:
    print("RSE Engineering UI verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE Engineering UI verification: PASS")
print(" six-page responsibility split including dedicated Route page: PASS")
print(" Configure parameters/modes/actions preserved: PASS")
print(" specialized Configure pages do not duplicate Route authority: PASS")
print(" one-button endpoint-driven RX/TX controls on shared Route page: PASS")
print(" one-button Direction cycle preserved for measurement/interface axes: PASS")
print(" redstone reference/source/sensor FieldDevice route authority: PASS")
print(" endpoint Engineering UI reachability + Shift diagnostics: PASS")
print(" universal + legacy fallback route authority parity: PASS")
print(" pneumatic regulator dual-endpoint route authority: PASS")
print(" range sensor old/new output invalidation on rotation: PASS")
print(" signal analyzer six-face route + history invalidation: PASS")
print(" signal probe six-face measurement-axis rotation: PASS")
print(" cable terminal physical-interface rotation: PASS")
print(" free-space optical Configure/Route responsibility split: PASS")
print(" fixed Operations Monitor port contract preserved: PASS")
print(" full-height page workspace / no duplicate route schematic: PASS")
print(" narrow-screen I/O Compass fail-safe: PASS")
print(" shared pixel-clamped long-form text policy: PASS")
print(" server-authoritative ROLE / HEALTH / EVIDENCE HMI: PASS")
print(" client UI authority boundary: PASS")
