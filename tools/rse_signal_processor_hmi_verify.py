#!/usr/bin/env python3
"""Static regression gate for Signal Processor HMI / Shift shortcut authority sharing."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
BLOCK = ROOT / "src/main/java/dev/redstoneengineering/block"
MENU = ROOT / "src/main/java/dev/redstoneengineering/ui/menu/SignalProcessorMenu.java"
SCREEN = ROOT / "src/main/java/dev/redstoneengineering/client/ui/SignalProcessorScreen.java"
LD_UI = ROOT / "src/main/java/dev/redstoneengineering/ui/ldlib/SignalProcessorLdUi.java"
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
ld_ui = read(LD_UI)
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

require(menu, "PrecisionFilterBlock.stepRate(level, blockPos, forward)", "SignalProcessorMenu.java")
require(menu, "EdgeDetectorBlock.stepMode(level, blockPos, forward)", "SignalProcessorMenu.java")
require(menu, "PulseShaperBlock.stepWidth(level, blockPos, forward)", "SignalProcessorMenu.java")
require(menu, "BUTTON_PARAMETER_PREVIOUS", "SignalProcessorMenu.java")
require(menu, "BUTTON_PARAMETER_NEXT", "SignalProcessorMenu.java")

require(screen, "extends LdlibEngineeringHostScreen<SignalProcessorMenu>", "SignalProcessorScreen.java")
for token in (
    "PIONEER PATTERN • SIGNAL PROCESSOR MODEL",
    "e[n] = edge_mode(x[n-1], x[n]); e[n] ⇒ y=15 for 2 ticks",
    "rising edge(x) ⇒ y=15 for W ticks; otherwise y=0",
    "y[n+1] = y[n] + clamp(x[n]-y[n], -r, +r)",
    "Cycle edge mode ▶",
    "setParameterFromUi",
    "Cycle RX ▶",
    "Cycle TX ▶",
    "Edge chronology is retained by server runtime",
    "Observer readback never initializes or retriggers runtime state",
    "RseLdUiComponents.authorityFooter()",
):
    require(ld_ui, token, "SignalProcessorLdUi.java")

for token in (
    "SignalProcessorLdUi.create(this, inventory.player)",
    "cycleParameterForward",
    "setParameterFromUi",
    "cycleInputForward",
    "cycleOutputForward",
):
    require(menu, token, "SignalProcessorMenu.java")

require(field_ui, "block instanceof PrecisionFilterBlock || block instanceof EdgeDetectorBlock || block instanceof PulseShaperBlock", "FieldDeviceUi.java")
require(field_ui, "new SignalProcessorMenu(id, inv, pos)", "FieldDeviceUi.java")

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
print("  Edge Detector: HMI + Shift share stepMode")
print("  Pulse Shaper: HMI + Shift share stepWidth")
print("  Precision Filter: HMI + Shift share stepRate")
print("  LDLib2 HMI preserves model / parameter / runtime / route / evidence semantics")
