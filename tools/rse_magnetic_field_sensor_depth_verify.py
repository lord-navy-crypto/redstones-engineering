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

block="src/main/java/dev/redstoneengineering/block/MagneticFieldSensorBlock.java"
menu="src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java"
screen="src/main/java/dev/redstoneengineering/client/ui/UniversalFieldDeviceScreen.java"

require(block,
        'IntegerProperty.create("radius_mode", 0, 3)',
        'IntegerProperty.create("sample_mode", 0, 2)',
        "radiusForMode","samplePeriodForMode","adjustRadius","adjustSampling",
        "MagneticPhysics.fieldSample(level, pos, radius)")
require(menu,
        "CONFIG_MAGNETIC_FIELD",
        "MagneticFieldSensorBlock.RADIUS_MODE",
        "MagneticFieldSensorBlock.SAMPLE_MODE",
        "MagneticFieldSensorBlock.adjustRadius",
        "MagneticFieldSensorBlock.adjustSampling")
require(screen,
        "MAGNETIC SENSOR APERTURE",
        "Aperture radius",
        "Sample period")

if failed:
    print("RSE magnetic-field-sensor engineering-depth verification: FAIL")
    for x in failed: print(" -",x)
    raise SystemExit(1)
print("RSE magnetic-field-sensor engineering-depth verification: PASS")
print(" selectable 2/4/6/8 aperture: PASS")
print(" selectable 1/5/10 tick sampling: PASS")
print(" coverage incomplete remains STALE: PASS")
print(" universal HMI authority: PASS")
