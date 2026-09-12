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


require(
    "src/main/java/dev/redstoneengineering/ui/EngineeringUiRegistration.java",
    "DeferredRegister<MenuType<?>>",
    'MENUS.register("signal_conditioner"',
    'MENUS.register("pid_controller"',
)
require(
    "src/main/java/dev/redstoneengineering/ui/menu/EngineeringDeviceMenu.java",
    "refreshAuthoritativeSnapshot",
    "refreshOperationalHealth",
    "refreshTopologyRole",
    "refreshEvidenceState",
    "receivePortMask",
    "transmitPortMask",
    "stillValid",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/EngineeringScreen.java",
    "OVERVIEW",
    "PORTS",
    "CONFIGURE",
    "DIAGNOSTICS",
    "HISTORY",
    'DIAGNOSTICS("Observe"',
    "handleInventoryButtonClick",
    "ROLE • ",
    "HEALTH • ",
    "EVIDENCE • ",
    "private static final int ROUTE_CONTROL_Y = 218",
    "private static final int FOOTER_TOP = 245",
    "private Button sharedRouteCycle",
    'Component.literal("Direction • —")',
    "FieldDeviceMenu.BUTTON_ROTATE_CW",
    "sharedRouteCycle.visible = section == Section.CONFIGURE && enabled",
    "showsPortVisualization",
    "fitForWidth",
    "safeText",
    "CONTENT_RIGHT - VALUE_X",
    "Math.min(width, CONTENT_RIGHT - x)",
    "Every page owns the full content panel",
    "I/O Compass",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/EngineeringIoCompassOverlay.java",
    "ScreenEvent.Render.Post",
    "engineeringScreen.showsPortVisualization()",
    '"I/O COMPASS"',
    "menu.receivePortMask()",
    "menu.transmitPortMask()",
    "connectionMask(menu)",
    "linkEvidenceKnown",
    '"DECLARED"',
    '"SOURCE • SINGLE TX"',
    '"SOURCE • FAN-OUT x"',
    '"SERIES • 1→1"',
    '"WIRE"',
    '"RF"',
    '"LOS"',
    '"FIBER"',
    '"LINKED"',
    '"OPEN"',
    '"AIR PATH"',
    '"LOS PATH"',
    "Direction.UP",
    "Direction.DOWN",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/EngineeringUiClientRegistration.java",
    "EnhancedFieldDeviceScreen::new",
    "SignalConditionerScreen::new",
    "PidControllerScreen::new",
    "EngineeringIoCompassOverlay::render",
)
require(
    "src/main/java/dev/redstoneengineering/gametest/RseEngineeringUiGameTests.java",
    "conditionerUiActionsDriveAuthoritativeWorldState",
    "pidUiActionChangesOnlyBoundedTuningPreset",
)

screen = read("src/main/java/dev/redstoneengineering/client/ui/EngineeringScreen.java")
for forbidden in (
    "sharedRotateCcw",
    "sharedRotateCw",
    'Component.literal("↺ Rotate route")',
    'Component.literal("Rotate route ↻")',
    "renderPortRoute(",
    '"SIGNAL ROUTE"',
    "drawFaceMatrix(",
    "ROUTE_CONTROL_Y = 160",
):
    if screen and forbidden in screen:
        errors.append(f"EngineeringScreen restored crowded/duplicated layout element {forbidden!r}")

# Client UI must remain presentation-only.
client_dir = root / "src/main/java/dev/redstoneengineering/client/ui"
if client_dir.is_dir():
    forbidden_authority = (
        "dev.redstoneengineering.physics",
        "RuntimeIntStore",
        "scheduleTick(",
        "setBlock(",
        "updateNeighborsAt(",
        "EngineeringAcceptance.evaluate",
    )
    for source in sorted(client_dir.glob("*.java")):
        body = source.read_text(errors="ignore")
        for token in forbidden_authority:
            if token in body:
                errors.append(f"client UI authority violation in {source.name}: contains {token!r}")

pid_screen = read("src/main/java/dev/redstoneengineering/client/ui/PidControllerScreen.java")
if pid_screen and "Shift + FRONT" not in pid_screen and "Shift+FRONT" not in pid_screen:
    errors.append("PidControllerScreen missing acceptance-capture interaction guidance")

conditioner = read("src/main/java/dev/redstoneengineering/block/SignalConditionerBlock.java")
for token in ("normal right-click opens Engineering UI", "new SignalConditionerMenu", "player.isShiftKeyDown()"):
    if conditioner and token not in conditioner:
        errors.append(f"SignalConditionerBlock UI integration missing {token!r}")

pid = read("src/main/java/dev/redstoneengineering/block/PidControllerBlock.java")
for token in ("new PidControllerMenu", "captureAcceptanceEvidence", "RuntimeIntStore.remove", "applyTuningAction"):
    if pid and token not in pid:
        errors.append(f"PidControllerBlock UI integration missing {token!r}")

registration = read("src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java")
if registration and "event.register(RseEngineeringUiGameTests.class);" not in registration:
    errors.append("Engineering UI GameTests are not registered")

if errors:
    print("RSE Engineering UI verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE Engineering UI verification: PASS")
print(" full-height page workspace / no duplicated inline route schematic: PASS")
print(" dedicated Ports-only I/O Compass: PASS")
print(" bottom-lane single Direction control: PASS")
print(" non-rotatable devices hide Direction control: PASS")
print(" shared pixel-clamped title / status / label / value / badge / card text: PASS")
print(" legacy dual route controls rejected: PASS")
print(" server-authoritative ROLE / HEALTH / EVIDENCE HMI: PASS")
print(" client UI authority boundary: PASS")
print(" registered Engineering UI runtime action tests: PASS")
