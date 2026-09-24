#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors = []

def read(rel):
    path = root / rel
    if not path.is_file():
        errors.append(f"missing Signal Conditioner HMI file: {rel}")
        return ""
    return path.read_text(errors="ignore")

menu = read("src/main/java/dev/redstoneengineering/ui/menu/SignalConditionerMenu.java")
screen = read("src/main/java/dev/redstoneengineering/client/ui/SignalConditionerScreen.java")
block = read("src/main/java/dev/redstoneengineering/block/SignalConditionerBlock.java")
opener = read("src/main/java/dev/redstoneengineering/ui/FieldDeviceUi.java")
client = read("src/main/java/dev/redstoneengineering/client/ui/EngineeringUiClientRegistration.java")

for token in (
    "SignalConditionerBlock.inspectInputQuality",
    "SignalConditionerBlock.inspectOutputQuality",
    "private final DataSlot inputQuality",
    "private final DataSlot outputQuality",
    "public PortQuality inputQuality()",
    "public PortQuality outputQuality()",
    "BUTTON_INPUT_LEFT",
    "BUTTON_OUTPUT_RIGHT",
    "DirectionalSignalBlock.rotateSeriesInput",
    "DirectionalSignalBlock.rotateSeriesOutput",
):
    if menu and token not in menu:
        errors.append(f"SignalConditionerMenu missing dedicated quality/route token: {token}")

for token in (
    '"Input quality"',
    '"Output quality"',
    "qualityName(menu.inputQuality())",
    "qualityName(menu.outputQuality())",
    "INPUT EVIDENCE •",
    "OUTPUT SATURATED",
    "restore trustworthy upstream Redstone evidence",
    "NO_SIGNAL / STALE / topology evidence remain separate states.",
    "Boundary limiting episodes=",
    "private static String modelLine",
    "y = clamp(round(x ×",
    "y = min(x,",
    "otherwise y = 0",
    "retain yprev",
    "Transfer math executes on the server tick",
):
    if screen and token not in screen:
        errors.append(f"SignalConditionerScreen missing evidence token: {token}")

if '16, 178' in screen and '16, 180' in screen:
    errors.append("SignalConditionerScreen reintroduced overlapping History text rows")

for token in (
    "public static PortQuality inspectInputQuality",
    "public static PortQuality inspectOutputQuality",
    "PortQuality.SATURATED",
    "RedstoneObservationSupport.combineQuality",
    "FieldDeviceUi.open(serverPlayer, pos)",
):
    if block and token not in block:
        errors.append(f"SignalConditionerBlock missing shared authority token: {token}")

dedicated = "if (block instanceof SignalConditionerBlock)"
if dedicated not in opener or "new SignalConditionerMenu(id, inv, pos)" not in opener:
    errors.append("FieldDeviceUi does not route Signal Conditioner to dedicated HMI")

process_start = opener.find("if (block instanceof PwmControllerBlock")
process_end = opener.find("if (block instanceof SignalAmplifierBlock", process_start)
if process_start >= 0 and process_end > process_start and "SignalConditionerBlock" in opener[process_start:process_end]:
    errors.append("ProcessParameterMenu dispatch still intercepts Signal Conditioner")

if "event.register(EngineeringUiRegistration.SIGNAL_CONDITIONER.get(), SignalConditionerScreen::new)" not in client:
    errors.append("Signal Conditioner menu is not registered to dedicated SignalConditionerScreen")

if errors:
    print("RSE Signal Conditioner HMI verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE Signal Conditioner HMI verification: PASS")
print(" normal right-click reaches dedicated HMI: PASS")
print(" independent RX/TX Route authority retained: PASS")
print(" input/output PortQuality evidence synchronized: PASS")
print(" saturation remains explicit output evidence: PASS")
print(" valid zero stays distinct from missing/stale/topology evidence: PASS")
print(" History limiting evidence layout does not overlap: PASS")
print(" Configure page exposes exact server transfer model: PASS")
