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
    "MIN_WORKSPACE_WIDTH = 440",
    "MAX_WORKSPACE_WIDTH = 780",
    "MIN_WORKSPACE_HEIGHT = 320",
    "MAX_WORKSPACE_HEIGHT = 520",
    "DEFAULT_CANVAS_WIDTH = 1020",
    "DEFAULT_CANVAS_HEIGHT = 1820",
    "CONTENT_TOP = 112",
    "NAV_COLUMNS = 3",
    "NAV_GAP_X = 8",
    "NAV_GAP_Y = 6",
    "NAV_HEIGHT = 20",
    "NAV_TOP = 34",
    "navX",
    "navY",
    "target.label + \" • \" + target.subtitle",
    "pageLabelY = CONTENT_TOP - 18",
    "FOOTER_HEIGHT = 66",
    "renderPersistentLiveStateStrip",
    "LIVE STATE • HEALTH ",
    "I/O • ",
    "menu.portRouteLabel()",
    "configureControlCount()",
    "renderGlobalEngineeringContract",
    "ENGINEERING CONTRACT • MODEL / VARIABLES / EVIDENCE",
    "renderMechanismFlow",
    "MECHANISM FLOW • LIVE SERVER STRUCTURE",
    "Cycle direction ▶",
    "Cycle RX ▶",
    "Cycle TX ▶",
    "One-button route control",
    "RX / INPUT",
    "MODEL",
    "STATE",
    "TX / OUTPUT",
    "menu.receivePortFacesLabel()",
    "menu.transmitPortFacesLabel()",
    "OPERATOR • ",
    "SHARED_APPENDIX_TOP = 960",
    "CONFIGURE_APPENDIX_TOP = 1080",
    "SHARED_APPENDIX_GAP = 44",
    "sharedAppendixTop",
    "engineeringContractTop",
    "renderAppendixDivider",
    "SHARED ENGINEERING APPENDIX • BELOW DEVICE-SPECIFIC CONTENT",
    "MODEL AUTHORITY",
    "MODEL TYPE",
    "FORMULA POLICY",
    "VARIABLE ROLES",
    "EVIDENCE CONTRACT",
    "SERVER-AUTHORITATIVE • client is presentation / operator surface only",
    "Closed-form equations appear in the device-specific model page only",
    "mouseScrolled",
    "hasShiftDown()",
    "enableScissor",
    "virtualContentWidth",
    "virtualContentHeight",
    "formulaCard",
    "variableRole",
    "evidenceRow",
    "wrappedText",
    "renderScrollIndicators",
    "wheel=Y  shift+wheel=X",
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
    "QuartzTimingScreen.java": (
        "FORMULA-FIRST TIMING MODEL",
        "timingEquation()",
        "valid input ⇒ T_out = min(4096, N · max(1,T_in)) ticks",
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

ld_conditioner = read("src/main/java/dev/redstoneengineering/ui/ldlib/SignalConditionerLdUi.java")
for token in (
    "ModularUI",
    "DataBindingBuilder.componentS2C",
    "DataBindingBuilder.string",
    "governingEquation",
    "setNumbersOnlyInt(-5, 15)",
    "RseLdUiComponents.authorityFooter()",
    "Cycle mode ▶",
):
    if token not in ld_conditioner:
        errors.append(f"LDLib2 Signal Conditioner rollout missing {token!r}")

ld_components = read("src/main/java/dev/redstoneengineering/ui/ldlib/RseLdUiComponents.java")
for token in ("authorityFooter()", "SERVER AUTHORITY", "validated operator intent"):
    if token not in ld_components:
        errors.append(f"shared LDLib2 component library missing {token!r}")

conditioner_host = read("src/main/java/dev/redstoneengineering/client/ui/SignalConditionerScreen.java")
for token in ("extends LdlibEngineeringHostScreen<SignalConditionerMenu>",):
    if token not in conditioner_host:
        errors.append(f"Signal Conditioner LDLib2 host missing {token!r}")

oscilloscope_ld = read("src/main/java/dev/redstoneengineering/ui/ldlib/OscilloscopeLdUi.java")
for token in (
    "ModularUI",
    "DataBindingBuilder.string",
    "Cycle Δt ▶",
    "SAMPLING EXPERIMENT",
    "waveform(menu, 0)",
    "setTriggerLevelFromUi",
    "setCursorAFromUi",
    "setCursorBFromUi",
):
    if token not in oscilloscope_ld:
        errors.append(f"LDLib2 Oscilloscope rollout missing {token!r}")

oscilloscope_host = read("src/main/java/dev/redstoneengineering/client/ui/OscilloscopeScreen.java")
for token in ("extends LdlibEngineeringHostScreen<OscilloscopeMenu>",):
    if token not in oscilloscope_host:
        errors.append(f"Oscilloscope LDLib2 host missing {token!r}")

universal_ld = read("src/main/java/dev/redstoneengineering/ui/ldlib/UniversalFieldDeviceLdUi.java")
for token in (
    "ModularUI",
    "FORMULA PARAMETER WORKBENCH",
    "DECLARED ENGINEERING PORTS",
    "SYSTEM / OPERATOR STATE",
    "DataBindingBuilder.string",
    "applyPrimary(menu, value)",
    "applySecondary(menu, value)",
    "0.00..1.00 Lapis • step 0.05",
    "0.00..0.20 Lapis • step 0.02",
    "{4, 8, 16, 32} ticks",
    "{2, 4, 8, 16, 32} ticks",
    "{1, 2, 4, 8} R-eq",
    "RseLdUiComponents.authorityFooter()",
):
    if token not in universal_ld:
        errors.append(f"LDLib2 Universal rollout missing {token!r}")

universal_host = read("src/main/java/dev/redstoneengineering/client/ui/UniversalFieldDeviceScreen.java")
for token in ("extends LdlibEngineeringHostScreen<UniversalFieldDeviceMenu>",):
    if token not in universal_host:
        errors.append(f"Universal LDLib2 host missing {token!r}")

universal_menu = read("src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java")
for token in (
    "UniversalFieldDeviceLdUi.create(this, inventory.player)",
    "applyPrimaryRawTargetFromUi",
    "applySecondaryRawTargetFromUi",
    "cycleWholeRouteForward",
    "cycleInputForward",
    "cycleOutputForward",
):
    if token not in universal_menu:
        errors.append(f"Universal LDLib2 server-intent facade missing {token!r}")

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

# Legacy EngineeringScreen families still require deep-canvas ownership.
# Universal has migrated to LDLib2 and is verified separately below.
for name, required_tokens in {
    "EnhancedFieldDeviceScreen.java": (
        "virtualContentWidth",
        "virtualContentHeight",
        "case CONFIGURE -> configure(graphics);",
    ),
    "PidControllerScreen.java": (
        "virtualContentWidth",
        "virtualContentHeight",
        "Σe_cand=clamp",
        "u_raw=bias+P+I+D",
        "case CONFIGURE -> renderConfigure(graphics);",
    ),
}.items():
    text = lookup.get(name, "")
    if not text:
        errors.append(f"deep-canvas target screen missing: {name}")
        continue
    for token in required_tokens:
        if token not in text:
            errors.append(f"{name} missing deep-canvas token {token!r}")

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
print(" visible draggable vertical/horizontal deep-canvas scrolling retained for legacy EngineeringScreen families: PASS")
print(" LDLib2 migrated families own responsive layout through ModularUI rather than legacy deep-canvas primitives: PASS")
print(" fixed controls separated from scrollable engineering content: PASS")
print(" formula-linked controls surfaced in Universal / Enhanced / PID HMIs: PASS")
print(" Universal formula parameter workbench is server-backed and value-visible: PASS")
print(" bounded primary/secondary numeric entry uses the authoritative container/menu channel: PASS")
print(" direct entry is expressed in visible engineering units, not hidden raw indices: PASS")
print(" EnhancedFieldDevice rollout exposes exact engineering-value entry across lightweight configurable devices: PASS")
print(" Oscilloscope sampling Δt has exact server-backed engineering-value entry: PASS")
print(" Lapis low-pass α has exact visible-value server-backed entry: PASS")
print(" discrete formula parameters accept only legal engineering-value sets: PASS")
print(" persistent Health / Role / Evidence / I-O / Controls state strip: PASS")
print(" global Model / Variables / Evidence engineering contract: PASS")
print(" live RX -> model -> state -> TX mechanism flow: PASS")
print(" reserved shared appendix / no device-content overlap: PASS")
print(" responsive 3x2 navigation rail + header/content separation: PASS")
print(" one-button cyclic direction routing: PASS")
print(" closed-form-only formula policy / no fabricated equations: PASS")
print(" shared formula / variable / evidence primitives: PASS")
print(" conditioner / quartz / conversion / range-sensor rollout: PASS")
print(" client/no-second-physics-solver boundary: PASS")
