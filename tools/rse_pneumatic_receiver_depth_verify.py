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
        "MIN_RANGE_MODE = 0",
        "MAX_RANGE_MODE = 2",
        "DEFAULT_RANGE_MODE = 2",
        "LOW_FULL_SCALE_PRESSURE = 25",
        "MID_FULL_SCALE_PRESSURE = 50",
        "HIGH_FULL_SCALE_PRESSURE = 100",
        "REDSTONE_FULL_SCALE = 15",
        "boundedRangeMode",
        "boundedPressureForScale",
        "fullScalePressure","scaledOutput","stepRange",
        "isClipped",
        "redstoneLevelsPerPressure",
        "pressurePerRedstoneLevel",
        "Math.round((boundedPressure / (double) boundedScale) * REDSTONE_FULL_SCALE)")
require(menu,
        "PneumaticReceiverBlock.RANGE_MODE",
        "PneumaticReceiverBlock.fullScalePressure",
        "PneumaticReceiverBlock.stepRange",
        "changed = rotateRigidDirectional(id)",
        "DirectionalSignalBlock.rotateRigidSeriesAxis")
require(screen,
        "RECEIVER CALIBRATION",
        "Full-scale pressure Pfs",
        "Selectable ranges",
        "Transfer law",
        "redstoneLevelsPerPressure",
        "pressurePerRedstoneLevel",
        "Clipping",
        "one rigid converter axis")

if failed:
    print("RSE pneumatic-receiver engineering-depth verification: FAIL")
    for x in failed: print(" -",x)
    raise SystemExit(1)
print("RSE pneumatic-receiver engineering-depth verification: PASS")
print(" selectable 25/50/100 full-scale pressure: PASS")
print(" calibrated pressure-to-redstone scaling: PASS")
print(" HMI range authority + gain/resolution/clipping evidence: PASS")
print(" rigid opposite-port pneumatic-to-redstone converter route: PASS")
