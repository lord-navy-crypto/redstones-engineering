#!/usr/bin/env python3
"""Source-level guard for readback-only parameter scales in both shared LDLib2 HMIs.

This is not a substitute for the Java build, GameTests, or runClient screenshots.
"""
from pathlib import Path
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
base = root / "src/main/java/dev/redstoneengineering/ui/ldlib"
plot = (base / "ParameterScaleElement.java").read_text(encoding="utf-8")
field = (base / "EnhancedFieldDeviceLdUi.java").read_text(encoding="utf-8")
universal = (base / "UniversalFieldDeviceLdUi.java").read_text(encoding="utf-8")
errors = []

def require(area, source, *tokens):
    for token in tokens:
        if token not in source:
            errors.append(f"{area} missing {token!r}")

require("plot", plot,
        "drawBackgroundAdditional(GUIContext g)",
        "snapshotReady.getAsBoolean()",
        "confirmedValue.getAsDouble()",
        "Double.isFinite(value)",
        "legalValues.clone()",
        "SERVER CONFIRMED POSITION",
        "NOT READY • awaiting server")
for forbidden in ("getBlockState(", "setBlock(", "applyPrimary", "applySecondary",
                  "getLevel(", "import dev.redstoneengineering.physics."):
    if forbidden in plot:
        errors.append("render-only parameter gauge contains forbidden operation: " + forbidden)
require("FieldDevice", field,
        "new ParameterScaleElement(formulaSymbol(m.kind()), m::snapshotReady",
        "directMinimum(m.kind())",
        "directMaximum(m.kind())",
        ".filter(value -> validDirectValue(kind, value))",
        "m.applyPrimaryEngineeringValueFromUi(parsed)")
require("Universal", universal,
        "primaryValueScale(menu, kind)",
        "secondaryValueScale(menu, kind)",
        "menu::snapshotReady",
        "numericReadback(primaryDisplay(menu))",
        "numericReadback(secondaryDisplay(menu))",
        "case 0 -> \"8\";",
        "case 1 -> \"16\";",
        "case 2 -> \"32\";",
        "case 3 -> \"64\";",
        "INVALID RANGE INDEX",
        "if (raw != null) menu.applySecondaryRawTargetFromUi(raw)")
if 'CONFIG_LAPIS_RANGE) return Integer.toString(menu.configSecondary())' in universal:
    errors.append("Lapis range readback still displays the internal 0..3 index")

# Enumerate every primary direct-entry kind and require a legal-mark policy.
primary = universal.split("private static boolean primaryDirectKind(int kind) {", 1)[-1].split(
    "private static boolean secondaryDirectKind", 1)[0]
marks = universal.split("private static double[] primaryLegalMarks(int kind) {", 1)[-1].split(
    "private static double[] integerMarks(", 1)[0]
primary_kinds = set(re.findall(r"UniversalFieldDeviceMenu\.(CONFIG_[A-Z0-9_]+)", primary))
marked_kinds = set(re.findall(r"UniversalFieldDeviceMenu\.(CONFIG_[A-Z0-9_]+)", marks))
if primary_kinds != marked_kinds:
    errors.append(f"Primary numeric choices not fully represented: {primary_kinds ^ marked_kinds}")

if errors:
    print("RSE OBSERVABLE PARAMETER SCALE: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)
print("RSE OBSERVABLE PARAMETER SCALE: PASS")
print(f" universal primary numeric kinds with legal marks: {len(primary_kinds)}")
print(" shared FieldDevice numeric controls have snapshot-gated server readback plots")
print(" Lapis configured range uses 8/16/32/64 blocks, not its internal index")
print(" does not claim Java compile, client runtime or Minecraft GameTest acceptance")
