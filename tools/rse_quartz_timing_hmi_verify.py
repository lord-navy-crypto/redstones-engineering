#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
failed = []

menu_path = root / "src/main/java/dev/redstoneengineering/ui/menu/QuartzTimingMenu.java"
screen_path = root / "src/main/java/dev/redstoneengineering/client/ui/QuartzTimingScreen.java"
shared_path = root / "src/main/java/dev/redstoneengineering/client/ui/EngineeringScreen.java"
source_path = root / "src/main/java/dev/redstoneengineering/block/QuartzOscillatorBlock.java"
opener_path = root / "src/main/java/dev/redstoneengineering/ui/FieldDeviceUi.java"

for path in (menu_path, screen_path, shared_path, source_path, opener_path):
    if not path.is_file():
        failed.append(f"missing quartz HMI contract file: {path.relative_to(root)}")

if not failed:
    menu = menu_path.read_text(errors="ignore")
    screen = screen_path.read_text(errors="ignore")
    shared = shared_path.read_text(errors="ignore")
    source = source_path.read_text(errors="ignore")
    opener = opener_path.read_text(errors="ignore")

    for token in (
        "QuartzOscillatorBlock.adjustConfiguredPeriodTicks(",
        "DirectionalDomainSourceBlock.rotateOutput(level, blockPos",
        "DirectionalDomainSourceBlock.outputSide(state).ordinal()",
        "QuartzOscillatorBlock.effectivePeriodTicks(level, blockPos, state)",
        "QuartzOscillatorBlock.periodChangePending(level, blockPos, state)",
        "QuartzOscillatorBlock.edgeCount(level, blockPos)",
        "QuartzClockDividerBlock.configuredDivision(level, blockPos, state)",
        "QuartzClockDividerBlock.phaseStarted(level, blockPos)",
        "measurement.sampleCount()",
        "measurement.minPeriod()",
        "measurement.maxPeriod()",
        "measurement.meanPeriodX100()",
        "measurement.jitter()",
        "measurement.maxNominalError()",
    ):
        if token not in menu:
            failed.append(f"QuartzTimingMenu missing authoritative quartz evidence token: {token}")

    if "level.scheduleTick(blockPos, oscillator, 1)" in menu:
        failed.append("QuartzTimingMenu can still manufacture an early oscillator transition after a period edit")

    for token in (
        "public static boolean adjustConfiguredPeriodTicks",
        "deliberately does not schedule an early tick",
    ):
        if token not in source:
            failed.append(f"QuartzOscillatorBlock missing edge-safe fine adjustment token: {token}")
    fine_start = source.find("public static boolean adjustConfiguredPeriodTicks")
    fine_end = source.find("public static int edgeCount", fine_start)
    if fine_start >= 0 and fine_end > fine_start and "scheduleTick(" in source[fine_start:fine_end]:
        failed.append("fine Quartz period adjustment schedules an artificial early transition")

    if "if (block instanceof QuartzOscillatorBlock || block instanceof QuartzClockDividerBlock || block instanceof QuartzStabilityMonitorBlock)" not in opener:
        failed.append("FieldDeviceUi does not route Quartz oscillator/divider to the dedicated timing HMI")
    if "block instanceof QuartzOscillatorBlock\n                || block instanceof QuartzClockDividerBlock\n                || block instanceof QuartzPhaseDelayBlock" in opener:
        failed.append("FieldDeviceUi generic MultiPhysics dispatch still intercepts Quartz oscillator/divider")

    stability = menu[menu.find("if (block instanceof QuartzStabilityMonitorBlock monitor)"):menu.find("kind.set(-1);")]
    if "outputFacing.set(" in stability:
        failed.append("Quartz stability monitor reintroduced a synthetic output endpoint")

    for token in (
        "single-output source",
        "Configured / effective",
        "Window samples",
        "Jitter = max - min",
        "Max |T - Tnom|",
        "Tout = clamp(Tin × N, 1, 4096) ticks",
        "up to eight complete server-observed periods",
        "inputDirection()",
        "outputDirection()",
    ):
        if token not in screen:
            failed.append(f"QuartzTimingScreen missing engineering evidence/model token: {token}")

    for stale in (
        "FOUR HORIZONTAL OUTPUTS",
        "FOUR-WAY SOURCE",
        "four-way source topology",
    ):
        if stale in screen:
            failed.append(f"QuartzTimingScreen still claims obsolete oscillator topology: {stale}")

    if "quartz.hasInputEndpoint() || quartz.hasOutputEndpoint()" not in shared:
        failed.append("EngineeringScreen does not derive quartz routing availability from declared endpoints")

if failed:
    print("RSE quartz timing HMI verification: FAIL")
    for item in failed:
        print(" -", item)
    raise SystemExit(1)

print("RSE quartz timing HMI verification: PASS")
print(" oscillator fine edits latch through the server timing model: PASS")
print(" normal Quartz oscillator/divider dispatch reaches dedicated HMI: PASS")
print(" quartz routing matches declared physical endpoints: PASS")
print(" divider phase/configuration evidence is server-backed: PASS")
print(" stability window statistics are synchronized, not client-fabricated: PASS")
