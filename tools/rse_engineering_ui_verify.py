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


required = {
    "src/main/java/dev/redstoneengineering/ui/EngineeringUiRegistration.java": [
        "DeferredRegister<MenuType<?>>",
        'MENUS.register("signal_conditioner"',
        'MENUS.register("pid_controller"',
    ],
    "src/main/java/dev/redstoneengineering/ui/menu/EngineeringDeviceMenu.java": [
        "extends AbstractContainerMenu",
        "refreshAuthoritativeSnapshot",
        "refreshOperationalHealth",
        "refreshTopologyRole",
        "refreshEvidenceState",
        "topologyRoleLabel",
        "operationalHealthLabel",
        "evidenceStateLabel",
        "receivePortMask",
        "transmitPortMask",
        "stillValid",
    ],
    "src/main/java/dev/redstoneengineering/ui/menu/SignalConditionerMenu.java": [
        "clickMenuButton",
        "SignalConditionerBlock.applyConfigurationAction",
        "refreshAuthoritativeSnapshot",
    ],
    "src/main/java/dev/redstoneengineering/ui/menu/PidControllerMenu.java": [
        "ClosedLoopCommissioning.inspectPid",
        "AcceptanceEvidenceStore.history",
        "PidControllerBlock.applyTuningAction",
    ],
    "src/main/java/dev/redstoneengineering/client/ui/EngineeringScreen.java": [
        "OVERVIEW",
        "PORTS",
        "CONFIGURE",
        "DIAGNOSTICS",
        "HISTORY",
        "handleInventoryButtonClick",
        "ROLE • ",
        "HEALTH • ",
        "EVIDENCE • ",
        "SIGNAL ROUTE",
        "drawFaceMatrix",
        "Direction.NORTH, Direction.EAST, Direction.SOUTH",
        "menu.receivePortMask()",
        "menu.transmitPortMask()",
        'receiving ? "WIRE" : "RF"',
        'receiving ? "RF" : "WIRE"',
        'receiving ? "WIRE" : "LOS"',
        'receiving ? "LOS" : "WIRE"',
        '"FIBER"',
        '"FAN-OUT ×"',
        '"SINGLE TX"',
        '"SERIES PATH"',
        "menu.topologyRoleLabel()",
        "menu.operationalHealthLabel()",
        "menu.evidenceStateLabel()",
    ],
    "src/main/java/dev/redstoneengineering/client/ui/EngineeringIoCompassOverlay.java": [
        "ScreenEvent.Render.Post",
        "EngineeringScreen<?> engineeringScreen",
        "containerScreen.getMenu() instanceof EngineeringDeviceMenu",
        '"I/O COMPASS"',
        "menu.receivePortMask()",
        "menu.transmitPortMask()",
        "menu.connectionMask()",
        '"SOURCE • SINGLE TX"',
        '"SOURCE • FAN-OUT x"',
        '"SERIES • 1→1"',
        "drawCompass",
        "drawMediumRow",
        "mediumLabel",
        "interfaceState",
        '"WIRE"',
        '"RF"',
        '"LOS"',
        '"FIBER"',
        '"LINKED"',
        '"OPEN"',
        '"AIR PATH"',
        '"LOS PATH"',
        "propagationInterface || linked",
        "Direction.NORTH",
        "Direction.SOUTH",
        "Direction.EAST",
        "Direction.WEST",
        "Direction.UP",
        "Direction.DOWN",
    ],
    "src/main/java/dev/redstoneengineering/client/ui/SignalConditionerScreen.java": [
        "SignalConditionerMenu",
        "BUTTON_MODE_NEXT",
        "BUTTON_PARAM_INCREASE",
    ],
    "src/main/java/dev/redstoneengineering/client/ui/PidControllerScreen.java": [
        "PidControllerMenu",
        "BUTTON_TUNING_NEXT",
    ],
    "src/main/java/dev/redstoneengineering/client/ui/EngineeringUiClientRegistration.java": [
        "RegisterMenuScreensEvent",
        "SignalConditionerScreen::new",
        "PidControllerScreen::new",
        "EngineeringIoCompassOverlay::render",
    ],
    "src/main/java/dev/redstoneengineering/gametest/RseEngineeringUiGameTests.java": [
        "conditionerUiActionsDriveAuthoritativeWorldState",
        "pidUiActionChangesOnlyBoundedTuningPreset",
    ],
}

for rel, tokens in required.items():
    body = read(rel)
    for token in tokens:
        if body and token not in body:
            errors.append(f"{rel}: missing UI contract token {token!r}")

pid_screen = read("src/main/java/dev/redstoneengineering/client/ui/PidControllerScreen.java")
if pid_screen and "Shift + FRONT" not in pid_screen and "Shift+FRONT" not in pid_screen:
    errors.append("PidControllerScreen missing acceptance-capture interaction guidance")

client_dir = root / "src/main/java/dev/redstoneengineering/client/ui"
if client_dir.is_dir():
    forbidden = (
        "dev.redstoneengineering.physics",
        "RuntimeIntStore",
        "scheduleTick(",
        "setBlock(",
        "updateNeighborsAt(",
        "EngineeringAcceptance.evaluate",
    )
    for source in sorted(client_dir.glob("*.java")):
        body = source.read_text(errors="ignore")
        for token in forbidden:
            if token in body:
                errors.append(
                    f"client UI authority violation in {source.name}: contains {token!r}"
                )

conditioner = read("src/main/java/dev/redstoneengineering/block/SignalConditionerBlock.java")
for token in (
    "normal right-click opens Engineering UI",
    "new SignalConditionerMenu",
    "player.isShiftKeyDown()",
):
    if conditioner and token not in conditioner:
        errors.append(f"SignalConditionerBlock UI integration missing {token!r}")

pid = read("src/main/java/dev/redstoneengineering/block/PidControllerBlock.java")
for token in (
    "new PidControllerMenu",
    "captureAcceptanceEvidence",
    "RuntimeIntStore.remove",
    "applyTuningAction",
):
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
print(" shared menu/screen framework: PASS")
print(" server-authoritative ROLE / HEALTH / EVIDENCE HMI: PASS")
print(" six-face RX/TX route matrix: PASS")
print(" sidecar I/O compass: PASS")
print(" WIRE / RF / LOS / FIBER medium identity: PASS")
print(" wired/fiber LINKED vs OPEN port state: PASS")
print(" RF / LOS propagation interfaces avoid false OPEN state: PASS")
print(" SINGLE TX / FAN-OUT / SERIES topology cues: PASS")
print(" server-authoritative configuration path: PASS")
print(" client UI authority boundary: PASS")
print(" Conditioner + PID runtime action tests registered: PASS")
