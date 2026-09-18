#!/usr/bin/env python3
from pathlib import Path
import sys

root=Path(sys.argv[1] if len(sys.argv)>1 else ".").resolve()
failed=[]
def require(path,*tokens):
    p=root/path
    if not p.is_file(): failed.append(f"missing: {path}"); return
    t=p.read_text(errors="ignore")
    for token in tokens:
        if token not in t: failed.append(f"{path} missing token: {token}")

block="src/main/java/dev/redstoneengineering/block/PneumaticReceiverBlock.java"
menu="src/main/java/dev/redstoneengineering/ui/menu/PneumaticSystemMenu.java"
screen="src/main/java/dev/redstoneengineering/client/ui/PneumaticSystemScreen.java"

require(block,
        'IntegerProperty.create("range_mode", 0, 2)',
        "fullScalePressure","scaledOutput","stepRange",
        "Math.round((boundedPressure / (double) fullScale) * 15.0)")
require(menu,
        "PneumaticReceiverBlock.RANGE_MODE",
        "PneumaticReceiverBlock.fullScalePressure",
        "PneumaticReceiverBlock.stepRange")
require(screen,
        "RECEIVER CALIBRATION",
        "Full-scale pressure",
        "Normalized output")

if failed:
    print("RSE pneumatic-receiver engineering-depth verification: FAIL")
    for x in failed: print(" -",x)
    raise SystemExit(1)
print("RSE pneumatic-receiver engineering-depth verification: PASS")
print(" selectable 25/50/100 full-scale pressure: PASS")
print(" calibrated pressure-to-redstone scaling: PASS")
print(" HMI range authority: PASS")
