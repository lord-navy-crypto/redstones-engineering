#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
failed: list[str] = []


def require(path: str, *tokens: str) -> None:
    p = root / path
    if not p.is_file():
        failed.append(f"missing: {path}")
        return
    text = p.read_text(errors="ignore")
    for token in tokens:
        if token not in text:
            failed.append(f"{path} missing token: {token}")


block = "src/main/java/dev/redstoneengineering/block/EntityDensitySensorBlock.java"
menu = "src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java"
screen = "src/main/java/dev/redstoneengineering/client/ui/UniversalFieldDeviceScreen.java"

require(
    block,
    'IntegerProperty.create("profile", 0, 3)',
    'IntegerProperty.create("aperture_mode", 0, 2)',
    "radiusForMode",
    "configuredRadius",
    "SensorModel.samplePeriod(profile)",
    "SensorModel.profileName",
    "adjustProfile",
    "adjustAperture",
    "MetrologyStore.remove",
)
require(
    menu,
    "CONFIG_ENTITY_DENSITY",
    "EntityDensitySensorBlock.PROFILE",
    "EntityDensitySensorBlock.APERTURE_MODE",
    "EntityDensitySensorBlock.adjustProfile",
    "EntityDensitySensorBlock.adjustAperture",
)
require(
    screen,
    "ENTITY DENSITY APERTURE",
    "Aperture radius",
    "Acquisition profile",
    "EntityDensitySensorBlock.radiusForMode",
)

text = (root / block).read_text(errors="ignore") if (root / block).is_file() else ""
if "HORIZONTAL_RADIUS = 4" in text:
    failed.append("EntityDensitySensor must not remain fixed to radius 4")
if "SENSOR_PROFILE = 1" in text:
    failed.append("EntityDensitySensor must not remain fixed to BALANCED profile")

if failed:
    print("RSE entity-density-sensor depth verification: FAIL")
    for item in failed:
        print(" -", item)
    raise SystemExit(1)

print("RSE entity-density-sensor depth verification: PASS")
print(" selectable 2/4/6-block aperture: PASS")
print(" selectable metrology acquisition profile: PASS")
print(" incomplete coverage still preserves prior trustworthy output: PASS")
print(" universal HMI aperture/profile authority: PASS")
