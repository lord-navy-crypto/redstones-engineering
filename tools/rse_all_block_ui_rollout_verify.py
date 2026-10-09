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
    "PidControllerScreen": (
        "extends LdlibEngineeringHostScreen<PidControllerMenu>",
    ),
    "LapisLowPassScreen": (
        "extends LdlibEngineeringHostScreen<LapisLowPassMenu>",
    ),
    "IndustrialBufferScreen": (
        "extends LdlibEngineeringHostScreen<IndustrialBufferMenu>",
    ),
    "WorkcellControllerScreen": (
        "extends LdlibEngineeringHostScreen<WorkcellControllerMenu>",
    ),
    "OperationsMonitorScreen": (
        "extends LdlibEngineeringHostScreen<OperationsMonitorMenu>",
    ),
    "EnhancedFieldDeviceScreen": (
        "extends LdlibEngineeringHostScreen<FieldDeviceMenu>",
    ),
    "FieldDeviceScreen": (
        "extends LdlibEngineeringHostScreen<FieldDeviceMenu>",
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
            "PidControllerScreen": "src/main/java/dev/redstoneengineering/ui/ldlib/PidControllerLdUi.java",
            "LapisLowPassScreen": "src/main/java/dev/redstoneengineering/ui/ldlib/LapisLowPassLdUi.java",
            "IndustrialBufferScreen": "src/main/java/dev/redstoneengineering/ui/ldlib/IndustrialBufferLdUi.java",
            "WorkcellControllerScreen": "src/main/java/dev/redstoneengineering/ui/ldlib/WorkcellControllerLdUi.java",
            "OperationsMonitorScreen": "src/main/java/dev/redstoneengineering/ui/ldlib/OperationsMonitorLdUi.java",
            "EnhancedFieldDeviceScreen": "src/main/java/dev/redstoneengineering/ui/ldlib/EnhancedFieldDeviceLdUi.java",
            "FieldDeviceScreen": "src/main/java/dev/redstoneengineering/ui/ldlib/EnhancedFieldDeviceLdUi.java",
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
                "MECHANISM FLOW • SERVER-AUTHORITATIVE",
                "READ-ONLY TOPOLOGY • no server-supported route mutation for this device",
                "no fake action is exposed when the authoritative server model has no explicit action",
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
        if screen_name == "PidControllerScreen":
            for token in (
                "PIONEER PATTERN • CONTROL / ACCEPTANCE MODEL",
                "e=SP−PV",
                "anti-windup",
                "PidTrendPlotElement",
                "Cycle tuning preset ▶",
                "Capture acceptance",
                "PIONEER WORKFLOW • CLOSED-LOOP COMMISSIONING TRIAL",
            ):
                if token not in ld_ui:
                    errors.append(f"PidControllerScreen LDLib2 UI missing {token!r}")
        if screen_name == "LapisLowPassScreen":
            for token in (
                "y[n] = y[n-1] + α",
                "DataBindingBuilder.string",
                "Restore default α",
                "Cycle RX ▶",
                "Cycle TX ▶",
                "LIVE SUBSTITUTION",
                "RseLdUiComponents.authorityFooter()",
            ):
                if token not in ld_ui:
                    errors.append(f"LapisLowPassScreen LDLib2 UI missing {token!r}")
        if screen_name == "IndustrialBufferScreen":
            for token in (
                "PIONEER PATTERN • OPERATIONS / WIP MODEL",
                "WIP PRESSURE",
                "LOT IDENTITY",
                "WORKCELL ROLES",
                "PERSISTED WIP",
                "RseLdUiComponents.authorityFooter()",
            ):
                if token not in ld_ui:
                    errors.append(f"IndustrialBufferScreen LDLib2 UI missing {token!r}")
        if screen_name == "WorkcellControllerScreen":
            for token in (
                "INPUT → WORKCELL → OUTPUT",
                "PIONEER PATTERN • WORKCELL ADMISSION GATE",
                "PERMIT",
                "HOLD",
                "Operations Binding Tool",
                "AUTHORITY BOUNDARY",
                "RseLdUiComponents.authorityFooter()",
            ):
                if token not in ld_ui:
                    errors.append(f"WorkcellControllerScreen LDLib2 UI missing {token!r}")
        if screen_name == "OperationsMonitorScreen":
            for token in (
                "PIONEER PATTERN • PLANT STATE / KPI AUTHORITY",
                "WORLD PLANT STATE",
                "PLANT EVENT TIMELINE",
                "FIRST OUT",
                "First-out source",
                "WITHHELD • EVIDENCE MISSING",
                "OBSERVER AUTHORITY BOUNDARY",
                "RseLdUiComponents.authorityFooter()",
            ):
                if token not in ld_ui:
                    errors.append(f"OperationsMonitorScreen LDLib2 UI missing {token!r}")
        if screen_name in ("EnhancedFieldDeviceScreen", "FieldDeviceScreen"):
            for token in (
                "PIONEER PATTERN • SHARED FIELD DEVICE",
                "PIONEER PATTERN • SOURCE / MEDIUM INTEGRITY",
                "DataBindingBuilder.string",
                "Exact engineering value",
                "READ-ONLY HMI • no fake control",
                "Cycle direction ▶",
                "Cycle RX ▶",
                "Cycle TX ▶",
                "PortQuality",
                "RseLdUiComponents.authorityFooter()",
            ):
                if token not in ld_ui:
                    errors.append(f"EnhancedFieldDeviceScreen LDLib2 UI missing {token!r}")
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

# Tabbed LDLib2 UX gate: every registered block HMI family must keep its
# two-axis viewport, and the shared implementation must preserve navigation.
ldlib_dir = ROOT / "src/main/java/dev/redstoneengineering/ui/ldlib"
ldlib_hmis = sorted(ldlib_dir.glob("*LdUi.java"))
if len(ldlib_hmis) != 23:
    errors.append(f"expected exactly 23 LDLib2 HMI families, found {len(ldlib_hmis)}")
for hmi in ldlib_hmis:
    source = hmi.read_text(errors="ignore")
    if "RseLdUiComponents.responsiveUi(root, player," not in source:
        errors.append(f"{hmi.name}: UI does not use dynamic GUI-scaled viewport")
    # Dynamic outer size is ineffective when its immediate content root remains
    # fixed-width/height. Every HMI root must follow the allocated screen canvas.
    if ".layout(l -> l.widthPercent(100).heightPercent(100)" not in source:
        errors.append(f"{hmi.name}: root is fixed-size and can overflow scaled viewport")

    has_shared_workspace = "RseLdUiComponents.tabbedWorkspace(" in source
    has_native_workspace = "new ScrollerView()" in source and "ScrollerMode.BOTH" in source
    if not (has_shared_workspace or has_native_workspace):
        errors.append(f"{hmi.name}: no dual-axis tabbed workspace")
    if has_shared_workspace and source.count("RseLdUiComponents.tabbedWorkspace(") != 1:
        errors.append(f"{hmi.name}: repeated/nested tabbed workspace")
    if has_native_workspace and not (
        "RseLdUiComponents.standaloneTabs(workspace," in source
        or ("horizontalScroller" in source and "verticalScroller" in source)
    ):
        errors.append(f"{hmi.name}: native workspace does not reset both axes on page changes")

for token in (
    "new ScrollerView()",
    "ScrollerMode.BOTH",
    "ScrollDisplay.AUTO",
    "setDisplay(candidate == page)",
    "horizontalScroller.setNormalizedValue(0)",
    "verticalScroller.setNormalizedValue(0)",
    ".paddingAll(12)",
    "screen.getWidth() - 24",
    "screen.getHeight() - 30",
    "TextWrap.WRAP",
    ".adaptiveWidth(false)",
    ".adaptiveHeight(true)",
    ".flexWrap(FlexWrap.WRAP)",
):
    if token not in components:
        errors.append(f"shared LDLib2 viewport / text contract missing {token!r}")

# Dense engineering HMI usability: a shared 850px logical content pane must
# not demand horizontal scrolling in an ordinary 500-620px Minecraft window.
# Both columns and readouts must wrap, with scroll retained for truly narrow GUIs.
for token in (
    "public static Label note(String text)",
    ".widthPercent(100).minWidth(readableMinWidth)",
    "page.layout(l -> l.widthPercent(100).paddingAll(12)",
    "flexWrap(FlexWrap.WRAP).gapAll(9)",
    "liveText(value).layout(l -> l.flex(1).minWidth(165))",
    'i == 0 ? "▶ " + labels[i] : labels[i]',
    "tabButtons.get(j).setText(j == selectedIndex",
):
    if token not in components:
        errors.append(f"Shared viewport/active tab readability regressed: {token!r}")
for family, minimum_width in (("UniversalFieldDevice", 440), ("SignalConditioner", 410)):
    text = read(f"src/main/java/dev/redstoneengineering/ui/ldlib/{family}LdUi.java")
    for token in (".widthPercent(100).minWidth(" + str(minimum_width) + ")",
                  ".widthPercent(100).paddingAll(12).gapAll(10)"):
        if token not in text:
            errors.append(f"{family}: fixed width horizontal-scroll regression {token!r}")
for family in ("OpticalSystem", "DigitalCommunication", "LapisLowPass",
               "MediaConversion", "ReliabilitySystem", "EnhancedFieldDevice",
               "WorkcellController", "SignalProcessor"):
    text = read(f"src/main/java/dev/redstoneengineering/ui/ldlib/{family}LdUi.java")
    if "RseLdUiComponents.note(" not in text:
        errors.append(f"{family}: long unwrapped engineering notes reintroduced")

# Narrow panels must allow real server-bound controls to break onto the
# next line; otherwise the right-most route or preset button is unclickable.
for family in ("EnhancedFieldDevice", "OpticalSystem", "PneumaticSystem",
               "ReliabilitySystem", "DigitalCommunication", "MagneticSystem",
               "UniversalFieldDevice", "SignalConditioner"):
    code = read(f"src/main/java/dev/redstoneengineering/ui/ldlib/{family}LdUi.java")
    for line in code.splitlines():
        if "flexDirection(YogaFlexDirection.ROW).gapAll(" in line:
            errors.append(f"{family}: unwrapped operator/control row can clip buttons")

# The three high-density trace/controller canvases previously forced an
# 840-950px worksheet even at normal 640-690px GUI width.
for family, minimum_width in (("Oscilloscope", 520), ("LogicAnalyzer", 510), ("PidController", 540)):
    code = read(f"src/main/java/dev/redstoneengineering/ui/ldlib/{family}LdUi.java")
    for token in (f".widthPercent(100).minWidth({minimum_width})",
                  ".widthPercent(100).paddingAll(12).gapAll(10)"):
        if token not in code:
            errors.append(f"{family}: viewport-based graph/trace canvas lost {token!r}")
    if "flexDirection(YogaFlexDirection.ROW).gapAll(" in code:
        errors.append(f"{family}: operator row will clip at narrow GUI scales")

# The five stand-alone instruments all use one active-tab renderer, which
# preserves clickability at small scales and highlights the currently visible page.
for token in ("public static UIElement standaloneTabs(", "flexWrap(FlexWrap.WRAP)",
              'j == selected ? "▶ " + labels[j] : labels[j]',
              "scroller.horizontalScroller.setNormalizedValue(0)",
              "scroller.verticalScroller.setNormalizedValue(0)"):
    if token not in components:
        errors.append(f"Standalone LDLib2 active-tab helper regressed: {token!r}")
for name in ("UniversalFieldDevice", "SignalConditioner", "Oscilloscope", "PidController", "LogicAnalyzer"):
    source = read(f"src/main/java/dev/redstoneengineering/ui/ldlib/{name}LdUi.java")
    if "RseLdUiComponents.standaloneTabs(workspace," not in source:
        errors.append(f"{name}LdUi.java: active tabs lost shared wrap/selection/scroll contract")
    if "tabButton(" in source:
        errors.append(f"{name}LdUi.java: duplicate legacy tab handlers still present")

# A client menu constructs the LDLib2 tree before vanilla tracked DataSlots
# arrive. Without a static shape primer the screen can lock in a default
# read-only page and hide real Pioneer formulas/controls after synchronization.
client_primers = {
    "FieldDeviceMenu": "primeClientUiShape(level.getBlockState(blockPos))",
    "UniversalFieldDeviceMenu": "primeClientUiShape(level.getBlockState(blockPos))",
    "QuartzTimingMenu": "primeClientUiKind(level.getBlockState(blockPos).getBlock())",
    "MagneticSystemMenu": "primeClientUiKind(level.getBlockState(blockPos).getBlock())",
    "PneumaticSystemMenu": "primeClientUiKind(level.getBlockState(blockPos).getBlock())",
    "AmethystSystemMenu": "primeClientUiKind(level.getBlockState(blockPos).getBlock())",
    "OpticalSystemMenu": "primeClientUiKind(level.getBlockState(blockPos).getBlock())",
    "RadioLinkMenu": "primeClientUiKind(level.getBlockState(blockPos).getBlock())",
    "SignalProcessorMenu": "primeClientUiKind(level.getBlockState(blockPos).getBlock())",
    "ReliabilitySystemMenu": "primeClientUiKind(level.getBlockState(blockPos).getBlock())",
    "DigitalCommunicationMenu": "kind.set(kindOf(level.getBlockState(blockPos).getBlock()))",
    "MediaConversionMenu": "mode.set(block instanceof RedstoneToLapisScalerBlock",
}
for menu, primer in client_primers.items():
    code = read(f"src/main/java/dev/redstoneengineering/ui/menu/{menu}.java")
    if primer not in code:
        errors.append(f"{menu}: client HMI schema not primed before tracked DataSlot synchronization")

universal_menu = read("src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java")
for kinds, getter_prefix in (
    ("configKind", "uiConfigKind"),
    ("pioneerMeasurementKind", "uiMeasurementKind"),
    ("pioneerProcessKind", "uiProcessKind"),
):
    snapshot_constants = set(re.findall(
        rf'{kinds}\.set\(((?:CONFIG|PIONEER)_[A-Z0-9_]+)\)', universal_menu))
    marker = f"private static int {getter_prefix}(Block block)"
    fragment = universal_menu.split(marker, 1)[-1].split("    private static int ", 1)[0]
    client_constants = set(re.findall(r'return ((?:CONFIG|PIONEER)_[A-Z0-9_]+);', fragment))
    if not snapshot_constants.issubset(client_constants):
        errors.append(
            f"UniversalFieldDeviceMenu {getter_prefix}: server kinds not covered by client UI shape "
            + ", ".join(sorted(snapshot_constants - client_constants)))
universal_ui = read("src/main/java/dev/redstoneengineering/ui/ldlib/UniversalFieldDeviceLdUi.java")
for token in ("modelPreviewPanel(menu)", "PIONEER • ACTUAL IMPLEMENTED MODEL",
              "processEquation(menu.pioneerProcessKind())",
              "measurementEquation(menu.pioneerMeasurementKind())",
              "ADJUSTABLE", "secondaryDirectKind(kind)"):
    if token not in universal_ui:
        errors.append(f"UniversalFieldDeviceLdUi missing front-page model/tuning contract {token!r}")

# Guard against treating a legitimate zero-valued tuning parameter as an
# absent value. FieldDeviceMenu synchronizes the authoritative setpoint in
# different slots by device kind; a fallback based on != 0 silently displays
# the wrong physical variable (e.g. optical channel zero -> incoming intensity).
field_hmi = read("src/main/java/dev/redstoneengineering/ui/ldlib/EnhancedFieldDeviceLdUi.java")
control_section = field_hmi.split("private static int controlValue(FieldDeviceMenu m) {", 1)[-1].split("\n    }", 1)[0]
expected_control_slots = {
    "KIND_PROBE": "secondary",
    "KIND_FILTER": "tertiary",
    "KIND_REFERENCE": "primary",
    "KIND_LAPIS_SOURCE": "primary",
    "KIND_DIGITAL_REGENERATOR": "tertiary",
    "KIND_PRESSURE_REGULATOR": "secondary",
    "KIND_PNEUMATIC_RELIEF_VALVE": "tertiary",
    "KIND_PERMANENT_MAGNET": "primary",
    "KIND_INDUCTION_COIL": "tertiary",
    "KIND_OPTICAL_EMITTER": "primary",
    "KIND_OPTICAL_CHANNEL_FILTER": "tertiary",
    "KIND_OPTICAL_ATTENUATOR": "tertiary",
    "KIND_MECHANICAL_EXCITER": "secondary",
    "KIND_HYDRO_EXCITER": "secondary",
}
actual_control_slots = {}
for case_labels, field in re.findall(
        r"case\s+([\s\S]*?)\s*->\s*m\.(primary|secondary|tertiary)\(\);",
        control_section):
    for kind in re.findall(r"FieldDeviceMenu\.(KIND_[A-Z0-9_]+)", case_labels):
        actual_control_slots[kind] = field
for kind, slot in expected_control_slots.items():
    if actual_control_slots.get(kind) != slot:
        errors.append(f"FieldDevice {kind}: tuning readback must use {slot}, not {actual_control_slots.get(kind)}")
if set(actual_control_slots) != set(expected_control_slots):
    errors.append("FieldDevice control readback case coverage diverges from server-supported direct-entry kinds")
if 'm.tertiary() != 0 ? m.tertiary() : m.primary()' in field_hmi:
    errors.append("FieldDevice must not use value-dependent slot fallback for zero-valued setpoints")

# Pioneer overview must display the law AND actual tuning value together.
# A correct Configure editor hidden behind a separate tab is not enough.
field_overview = field_hmi.split("private static UIElement modelPanel(FieldDeviceMenu m) {", 1)[-1].split(
    "private static UIElement livePanel(FieldDeviceMenu m) {", 1)[0]
for token in ("PIONEER / MODEL / VARIABLES", "formulaCard(modelContract(m.kind()))",
              "if (directEntryKind(m.kind()))", '"ADJUSTABLE"', "controlValue(m)",
              "directRangeLabel(m.kind())"):
    if token not in field_overview:
        errors.append(f"FieldDevice Pioneer overview missing formula-linked control readback {token!r}")

# The Pioneer screen is a semantic view over six server-owned snapshot slots.
# Keep labels, units, and special formatting aligned with those assignments,
# not just the mere presence of a formula string.
pioneer_source = read("src/main/java/dev/redstoneengineering/ui/ldlib/UniversalFieldDeviceLdUi.java")
pioneer_server = read("src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java")
pioneer_labels = pioneer_source.split("private static String[] processLabels(int kind)", 1)[-1].split(
    "private static String[] processUnits", 1)[0]
pioneer_units = pioneer_source.split("private static String[] processUnits(int kind)", 1)[-1].split(
    "private static String[] processRoles", 1)[0]
pioneer_values = pioneer_source.split("private static String processValue(", 1)[-1].split(
    "private static String processEquation", 1)[0]
for kind, labels in (
    ("PIONEER_PROCESS_COPPER_WIRE", '"V_node","drivers","ports","","",""'),
    ("PIONEER_PROCESS_COPPER_VOLTAGE_SOURCE", '"V_set","output faces","","","",""'),
    ("PIONEER_PROCESS_QUARTZ_LAPIS_SAMPLER", '"x_L","clock","y_hold","hold quality","",""'),
    ("PIONEER_PROCESS_SOUL_METER", '"Q_s","y_R","y_R(model)","","",""'),
    ("PIONEER_PROCESS_MOLECULAR_RECEIVER", '"c_raw","c_filt","peak","sensitivity index","g","y_R"'),
    ("PIONEER_PROCESS_THERMAL_CALORIMETER", '"T","ΔT_20t","C_mean","C·ΔT","N_mass","history ready"'),
):
    if kind == "PIONEER_PROCESS_COPPER_WIRE":
        # Shared with COPPER_JUNCTION in the same switch case.
        if labels not in pioneer_labels or "PIONEER_PROCESS_COPPER_JUNCTION" not in pioneer_labels:
            errors.append("Pioneer Copper wire/junction drivers and port count are not labeled by server slot")
    elif f"{kind} -> a({labels})" not in pioneer_labels:
        errors.append(f"{kind}: labels do not match the server-owned snapshot order")
for piece in (
    'a("T-index","T-index","capacity units","relative heat","bodies","")',
    'case UniversalFieldDeviceMenu.PIONEER_PROCESS_QUARTZ_LAPIS_SAMPLER -> a("Lapis","pulse","Lapis","","","")',
):
    if piece not in pioneer_units:
        errors.append(f"Pioneer units do not align with actual snapshot: {piece}")
for piece in (
    "if (slot == 0 || slot == 2) return String.format(Locale.ROOT, \"%.2f\", raw / 100.0);",
    'if (slot == 3) {',
    'qualities[raw].name()',
):
    if piece not in pioneer_values:
        errors.append(f"Quartz held Lapis quantity/quality not decoded: {piece}")
for piece in (
    "pioneerProcessSecondary.set(CopperWireBlock.driverCount(level, blockPos))",
    "pioneerProcessTertiary.set(wire.engineeringPorts(state).size())",
    "pioneerProcessTertiary.set(QuartzTriggeredLapisSamplerBlock.heldValue(level, blockPos))",
    "pioneerProcessQuaternary.set(QuartzTriggeredLapisSamplerBlock.heldQuality(level, blockPos).ordinal())",
    "pioneerProcessTertiary.set(Math.min(15, (observation.value() * 15) / 100))",
    "pioneerProcessSecondary.set(history.deltaTemperature())",
    "pioneerProcessTertiary.set(sample.heatCapacity())",
    "pioneerProcessQuaternary.set(history.deltaTemperature() * sample.heatCapacity())",
    "pioneerProcessQuinary.set(sample.bodyCount())",
    "pioneerProcessSenary.set(history.initialized() ? 1 : 0)",
):
    if piece not in pioneer_server:
        errors.append(f"Pioneer server snapshot slot contract changed: {piece}")

# Never label measurement coverage as a tunable 'profile' or a derived
# redstone output as 'evidence'. This affects the experimenter's interpretation.
process_roles = pioneer_source.split("private static String[] processRoles(int kind)", 1)[-1].split(
    "private static String processValue(", 1)[0]
measurement_values = pioneer_source.split("private static String measurementValue(", 1)[-1].split(
    "private static String measurementEquation", 1)[0]
if "UniversalFieldDeviceMenu.profileNameForUi(raw)" not in measurement_values:
    errors.append("Pioneer sensor profile columns must use menu-owned readable names, not bare indices")
if "import dev.redstoneengineering.physics." in pioneer_source:
    errors.append("Pioneer LDLib2 UI must not import the physics solver directly")
if "SensorModel.profileName(profile)" not in pioneer_server:
    errors.append("Universal menu lost its server-model sensor profile presentation adapter")
for fragment in (
    "PIONEER_MEASUREMENT_LIGHT && slot == 2",
    "PIONEER_MEASUREMENT_LAPIS_RANGE && slot == 3",
):
    if fragment not in measurement_values:
        errors.append(f"Pioneer synchronized measurement profile decode is absent: {fragment}")
measurement_roles = pioneer_source.split("private static String[] measurementRoles(int kind)", 1)[-1].split(
    "private static String measurementValue(", 1)[0]
for role_text in (
    'a("MEASURED","EVIDENCE","TOPOLOGY","","","")',
    'a("ADJUSTABLE","TOPOLOGY","","","","")',
    'a("ADJUSTABLE","MEASURED","MEASURED","MEASURED","FIXED","FIXED")',
):
    if role_text not in process_roles:
        errors.append(f"Pioneer process variable semantic roles missing: {role_text}")
for role_text in (
    'PIONEER_MEASUREMENT_TANK ->',
    'a("MEASURED","EVIDENCE","EVIDENCE","DERIVED")',
    'PIONEER_MEASUREMENT_ENTITY_DENSITY ->',
    'a("MEASURED","EVIDENCE","DERIVED","FIXED")',
):
    if role_text not in measurement_roles:
        errors.append(f"Pioneer measurement roles inconsistent with solver: {role_text}")

# High-consequence commissioning and safety presentation: fail closed.
# These guards target real regressions found across the 122-block Pioneer HMI:
# absent evidence must never be rendered as HEALTHY / NOMINAL / AT TARGET.
reliability_ui = read("src/main/java/dev/redstoneengineering/ui/ldlib/ReliabilitySystemLdUi.java")
reliability_status = reliability_ui.split("private static String stateName(ReliabilitySystemMenu m)", 1)[-1].split(
    "private static String maintenanceName", 1)[0]
for token in ("PortQuality.FAULT", "PortQuality.DOMAIN_MISMATCH", "PortQuality.TOPOLOGY_ERROR",
              "PortQuality.STALE", "PortQuality.NO_SIGNAL", "PortQuality.NOT_READY",
              '"UNVERIFIED • "', '"EVIDENCE FAULT • "', '"SATURATED • CHECK INPUT"'):
    if token not in reliability_status:
        errors.append(f"Reliability safe-state display lost fail-closed witness check: {token}")
if reliability_status.find("PortQuality.NO_SIGNAL") > reliability_status.find('case ReliabilitySystemMenu.KIND_WATCHDOG'):
    errors.append("Reliability UI says HEALTHY / NOMINAL before checking evidence quality")

pneumatic_ui = read("src/main/java/dev/redstoneengineering/ui/ldlib/PneumaticSystemLdUi.java")
pneumatic_diag = pneumatic_ui.split("private static String diagnosis(PneumaticSystemMenu m)", 1)[-1].split(
    "private static String nextAction(", 1)[0]
if "m.cylinderSamples()<=0" not in pneumatic_diag or "ACTUATOR INPUT NOT VERIFIED" not in pneumatic_diag:
    errors.append("Pneumatic cylinder AT TARGET is not gated by real pressure and retained response evidence")
if pneumatic_diag.find("m.cylinderSamples()<=0") > pneumatic_diag.find('"AT TARGET'):
    errors.append("Pneumatic cylinder announces arrival before observing a response sample")
if "STORAGE PRESSURE UNVERIFIED" not in pneumatic_diag:
    errors.append("Reservoir must not report empty/charging on missing pressure evidence")

optical_ui = read("src/main/java/dev/redstoneengineering/ui/ldlib/OpticalSystemLdUi.java")
for token in (
    'case OpticalSystemMenu.KIND_ATTENUATOR -> "loss L"',
    'case OpticalSystemMenu.KIND_SPLITTER -> "I_branch_A"',
    'kind == OpticalSystemMenu.KIND_ATTENUATOR ? 8 : 15',
    'OpticalSystemMenu.KIND_FREE_SPACE_RX) ? 3 : 15',
    '"NOT APPLICABLE"',
    'm.kind()!=OpticalSystemMenu.KIND_RECEIVER && m.kind()!=OpticalSystemMenu.KIND_METER',
):
    if token not in optical_ui:
        errors.append(f"Optical tuning / evidence presentation mismatch: {token}")

optical_menu = read("src/main/java/dev/redstoneengineering/ui/menu/OpticalSystemMenu.java")
for method, terminal in (
    ("receiverCommissioning(PortQuality q,", "private static CommissioningStatus opticalCommissioning("),
    ("opticalCommissioning(PortQuality q,", "private static boolean hardCommissioningFault("),
):
    fragment = optical_menu.split(method, 1)[-1].split(terminal, 1)[0]
    if "hardCommissioningFault(q)" not in fragment:
        errors.append(f"{method}: missing hard-fault witness")
    if fragment.find("hardCommissioningFault(q)") > fragment.find("CommissioningStatus.NOT_READY"):
        errors.append(f"{method}: hard faults must outrank insufficient coverage")
    if "q != PortQuality.VALID" not in fragment:
        errors.append(f"{method}: PASS must require a VALID input witness")

pneumatic_menu = read("src/main/java/dev/redstoneengineering/ui/menu/PneumaticSystemMenu.java")
flow_verdict = pneumatic_menu.split("flowCommissioning(long samples,", 1)[-1].split(
    "private static boolean hardFault(", 1)[0]
for token in ("hardFault(in)", "hardFault(out)", "hardFault(upstream)", "hardFault(downstream)"):
    if token not in flow_verdict:
        errors.append(f"Pneumatic flow commissioning ignores hard-fault witness: {token}")
if flow_verdict.find("hardFault(in)") > flow_verdict.find("samples < 4"):
    errors.append("Flow-meter hard faults must outrank incomplete sample windows")

# Radio collision remains a confirmed topology fault even with incomplete
# chunk/obstacle coverage. Missing frames and degraded quality cannot be healthy.
radio_menu = read("src/main/java/dev/redstoneengineering/ui/menu/RadioLinkMenu.java")
radio_quality = radio_menu.split("private static PortQuality receptionQuality(", 1)[-1].split(
    "public boolean clickMenuButton", 1)[0]
if "reception.collision()" not in radio_quality or "reception.coverageComplete()" not in radio_quality:
    errors.append("Radio quality adapter is missing collision/coverage evidence")
elif radio_quality.find("reception.collision()") > radio_quality.find("reception.coverageComplete()"):
    errors.append("Known radio collision must outrank incomplete scan coverage")
radio_ui = read("src/main/java/dev/redstoneengineering/ui/ldlib/RadioLinkLdUi.java")
radio_diagnosis = radio_ui.split("private static String diagnosis(", 1)[-1].split(
    "private static String nextAction(", 1)[0]
for token in ('"NO RADIO FRAME / SOURCE"', '"UNVERIFIED RADIO INPUT • "',
              'm.quality()!=dev.redstoneengineering.core.port.PortQuality.VALID'):
    if token not in radio_diagnosis:
        errors.append(f"Radio diagnosis may incorrectly report HEALTHY on invalid quality: {token}")
if radio_diagnosis.find("m.quality()!=dev.redstoneengineering.core.port.PortQuality.VALID") > radio_diagnosis.find('"HEALTHY LINK"'):
    errors.append("Radio HEALTHY needs a VALID quality witness")

# High-risk operations and industrial link status must not bury hard faults
# beneath missing optional telemetry or claim a VALID link on a fault witness.
ops_ui = read("src/main/java/dev/redstoneengineering/ui/ldlib/OperationsMonitorLdUi.java")
ops_diagnosis = ops_ui.split("private static String systemDiagnosis(OperationsMonitorMenu m)", 1)[-1].split(
    "private static String nextActionText(", 1)[0]
ops_action = ops_ui.split("private static String nextActionText(OperationsMonitorMenu m)", 1)[-1].split(
    "private static String ", 1)[0]
for method, fragment in (("diagnosis", ops_diagnosis), ("nextAction", ops_action)):
    fault = fragment.find("m.electricalActiveTripCount() > 0")
    missing_run = fragment.find("!m.runEvidenceValid()")
    if fault < 0 or missing_run < 0 or fault > missing_run:
        errors.append(f"Operations Monitor {method}: protection trips are hidden by missing RUN evidence")
    fail = fragment.find("m.copperEvidenceActiveFailedCount() > 0")
    missing_queue = fragment.find("m.queueEvidenceSources() == 0")
    if fail < 0 or missing_queue < 0 or fail > missing_queue:
        errors.append(f"Operations Monitor {method}: Copper failures are hidden by missing QUEUE evidence")

digital_ui = read("src/main/java/dev/redstoneengineering/ui/ldlib/DigitalCommunicationLdUi.java")
digital_diag = digital_ui.split("private static String diagnosis(DigitalCommunicationMenu m)", 1)[-1].split(
    "private static String nextAction(", 1)[0]
for token in (
    "PortQuality.TOPOLOGY_ERROR", "PortQuality.DOMAIN_MISMATCH",
    "PortQuality.FAULT", "PortQuality.NOT_READY", "PortQuality.SATURATED",
    '"LINK INPUT / OUTPUT FAULT"', '"LINK NOT READY • EVIDENCE INCOMPLETE"',
):
    if token not in digital_diag:
        errors.append(f"Digital link may falsely report healthy on invalid quality: {token}")
if digital_diag.find("PortQuality.FAULT") > digital_diag.find('"8-BIT PARALLEL BUS HEALTHY"'):
    errors.append("Digital link HEALTHY must follow authoritative input/output fault checks")

# Physics-mechanism reveal gate: previously hidden runtime slots are now
# named against their server-owned snapshot contracts. This is not a request
# for additional local physics or invented editable controls.
mechanism_families = {
    "MagneticSystem": (
        ("mechanismPanel(m)", "total scanned cells (X+Y+Z)",
         "∂B along X", "∂B along Y", "∂B along Z",
         "connected Copper feeds", "NOT RETAINED IN HMI"),
        ("extra.set(gx.scannedCells() + gy.scannedCells() + gz.scannedCells())",
         "primary.set(gx.value()); secondary.set(gy.value()); tertiary.set(gz.value())",
         "secondary.set(input.voltage())", "tertiary.set(input.connectedFeeds())")
    ),
    "QuartzTiming": (
        ("counted rising edges", "divider initialized", "measurement initialized",
         "reference edge seen", "current measurement",
         "4096 tick saturation", "NOT RETAINED"),
        ("runtimeA.set(QuartzClockDividerBlock.countedEdges(level, blockPos))",
         "runtimeB.set(QuartzClockDividerBlock.initialized(level, blockPos) ? 1 : 0)",
         "runtimeA.set(measurement.initialized() ? 1 : 0)",
         "runtimeB.set(measurement.referenceEdgeSeen() ? 1 : 0)",
         "runtimeC.set(measurement.currentMeasurement() ? 1 : 0)")
    ),
    "AmethystSystem": (
        ("resonanceMechanism(m)", "absolute detuning |Δf_idx|",
         "quality-factor index", "server bandwidth index",
         "scanned / expected cells", "UNVERIFIED RESONANCE EVIDENCE",
         "SOURCE IDLE / NO TRANSMISSION"),
        ("extraA.set(e.bandwidth()); extraB.set(e.outputAmplitude())",
         "extraB.set(s.scannedCells()); stateFlag.set(s.expectedCells())",
         "stateFlag.set(e.saturated() ? 2 : e.responding() ? 1 : 0)")
    ),
    "SignalProcessor": (
        ("control(menu)", "runtime(menu)", "OUTPUT",
         "SERVER RETAINED", "configured pulse width"),
        ("runtimeA.set(EdgeDetectorBlock.pulseRemaining(level, blockPos))",
         "runtimeB.set(EdgeDetectorBlock.edgeCount(level, blockPos))",
         "runtimeC.set(EdgeDetectorBlock.lastEdgeAgeTicks(level, blockPos))")
    ),
    "PneumaticSystem": (
        ("pneumaticMechanism(m)", "primaryMetric(m.kind())",
         "secondaryMetric(m.kind())", "P_stored", "P_set",
         "flow proxy", "vent count", "physical path edges",
         "winning path edges", "retained samples"),
        ("primary.set(PneumaticFlowMeterBlock.flowProxy(level, blockPos))",
         "secondary.set(PneumaticFlowMeterBlock.pressureDrop(level, blockPos))",
         "tertiary.set(PneumaticFlowMeterBlock.inletPressure(level, blockPos))",
         "auxiliary.set(PneumaticFlowMeterBlock.outletPressure(level, blockPos))",
         "cylinderPathEdges.set(path.pathEdges())",
         "cylinderSamples.set(PneumaticCylinderBlock.samples(level, blockPos))")
    ),
}
for family, (ui_tokens, menu_tokens) in mechanism_families.items():
    ui_code = read(f"src/main/java/dev/redstoneengineering/ui/ldlib/{family}LdUi.java")
    menu_code = read(f"src/main/java/dev/redstoneengineering/ui/menu/{family}Menu.java")
    for token in ui_tokens:
        if token not in ui_code:
            errors.append(f"{family}: Pioneer mechanism/variable not visible: {token}")
    for token in menu_tokens:
        if token not in menu_code:
            errors.append(f"{family}: expected server-owned snapshot field absent: {token}")
for family in ("MagneticSystem", "AmethystSystem", "PneumaticSystem"):
    ui_code = read(f"src/main/java/dev/redstoneengineering/ui/ldlib/{family}LdUi.java")
    if "import dev.redstoneengineering.physics." in ui_code:
        errors.append(f"{family} UI must not import or run its own physics solver")
quartz_ui = read("src/main/java/dev/redstoneengineering/ui/ldlib/QuartzTimingLdUi.java")
if '"RUNTIME","A/B/C"' in quartz_ui:
    errors.append("Quartz runtime A/B/C is not a meaningful experiment variable")

# Broad shared-device home-page reveal. A generic contract page must
# actually display server-provided operands and PortQuality before a user digs
# through tabs; keep the full six-slot Pioneer inspector intact as well.
universal_preview = read("src/main/java/dev/redstoneengineering/ui/ldlib/UniversalFieldDeviceLdUi.java")
overview_fragment = universal_preview.split("private static UIElement modelPreviewPanel(",1)[-1].split(
    "private static UIElement portsPanel(",1)[0]
for token in (
    "appendPioneerLivePreview(menu, panel)",
    "processLabels(process)", "processUnits(process)", "processRoles(process)",
    "measurementLabels(measurement)", "measurementUnits(measurement)",
    "measurementRoles(measurement)", "processValue(menu, process, slot)",
    "measurementValue(menu, measurement, slot)",
    "menu.pioneerProcessEvidenceQuality().name()",
    "menu.pioneerEvidenceQuality().name()", "shown<3",
):
    if token not in overview_fragment:
        errors.append(f"Universal Pioneer Overview hides a live variable/quality field: {token}")
for token in ("private static UIElement processPioneerPanel(", "private static UIElement measurementPioneerPanel("):
    if token not in universal_preview:
        errors.append(f"Universal Pioneer deep evidence/variables page missing: {token}")

# Reliability observer controls must be real; PID model home must include
# server-owned intermediate states rather than only a line plot.
reliability_reveal = read("src/main/java/dev/redstoneengineering/ui/ldlib/ReliabilitySystemLdUi.java")
reliability_config = reliability_reveal.split("private static UIElement parameterPanel(",1)[-1].split(
    "private static UIElement statePanel(",1)[0]
for token in ('if (adjustable(m.kind()))', 'if (adjustable(m.kind())) {',
              'NO EDITABLE PARAMETER', 'Maintenance action'):
    if token not in reliability_config:
        errors.append(f"Reliability inspector has false/missing parameter action: {token}")
if "setNumbersOnlyInt(0, 160)" in reliability_reveal:
    errors.append("Reliability HMI uses a universal 160-wide editor instead of kind-specific bounds")
pid_reveal = read("src/main/java/dev/redstoneengineering/ui/ldlib/PidControllerLdUi.java")
for token in ('liveMechanismPanel(m)', 'PIONEER • LIVE CONTROL CHAIN',
              'm.pTerm()', 'm.iTerm()', 'm.dTerm()', 'm.integralState()',
              'm.derivativeState()', 'm.antiWindupHolding()',
              'm.unsaturatedOutput()', 'm.plantDetected()'):
    if token not in pid_reveal:
        errors.append(f"PID Model page hides server-owned mechanism: {token}")

# Pioneer instrumentation round: controllers and oscilloscopes must expose
# their actual synchronized intermediate values; empty captures and partially
# invalid windows cannot be presented as meaningful steady-state evidence.
instrument_reveals = {
    "PidController": (
        ("PIONEER • SERVER PID TERM DECOMPOSITION", "m.bias()",
         "m.pTerm()", "m.iTerm()", "m.dTerm()", "m.integralState()",
         "m.derivativeState()", "m.antiWindupHolding()", "m.unsaturatedOutput()",
         "m.trialScoreDelta()", "m.plantReady()"),
        ("pTerm.set(terms.pTerm())", "iTerm.set(terms.iTerm())",
         "dTerm.set(terms.dTerm())", "bias.set(terms.bias())",
         "antiWindupHolding.set(terms.antiWindupHolding() ? 1 : 0)")
    ),
    "Oscilloscope": (
        ("channelPhysics(menu, 0)", "channelPhysics(menu, 1)",
         "menu.minimum(channel)", "menu.maximum(channel)", "menu.peakToPeak(channel)",
         "menu.average100(channel)", "menu.periodSamples(channel)",
         "menu.frequencyMilliHz(channel)", "menu.aliasRisk(channel)",
         "menu.experimentSamplesDelta()", "menu.experimentFrequencyDeltaMilliHz()",
         "menu.baselineCoverage()", "menu.candidateCoverage()",
         'menu.sampleCount()>0', "BOUNDED NETWORK SCAN"),
        ("minimum[channel].set(scope.minimum(channel))",
         "average100[channel].set(scope.average100(channel))",
         "frequencyMilliHz[channel].set(scope.estimatedFrequencyMilliHz(channel))",
         "experimentSamplesDelta.set(scope.samplingExperimentSamplesPerCycleDelta())")
    ),
    "LogicAnalyzer": (
        ('captureState(m.captureState())', "m.bounded()",
         "m.validSamples(c)>0", "m.validSamples(c)>1", "m.duty(c)", "m.rising(c)", "m.falling(c)",
         "m.probeCount(c)", "m.shieldedCableNodes()"),
        ("duty[channel].set(analyzer.dutyPercent(channel))",
         "transitionRate[channel].set(analyzer.transitionRatePercent(channel))",
         "rising[channel].set(analyzer.rising(channel))",
         "falling[channel].set(analyzer.falling(channel))")
    ),
    "SignalAnalyzer": (
        ("measurementPanel(m)", "completeValidWindow(m)",
         "m.validWindowCount()==m.windowCount()", "raw mean − reference",
         "PER-SAMPLE CLAMP", "trialReady(m)", "NOT READY"),
        ("average100.set(snapshot.average100())",
         "validWindowCount.set(snapshot.validWindowCount())",
         "measurementQuality.set(snapshot.measurementQuality().ordinal())")
    )
}
for family, (display_tokens, snapshot_tokens) in instrument_reveals.items():
    ui_code = read(f"src/main/java/dev/redstoneengineering/ui/ldlib/{family}LdUi.java")
    menu_code = read(f"src/main/java/dev/redstoneengineering/ui/menu/{family}Menu.java")
    for token in display_tokens:
        if token not in ui_code:
            errors.append(f"{family}: missing authority-backed Pioneer instrument variable {token}")
    for token in snapshot_tokens:
        if token not in menu_code:
            errors.append(f"{family}: missing server snapshot for display {token}")
    if "import dev.redstoneengineering.physics." in ui_code:
        errors.append(f"{family}: UI must not run physics solver directly")
scope_ui = read("src/main/java/dev/redstoneengineering/ui/ldlib/OscilloscopeLdUi.java")
if 'menu.coverage(channel)>0' not in scope_ui or 'menu.frequencyMilliHz(channel)>0' not in scope_ui:
    errors.append("Oscilloscope empty capture must not render bogus measurement frequency")
signal_ui = read("src/main/java/dev/redstoneengineering/ui/ldlib/SignalAnalyzerLdUi.java")
if 'm.validWindowCount()==m.windowCount()' not in signal_ui or 'trialReady(m)' not in signal_ui:
    errors.append("Signal Analyzer invalid window/trial cannot report stable result or zero deltas")

# Pioneer instrument integrity regression: a missing server observation is not
# a zero measurement and cannot certify a healthy medium or a complete field.
new_evidence_guards = {
    "MagneticSystem": ("m.complete()", "NOT READY • X/Y/Z scan incomplete",
                       "UNVERIFIED • Copper input", "NOT READY • scan incomplete"),
    "DigitalCommunication": ("verifiedValue(", "m.mediumAgeTicks() < 0",
                             "MEDIUM NOT READY • NO TIMED SAMPLE",
                             "NOT READY • no synchronized medium sample"),
    "QuartzTiming": ("m.dividerInputValid()", "upstream clock",
                     "NOT READY • input clock unverified"),
    "IndustrialBuffer": ("m.snapshotPresent()", "NOT READY • buffer snapshot missing",
                         "No lots in the bounded opening snapshot"),
    "AmethystSystem": ("reliableResonance(m)", "NOT READY • spectrum evidence incomplete",
                       "UNVERIFIED • input evidence"),
    "OpticalSystem": ("opticalBudgetReady(m)", "hasDerivedPair(m.kind())",
                      "NOT READY • no valid same-channel neighbors",
                      "UNVERIFIED • source / receiver path"),
    "PneumaticSystem": ("primaryReadout(m)", "secondarySnapshot(m)",
                        "flowMeterReady(m)", "cylinderReady(m)",
                        "witnessReadout(m)", "NOT READY • sample/witness evidence incomplete",
                        "NOT READY • relief pressure unverified"),
    "MediaConversion": ("conversionReady(menu)", "NOT READY • no verified quantization pair",
                        "menu.inputQuality()==dev.redstoneengineering.core.port.PortQuality.VALID",
                        "menu.outputQuality()==dev.redstoneengineering.core.port.PortQuality.VALID"),
    "LapisLowPass": ("predictionReady(m)", "m.runtimePresent()",
                     "NOT READY • no retained state", "NOT READY • valid RX and initialized filter required"),
    "EnhancedFieldDevice": ("qualifiedRawMetric(m", "m.evidenceQualityKnown()",
                            "m.snapshotReady()", "NOT READY • awaiting server snapshot",
                            "PORT QUALITY NOT REPORTED", "NOT READY • raw=",
                            "CONFIG / TOPOLOGY • PORT QUALITY NOT REPORTED"),
    "WorkcellController": ("m.inspectionReady()", "NOT READY • awaiting server workcell inspection",
                           "NOT READY • no inspected decision", "NOT READY • input buffer/capacity evidence missing",
                           "UNVERIFIED • no fault assessment"),
    "ReliabilitySystem": ("primaryReadout(m)", "secondaryReadout(m)", "tertiaryReadout(m)",
                          "evidenceInterpretation(m)", "raw/retained != current measured value"),
    "SignalConditioner": ("boundaryInterpretation(menu)", "threshold gate, not saturation",
                          "deadband hold, not saturation",
                          "SATURATED • 0..15 transfer limit"),
}
for family, evidence_tokens in new_evidence_guards.items():
    ui_code = read(f"src/main/java/dev/redstoneengineering/ui/ldlib/{family}LdUi.java")
    for token in evidence_tokens:
        if token not in ui_code:
            errors.append(f"{family}: absent validity-qualified display guard {token!r}")
for family, menu_token in (
    ("QuartzTiming", "dividerInputValid.set(inputSample.valid() ? 1 : 0)"),
    ("IndustrialBuffer", "snapshotPresent.set(snapshot == null ? 0 : 1)"),
):
    menu_code = read(f"src/main/java/dev/redstoneengineering/ui/menu/{family}Menu.java")
    if menu_token not in menu_code:
        errors.append(f"{family}: server-side evidence provenance not synchronized")

field_device_server = read("src/main/java/dev/redstoneengineering/ui/menu/FieldDeviceMenu.java")
if 'return index >= 0 && index < values.length ? values[index] : PortQuality.NOT_READY;' not in field_device_server:
    errors.append("Field Device invalid/unknown quality sentinel must not decode to VALID")
if 'public boolean evidenceQualityKnown()' not in field_device_server:
    errors.append("Field Device must differentiate absent quality from a measured port quality")
for token in ("snapshotReady.set(0)", "snapshotReady.set(1)", "public boolean snapshotReady()"):
    if token not in field_device_server:
        errors.append(f"Field Device must not certify port quality before first server snapshot: {token!r}")
workcell_server = read("src/main/java/dev/redstoneengineering/ui/menu/WorkcellControllerMenu.java")
for token in ("inspectionReady.set(0)", "inspectionReady.set(1)", "public boolean inspectionReady()"):
    if token not in workcell_server:
        errors.append(f"Workcell Controller must synchronize authoritative inspection readiness: {token!r}")

# Shared plot rendering regression: sample points and markers must stay
# inside the half-open LDLib2 draw rectangle. A sample at the maximum
# signal value, a LOW digital edge, or a rightmost cursor cannot bleed into
# the neighboring tab or control.
plot = read("src/main/java/dev/redstoneengineering/client/ui/EngineeringPlot.java")
for token in (
    "width < 3 || height < 3",
    "slot * (width - 1)",
    "height - 1",
    "boundedPoint(graphics, px, py, x, y, width, height, color)",
    "Math.max(x, px - 2)",
    "Math.min(x + width, px + 3)",
    "slot < 0 || slot >= sampleCount",
    "Math.min(y + height, py + 2)",
):
    if token not in plot:
        errors.append(f"EngineeringPlot half-open draw safety regression: {token!r}")
if "slot * width / (float) denominator" in plot or "y + height - Math.round" in plot:
    errors.append("EngineeringPlot still uses inclusive far-edge coordinate")
signal_plot = read("src/main/java/dev/redstoneengineering/ui/ldlib/SignalAnalyzerPlotElement.java")
if "if(menu.validWindowCount()>0)" not in signal_plot:
    errors.append("Signal Analyzer invalid-only rolling window draws fabricated mean marker")
signal_hmi = read("src/main/java/dev/redstoneengineering/ui/ldlib/SignalAnalyzerLdUi.java")
for token in ("m.mode()==SignalAnalyzerBlock.TAP", "TAP mode never drives Redstone",
              "m.validWindowCount()>0", "no valid rolling samples",
              "need ≥2 valid samples"):
    if token not in signal_hmi:
        errors.append(f"Signal Analyzer TAP/statistics evidence regression: {token!r}")

# Tracked DataSlots start at zero on the client. Zero measurements and
# absent server captures cannot share the same rendered trace. Each graph
# must wait for a real capture or telemetry count, not just an initialized GUI.
for plot_family, guard in (
    ("Oscilloscope", "menu.sampleCount() <= 0"),
    ("LogicAnalyzer", "menu.sampleCount() <= 0"),
    ("PidTrend", "menu.trendCount() <= 0"),
    ("SignalAnalyzer", "!menu.snapshotReady() || menu.validWindowCount() <= 0"),
):
    source = read(f"src/main/java/dev/redstoneengineering/ui/ldlib/{plot_family}PlotElement.java")
    if guard not in source:
        errors.append(f"{plot_family} plot renders initial DataSlot zeros as fabricated samples")
metrology_menu = read("src/main/java/dev/redstoneengineering/ui/menu/SignalAnalyzerMenu.java")
for token in ("snapshotReady.set(0)", "snapshotReady.set(1)",
              "measurementQuality.set(PortQuality.NOT_READY.ordinal())",
              "public boolean snapshotReady()"):
    if token not in metrology_menu:
        errors.append(f"Signal Analyzer pre-synchronization protection missing: {token!r}")
if "m.snapshotReady() && m.windowCount()>0" not in signal_hmi:
    errors.append("Signal Analyzer HMI reports complete evidence before first synchronized snapshot")

# Startup evidence must never default to VALID (ordinal zero), and the
# world dashboard must not report nonexistent zero-valued observations.
for family, tokens in {
    "CopperCircuitMeter": (
        "snapshotReady.set(0)", "snapshotReady.set(1)",
        "quality.set(PortQuality.NOT_READY.ordinal())", "public boolean snapshotReady()",
    ),
    "RangeSensor": (
        "snapshotReady.set(0)", "snapshotReady.set(1)",
        "public boolean snapshotReady()", "evidenceValid.set(scan.complete() ? 1 : 0)",
    ),
    "OperationsMonitor": (
        "snapshotReady.set(0)", "snapshotReady.set(1)",
        "firstOutKind.set(-1)", "public boolean snapshotReady()",
    ),
}.items():
    server_source = read(f"src/main/java/dev/redstoneengineering/ui/menu/{family}Menu.java")
    for token in tokens:
        if token not in server_source:
            errors.append(f"{family}: unsynced server evidence may appear valid: {token!r}")
for family, tokens in {
    "CopperCircuitMeter": (
        "m.snapshotReady() && m.quality()", "first server snapshot pending",
        "awaiting server diagnostic",
    ),
    "RangeSensor": (
        "distanceReadout(menu)", "menu.evidenceValid()", "CLEAR • no target within",
        "range not synchronized", "route awaiting server",
    ),
    "OperationsMonitor": (
        "m.snapshotReady()", "NO WORKCELLS", "NO BOUND RESOURCES",
        "NOT READY • protection ledger pending", "NOT READY • server first-out pending",
    ),
}.items():
    ui_source = read(f"src/main/java/dev/redstoneengineering/ui/ldlib/{family}LdUi.java")
    for token in tokens:
        if token not in ui_source:
            errors.append(f"{family}: unsynced UI presentation regression: {token!r}")

# Prevent every new zero-initialized device DataSlot from briefly decoding
# PortQuality ordinal 0 as VALID before server sync. Quality is not a
# cosmetic status: it determines whether measured/derived values are trusted.
device_quality_fields = {
    "MagneticSystem": ("quality",),
    "OpticalSystem": ("quality",),
    "PneumaticSystem": ("inputQuality", "outputQuality", "upstreamQuality", "downstreamQuality"),
    "AmethystSystem": ("quality",),
    "DigitalCommunication": ("inputQuality", "outputQuality"),
    "RadioLink": ("quality",),
    "QuartzTiming": ("quality",),
    "ReliabilitySystem": ("quality",),
    "LapisLowPass": ("inputQuality", "outputQuality"),
    "MediaConversion": ("inputQuality", "outputQuality"),
}
for family, field_names in device_quality_fields.items():
    menu_src = read(f"src/main/java/dev/redstoneengineering/ui/menu/{family}Menu.java")
    for field in field_names:
        token = f"{field}.set(PortQuality.NOT_READY.ordinal());"
        if token not in menu_src:
            errors.append(f"{family}: port quality {field} may appear VALID before first server sync")

base_menu = read("src/main/java/dev/redstoneengineering/ui/menu/EngineeringDeviceMenu.java")
for token in ("HEALTH_UNVERIFIED = -1", "operationalHealth.set(HEALTH_UNVERIFIED)",
              "case HEALTH_UNVERIFIED ->", "evidenceState.set(EVIDENCE_NOT_READY)",
              'return "RX / TX • awaiting server route evidence"'):
    if token not in base_menu:
        errors.append(f"Global Health/Port pre-sync misreport regressed: {token!r}")
universal = read("src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java")
for token in ("for (DataSlot quality : qualities) quality.set(PortQuality.NOT_READY.ordinal())",
              "public boolean snapshotReady()", "snapshotReady.set(0)", "snapshotReady.set(1)",
              "if (!snapshotReady()) return PortQuality.NOT_READY"):
    if token not in universal:
        errors.append(f"Universal Field Device client shape masquerades as world evidence: {token!r}")
universal_ui = read("src/main/java/dev/redstoneengineering/ui/ldlib/UniversalFieldDeviceLdUi.java")
if "if (!menu.snapshotReady()) return" not in universal_ui:
    errors.append("Universal Field Device exposes initial zeroed port values before server snapshot")

# Signal processor and series conditioner distinguish real redstone level 0
# from the client's zero-filled DataSlots before the first server sync.
for family in ("SignalProcessor", "SignalConditioner"):
    menu = read(f"src/main/java/dev/redstoneengineering/ui/menu/{family}Menu.java")
    ui = read(f"src/main/java/dev/redstoneengineering/ui/ldlib/{family}LdUi.java")
    for token in ("snapshotReady.set(0)", "snapshotReady.set(1)",
                  "public boolean snapshotReady()"):
        if token not in menu:
            # Signal Processor records readiness from its supported device kind.
            if not (family == "SignalProcessor" and token == "snapshotReady.set(1)"
                    and "snapshotReady.set(kind.get() >= 0 ? 1 : 0)" in menu):
                errors.append(f"{family}: initial server snapshot readiness missing {token!r}")
    if "snapshotReady()" not in ui or "NOT READY" not in ui:
        errors.append(f"{family}: unsynchronized transfer or pulse shown as measured zero")
processor_ui = read("src/main/java/dev/redstoneengineering/ui/ldlib/SignalProcessorLdUi.java")
for token in ("NO • await first sample", "no server response evidence",
              "NOT READY • runtime pending", "NOT READY • parameter pending",
              "m.snapshotReady() && m.initialized()", "Integer.toString(m.runtimeA())"):
    if token not in processor_ui:
        errors.append(f"Signal Processor retained history / response evidence regression: {token!r}")
conditioner_ui = read("src/main/java/dev/redstoneengineering/ui/ldlib/SignalConditionerLdUi.java")
for token in ("snapshotReady() ? menu.input()", "snapshotReady() ? menu.output()",
              "NOT READY • no synchronized boundary result"):
    if token not in conditioner_ui:
        errors.append(f"Signal Conditioner redstone measured-zero distinction regression: {token!r}")

encyclopedia = read("src/main/resources/assets/redstoneengineering/models/item/redstone_encyclopedia.json")
if "minecraft:block/smooth_quartz" in encyclopedia:
    errors.append("RSE Encyclopedia item points at nonexistent vanilla smooth_quartz texture")
if '"pages": "minecraft:block/quartz_block_bottom"' not in encyclopedia:
    errors.append("RSE Encyclopedia pages must use existing vanilla quartz_block_bottom texture")

if errors:
    print("RSE ALL-BLOCK UI ROLLOUT VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE ALL-BLOCK UI ROLLOUT VERIFY: PASS")
print(f" registered blocks reconciled: {len(registered_blocks)} / 122")
print(f" block-facing UI families: {len(block_facing)}")
print(f" legacy EngineeringScreen families: {len(engineering_families)}")
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
print(f" tabbed bidirectional LDLib2 HMI coverage: {len(ldlib_hmis)} / 23")
print(" scaled-screen outer bounds + wrapping tabs and long labels: PASS")
print(" root-percent viewport sizing across 23 LDLib2 HMI families: PASS")
print(" RSE Encyclopedia vanilla item texture reference: PASS")
print(f" pre-sync client HMI shape primers: {len(client_primers)} / {len(client_primers)}")
print(" Universal Pioneer formula preview / real tuning readouts: PASS")
print(" FieldDevice zero-safe parameter readback / server slot parity: PASS")
print(" FieldDevice Pioneer Overview formula + adjustable variable visibility: PASS")
print(" Pioneer Copper / Quartz / Soul / Calorimeter snapshot slot alignment: PASS")
print(" Pioneer solver / measurement role semantics and status evidence: PASS")
print(" Pioneer named sensor profile decoding: PASS")
print(" Reliability/Pneumatic/Optical fail-closed high-risk commissioning: PASS")
print(" Radio collision precedence and no-frame diagnosis: PASS")
print(" Operations protection priority / digital link fail-closed evidence: PASS")
print(" Pioneer physical mechanisms / server slot provenance in five device families: PASS")
print(" Universal all-family Overview live-variable previews and authority evidence: PASS")
print(" Pioneer PID / scope / logic / signal analyzer instrument mechanism provenance: PASS")
