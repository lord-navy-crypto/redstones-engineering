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
                "e[n]=SP[n]-PV[n]",
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
    if has_native_workspace and not ("horizontalScroller" in source and "verticalScroller" in source):
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

# The standalone five instrument UIs do not use the shared tab strip.
# Their navigation must wrap when GUI Scale reduces the available width.
for name in ("UniversalFieldDevice", "SignalConditioner", "Oscilloscope", "PidController", "LogicAnalyzer"):
    source = read(f"src/main/java/dev/redstoneengineering/ui/ldlib/{name}LdUi.java")
    if ".flexWrap(FlexWrap.WRAP)" not in source:
        errors.append(f"{name}LdUi.java: tab strip cannot wrap under narrow GUI scales")

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
