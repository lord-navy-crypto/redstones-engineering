#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing Engineering UI file: {rel}")
        return ""
    return path.read_text(errors="ignore")


def require(rel: str, *tokens: str) -> None:
    body = read(rel)
    for token in tokens:
        if body and token not in body:
            errors.append(f"{rel}: missing UI safety token {token!r}")


require("src/main/java/dev/redstoneengineering/client/ui/EngineeringScreen.java",
        "OVERVIEW", "PORTS", "CONFIGURE", "DIAGNOSTICS", "HISTORY",
        'DIAGNOSTICS("Observe"', "ROLE • ", "HEALTH • ", "EVIDENCE • ",
        "ROUTE_CONTROL_Y = 218", "FOOTER_TOP = 245", "fitForWidth", "safeText",
        "isConfigureSection()", "showsPortVisualization", "CONTENT_RIGHT - VALUE_X")
require("src/main/java/dev/redstoneengineering/client/ui/EngineeringIoCompassOverlay.java",
        "engineeringScreen.showsPortVisualization()", '"I/O COMPASS"',
        "boolean rightFits", "boolean leftFits", "if (!rightFits && !leftFits) return;",
        "screen.height - margin - panelHeight", "connectionMask(menu)", "linkEvidenceKnown",
        '"DECLARED"', '"AIR PATH"', '"LOS PATH"', '"LINKED"', '"OPEN"')
require("src/main/java/dev/redstoneengineering/client/ui/EnhancedFieldDeviceScreen.java",
        "safeText(g, engineeringHint()", "safeText(g, diagnosticHint()",
        "fitForWidth(label, 72)", "fitForWidth(value, 72)")
require("src/main/java/dev/redstoneengineering/client/ui/UniversalFieldDeviceScreen.java",
        "directionCycle.visible = isConfigureSection() && active")

single_direction_screens = (
    "SignalConditionerScreen.java",
    "UniversalFieldDeviceScreen.java",
    "RangeSensorScreen.java",
    "SignalProcessorScreen.java",
    "QuartzTimingScreen.java",
    "RadioLinkScreen.java",
    "DigitalCommunicationScreen.java",
    "PneumaticSystemScreen.java",
    "OpticalSystemScreen.java",
    "AmethystSystemScreen.java",
    "MagneticSystemScreen.java",
    "ReliabilitySystemScreen.java",
)
for name in single_direction_screens:
    body = read("src/main/java/dev/redstoneengineering/client/ui/" + name)
    if not body:
        continue
    if "rotateLeft" in body or "rotateRight" in body:
        errors.append(f"{name}: restored dual direction/orientation controls")

for name in (
    "EnhancedFieldDeviceScreen.java", "SignalConditionerScreen.java", "PidControllerScreen.java",
    "OscilloscopeScreen.java", "LogicAnalyzerScreen.java", "SignalAnalyzerScreen.java",
    "UniversalFieldDeviceScreen.java", "RangeSensorScreen.java", "SignalProcessorScreen.java",
    "QuartzTimingScreen.java", "RadioLinkScreen.java", "DigitalCommunicationScreen.java",
    "PneumaticSystemScreen.java", "OpticalSystemScreen.java", "AmethystSystemScreen.java",
    "MagneticSystemScreen.java", "ReliabilitySystemScreen.java", "OperationsMonitorScreen.java",
):
    body = read("src/main/java/dev/redstoneengineering/client/ui/" + name)
    if not body:
        continue
    # Long-form explanatory text should use the shared pixel-clamped helper.
    suspicious = (
        'drawString(font, "This ', 'drawString(font,"This ',
        'drawString(font, "The ', 'drawString(font,"The ',
        'drawString(font, "Observer ', 'drawString(font,"Observer ',
        'drawString(font, "Buttons ', 'drawString(font,"Buttons ',
    )
    for token in suspicious:
        if token in body:
            errors.append(f"{name}: long explanatory text bypasses safeText via {token!r}")

screen = read("src/main/java/dev/redstoneengineering/client/ui/EngineeringScreen.java")
for forbidden in ("sharedRotateCcw", "sharedRotateCw", '"SIGNAL ROUTE"', "drawFaceMatrix(", "ROUTE_CONTROL_Y = 160"):
    if forbidden in screen:
        errors.append(f"EngineeringScreen restored crowded/duplicated layout element {forbidden!r}")

client_dir = root / "src/main/java/dev/redstoneengineering/client/ui"
if client_dir.is_dir():
    forbidden_authority = ("dev.redstoneengineering.physics", "RuntimeIntStore", "scheduleTick(", "setBlock(", "updateNeighborsAt(", "EngineeringAcceptance.evaluate")
    for source in sorted(client_dir.glob("*.java")):
        body = source.read_text(errors="ignore")
        for token in forbidden_authority:
            if token in body:
                errors.append(f"client UI authority violation in {source.name}: contains {token!r}")

require("src/main/java/dev/redstoneengineering/client/ui/EngineeringUiClientRegistration.java",
        "EnhancedFieldDeviceScreen::new", "UniversalFieldDeviceScreen::new", "AmethystSystemScreen::new",
        "ReliabilitySystemScreen::new", "OperationsMonitorScreen::new", "EngineeringIoCompassOverlay::render")
require("src/main/java/dev/redstoneengineering/gametest/RseEngineeringUiGameTests.java",
        "conditionerUiActionsDriveAuthoritativeWorldState", "pidUiActionChangesOnlyBoundedTuningPreset")

if errors:
    print("RSE Engineering UI verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE Engineering UI verification: PASS")
print(" five-page responsibility split: PASS")
print(" full-height page workspace / no duplicate route schematic: PASS")
print(" narrow-screen I/O Compass fail-safe: PASS")
print(" single Direction/orientation control policy: PASS")
print(" inapplicable Configure controls hidden, not grey placeholder clutter: PASS")
print(" shared pixel-clamped long-form text policy: PASS")
print(" server-authoritative ROLE / HEALTH / EVIDENCE HMI: PASS")
print(" client UI authority boundary: PASS")
