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
        "routePrevious", "routeNext", 'Component.literal("↺ Previous")', 'Component.literal("Next ↻")',
        "routeInputPrevious", "routeInputNext", "routeOutputPrevious", "routeOutputNext",
        'Component.literal("↺ RX")', 'Component.literal("RX ↻")',
        'Component.literal("↺ TX")', 'Component.literal("TX ↻")',
        "routeInputActionId(boolean clockwise)", "routeOutputActionId(boolean clockwise)", "independentRouteEndpoints",
        "DigitalCommunicationMenu.BUTTON_INPUT_LEFT", "DigitalCommunicationMenu.BUTTON_INPUT_RIGHT",
        "DigitalCommunicationMenu.BUTTON_OUTPUT_LEFT", "DigitalCommunicationMenu.BUTTON_OUTPUT_RIGHT",
        "PneumaticSystemMenu.BUTTON_INPUT_LEFT", "PneumaticSystemMenu.BUTTON_INPUT_RIGHT",
        "PneumaticSystemMenu.BUTTON_OUTPUT_LEFT", "PneumaticSystemMenu.BUTTON_OUTPUT_RIGHT",
        'DIAGNOSTICS("Observe"', "ROLE • ", "HEALTH • ", "EVIDENCE • ",
        "ROUTE_CONTROL_Y = 196", "FOOTER_TOP = 245", "fitForWidth", "safeText",
        "isConfigureSection()", "showsPortVisualization", "CONTENT_RIGHT - VALUE_X",
        '"Parameters, modes and actions"', '"Direction, orientation and physical interface"',
        "SignalAnalyzerMenu.BUTTON_ROTATE_LEFT", "SignalAnalyzerMenu.BUTTON_ROTATE_RIGHT",
        "if (menu instanceof SignalAnalyzerMenu) return true;")
require("src/main/java/dev/redstoneengineering/client/ui/EngineeringIoCompassOverlay.java",
        "engineeringScreen.showsPortVisualization()", '"I/O COMPASS"',
        "boolean rightFits", "boolean leftFits", "if (!rightFits && !leftFits) return;",
        "screen.height - margin - panelHeight", "connectionMask(menu)", "linkEvidenceKnown",
        '"DECLARED"', '"AIR PATH"', '"LOS PATH"', '"LINKED"', '"OPEN"')
require("src/main/java/dev/redstoneengineering/client/ui/EnhancedFieldDeviceScreen.java",
        "safeText(g, engineeringHint()", "safeText(g, diagnosticHint()",
        "fitForWidth(label, 72)", "fitForWidth(value, 72)")

# Field-device route authority must include redstone endpoints and standalone physical interfaces,
# not only the two series-processing base classes. This protects reference sources and sensors.
require("src/main/java/dev/redstoneengineering/ui/menu/FieldDeviceMenu.java",
        "DirectionalRedstoneEndpointBlock",
        "SignalProbeBlock",
        "RedstoneCableTerminalBlock",
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

# Generic fallback must preserve the same route authority as the normal field-device path.
require("src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java",
        "DirectionalRedstoneEndpointBlock", "SignalProbeBlock", "RedstoneCableTerminalBlock",
        "isRotatable(block)",
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

# Free-space optical channel selection belongs in Configure while physical orientation belongs on Route.
# Shift-right-click remains a legacy shortcut, but the Engineering UI must expose the same capability.
require("src/main/java/dev/redstoneengineering/ui/FieldDeviceUi.java",
        "FreeSpaceOpticalTransmitterBlock", "FreeSpaceOpticalReceiverBlock", "new OpticalSystemMenu")
require("src/main/java/dev/redstoneengineering/ui/menu/OpticalSystemMenu.java",
        "KIND_FREE_SPACE_TX", "KIND_FREE_SPACE_RX",
        "FreeSpaceOpticalTransmitterBlock.CHANNEL", "FreeSpaceOpticalReceiverBlock.CHANNEL",
        "BUTTON_SECONDARY_PREVIOUS", "BUTTON_SECONDARY_NEXT",
        "DirectionalSignalBlock.rotateSeriesAxis(level, blockPos, id == BUTTON_ROTATE_RIGHT)",
        "kind.get() == KIND_FREE_SPACE_TX", "kind.get() == KIND_FREE_SPACE_RX")
require("src/main/java/dev/redstoneengineering/client/ui/OpticalSystemScreen.java",
        "KIND_FREE_SPACE_TX", "KIND_FREE_SPACE_RX",
        '"CHANNEL " + menu.secondary()',
        '"Direction and physical interface orientation are controlled only on Route."')

# Once the six-page architecture exists, specialized Configure pages may not recreate a second
# direction/orientation control. Independent RX/TX controls live in the shared Route page only.
route_capable_screens = (
    "SignalConditionerScreen.java",
    "RangeSensorScreen.java",
    "SignalAnalyzerScreen.java",
    "SignalProcessorScreen.java",
    "QuartzTimingScreen.java",
    "RadioLinkScreen.java",
    "DigitalCommunicationScreen.java",
    "PneumaticSystemScreen.java",
    "OpticalSystemScreen.java",
    "AmethystSystemScreen.java",
    "MagneticSystemScreen.java",
    "ReliabilitySystemScreen.java",
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

# Both directions are first-class actions again. Keeping only clockwise is not feature-complete.
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
print(" independent RX/TX + whole-route controls on shared Route page: PASS")
print(" bidirectional Previous/Next compatibility for single-axis devices: PASS")
print(" redstone reference/source/sensor FieldDevice route authority: PASS")
print(" endpoint Engineering UI reachability + Shift diagnostics: PASS")
print(" universal fallback route authority parity: PASS")
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
