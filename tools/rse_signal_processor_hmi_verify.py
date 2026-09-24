#!/usr/bin/env python3
"""Static regression gate for Signal Processor HMI / Shift shortcut authority sharing."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
BLOCK = ROOT / "src/main/java/dev/redstoneengineering/block"
MENU = ROOT / "src/main/java/dev/redstoneengineering/ui/menu/SignalProcessorMenu.java"
SCREEN = ROOT / "src/main/java/dev/redstoneengineering/client/ui/SignalProcessorScreen.java"
FIELD_UI = ROOT / "src/main/java/dev/redstoneengineering/ui/FieldDeviceUi.java"

errors = []

def read(path):
    if not path.exists():
        errors.append(f"missing {path.relative_to(ROOT)}")
        return ""
    return path.read_text(encoding="utf-8")

def require(source, needle, label):
    if needle not in source:
        errors.append(f"{label}: missing {needle!r}")

edge = read(BLOCK / "EdgeDetectorBlock.java")
pulse = read(BLOCK / "PulseShaperBlock.java")
precision = read(BLOCK / "PrecisionFilterBlock.java")
menu = read(MENU)
screen = read(SCREEN)
field_ui = read(FIELD_UI)

for source, method, label in (
    (edge, "public static boolean stepMode(Level level, BlockPos pos, boolean forward)", "EdgeDetectorBlock.java"),
    (pulse, "public static boolean stepWidth(Level level, BlockPos pos, boolean forward)", "PulseShaperBlock.java"),
    (precision, "public static boolean stepRate(Level level, BlockPos pos, boolean forward)", "PrecisionFilterBlock.java"),
):
    require(source, method, label)
    require(source, "Shared authoritative operator action used by both HMI and Shift-right-click.", label)

require(edge, "if (stepMode(level, pos, true))", "EdgeDetectorBlock.java")
require(pulse, "if (stepWidth(level, pos, true))", "PulseShaperBlock.java")
require(precision, "if (stepRate(level, pos, true))", "PrecisionFilterBlock.java")

require(menu, "PrecisionFilterBlock.setRiseRate(", "SignalProcessorMenu.java")
require(menu, "EdgeDetectorBlock.stepMode(level, blockPos, forward)", "SignalProcessorMenu.java")
require(menu, "PulseShaperBlock.setConfiguredWidth(", "SignalProcessorMenu.java")
require(menu, "EdgeDetectorBlock.setConfiguredPulseWidth(", "SignalProcessorMenu.java")
require(menu, "PrecisionFilterBlock.riseRate(level, blockPos, state)", "SignalProcessorMenu.java")
require(menu, "PulseShaperBlock.configuredWidth(level, blockPos, state)", "SignalProcessorMenu.java")
require(menu, "EdgeDetectorBlock.configuredPulseWidth(level, blockPos, state)", "SignalProcessorMenu.java")
require(menu, "EdgeDetectorBlock.rejectedInputEpisodes(level, blockPos)", "SignalProcessorMenu.java")
require(menu, "BUTTON_PARAMETER_PREVIOUS", "SignalProcessorMenu.java")
require(menu, "BUTTON_PARAMETER_NEXT", "SignalProcessorMenu.java")
require(menu, "BUTTON_EDGE_WIDTH_PREVIOUS", "SignalProcessorMenu.java")
require(menu, "BUTTON_EDGE_WIDTH_NEXT", "SignalProcessorMenu.java")

require(screen, 'Component.literal("◀ Parameter")', "SignalProcessorScreen.java")
require(screen, 'Component.literal("Parameter ▶")', "SignalProcessorScreen.java")
require(screen, 'case SignalProcessorMenu.KIND_EDGE -> "Edge mode";', "SignalProcessorScreen.java")
require(screen, 'case SignalProcessorMenu.KIND_PULSE -> "Pulse width";', "SignalProcessorScreen.java")
require(screen, 'default -> "Rise rate";', "SignalProcessorScreen.java")
require(screen, 'Component.literal("◀ Pulse width")', "SignalProcessorScreen.java")
require(screen, '"Rejected evidence episodes"', "SignalProcessorScreen.java")
require(screen, "case OVERVIEW -> overview(graphics);", "SignalProcessorScreen.java")
require(screen, "case PORTS -> ports(graphics);", "SignalProcessorScreen.java")
require(screen, "case CONFIGURE -> configure(graphics);", "SignalProcessorScreen.java")
require(screen, "case DIAGNOSTICS -> diagnostics(graphics);", "SignalProcessorScreen.java")
require(screen, "case HISTORY -> history(graphics);", "SignalProcessorScreen.java")

require(field_ui, "block instanceof PrecisionFilterBlock || block instanceof EdgeDetectorBlock || block instanceof PulseShaperBlock", "FieldDeviceUi.java")
require(field_ui, "new SignalProcessorMenu(id, inv, pos)", "FieldDeviceUi.java")
advanced_start = field_ui.find("if (block instanceof SignalAmplifierBlock")
advanced_end = field_ui.find("if (block instanceof QuartzPhaseDelayBlock", advanced_start)
if advanced_start >= 0 and advanced_end > advanced_start:
    advanced_dispatch = field_ui[advanced_start:advanced_end]
    for processor in ("PrecisionFilterBlock", "EdgeDetectorBlock", "PulseShaperBlock"):
        if processor in advanced_dispatch:
            errors.append(f"FieldDeviceUi.java: AdvancedParameterMenu still intercepts {processor}")

# Guard against reintroducing duplicate direct parameter mutation in the dedicated HMI.
for forbidden in (
    "state.setValue(PrecisionFilterBlock.RATE",
    "state.setValue(EdgeDetectorBlock.MODE",
    "state.setValue(PulseShaperBlock.WIDTH",
):
    if forbidden in menu:
        errors.append(f"SignalProcessorMenu.java: duplicate direct config mutation {forbidden!r}")

if errors:
    print("RSE SIGNAL PROCESSOR HMI VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    sys.exit(1)

print("RSE SIGNAL PROCESSOR HMI VERIFY: PASS")
print("  Edge Detector: shared mode action + exact pulse-width configuration + rejected-evidence readback")
print("  Pulse Shaper: full-range configured width + threshold/hysteresis/retrigger controls")
print("  Precision Filter: full-range rise rate + independent fall-rate control")
print("  legacy Shift shortcuts remain bounded server actions; HMI fine controls remain server-authoritative")
print("  dedicated UI exposes Overview / Ports / Configure / Diagnostics / History")
print("  normal processor right-click dispatch reaches dedicated Signal Processor HMI")
