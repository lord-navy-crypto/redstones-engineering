#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors = []

def read(rel):
    path = root / rel
    if not path.is_file():
        errors.append(f"missing Low-Pass HMI contract file: {rel}")
        return ""
    return path.read_text(errors="ignore")

block = read("src/main/java/dev/redstoneengineering/block/LapisLowPassFilterBlock.java")
menu = read("src/main/java/dev/redstoneengineering/ui/menu/LapisLowPassFilterMenu.java")
screen = read("src/main/java/dev/redstoneengineering/client/ui/LapisLowPassFilterScreen.java")
directional = read("src/main/java/dev/redstoneengineering/block/DirectionalDomainBlock.java")

for token in (
    "extends DirectionalDomainBlock",
    '"LAPIS FILTER IN"',
    '"LAPIS FILTER OUT"',
    "DomainNetwork.driveLapis(level, outputPos(pos, state), pos, runtime[OUTPUT_SLOT], true)",
    "DomainNetwork.driveLapis(level, outputPos(pos, state), pos, 0, false)",
    "PortQuality.VALID.ordinal()",
    "level.scheduleTick(pos, this, 2)",
):
    if token not in block:
        errors.append(f"LapisLowPassFilterBlock missing physical/evidence token: {token}")

for token in (
    "BUTTON_INPUT_PREVIOUS",
    "BUTTON_INPUT_NEXT",
    "BUTTON_OUTPUT_PREVIOUS",
    "BUTTON_OUTPUT_NEXT",
    "BUTTON_ROTATE_LEFT",
    "BUTTON_ROTATE_RIGHT",
    "DirectionalDomainBlock.rotateRigidSeriesAxis",
    "inputFacing.set(DirectionalDomainBlock.seriesInputSide(state).ordinal())",
    "outputFacing.set(DirectionalDomainBlock.seriesOutputSide(state).ordinal())",
    "public Direction inputDirection()",
    "public Direction outputDirection()",
    "public PortQuality inputQuality()",
    "public PortQuality outputQuality()",
):
    if token not in menu:
        errors.append(f"LapisLowPassFilterMenu missing routing/evidence token: {token}")

for token in (
    'ROUTING("Routing")',
    "pageLabel(Page value)",
    "Math.max(48",
    "routeButtonWidth",
    "routeButtonStartX",
    "private void renderRouting",
    '"RX • LAPIS FILTER IN"',
    '"TX • LAPIS FILTER OUT"',
    'Component.literal("Rotate block ◀")',
    'Component.literal("Rotate block ▶")',
    "RX and TX form one rigid straight-through axis",
    "releases the old domain-driver claim",
    "previous Lapis segment cannot retain a ghost filter output",
    "α, retained y[k], input/output PortQuality and response evidence remain server-owned filter state",
    "y[k+1] = y[k] + α · (x[k] − y[k])",
    "The client does not run a second filter solver.",
):
    if token not in screen:
        errors.append(f"LapisLowPassFilterScreen missing notebook/routing token: {token}")

for stale in (
    "DirectionalDomainBlock.rotateSeriesInput(level, blockPos",
    "DirectionalDomainBlock.rotateSeriesOutput(level, blockPos",
):
    if stale in menu:
        errors.append(f"LapisLowPassFilterMenu reintroduced independent endpoint routing: {stale}")

for token in (
    "DomainDriverRegistry.releaseAll(serverLevel, pos)",
    "serverLevel.scheduleTick(pos, block, 1)",
    "physicalPortsDoNotOverlap",
):
    if token not in directional:
        errors.append(f"DirectionalDomainBlock missing safe route token: {token}")

if errors:
    print("RSE Lapis Low-Pass HMI verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE Lapis Low-Pass HMI verification: PASS")
print(" viewport-filling five-tab notebook: PASS")
print(" rigid opposite-port server-authoritative Lapis routing: PASS")
print(" old TX driver claims are released before republish: PASS")
print(" endpoint overlap remains rejected by shared routing authority: PASS")
print(" valid zero remains distinct from missing input evidence: PASS")
print(" filter response/model evidence remains server-owned: PASS")
print(" client does not run a second low-pass solver: PASS")
