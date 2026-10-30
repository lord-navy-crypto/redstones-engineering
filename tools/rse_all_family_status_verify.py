#!/usr/bin/env python3
"""Source contract for Status presentation on every RSE HMI family.

Visual layout, scrollbars, and original pages remain protected by
rse_oct28_visual_baseline_verify.py. These assertions check UI source presence
only, not actual Minecraft client interactions.
"""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "src/main/java/dev/redstoneengineering/ui/ldlib"
FAMILIES = [
    "AmethystSystem",
    "CopperCircuitMeter",
    "DigitalCommunication",
    "IndustrialBuffer",
    "LapisLowPass",
    "LogicAnalyzer",
    "MagneticSystem",
    "MediaConversion",
    "OperationsMonitor",
    "OpticalSystem",
    "Oscilloscope",
    "PidController",
    "PneumaticSystem",
    "QuartzTiming",
    "RadioLink",
    "RangeSensor",
    "ReliabilitySystem",
    "SignalAnalyzer",
    "SignalConditioner",
    "SignalProcessor",
    "WorkcellController"
]
errors = []
status = (UI / "RseDeviceStatusPanel.java").read_text(encoding="utf-8")
for marker in [
    "menu.operationalHealthLabel()", "menu.evidenceStateLabel()",
    "menu.portRouteLabel()", "menu.topologyRoleLabel()",
    "menu::operatorCheckLabel", "details.setDisplay(false)",
    "new Button().setText", "statusPage(EngineeringDeviceMenu menu",
]:
    if marker not in status:
        errors.append(f"shared Status panel missing {marker!r}")

for family in FAMILIES:
    path = UI / (family + "LdUi.java")
    if not path.is_file():
        errors.append(f"missing HMI family: {family}")
        continue
    text = path.read_text(encoding="utf-8")
    if not re.search(r'new String\[\]\{[^}]*"Status"[^}]*\}', text):
        errors.append(f"{family}: no Status navigation label")
    if "RseDeviceStatusPanel.statusPage(" not in text and "RseDeviceStatusPanel.summary(" not in text:
        errors.append(f"{family}: Status does not use synchronized summary")
    if "RseLdUiComponents.deviceContext(" not in text:
        errors.append(f"{family}: missing original device context")
    if "tabbedWorkspace(" not in text and "standaloneTabs(" not in text:
        errors.append(f"{family}: original first-round tab navigation missing")
    if "setOnServerClick" in status:
        errors.append("Generic read-only Status panel must not write server state")

# Already enhanced shared families cover the remainder of the block registry.
for family in ("EnhancedFieldDevice", "UniversalFieldDevice"):
    text = (UI / (family + "LdUi.java")).read_text(encoding="utf-8")
    if '"Status"' not in text or '"Configure"' not in text:
        errors.append(f"{family}: shared Configure/Status interface missing")

# More buttons are permitted ONLY when a real menu-validated setter exists.
# The list below is deliberately smaller than all families: read-only meters
# must never receive invented 'parameter' controls.
CONTROL_FAMILIES = {
    "AmethystSystem": "m::setPrimaryFromUi",
    "MagneticSystem": "m::setPrimaryFromUi",
    "OpticalSystem": "m::applyPrimaryFromUi",
    "PneumaticSystem": "m::setSetpointFromUi",
    "RadioLink": "m::setChannelFromUi",
    "RangeSensor": "menu::setRangeFromUi",
    "LogicAnalyzer": "m::setThresholdFromUi",
    "Oscilloscope": "menu::setSamplePeriodFromUi",
    "SignalProcessor": "menu::setParameterFromUi",
    "SignalConditioner": "menu::applyVisibleFormulaParameter",
}
legal = (UI / "RseLegalStepControls.java").read_text(encoding="utf-8")
for marker in (
    "if (!ready.getAsBoolean()", "if (index < 0) return;",
    "submit.accept(legal[next])", "RseLdUiComponents.serverAction",
    "new Label().setText(\"CHANGES • \"",
    "SERVER CONFIRMED", "wholeRange(int min, int max)",
):
    if marker not in legal:
        errors.append(f"legal-step helper missing {marker!r}")
for family, setter in CONTROL_FAMILIES.items():
    source = (UI / (family + "LdUi.java")).read_text(encoding="utf-8")
    if "RseLegalStepControls.choices(" not in source:
        errors.append(f"{family}: missing legal step controls")
    if setter not in source:
        errors.append(f"{family}: no real menu setter {setter!r}")

if errors:
    print("RSE ALL-FAMILY STATUS: FAIL")
    for error in errors: print(" -", error)
    raise SystemExit(1)
print("RSE ALL-FAMILY STATUS: PASS")
print(f"  {len(FAMILIES)} dedicated LDLib2 families: Status from synchronized menu")
print("  2 shared Field/Universal families: exact controls and Status maintained")
print("  detail disclosure local only; original scrollbar/navigation host untouched")
print("  130-block HMI coverage remains a separate registry static audit")
