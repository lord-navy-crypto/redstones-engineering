#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    file = ROOT / path
    if not file.exists():
        fail(f"missing required file: {path}")
    return file.read_text(encoding="utf-8")


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        fail(f"{label}: missing required contract: {needle}")


def forbid(text: str, needle: str, label: str) -> None:
    if needle in text:
        fail(f"{label}: legacy single-axis assumption remains: {needle}")


def fail(message: str) -> None:
    print(f"[series-endpoint-cleanup] FAIL: {message}", file=sys.stderr)
    raise SystemExit(1)


helper = read("src/main/java/dev/redstoneengineering/ui/menu/SeriesRouteActions.java")
require(helper, "receivers != 1 || transmitters != 1", "SeriesRouteActions")
require(helper, "DirectionalSignalBlock.seriesInputSide(state)", "SeriesRouteActions")
require(helper, "DirectionalSignalBlock.seriesOutputSide(state)", "SeriesRouteActions")
require(helper, "DirectionalDomainBlock.seriesInputSide(state)", "SeriesRouteActions")
require(helper, "DirectionalDomainBlock.seriesOutputSide(state)", "SeriesRouteActions")
require(helper, "rotateSeriesInput", "SeriesRouteActions")
require(helper, "rotateSeriesOutput", "SeriesRouteActions")

screen = read("src/main/java/dev/redstoneengineering/client/ui/EngineeringScreen.java")
require(screen, "import dev.redstoneengineering.ui.menu.SeriesRouteActions;", "EngineeringScreen")
if screen.count("SeriesRouteActions.supports(menu)") < 3:
    fail("EngineeringScreen: formal-port endpoint routing must drive RX, TX, and endpoint visibility")
require(screen, "SeriesRouteActions.BUTTON_INPUT_RIGHT", "EngineeringScreen")
require(screen, "SeriesRouteActions.BUTTON_OUTPUT_RIGHT", "EngineeringScreen")

conditioner_menu = read("src/main/java/dev/redstoneengineering/ui/menu/SignalConditionerMenu.java")
require(conditioner_menu, "private final DataSlot inputFacing", "SignalConditionerMenu")
require(conditioner_menu, "DirectionalSignalBlock.seriesInputSide(state)", "SignalConditionerMenu")
require(conditioner_menu, "SeriesRouteActions.handle(this, player, id)", "SignalConditionerMenu")
forbid(conditioner_menu, "return outputDirection().getOpposite()", "SignalConditionerMenu")

conditioner_block = read("src/main/java/dev/redstoneengineering/block/SignalConditionerBlock.java")
require(conditioner_block, "DirectionalSignalBlock.seriesInputSide(state)", "SignalConditionerBlock")
require(conditioner_block, "DirectionalSignalBlock.rotateWholeRoute", "SignalConditionerBlock")
forbid(conditioner_block, "return state.getValue(FACING).getOpposite()", "SignalConditionerBlock")
forbid(conditioner_block, "one authoritative series axis", "SignalConditionerBlock")

processors = read("src/main/java/dev/redstoneengineering/ui/menu/SignalProcessorMenu.java")
require(processors, "DirectionalSignalBlock.seriesInputSide(state)", "SignalProcessorMenu")
require(processors, "SeriesRouteActions.handle(this, player, id)", "SignalProcessorMenu")
require(processors, "DirectionalSignalBlock.rotateWholeRoute", "SignalProcessorMenu")
forbid(processors, "out.getOpposite()", "SignalProcessorMenu")
forbid(processors, "outputDirection().getOpposite()", "SignalProcessorMenu")

quartz = read("src/main/java/dev/redstoneengineering/ui/menu/QuartzTimingMenu.java")
require(quartz, "DirectionalDomainBlock.seriesInputSide(state)", "QuartzTimingMenu")
require(quartz, "SeriesRouteActions.handle(this, player, id)", "QuartzTimingMenu")
require(quartz, "DirectionalDomainBlock.rotateWholeRoute", "QuartzTimingMenu")
require(quartz, "DirectionalDomainBlock.rotateSeriesInput", "QuartzTimingMenu observer boundary")
forbid(quartz, "logicalOut.getOpposite()", "QuartzTimingMenu")
forbid(quartz, "blockPos.relative(out.getOpposite())", "QuartzTimingMenu")

optical = read("src/main/java/dev/redstoneengineering/ui/menu/OpticalSystemMenu.java")
require(optical, "SeriesRouteActions.handle(this, player, id)", "OpticalSystemMenu")
require(optical, "DirectionalSignalBlock.seriesInputSide(state)", "OpticalSystemMenu")
require(optical, "DirectionalDomainBlock.rotateWholeRoute", "OpticalSystemMenu")
forbid(optical, "engineeringSnapshot(level, blockPos, state, out.getOpposite())", "OpticalSystemMenu")

amethyst = read("src/main/java/dev/redstoneengineering/ui/menu/AmethystSystemMenu.java")
require(amethyst, "SeriesRouteActions.handle(this, player, id)", "AmethystSystemMenu")
require(amethyst, "DirectionalDomainBlock.rotateWholeRoute", "AmethystSystemMenu")

reliability = read("src/main/java/dev/redstoneengineering/ui/menu/ReliabilitySystemMenu.java")
require(reliability, "SeriesRouteActions.handle(this, player, id)", "ReliabilitySystemMenu")
if reliability.count("DirectionalSignalBlock.seriesInputSide(state)") < 2:
    fail("ReliabilitySystemMenu: Watchdog and position-sensor readbacks must use true RX")
require(reliability, "DirectionalSignalBlock.rotateWholeRoute", "ReliabilitySystemMenu")
forbid(reliability, "snapshotQuality(watchdog, state, out.getOpposite())", "ReliabilitySystemMenu")
forbid(reliability, "snapshotValue(sensor, state, out.getOpposite())", "ReliabilitySystemMenu")

print("[series-endpoint-cleanup] PASS")
print("  formal 1RX/1TX controls are shared and topology-gated")
print("  Conditioner and signal processors use true INPUT_FACING")
print("  Quartz observer boundary is preserved")
print("  optical/amethyst/reliability specialized menus are endpoint-aware")
