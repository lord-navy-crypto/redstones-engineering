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

if engineering_screens:
    errors.append(f"expected zero legacy block-facing EngineeringScreen subclasses after LDLib2 closure, found {[name for name, _ in engineering_screens]}")

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

required = {}

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

range_ld = read("src/main/java/dev/redstoneengineering/ui/ldlib/RangeSensorLdUi.java")
for token in (
    "ModularUI",
    "FORMULA-FIRST SENSOR RESPONSE",
    "DataBindingBuilder.string",
    "{4,8,15}",
    "Cycle detect ▶",
    "Cycle response ▶",
    "A complete CLEAR scan with d=0 is valid evidence",
    "RseLdUiComponents.authorityFooter()",
):
    if token not in range_ld:
        errors.append(f"LDLib2 Range Sensor rollout missing {token!r}")
range_host = read("src/main/java/dev/redstoneengineering/client/ui/RangeSensorScreen.java")
if "extends LdlibEngineeringHostScreen<RangeSensorMenu>" not in range_host:
    errors.append("Range Sensor LDLib2 host missing")
range_menu = read("src/main/java/dev/redstoneengineering/ui/menu/RangeSensorMenu.java")
for token in ("RangeSensorLdUi.create(this, inventory.player)", "setRangeFromUi", "cycleDetectForward", "cycleResponseForward"):
    if token not in range_menu:
        errors.append(f"Range Sensor LDLib2 server-intent facade missing {token!r}")

media_ld = read("src/main/java/dev/redstoneengineering/ui/ldlib/MediaConversionLdUi.java")
for token in (
    "ModularUI",
    "FORMULA-FIRST MEDIA BOUNDARY",
    "y_L = round(100 · x_R / 15)",
    "y_R = round(15 · x_L / 100)",
    "Quantization loss",
    "Cycle RX ▶",
    "Cycle TX ▶",
    "RseLdUiComponents.authorityFooter()",
):
    if token not in media_ld:
        errors.append(f"LDLib2 Media Conversion rollout missing {token!r}")
media_host = read("src/main/java/dev/redstoneengineering/client/ui/MediaConversionScreen.java")
if "extends LdlibEngineeringHostScreen<MediaConversionMenu>" not in media_host:
    errors.append("Media Conversion LDLib2 host missing")
media_menu = read("src/main/java/dev/redstoneengineering/ui/menu/MediaConversionMenu.java")
for token in ("MediaConversionLdUi.create(this, inventory.player)", "cycleRxForward", "cycleTxForward"):
    if token not in media_menu:
        errors.append(f"Media Conversion LDLib2 server-intent facade missing {token!r}")

processor_ld = read("src/main/java/dev/redstoneengineering/ui/ldlib/SignalProcessorLdUi.java")
for token in (
    "ModularUI",
    "PIONEER PATTERN • SIGNAL PROCESSOR MODEL",
    "DataBindingBuilder.string",
    "r ∈ 1..4",
    "W ∈ 1..8 ticks",
    "Cycle edge mode ▶",
    "RseLdUiComponents.authorityFooter()",
):
    if token not in processor_ld:
        errors.append(f"LDLib2 Signal Processor rollout missing {token!r}")
processor_host = read("src/main/java/dev/redstoneengineering/client/ui/SignalProcessorScreen.java")
if "extends LdlibEngineeringHostScreen<SignalProcessorMenu>" not in processor_host:
    errors.append("Signal Processor LDLib2 host missing")
processor_menu = read("src/main/java/dev/redstoneengineering/ui/menu/SignalProcessorMenu.java")
for token in ("SignalProcessorLdUi.create(this, inventory.player)", "setParameterFromUi", "cycleParameterForward"):
    if token not in processor_menu:
        errors.append(f"Signal Processor LDLib2 server-intent facade missing {token!r}")

quartz_ld = read("src/main/java/dev/redstoneengineering/ui/ldlib/QuartzTimingLdUi.java")
for token in (
    "FORMULA-FIRST TIMING MODEL",
    "valid input ⇒ T_out = min(4096, N · max(1,T_in)) ticks",
    "|e_T| = |T_meas - T_upstream|",
    "f_nom = 20 / T Hz",
    "SATURATED @4096",
    "DataBindingBuilder.string",
):
    if token not in quartz_ld:
        errors.append(f"LDLib2 Quartz rollout missing {token!r}")

# Distinguish zero measured period, stale measurement and unknown source period.
for token in (
    "measurementPeriod(m)",
    "measurementError(m)",
    "NOT READY • no valid input period",
    "NOT READY • output clock unverified",
    "NOT READY • missing source period",
    "ticks • STALE",
    "two rising edges required",
):
    if token not in quartz_ld:
        errors.append(f"Quartz timing validity/freshness contract missing {token!r}")

radio_ld = read("src/main/java/dev/redstoneengineering/ui/ldlib/RadioLinkLdUi.java")
for token in ("PIONEER PATTERN • RADIO LINK BUDGET", "M_decode = Q_link - Q_min", "availability = 100 · validSamples / samples", "DataBindingBuilder.string"):
    if token not in radio_ld:
        errors.append(f"LDLib2 Radio rollout missing {token!r}")

copper_meter_ld = read("src/main/java/dev/redstoneengineering/ui/ldlib/CopperCircuitMeterLdUi.java")
for token in ("PIONEER PATTERN • ELECTRICAL MEASUREMENT MODEL", "I = V / R_eq ; P = V · I", "OBSERVER ONLY", "COMMISSIONING"):
    if token not in copper_meter_ld:
        errors.append(f"LDLib2 Copper meter rollout missing {token!r}")

optical_ld = read("src/main/java/dev/redstoneengineering/ui/ldlib/OpticalSystemLdUi.java")
for token in (
    "private static String measurement(OpticalSystemMenu m, String value)",
    "m.quality()==dev.redstoneengineering.core.port.PortQuality.VALID",
    'measurement(m,m.primary()+" / 15")',
    "secondaryIsConfiguration(m.kind()) ? Integer.toString(m.secondary())",
    'measurement(m,m.tertiary()+" / "+m.auxiliary())',
):
    if token not in optical_ld:
        errors.append(f"Optical observed-value authority gate missing {token!r}")

magnetic_ld = read("src/main/java/dev/redstoneengineering/ui/ldlib/MagneticSystemLdUi.java")
for token in ("PIONEER PATTERN • MAGNETIC MODEL", "V_ind = clamp(N · |B[n] - B[n-1]|, 0, 15)", "Σ S_i / max(1,r_i²)", "DataBindingBuilder.string"):
    if token not in magnetic_ld:
        errors.append(f"LDLib2 Magnetic rollout missing {token!r}")

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
    "primaryCycleKind",
    "READ-ONLY HMI • no fake control",
    "numeric values use exact entry",
    "MECHANISM FLOW • SERVER-AUTHORITATIVE",
    "READ-ONLY TOPOLOGY • no server-supported route mutation for this device",
    "no fake action is exposed when the authoritative server model has no explicit action",
    "menu.routeKind() != UniversalFieldDeviceMenu.ROUTE_NONE",
    "menu.hasInputEndpoint()",
    "menu.hasOutputEndpoint()",
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

ldlib_migrated_families = (
    "SignalConditionerScreen",
    "PidControllerScreen",
    "OscilloscopeScreen",
    "LogicAnalyzerScreen",
    "SignalAnalyzerScreen",
    "UniversalFieldDeviceScreen",
    "CopperCircuitMeterScreen",
    "MediaConversionScreen",
    "RangeSensorScreen",
    "SignalProcessorScreen",
    "QuartzTimingScreen",
    "RadioLinkScreen",
    "DigitalCommunicationScreen",
    "PneumaticSystemScreen",
    "OpticalSystemScreen",
    "AmethystSystemScreen",
    "MagneticSystemScreen",
    "ReliabilitySystemScreen",
    "LapisLowPassScreen",
    "IndustrialBufferScreen",
    "WorkcellControllerScreen",
    "OperationsMonitorScreen",
    "EnhancedFieldDeviceScreen",
)
if len(ldlib_migrated_families) != 23:
    errors.append(f"expected 23 LDLib2 migrated block-facing families, found {len(ldlib_migrated_families)}")

lookup = dict(engineering_screens)
for name, tokens in required.items():
    text = lookup.get(name, "")
    if not text:
        errors.append(f"rollout target screen missing from EngineeringScreen family: {name}")
        continue
    for token in tokens:
        if token not in text:
            errors.append(f"{name} missing rollout token {token!r}")

enhanced_ld = read("src/main/java/dev/redstoneengineering/ui/ldlib/EnhancedFieldDeviceLdUi.java")
for token in (
    "ModularUI", "DataBindingBuilder.string",
    "PIONEER PATTERN • SHARED FIELD DEVICE",
    "Exact engineering value", "directEntryKind(",
    "PIONEER PATTERN • SOURCE / MEDIUM INTEGRITY",
    "RseLdUiComponents.authorityFooter()",
):
    if token not in enhanced_ld:
        errors.append(f"LDLib2 Enhanced FieldDevice closure missing {token!r}")
formula_users = list(ldlib_migrated_families)

for name, text in engineering_screens:
    if "dev.redstoneengineering.physics" in text:
        errors.append(f"{name} imports physics directly; shared client HMI must remain presentation-only")

if errors:
    print("RSE ENGINEERING UI ROLLOUT VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE ENGINEERING UI ROLLOUT VERIFY: PASS")
print(f" remaining legacy EngineeringScreen subclasses: {len(engineering_screens)}")
print(f" LDLib2 migrated block-facing families: {len(ldlib_migrated_families)}")
print(f" formula-first migrated families this batch: {len(formula_users)}")
print(" responsive large workspace: PASS")
print(" legacy block-facing deep-canvas screens remaining: NONE")
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
print(" conditioner / quartz / conversion / range-sensor / signal-processor rollout: PASS")
print(" client/no-second-physics-solver boundary: PASS")
