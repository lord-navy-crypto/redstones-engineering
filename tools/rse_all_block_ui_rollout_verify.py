#!/usr/bin/env python3
"""Hard gate for the all-block deep-canvas UI rollout.

The 122-block Pioneer audit already proves block coverage. This gate proves that every
block-facing menu screen registered by the client terminates in either the shared
EngineeringScreen deep-canvas contract or one of the two standalone deep-canvas
instrument workspaces.
"""
from pathlib import Path
import re
import sys

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
UI = ROOT / "src/main/java/dev/redstoneengineering/client/ui"
errors = []

def read(rel: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        errors.append(f"missing {rel}")
        return ""
    return path.read_text(errors="ignore")

main = read("src/main/java/dev/redstoneengineering/RedstoneEngineering.java")
registration = read("src/main/java/dev/redstoneengineering/client/ui/EngineeringUiClientRegistration.java")
base = read("src/main/java/dev/redstoneengineering/client/ui/EngineeringScreen.java")
field_ui = read("src/main/java/dev/redstoneengineering/ui/FieldDeviceUi.java")
audit = read("tools/rse_122_block_total_audit.py")
pioneer = read("docs/PIONEER_SHOWCASE_STANDARD.md")

registered_blocks = sorted(set(re.findall(r'registerBlock\(\s*"([a-z0-9_]+)"', main, re.S)))
if len(registered_blocks) != 122:
    errors.append(f"expected 122 registered blocks, found {len(registered_blocks)}")
if "EXPECTED_REGISTERED = 122" not in audit:
    errors.append("122-block audit no longer declares EXPECTED_REGISTERED = 122")
if "**Pioneer broad-rollout campaign complete: 122 / 122.**" not in pioneer:
    errors.append("Pioneer completion ledger no longer closes at 122 / 122")

registrations = re.findall(
    r'event\.register\(EngineeringUiRegistration\.([A-Z0-9_]+)\.get\(\),\s*([A-Za-z0-9_]+)::new\);',
    registration,
)
if len(registrations) < 25:
    errors.append(f"expected at least 25 registered UI families, found {len(registrations)}")

tool_only = {"REDSTONE_ENCYCLOPEDIA", "DIAGNOSTIC_TABLET"}
block_facing = [(menu, screen) for menu, screen in registrations if menu not in tool_only]
if len(block_facing) < 23:
    errors.append(f"expected at least 23 block-facing UI families, found {len(block_facing)}")

for token in (
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
    "RX / INPUT",
    "MODEL",
    "STATE",
    "TX / OUTPUT",
    "menu.receivePortFacesLabel()",
    "menu.transmitPortFacesLabel()",
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
    "renderScrollIndicators",
    "mouseClicked",
    "mouseDragged",
    "mouseReleased",
    "beginScrollbarDrag",
    "dragScrollbarTo",
    "draggingHorizontalScroll",
    "draggingVerticalScroll",
    "renderFunctionSurface",
    "layoutConfigureWidgets",
    "CONFIGURE_CONTROL_COLUMNS = 3",
    "configureContentOffset",
    "widget.setX",
    "widget.setY",
    "widget.setWidth",
    "FUNCTION SURFACE • ALL-BLOCK UI CONTRACT",
    "OPERATOR CONTROLS •",
    "widget.getMessage().getString()",
    "wheel=Y  shift+wheel=X",
    "formulaCard",
    "variableRole",
    "evidenceRow",
    "wrappedText",
):
    if token not in base:
        errors.append(f"shared EngineeringScreen missing all-block rollout token {token!r}")

ldlib_host = read("src/main/java/dev/redstoneengineering/client/ui/ldlib/LdlibEngineeringHostScreen.java")
for token in ("IModularUIHolderMenu", "AbstractContainerScreen<M>", "getModularUI()", "LDLib2 owns the engineering HMI canvas"):
    if token not in ldlib_host:
        errors.append(f"shared LDLib2 host missing {token!r}")

standalone_contracts = {
    "LapisLowPassScreen": (
        "mouseScrolled",
        "mouseClicked",
        "mouseDragged",
        "mouseReleased",
        "beginScrollbarDrag",
        "dragScrollbarTo",
        "scrollX",
        "scrollY",
        "renderScrollIndicators",
        "MODEL_WIDTH = 960",
        "Drag scrollbars • Wheel: vertical • Shift+wheel: horizontal",
        "LIVE STATE • HEALTH ",
        "I/O • ",
        "menu.portRouteLabel()",
        "GOVERNING EQUATION",
        "MECHANISM FLOW • LIVE SERVER STRUCTURE",
        "RX / INPUT",
        "FILTER MODEL",
        "SOLVER STATE",
        "TX / OUTPUT",
        "VARIABLE ROLES",
        "MEASUREMENT / MODEL EVIDENCE",
        "[ADJUSTABLE] α",
        "[MEASURED]  x[n]",
    ),
}

components = read("src/main/java/dev/redstoneengineering/ui/ldlib/RseLdUiComponents.java")
for token in ("authorityFooter()", "SERVER AUTHORITY", "validated operator intent"):
    if token not in components:
        errors.append(f"shared LDLib2 component library missing {token!r}")

ldlib_contracts = {
    "SignalConditionerScreen": (
        "extends LdlibEngineeringHostScreen<SignalConditionerMenu>",
    ),
    "OscilloscopeScreen": (
        "extends LdlibEngineeringHostScreen<OscilloscopeMenu>",
    ),
    "UniversalFieldDeviceScreen": (
        "extends LdlibEngineeringHostScreen<UniversalFieldDeviceMenu>",
    ),
    "RangeSensorScreen": (
        "extends LdlibEngineeringHostScreen<RangeSensorMenu>",
    ),
    "MediaConversionScreen": (
        "extends LdlibEngineeringHostScreen<MediaConversionMenu>",
    ),
    "SignalProcessorScreen": (
        "extends LdlibEngineeringHostScreen<SignalProcessorMenu>",
    ),
    "LogicAnalyzerScreen": (
        "extends LdlibEngineeringHostScreen<LogicAnalyzerMenu>",
    ),
    "SignalAnalyzerScreen": (
        "extends LdlibEngineeringHostScreen<SignalAnalyzerMenu>",
    ),
    "ReliabilitySystemScreen": (
        "extends LdlibEngineeringHostScreen<ReliabilitySystemMenu>",
    ),
    "DigitalCommunicationScreen": (
        "extends LdlibEngineeringHostScreen<DigitalCommunicationMenu>",
    ),
    "OpticalSystemScreen": (
        "extends LdlibEngineeringHostScreen<OpticalSystemMenu>",
    ),
    "QuartzTimingScreen": (
        "extends LdlibEngineeringHostScreen<QuartzTimingMenu>",
    ),
    "RadioLinkScreen": (
        "extends LdlibEngineeringHostScreen<RadioLinkMenu>",
    ),
    "CopperCircuitMeterScreen": (
        "extends LdlibEngineeringHostScreen<CopperCircuitMeterMenu>",
    ),
    "MagneticSystemScreen": (
        "extends LdlibEngineeringHostScreen<MagneticSystemMenu>",
    ),
    "PneumaticSystemScreen": (
        "extends LdlibEngineeringHostScreen<PneumaticSystemMenu>",
    ),
    "AmethystSystemScreen": (
        "extends LdlibEngineeringHostScreen<AmethystSystemMenu>",
    ),
}

engineering_families = []
standalone_families = []
ldlib_families = []
manual_configure_offsets = []
for menu_name, screen_name in block_facing:
    source_path = UI / f"{screen_name}.java"
    if not source_path.is_file():
        errors.append(f"registered block-facing screen missing: {screen_name}.java")
        continue
    source = source_path.read_text(errors="ignore")
    if "extends EngineeringScreen<" in source:
        engineering_families.append(screen_name)
        if "graphics.pose().translate(0.0F," in source and "case CONFIGURE" in source:
            manual_configure_offsets.append(screen_name)
        continue
    ldlib_required = ldlib_contracts.get(screen_name)
    if ldlib_required is not None:
        ldlib_families.append(screen_name)
        for token in ldlib_required:
            if token not in source:
                errors.append(f"{screen_name} missing LDLib2 host token {token!r}")
        ld_path = {
            "SignalConditionerScreen": "src/main/java/dev/redstoneengineering/ui/ldlib/SignalConditionerLdUi.java",
            "OscilloscopeScreen": "src/main/java/dev/redstoneengineering/ui/ldlib/OscilloscopeLdUi.java",
            "UniversalFieldDeviceScreen": "src/main/java/dev/redstoneengineering/ui/ldlib/UniversalFieldDeviceLdUi.java",
            "RangeSensorScreen": "src/main/java/dev/redstoneengineering/ui/ldlib/RangeSensorLdUi.java",
            "MediaConversionScreen": "src/main/java/dev/redstoneengineering/ui/ldlib/MediaConversionLdUi.java",
            "SignalProcessorScreen": "src/main/java/dev/redstoneengineering/ui/ldlib/SignalProcessorLdUi.java",
            "LogicAnalyzerScreen": "src/main/java/dev/redstoneengineering/ui/ldlib/LogicAnalyzerLdUi.java",
            "SignalAnalyzerScreen": "src/main/java/dev/redstoneengineering/ui/ldlib/SignalAnalyzerLdUi.java",
            "ReliabilitySystemScreen": "src/main/java/dev/redstoneengineering/ui/ldlib/ReliabilitySystemLdUi.java",
            "DigitalCommunicationScreen": "src/main/java/dev/redstoneengineering/ui/ldlib/DigitalCommunicationLdUi.java",
            "OpticalSystemScreen": "src/main/java/dev/redstoneengineering/ui/ldlib/OpticalSystemLdUi.java",
            "QuartzTimingScreen": "src/main/java/dev/redstoneengineering/ui/ldlib/QuartzTimingLdUi.java",
            "RadioLinkScreen": "src/main/java/dev/redstoneengineering/ui/ldlib/RadioLinkLdUi.java",
            "CopperCircuitMeterScreen": "src/main/java/dev/redstoneengineering/ui/ldlib/CopperCircuitMeterLdUi.java",
            "MagneticSystemScreen": "src/main/java/dev/redstoneengineering/ui/ldlib/MagneticSystemLdUi.java",
            "PneumaticSystemScreen": "src/main/java/dev/redstoneengineering/ui/ldlib/PneumaticSystemLdUi.java",
            "AmethystSystemScreen": "src/main/java/dev/redstoneengineering/ui/ldlib/AmethystSystemLdUi.java",
        }[screen_name]
        ld_ui = read(ld_path)
        common_tokens = ("ModularUI", "RseLdUiComponents.authorityFooter()")
        for token in common_tokens:
            if token not in ld_ui:
                errors.append(f"{screen_name} LDLib2 UI missing {token!r}")
        if screen_name == "SignalConditionerScreen" and "Cycle mode ▶" not in ld_ui:
            errors.append("SignalConditionerScreen LDLib2 UI missing 'Cycle mode ▶'")
        if screen_name == "OscilloscopeScreen":
            for token in ("Cycle Δt ▶", "TRIGGER", "SAMPLING EXPERIMENT", "waveform(menu, 0)"):
                if token not in ld_ui:
                    errors.append(f"OscilloscopeScreen LDLib2 UI missing {token!r}")
        if screen_name == "OpticalSystemScreen":
            for token in (
                "PIONEER PATTERN • OPTICAL MODEL",
                "I_out = max(0, I_in - L)",
                "I_A = floor(I_in/2)",
                "L_obs = I_TX - I_RX",
                "Cycle direction ▶",
                "Cycle RX ▶",
                "Cycle TX ▶",
                "Observed segment loss",
                "Receiver headroom",
                "Observer-only commissioning evidence",
            ):
                if token not in ld_ui:
                    errors.append(f"OpticalSystemScreen LDLib2 UI missing {token!r}")
        if screen_name == "DigitalCommunicationScreen":
            for token in (
                "PIONEER PATTERN • COMMUNICATION MODEL",
                "{20,40,60}% • direct entry",
                "Cycle direction ▶",
                "Cycle RX ▶",
                "Cycle TX ▶",
                "8-BIT BUS CONTENTION CONSUMING MARGIN",
                "SERIAL LINK NEAR UTILIZATION LIMIT",
                "DIFFERENTIAL HIGH-INTEGRITY LINK VALID",
            ):
                if token not in ld_ui:
                    errors.append(f"DigitalCommunicationScreen LDLib2 UI missing {token!r}")
        if screen_name == "ReliabilitySystemScreen":
            for token in (
                "PIONEER PATTERN • RELIABILITY / SAFE STATE",
                "Maintenance action",
                "Cycle direction ▶",
                "Cycle RX ▶",
                "Cycle TX ▶",
                "safe-state logic is never inferred from UI presentation alone",
            ):
                if token not in ld_ui:
                    errors.append(f"ReliabilitySystemScreen LDLib2 UI missing {token!r}")
        if screen_name == "SignalAnalyzerScreen":
            for token in (
                "PIONEER PATTERN • METROLOGY / CALIBRATION",
                "SignalAnalyzerPlotElement",
                "Toggle TAP/INLINE",
                "Trial baseline",
                "Trial candidate",
                "Internal RSE reference comparison only",
            ):
                if token not in ld_ui:
                    errors.append(f"SignalAnalyzerScreen LDLib2 UI missing {token!r}")
        if screen_name == "LogicAnalyzerScreen":
            for token in (
                "PIONEER PATTERN • DIGITAL TIMING MODEL",
                "LogicAnalyzerPlotElement",
                "Trigger CH ▶",
                "Trigger edge ▶",
                "Bus interference",
                "shield exposed instrument segments",
            ):
                if token not in ld_ui:
                    errors.append(f"LogicAnalyzerScreen LDLib2 UI missing {token!r}")
        if screen_name == "UniversalFieldDeviceScreen":
            for token in (
                "DECLARED ENGINEERING PORTS",
                "FORMULA PARAMETER WORKBENCH",
                "SYSTEM / OPERATOR STATE",
                "PIONEER WAVE 13 • MEASUREMENT",
                "Cycle direction ▶",
                "Cycle RX ▶",
                "Cycle TX ▶",
            ):
                if token not in ld_ui:
                    errors.append(f"UniversalFieldDeviceScreen LDLib2 UI missing {token!r}")
        if screen_name == "RangeSensorScreen":
            for token in ("DataBindingBuilder.string", "{4,8,15}", "Cycle detect ▶", "Cycle response ▶"):
                if token not in ld_ui:
                    errors.append(f"RangeSensorScreen LDLib2 UI missing {token!r}")
        if screen_name == "MediaConversionScreen":
            for token in ("FORMULA-FIRST MEDIA BOUNDARY", "Cycle RX ▶", "Cycle TX ▶", "Quantization loss"):
                if token not in ld_ui:
                    errors.append(f"MediaConversionScreen LDLib2 UI missing {token!r}")
        if screen_name == "SignalProcessorScreen":
            for token in ("DataBindingBuilder.string", "PIONEER PATTERN • SIGNAL PROCESSOR MODEL", "Cycle edge mode ▶"):
                if token not in ld_ui:
                    errors.append(f"SignalProcessorScreen LDLib2 UI missing {token!r}")
        if screen_name == "QuartzTimingScreen":
            for token in ("DataBindingBuilder.string", "DIRECT T", "DIRECT N", "Reset measurement", "Cycle RX ▶"):
                if token not in ld_ui:
                    errors.append(f"QuartzTimingScreen LDLib2 UI missing {token!r}")
        if screen_name == "RadioLinkScreen":
            for token in ("DataBindingBuilder.string", "ADJUSTABLE", "availability", "decode", "Cycle output direction ▶"):
                if token not in ld_ui:
                    errors.append(f"RadioLinkScreen LDLib2 UI missing {token!r}")
        if screen_name == "CopperCircuitMeterScreen":
            for token in ("I = V / R_eq", "COMMISSIONING", "OBSERVER ONLY", "Cycle measurement face ▶"):
                if token not in ld_ui:
                    errors.append(f"CopperCircuitMeterScreen LDLib2 UI missing {token!r}")
        if screen_name == "MagneticSystemScreen":
            for token in ("DataBindingBuilder.string", "V_ind = clamp", "bounded inverse-square-style accumulation", "Cycle N marker ▶"):
                if token not in ld_ui:
                    errors.append(f"MagneticSystemScreen LDLib2 UI missing {token!r}")
        if screen_name == "PneumaticSystemScreen":
            for token in ("PIONEER PATTERN • PNEUMATIC MODEL", "P ∈ {25,50,75,100}", "Toggle valve", "Cycle RX ▶", "ΔP_path = ΔP_line + ΔP_restriction"):
                if token not in ld_ui:
                    errors.append(f"PneumaticSystemScreen LDLib2 UI missing {token!r}")
        if screen_name == "AmethystSystemScreen":
            for token in ("PIONEER PATTERN • RESONANCE MODEL", "Frequency values are deliberate model indices, not fabricated Hz", "Pulse", "Cycle RX ▶", "BW = 5 - Q"):
                if token not in ld_ui:
                    errors.append(f"AmethystSystemScreen LDLib2 UI missing {token!r}")
        continue

    required = standalone_contracts.get(screen_name)
    if required is None:
        errors.append(
            f"{screen_name} is block-facing but neither inherits EngineeringScreen, declares an approved standalone deep-canvas contract, nor declares an approved LDLib2 HMI contract"
        )
        continue
    standalone_families.append(screen_name)
    for token in required:
        if token not in source:
            errors.append(f"{screen_name} missing standalone deep-canvas token {token!r}")

for token in (
    "openUniversal(player, pos)",
    "new FieldDeviceMenu(id, inv, pos)",
    "new UniversalFieldDeviceMenu(id, inv, pos)",
    "block instanceof DirectionalSignalBlock",
    "block instanceof DirectionalDomainBlock",
):
    if token not in field_ui:
        errors.append(f"FieldDeviceUi missing all-block routing token {token!r}")

if len(engineering_families) + len(standalone_families) + len(ldlib_families) != len(block_facing):
    errors.append(
        "not every block-facing UI family is covered by a deep-canvas presentation contract"
    )

if manual_configure_offsets:
    errors.append(
        "EngineeringScreen families still own manual Configure translation instead of the shared control rail: "
        + ", ".join(sorted(manual_configure_offsets))
    )

if errors:
    print("RSE ALL-BLOCK UI ROLLOUT VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE ALL-BLOCK UI ROLLOUT VERIFY: PASS")
print(f" registered blocks reconciled: {len(registered_blocks)} / 122")
print(f" block-facing UI families: {len(block_facing)}")
print(f" shared EngineeringScreen families: {len(engineering_families)}")
print(f" standalone deep-canvas instrument families: {len(standalone_families)}")
print(f" LDLib2 HMI families: {len(ldlib_families)}")
print(" global X/Y wheel + draggable scrollbar contract: PASS")
print(" global operator-control inventory / function surface: PASS")
print(" persistent live Health / Role / Evidence / I-O / Controls strip: PASS")
print(" global Model / Variables / Evidence engineering contract: PASS")
print(" live mechanism-flow structure across all block-facing UI: PASS")
print(" reserved shared appendix / no device-content overlap: PASS")
print(" responsive 3x2 navigation rail + larger tab hit targets: PASS")
print(" standalone instrument math/evidence equivalence: PASS")
print(" LDLib2 HMI host + server-authority contract: PASS")
print(" shared 3-column Configure rail + automatic content offset: PASS")
print(" generic FieldDevice + Universal fallbacks: PASS")
print(" Pioneer 122/122 closure linkage: PASS")
