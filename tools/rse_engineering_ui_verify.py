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
        "EditBox", "submitSetpoint", "BUTTON_SETPOINT_DIRECT_BASE",
        "P ∈ {25,50,75,100}")
require("src/main/java/dev/redstoneengineering/ui/menu/PneumaticSystemMenu.java",
        "BUTTON_SETPOINT_DIRECT_BASE", "pressure % 25")

require("src/main/java/dev/redstoneengineering/client/ui/MagneticSystemScreen.java",
        "EditBox", "submitPrimary", "BUTTON_PRIMARY_DIRECT_BASE",
        "S = 1..15", "N = 1..4")
require("src/main/java/dev/redstoneengineering/ui/menu/MagneticSystemMenu.java",
        "BUTTON_PRIMARY_DIRECT_BASE", "PermanentMagnetBlock.STRENGTH", "InductionCoilBlock.TURNS")

require("src/main/java/dev/redstoneengineering/client/ui/EnhancedFieldDeviceScreen.java",
        "KIND_MECHANICAL_EXCITER", "KIND_HYDRO_EXCITER",
        "1..15 frequency index", '-> "f"')
require("src/main/java/dev/redstoneengineering/block/MechanicalExciterBlock.java",
        "setFrequency(Level level, BlockPos pos, int frequency)")
require("src/main/java/dev/redstoneengineering/block/HydroacousticExciterBlock.java",
        "setFrequency(Level level, BlockPos pos, int frequency)")
require("src/main/java/dev/redstoneengineering/ui/menu/FieldDeviceMenu.java",
        "MechanicalExciterBlock.setFrequency", "HydroacousticExciterBlock.setFrequency")

require("src/main/java/dev/redstoneengineering/client/ui/UniversalFieldDeviceScreen.java",
        "{6, 9, 12, 16} gain", "{8, 16, 32, 64} blocks", "1..3 severity",
        '"Cycle " + primaryName', "primaryPrevious.visible = false",
        "secondaryPrevious.visible = false")
require("src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java",
        "MolecularCloudReceiverBlock.SENSITIVITY",
        "LapisPrecisionRangeSensorBlock.RANGE_INDEX",
        "AlarmProcessorBlock.SEVERITY")

require("src/main/java/dev/redstoneengineering/client/ui/PidControllerScreen.java",
        "Cycle tuning preset ▶", "Cycle RX ▶", "Cycle TX ▶",
        "BUTTON_TUNING_NEXT", "BUTTON_INPUT_NEXT", "BUTTON_OUTPUT_NEXT")

require("src/main/java/dev/redstoneengineering/client/ui/CopperCircuitMeterScreen.java",
        "Cycle measure face ▶", "Cycle face • ")
require("src/main/java/dev/redstoneengineering/client/ui/MediaConversionScreen.java",
        "Cycle RX ▶", "Cycle TX ▶", "Cycle RX • ", "Cycle TX • ")

require("src/main/java/dev/redstoneengineering/client/ui/LogicAnalyzerScreen.java",
        "EditBox", "submitThreshold", "submitCursorA", "submitCursorB",
        "BUTTON_THRESHOLD_DIRECT_BASE", "BUTTON_CURSOR_A_DIRECT_BASE", "BUTTON_CURSOR_B_DIRECT_BASE",
        "1..15 • direct entry", "0..15 • direct entry")
require("src/main/java/dev/redstoneengineering/ui/menu/LogicAnalyzerMenu.java",
        "BUTTON_THRESHOLD_DIRECT_BASE = 17000", "BUTTON_CURSOR_A_DIRECT_BASE = 17100",
        "BUTTON_CURSOR_B_DIRECT_BASE = 17200", "setCursorA", "setCursorB")
require("src/main/java/dev/redstoneengineering/blockentity/LogicAnalyzerBlockEntity.java",
        "setCursorA(int slot)", "setCursorB(int slot)")

require("src/main/java/dev/redstoneengineering/client/ui/SignalAnalyzerScreen.java",
        "EditBox", "submitCalibration", "submitReference",
        "BUTTON_CALIBRATION_DIRECT_BASE", "BUTTON_REFERENCE_DIRECT_BASE",
        "-2..+2 • direct entry", "0..15 • direct entry")
require("src/main/java/dev/redstoneengineering/ui/menu/SignalAnalyzerMenu.java",
        "BUTTON_CALIBRATION_DIRECT_BASE = 18000", "BUTTON_REFERENCE_DIRECT_BASE = 18100",
        "setCalibrationOffset", "setReference")
require("src/main/java/dev/redstoneengineering/block/SignalAnalyzerBlock.java",
        "setCalibrationOffset(Level level, BlockPos pos, int offset)",
        "setReference(Level level, BlockPos pos, int reference)")

require("src/main/java/dev/redstoneengineering/client/ui/RangeSensorScreen.java",
        "EditBox", "submitRange", "BUTTON_RANGE_DIRECT_BASE",
        "{4,8,15} blocks • direct entry",
        "Cycle detect • ", "Cycle response • ")
require("src/main/java/dev/redstoneengineering/ui/menu/RangeSensorMenu.java",
        "BUTTON_RANGE_DIRECT_BASE = 16000",
        "range == 4 ? 0", "range == 8 ? 1", "range == 15 ? 2")

require("src/main/java/dev/redstoneengineering/client/ui/AmethystSystemScreen.java",
        "EditBox", "submitPrimary", "submitSecondary",
        "BUTTON_PRIMARY_DIRECT_BASE", "BUTTON_SECONDARY_DIRECT_BASE",
        "f_target", "Q_idx")
require("src/main/java/dev/redstoneengineering/ui/menu/AmethystSystemMenu.java",
        "BUTTON_PRIMARY_DIRECT_BASE = 15000", "BUTTON_SECONDARY_DIRECT_BASE = 15100",
        "AmethystResonatorBlock.FREQUENCY", "AmethystResonatorBlock.AMPLITUDE",
        "AmethystFrequencyFilterBlock.TARGET", "AmethystTunedResonatorBlock.NATURAL",
        "AmethystTunedResonatorBlock.Q_INDEX")

require("src/main/java/dev/redstoneengineering/client/ui/RadioLinkScreen.java",
        "EditBox", "submitChannel", "BUTTON_CHANNEL_DIRECT_BASE",
        "0..3 • direct entry")
require("src/main/java/dev/redstoneengineering/ui/menu/RadioLinkMenu.java",
        "BUTTON_CHANNEL_DIRECT_BASE = 14000",
        "RadioTransmitterBlock.CHANNEL", "RadioReceiverBlock.CHANNEL")

require("src/main/java/dev/redstoneengineering/client/ui/DigitalCommunicationScreen.java",
        "EditBox", "submitParameter", "BUTTON_PARAMETER_DIRECT_BASE",
        "{20,40,60}% • direct entry")
require("src/main/java/dev/redstoneengineering/ui/menu/DigitalCommunicationMenu.java",
        "BUTTON_PARAMETER_DIRECT_BASE = 13000", "percent == 20 ? 0",
        "percent == 40 ? 1", "percent == 60 ? 2")

require("src/main/java/dev/redstoneengineering/client/ui/OpticalSystemScreen.java",
        "EditBox", "submitPrimary", "submitSecondary",
        "BUTTON_PRIMARY_DIRECT_BASE", "BUTTON_SECONDARY_DIRECT_BASE")
require("src/main/java/dev/redstoneengineering/ui/menu/OpticalSystemMenu.java",
        "BUTTON_PRIMARY_DIRECT_BASE", "BUTTON_SECONDARY_DIRECT_BASE")

require("src/main/java/dev/redstoneengineering/client/ui/QuartzTimingScreen.java",
        "EditBox", "submitParameter", "BUTTON_PARAMETER_DIRECT_BASE",
        "T={2,4,8,16,32}", "N={2,4,8,16}")
require("src/main/java/dev/redstoneengineering/ui/menu/QuartzTimingMenu.java",
        "BUTTON_PARAMETER_DIRECT_BASE", "QuartzClockDividerBlock.cycleDivision")

require("src/main/java/dev/redstoneengineering/client/ui/ReliabilitySystemScreen.java",
        "EditBox", "submitParameter", "BUTTON_PARAMETER_DIRECT_BASE",
        "{20,40,80,160} ticks", "tol {0,1,2,4}", "T_fault {1,4,8,12}")
require("src/main/java/dev/redstoneengineering/ui/menu/ReliabilitySystemMenu.java",
        "BUTTON_PARAMETER_DIRECT_BASE", "value == 160", "value == 12")

require("src/main/java/dev/redstoneengineering/client/ui/OpticalSystemScreen.java",
        "EditBox", "submitPrimary", "submitSecondary",
        "BUTTON_PRIMARY_DIRECT_BASE", "BUTTON_SECONDARY_DIRECT_BASE",
        "CH_target", "I_set")
require("src/main/java/dev/redstoneengineering/ui/menu/OpticalSystemMenu.java",
        "BUTTON_PRIMARY_DIRECT_BASE = 9000", "BUTTON_SECONDARY_DIRECT_BASE = 9100",
        "OpticalEmitterBlock.INTENSITY", "OpticalChannelFilterBlock.TARGET",
        "OpticalAttenuatorBlock.LOSS", "FreeSpaceOpticalTransmitterBlock.CHANNEL",
        "FreeSpaceOpticalReceiverBlock.CHANNEL")

require("src/main/java/dev/redstoneengineering/client/ui/ReliabilitySystemScreen.java",
        "EditBox", "submitParameter", "BUTTON_PARAMETER_DIRECT_BASE",
        "{20,40,80,160} ticks", "{0,1,2,4} spread", "{1,4,8,12} redstone")
require("src/main/java/dev/redstoneengineering/ui/menu/ReliabilitySystemMenu.java",
        "BUTTON_PARAMETER_DIRECT_BASE = 10000", "value == 160 ? 3",
        "RedundantVoterBlock.TOLERANCE", "FaultLatchBlock.THRESHOLD")

require("src/main/java/dev/redstoneengineering/client/ui/QuartzTimingScreen.java",
        "EditBox", "submitParameter", "T={2,4,8,16,32}", "N={2,4,8,16}",
        "BUTTON_PARAMETER_DIRECT_BASE")
require("src/main/java/dev/redstoneengineering/ui/menu/QuartzTimingMenu.java",
        "BUTTON_PARAMETER_DIRECT_BASE = 11000", "QuartzClockDividerBlock.setDivision")
require("src/main/java/dev/redstoneengineering/block/QuartzClockDividerBlock.java",
        "setDivision(ServerLevel level, BlockPos pos, int divisor)")

require("src/main/java/dev/redstoneengineering/client/ui/SignalProcessorScreen.java",
        "EditBox", "submitParameter", "BUTTON_PARAMETER_DIRECT_BASE",
        "r ∈ 1..4", "W ∈ 1..8 ticks")
require("src/main/java/dev/redstoneengineering/ui/menu/SignalProcessorMenu.java",
        "BUTTON_PARAMETER_DIRECT_BASE", "PrecisionFilterBlock.setRate", "PulseShaperBlock.setWidth")
require("src/main/java/dev/redstoneengineering/block/PrecisionFilterBlock.java",
        "setRate(Level level, BlockPos pos, int rate)")
require("src/main/java/dev/redstoneengineering/block/PulseShaperBlock.java",
        "setWidth(Level level, BlockPos pos, int width)")

require("src/main/java/dev/redstoneengineering/client/ui/SignalConditionerScreen.java",
        "EditBox", "submitParameter", "BUTTON_PARAM_DIRECT_BASE",
        "visibleFormulaParameter", "Direct entry submits the formula value to the server")
require("src/main/java/dev/redstoneengineering/block/SignalConditionerBlock.java",
        "setFormulaParameter(Level level, BlockPos pos, int formulaValue)",
        "formulaValue + 5")
require("src/main/java/dev/redstoneengineering/ui/menu/SignalConditionerMenu.java",
        "BUTTON_PARAM_DIRECT_BASE", "setFormulaParameter(level, blockPos")

require("src/main/java/dev/redstoneengineering/client/ui/EnhancedFieldDeviceScreen.java",
        "safeText(g, engineeringHint()", "safeText(g, diagnosticHint()",
        "fitForWidth(label, 72)", "fitForWidth(value, 72)",
        "Exact engineering value", "directEntryKind()", "directRangeLabel()",
        "BUTTON_PRIMARY_DIRECT_BASE", '"Apply " + formulaSymbol()')

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
        "RedstoneCableTerminalBlock.rotateInterface(level, blockPos, clockwise)")
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
require("src/main/java/dev/redstoneengineering/client/ui/OpticalSystemScreen.java",
        "KIND_FREE_SPACE_TX", "KIND_FREE_SPACE_RX",
        '"CHANNEL " + menu.secondary()',
        '"Direction and physical interface orientation are controlled only on Route."')

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
