#!/usr/bin/env python3
"""First-round RSE detail/Status GUI upgrade: source/regression checks only.

The original scroll/navigation host must remain byte-identical. Added edit
buttons must use server menu callbacks with legal domains and server readback.
"""
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
LD="src/main/java/dev/redstoneengineering/ui/ldlib/"
ERRORS=[]

def text(p):
    return (ROOT/p).read_text(encoding="utf-8")

def check_file(p, tokens):
    s=text(p)
    for token in tokens:
        if token not in s:
            ERRORS.append(f"{p} missing {token!r}")

shared=LD+"RseLdUiComponents.java"
current=text(shared)
for token in (
    ".mode(ScrollerMode.BOTH)",
    ".verticalScrollDisplay(ScrollDisplay.AUTO)",
    ".horizontalScrollDisplay(ScrollDisplay.AUTO)",
    "scroller.layout(l -> l.flex(1).minHeight(150))",
    "scroller.horizontalScroller.setNormalizedValue(0)",
    "scroller.verticalScroller.setNormalizedValue(0)",
    'setText("DEVICE DETAILS ▾")',
    "details.setDisplay(false)",
    "fontSize(9f)",
    "fontSize(8f)",
):
    if token not in current:
        ERRORS.append(f"shared compact two-axis workspace missing {token!r}")

field=LD+"EnhancedFieldDeviceLdUi.java"
check_file(field,[
    'new String[]{"Overview", "Configure", "Topology", "Status", "Integrity", "Transport"}',
    'RseLdUiComponents.tabbedWorkspace(', 'new Label().setText("STATUS • LIVE SERVER REPORT")',
    'm::snapshotReady', 'menu.applyPrimaryEngineeringValueFromUi(target)',
    'validDirectValue(menu.kind(), target)', 'if (!menu.snapshotReady()',
    'RseLdUiComponents.serverAction("− Step"', 'RseLdUiComponents.serverAction("+ Step"',
    'RseLdUiComponents.serverAction("Minimum"', 'RseLdUiComponents.serverAction("Maximum"',
    'directStep(int kind)', 'adjustableEffect(int kind)', 'PARAMETER EFFECT',
    'SERVER CONFIRMED VALUE', 'new ParameterScaleElement(',
])
universal=LD+"UniversalFieldDeviceLdUi.java"
check_file(universal,[
    'new String[]{"Overview", "Configure", "Pioneer", "Status"}',
    '.mode(ScrollerMode.BOTH)', '.horizontalScrollDisplay(ScrollDisplay.AUTO)',
    '.verticalScrollDisplay(ScrollDisplay.AUTO)',
    'new Label().setText("STATUS • LIVE DEVICE REPORT")',
    'RseLdUiComponents.serverAction("− Legal"', 'RseLdUiComponents.serverAction("+ Legal"',
    'RseLdUiComponents.serverAction("Minimum"', 'RseLdUiComponents.serverAction("Maximum"',
    'stepLegal(UniversalFieldDeviceMenu menu, boolean primary, int step, boolean boundary)',
    'if (!menu.snapshotReady()) return;', 'if (index < 0) return;',
    'applyPrimary(menu, requested)', 'applySecondary(menu, requested)',
    'primaryLegalMarks(kind)', 'secondaryLegalMarks(kind)',
    'primaryEffect(int kind)', 'secondaryEffect(int kind)',
    'SERVER CONFIRMED', 'new ParameterScaleElement(',
])
if "new String[]{\"Overview\", \"Configure\", \"Pioneer\", \"Evidence\"}" in text(universal):
    ERRORS.append("Universal still exposes old verbose Evidence page")
if "new String[]{\"Overview\", \"Configure\", \"Topology\", \"Evidence\"" in text(field):
    ERRORS.append("Field still exposes old verbose Evidence page")
print("RSE OCT28 STATUS/CONTROLS: " + ("FAIL" if ERRORS else "PASS"))
for message in ERRORS: print(" - "+message)
if ERRORS: raise SystemExit(1)
print("  first-round two-axis ScrollerView, auto scrollbars and tab reset: PASS")
print("  compact fixed header, collapsed details and small status typography: PASS")
print("  FieldDevice legal step/boundary controls and server readback: PASS")
print("  Universal primary/secondary legal-step controls: PASS")
print("  concise Status pages and meaning of adjustable coefficients: PASS")
print("  real Minecraft GUI acceptance: NOT CLAIMED")
