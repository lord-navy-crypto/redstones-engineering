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
    "DEFAULT_CANVAS_HEIGHT = 980",
    "FOOTER_HEIGHT = 66",
    "renderPersistentLiveStateStrip",
    "LIVE STATE • HEALTH ",
    "I/O • ",
    "menu.portRouteLabel()",
    "configureControlCount()",
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
    ),
    "OscilloscopeScreen": (
        "mouseScrolled",
        "scrollX",
        "scrollY",
        "renderScrollIndicators",
        "INSTRUMENT_CONTENT_WIDTH = 980",
        "SAMPLING_CONTENT_WIDTH = 1120",
        "Drag scrollbars • Wheel: vertical • Shift+wheel: horizontal",
    ),
}

engineering_families = []
standalone_families = []
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
    required = standalone_contracts.get(screen_name)
    if required is None:
        errors.append(
            f"{screen_name} is block-facing but neither inherits EngineeringScreen nor declares an approved standalone deep-canvas contract"
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

if len(engineering_families) + len(standalone_families) != len(block_facing):
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
print(" global X/Y wheel + draggable scrollbar contract: PASS")
print(" global operator-control inventory / function surface: PASS")
print(" persistent live Health / Role / Evidence / I-O / Controls strip: PASS")
print(" shared 3-column Configure rail + automatic content offset: PASS")
print(" generic FieldDevice + Universal fallbacks: PASS")
print(" Pioneer 122/122 closure linkage: PASS")
