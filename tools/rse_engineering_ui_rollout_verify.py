#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors = []

def read(rel):
    path = root / rel
    if not path.is_file():
        errors.append(f"missing {rel}")
        return ""
    return path.read_text(errors="ignore")

base = read("src/main/java/dev/redstoneengineering/client/ui/EngineeringScreen.java")
screens_dir = root / "src/main/java/dev/redstoneengineering/client/ui"
engineering_screens = []
if screens_dir.is_dir():
    for path in sorted(screens_dir.glob("*Screen.java")):
        text = path.read_text(errors="ignore")
        if "extends EngineeringScreen<" in text:
            engineering_screens.append((path.name, text))

if len(engineering_screens) < 12:
    errors.append(f"expected broad EngineeringScreen family coverage, found only {len(engineering_screens)} subclasses")

for token in (
    "MIN_WORKSPACE_WIDTH = 420",
    "MAX_WORKSPACE_WIDTH = 720",
    "MIN_WORKSPACE_HEIGHT = 300",
    "MAX_WORKSPACE_HEIGHT = 460",
    "mouseScrolled",
    "hasShiftDown()",
    "enableScissor",
    "virtualContentWidth",
    "virtualContentHeight",
    "formulaCard",
    "variableRole",
    "evidenceRow",
    "wrappedText",
    "SCROLL X ",
):
    if token not in base:
        errors.append(f"shared engineering workspace missing rollout primitive {token!r}")

for forbidden in (
    "this.imageWidth = 320;",
    "this.imageHeight = 270;",
):
    if forbidden in base:
        errors.append(f"shared engineering workspace retained legacy fixed geometry {forbidden!r}")

required = {
    "SignalConditionerScreen.java": (
        "FORMULA-FIRST SERVER CONTROL",
        "governingEquation()",
        'variableRole(graphics, "MEASURED", "x"',
        'variableRole(graphics, "ADJUSTABLE"',
        'variableRole(graphics, "DERIVED", "y"',
    ),
    "QuartzTimingScreen.java": (
        "FORMULA-FIRST TIMING MODEL",
        "timingEquation()",
        "T_out = N · T_in",
        "|e_T| = |T_meas - T_upstream|",
        "f_nom = 20 / T  Hz",
    ),
    "MediaConversionScreen.java": (
        "FORMULA-FIRST MEDIA BOUNDARY",
        "conversionEquation()",
        "round(100 · x_R / 15)",
        "round(15 · x_L / 100)",
        'variableRole(g, "EVIDENCE", "quality"',
    ),
    "RangeSensorScreen.java": (
        "FORMULA-FIRST SENSOR RESPONSE",
        "responseEquation()",
        "d ≤ 0",
        "max(1, floor(R/2))",
        'variableRole(g, "EVIDENCE", "scan"',
    ),
}

lookup = dict(engineering_screens)
for name, tokens in required.items():
    text = lookup.get(name, "")
    if not text:
        errors.append(f"rollout target screen missing from EngineeringScreen family: {name}")
        continue
    for token in tokens:
        if token not in text:
            errors.append(f"{name} missing rollout token {token!r}")

formula_users = [name for name, text in engineering_screens if "formulaCard(" in text]
if len(formula_users) < 4:
    errors.append(f"expected formula-first rollout across at least four EngineeringScreen families, found {formula_users}")

for name, text in engineering_screens:
    if "dev.redstoneengineering.physics" in text:
        errors.append(f"{name} imports physics directly; shared client HMI must remain presentation-only")

if errors:
    print("RSE ENGINEERING UI ROLLOUT VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE ENGINEERING UI ROLLOUT VERIFY: PASS")
print(f" EngineeringScreen subclasses covered by shared responsive workspace: {len(engineering_screens)}")
print(f" formula-first migrated families this batch: {len(formula_users)}")
print(" responsive large workspace: PASS")
print(" vertical/horizontal scroll foundation: PASS")
print(" shared formula / variable / evidence primitives: PASS")
print(" conditioner / quartz / conversion / range-sensor rollout: PASS")
print(" client/no-second-physics-solver boundary: PASS")
