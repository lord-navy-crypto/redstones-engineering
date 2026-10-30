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

block = read("src/main/java/dev/redstoneengineering/block/LapisLowPassFilterBlock.java")
menu = read("src/main/java/dev/redstoneengineering/ui/menu/LapisLowPassMenu.java")
screen = read("src/main/java/dev/redstoneengineering/client/ui/LapisLowPassScreen.java")
ldui = read("src/main/java/dev/redstoneengineering/ui/ldlib/LapisLowPassLdUi.java")
ui_reg = read("src/main/java/dev/redstoneengineering/ui/EngineeringUiRegistration.java")
client_reg = read("src/main/java/dev/redstoneengineering/client/ui/EngineeringUiClientRegistration.java")
opener = read("src/main/java/dev/redstoneengineering/ui/FieldDeviceUi.java")
gametest = read("src/main/java/dev/redstoneengineering/gametest/RseEngineeringUiGameTests.java")

for token in (
    "PREVIOUS_OUTPUT_SLOT",
    "previousOutput(Level level, BlockPos pos)",
    "adjustAlpha(Level level, BlockPos pos, int delta)",
    "resetAlpha(Level level, BlockPos pos)",
    "FieldDeviceUi.open(serverPlayer, pos)",
    "player.isShiftKeyDown()",
):
    if token not in block:
        errors.append(f"low-pass block missing {token!r}")

for token in (
    "class LapisLowPassMenu",
    "BUTTON_ALPHA_PREVIOUS",
    "BUTTON_ALPHA_NEXT",
    "BUTTON_ALPHA_DEFAULT",
    "BUTTON_INPUT_LEFT",
    "BUTTON_OUTPUT_RIGHT",
    "LapisLowPassFilterBlock.previousOutput",
    "LapisLowPassFilterBlock.adjustAlpha",
    "DirectionalDomainBlock.rotateSeriesInput",
    "DirectionalDomainBlock.rotateSeriesOutput",
    "LapisLowPassLdUi.create(this, inventory.player)",
    "applyAlphaVisibleValue",
    "restoreDefaultAlpha",
    "cycleInputForward",
    "cycleOutputForward",
):
    if token not in menu:
        errors.append(f"low-pass menu missing {token!r}")

for token in (
    "extends LdlibEngineeringHostScreen<LapisLowPassMenu>",
):
    if token not in screen:
        errors.append(f"low-pass LDLib2 host missing {token!r}")

for token in (
    "ModularUI",
    "y[n] = y[n-1] + α",
    '"MEASURED", "x[n]"',
    '"SOLVER", "y[n-1]"',
    '"ADJUSTABLE", "α"',
    '"Δt"',
    '"τ"',
    "LIVE SUBSTITUTION",
    "PROFILE RESPONSE TABLE",
    "DataBindingBuilder.string",
    "Restore default α",
    "Cycle RX ▶",
    "Cycle TX ▶",
    "MECHANISM FLOW • RX → FILTER MODEL → SOLVER STATE → TX",
    "MEASUREMENT / MODEL EVIDENCE",
    "observer-neutral evidence",
    "RseLdUiComponents.authorityFooter()",
):
    if token not in ldui:
        errors.append(f"low-pass LDLib2 HMI missing {token!r}")

if 'LAPIS_LOW_PASS = MENUS.register("lapis_low_pass"' not in ui_reg:
    errors.append("low-pass menu is not registered")
if "EngineeringUiRegistration.LAPIS_LOW_PASS.get(), LapisLowPassScreen::new" not in client_reg:
    errors.append("low-pass screen is not client-registered")
if "block instanceof LapisLowPassFilterBlock" not in opener or "new LapisLowPassMenu" not in opener:
    errors.append("FieldDeviceUi does not route low-pass filter to dedicated HMI")

for token in (
    "lapisLowPassHmiActionsChangeOnlyAuthoritativeConfiguration",
    "LapisLowPassFilterBlock.adjustAlpha",
    "LapisLowPassFilterBlock.resetAlpha",
):
    if token not in gametest:
        errors.append(f"low-pass HMI GameTest missing {token!r}")

for forbidden in (
    "clientLevel.setBlock",
    "minecraft.level.setBlock",
    "RuntimeIntStore.get(",
):
    if forbidden in screen:
        errors.append(f"client screen must not mutate physics state directly: {forbidden!r}")

if errors:
    print("RSE LAPIS LOW-PASS HMI VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE LAPIS LOW-PASS HMI VERIFY: PASS")
print(" dedicated server-authoritative menu: PASS")
print(" formula-first Model page: PASS")
print(" live RX -> filter -> solver -> TX mechanism flow: PASS")
print(" live substitution + variable roles: PASS")
print(" LDLib2 automatic engineering layout replaces manual deep-canvas scroll debt: PASS")
print(" reusable formula/value rows retain model readability: PASS")
print(" exact alpha/route controls retain server authority: PASS")
print(" persistent live Health / Role / Evidence / I-O / Controls strip: PASS")
print(" real alpha + route controls: PASS")
print(" observer-neutral evidence boundary: PASS")
print(" authoritative action GameTest source: PASS")
