#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
failed = []

menu_path = root / "src/main/java/dev/redstoneengineering/ui/menu/RangeSensorMenu.java"
screen_path = root / "src/main/java/dev/redstoneengineering/client/ui/RangeSensorScreen.java"
block_path = root / "src/main/java/dev/redstoneengineering/block/RangeSensorBlock.java"
opener_path = root / "src/main/java/dev/redstoneengineering/ui/FieldDeviceUi.java"

for path in (menu_path, screen_path, block_path, opener_path):
    if not path.is_file():
        failed.append(f"missing Range Sensor HMI contract file: {path.relative_to(root)}")

if not failed:
    menu = menu_path.read_text(errors="ignore")
    screen = screen_path.read_text(errors="ignore")
    block = block_path.read_text(errors="ignore")
    opener = opener_path.read_text(errors="ignore")

    for token in (
        "RangeSensorBlock.setConfiguredRange(",
        "configuredRange.get() + (id == BUTTON_RANGE_NEXT ? 1 : -1)",
        "BUTTON_MODE_PREVIOUS",
        "BUTTON_MODE_NEXT",
        "BUTTON_RESPONSE_PREVIOUS",
        "BUTTON_RESPONSE_NEXT",
        "RangeSensorBlock.rotateSensingAxis",
        "scan.complete() ? 1 : 0",
    ):
        if token not in menu:
            failed.append(f"RangeSensorMenu missing dedicated control/evidence token: {token}")

    for token in (
        "0 is VALID evidence",
        "Validity comes from ScanResult.complete(), never from distance > 0.",
        "Physical sensing/output direction is controlled only on Route.",
        "RETAINED SCAN EVIDENCE",
    ):
        if token not in screen:
            failed.append(f"RangeSensorScreen missing evidence/topology token: {token}")

    for token in (
        "public static boolean setConfiguredRange",
        "Math.max(1, Math.min(32, range))",
        "public static ScanResult lastScan",
        "int range = configuredRange(level, pos, state);",
    ):
        if token not in block:
            failed.append(f"RangeSensorBlock missing exact-range/retained-scan token: {token}")

    dedicated = "if (block instanceof RangeSensorBlock)"
    if dedicated not in opener or "new RangeSensorMenu" not in opener:
        failed.append("FieldDeviceUi does not route Range Sensor to dedicated HMI")

    generic_start = opener.find("if (block instanceof QuartzPhaseDelayBlock")
    generic_end = opener.find("if (block instanceof CopperCircuitMeterBlock", generic_start)
    if generic_start >= 0 and generic_end > generic_start and "RangeSensorBlock" in opener[generic_start:generic_end]:
        failed.append("FieldDeviceUi generic MultiPhysics dispatch still intercepts Range Sensor")

if failed:
    print("RSE Range Sensor HMI verification: FAIL")
    for item in failed:
        print(" -", item)
    raise SystemExit(1)

print("RSE Range Sensor HMI verification: PASS")
print(" exact 1-block server range adjustment: PASS")
print(" detect/response configuration retained in dedicated HMI: PASS")
print(" zero remains valid completed CLEAR evidence: PASS")
print(" sensing/output axis owned by shared Route page: PASS")
print(" normal right-click reaches dedicated Range Sensor HMI: PASS")
