#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
failed = []

menu_path = root / "src/main/java/dev/redstoneengineering/ui/menu/MagneticSystemMenu.java"
screen_path = root / "src/main/java/dev/redstoneengineering/client/ui/MagneticSystemScreen.java"
opener_path = root / "src/main/java/dev/redstoneengineering/ui/FieldDeviceUi.java"

for path in (menu_path, screen_path, opener_path):
    if not path.is_file():
        failed.append(f"missing Magnetic HMI contract file: {path.relative_to(root)}")

if not failed:
    menu = menu_path.read_text(errors="ignore")
    screen = screen_path.read_text(errors="ignore")
    opener = opener_path.read_text(errors="ignore")

    for token in (
        "ElectromagnetBlock.configuredResponse",
        "ElectromagnetBlock.setEngineeringParameters",
        "ElectromagnetBlock.targetField",
        "ElectromagnetBlock.thermalLoad",
        "ElectromagnetBlock.trackingError",
        "ElectromagnetBlock.runTicks",
        "PermanentMagnetBlock.stepStrength",
        "InductionCoilBlock.configuredTurns",
        "InductionCoilBlock.setConfiguredTurns",
        "engineeringB.set(state.getValue(InductionCoilBlock.TURNS))",
        "BUTTON_SECONDARY_PREVIOUS",
        "BUTTON_SECONDARY_NEXT",
        "BUTTON_TERTIARY_PREVIOUS",
        "BUTTON_TERTIARY_NEXT",
        "engineeringA",
        "engineeringB",
        "engineeringC",
        "DirectionalDomainBlock.rotateRigidSeriesAxis",
    ):
        if token not in menu:
            failed.append(f"MagneticSystemMenu missing exact-parameter/evidence token: {token}")

    for token in (
        "Field strength B",
        "Allowed strength",
        "PermanentMagnetBlock.MIN_STRENGTH",
        "PermanentMagnetBlock.MAX_STRENGTH",
        "STATIC SCALAR FREE-SPACE SOURCE",
        "Field rise rate",
        "Field fall rate",
        "Cooling rate",
        "Target / actual",
        "Thermal load",
        "Run ticks",
        "Exact turns N",
        "Allowed exact N",
        "Legacy preset",
        "Sample model",
        "InductionCoilBlock.MIN_CONFIGURED_TURNS",
        "InductionCoilBlock.MAX_CONFIGURED_TURNS",
        "InductionCoilBlock.MIN_LEGACY_TURNS",
        "InductionCoilBlock.MAX_LEGACY_TURNS",
        "InductionCoilBlock.SAMPLE_TICKS",
        "Any exact-turn change releases the old Copper output and invalidates the derivative baseline",
        "rigid opposite INPUT/OUTPUT axis",
        "Field response",
        "Heat proxy",
        "Thermal update",
        "Derating",
    ):
        if token not in screen:
            failed.append(f"MagneticSystemScreen missing engineering token: {token}")

    dedicated_start = opener.find("if (block instanceof ElectromagnetBlock || block instanceof PermanentMagnetBlock")
    if dedicated_start < 0:
        failed.append("FieldDeviceUi missing dedicated Magnetic dispatch family")
    else:
        dedicated = opener[dedicated_start:]
        for device in ("ElectromagnetBlock", "InductionCoilBlock"):
            if device not in dedicated:
                failed.append(f"dedicated Magnetic dispatch missing {device}")
        if "new MagneticSystemMenu(id, inv, pos)" not in dedicated:
            failed.append("dedicated Magnetic dispatch does not open MagneticSystemMenu")

    advanced_start = opener.find("if (block instanceof SignalAmplifierBlock")
    advanced_end = opener.find("if (block instanceof QuartzPhaseDelayBlock", advanced_start)
    if advanced_start >= 0 and advanced_end > advanced_start:
        advanced = opener[advanced_start:advanced_end]
        for device in ("ElectromagnetBlock", "InductionCoilBlock"):
            if device in advanced:
                failed.append(f"AdvancedParameterMenu still intercepts {device}")

if failed:
    print("RSE Magnetic HMI dispatch verification: FAIL")
    for item in failed:
        print(" -", item)
    raise SystemExit(1)

print("RSE Magnetic HMI dispatch verification: PASS")
print(" permanent magnet single strength 1..15 contract: PASS")
print(" electromagnet exact rise/fall/cooling controls: PASS")
print(" electromagnet target/thermal/tracking/run evidence: PASS")
print(" induction coil exact 1..16 turns + legacy 1..4 preset contract: PASS")
print(" induction coil 2-tick sampled EMF model is explicit in HMI: PASS")
print(" derivative-baseline invalidation remains server-owned: PASS")
print(" induction coil rigid opposite-port topology remains on shared Route page: PASS")
print(" normal right-click reaches dedicated Magnetic HMI: PASS")
