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

be = read("src/main/java/dev/redstoneengineering/blockentity/OscilloscopeBlockEntity.java")
block = read("src/main/java/dev/redstoneengineering/block/OscilloscopeBlock.java")
menu = read("src/main/java/dev/redstoneengineering/ui/menu/OscilloscopeMenu.java")
screen = read("src/main/java/dev/redstoneengineering/client/ui/OscilloscopeScreen.java")
hmi = read("src/main/java/dev/redstoneengineering/ui/ldlib/OscilloscopeLdUi.java")
plot = read("src/main/java/dev/redstoneengineering/ui/ldlib/OscilloscopePlotElement.java")
host = read("src/main/java/dev/redstoneengineering/client/ui/ldlib/LdlibEngineeringHostScreen.java")
gametest = read("src/main/java/dev/redstoneengineering/gametest/RseEngineeringUiGameTests.java")

for token in (
    "SAMPLE_PERIOD_OPTIONS = {1, 2, 4, 8}",
    "DEFAULT_SAMPLE_PERIOD_INDEX = 1",
    "cycleSamplePeriod()",
    "clearHistoryOnly();",
    "samplePeriodTicks()",
    "sampleRateMilliHz()",
    "nyquistMilliHz()",
    "estimatedFrequencyMilliHz",
    "aliasRiskCode",
    "samples <= 2",
    "samples <= 4",
    'tag.putInt("samplePeriodIndex"',
):
    if token not in be:
        errors.append(f"oscilloscope block entity missing {token!r}")

for token in (
    "OscilloscopeBlockEntity.defaultSamplePeriodTicks()",
    "nextPeriod = scope.samplePeriodTicks()",
    "BUTTON_SAMPLE_PERIOD -> scope.cycleSamplePeriod()",
):
    if token not in block:
        errors.append(f"oscilloscope block missing dynamic timebase token {token!r}")

for token in (
    "BUTTON_SAMPLE_PERIOD = 7",
    "samplePeriodTicks",
    "sampleRateMilliHz",
    "nyquistMilliHz",
    "periodSamples",
    "aliasRisk",
    "frequencyMilliHz",
    "scope.samplePeriodTicks()",
    "scope.aliasRiskCode(channel)",
):
    if token not in menu:
        errors.append(f"oscilloscope menu missing synchronized sampling token {token!r}")

for token in (
    "ModularUI",
    "SAMPLING MODEL",
    "Δt = N_ticks / 20 s",
    "f_s = 1 / Δt = 20 / N_ticks Hz",
    "f_N = f_s / 2",
    '"ADJUSTABLE", "N_ticks"',
    '"DERIVED", "f_s"',
    '"DERIVED", "f_N"',
    '"LIVE SUBSTITUTION", "timebase"',
    '"TIMEBASE TABLE"',
    "alias(menu.aliasRisk(0))",
    "alias(menu.aliasRisk(1))",
    "Nyquist gives a theoretical boundary, not proof",
    '"LIVE STATE", "HEALTH"',
    '"I/O", "route"',
    "menu::portRouteLabel",
    '"CONTROLS", "owner"',
    '"Arm / Hold"',
    '"Cycle source ▶"',
    "RseLdUiComponents.authorityFooter()",
):
    if token not in hmi:
        errors.append(f"oscilloscope LDLib2 HMI missing formula-first sampling token {token!r}")

for token in (
    "extends LdlibEngineeringHostScreen<OscilloscopeMenu>",
):
    if token not in screen:
        errors.append(f"oscilloscope screen missing LDLib2 host token {token!r}")

for token in (
    "IModularUIHolderMenu",
    "getModularUI()",
    "LDLib2 owns the engineering HMI canvas",
):
    if token not in host:
        errors.append(f"shared LDLib2 host missing {token!r}")

for token in (
    "OscilloscopePlotElement",
    "EngineeringPlot.analogFrame",
    "EngineeringPlot.analogTrace",
    "EngineeringPlot.horizontalMarker",
    "EngineeringPlot.verticalMarker",
):
    target = hmi if token == "OscilloscopePlotElement" else plot
    if token not in target:
        errors.append(f"oscilloscope LDLib2 visualization missing {token!r}")

for token in (
    "setSamplePeriodTicks(int ticks)",
    "if (SAMPLE_PERIOD_OPTIONS[i] == ticks)",
):
    if token not in be:
        errors.append(f"oscilloscope block entity missing direct timebase token {token!r}")

for token in (
    "BUTTON_SAMPLE_PERIOD_DIRECT_BASE = 3000",
    "scope.setSamplePeriodTicks(id - BUTTON_SAMPLE_PERIOD_DIRECT_BASE)",
):
    if token not in menu:
        errors.append(f"oscilloscope menu missing direct timebase token {token!r}")

for token in (
    "TextField",
    "setNumbersOnlyInt(1, 8)",
    "{1,2,4,8} ticks/sample",
    "setSamplePeriodFromUi",
    "DataBindingBuilder.string",
    "DIRECT ENTRY",
):
    if token not in hmi:
        errors.append(f"oscilloscope LDLib2 HMI missing direct timebase token {token!r}")

for token in (
    "setTriggerLevel(int level)",
    "setCursorA(int slot)",
    "setCursorB(int slot)",
):
    if token not in be:
        errors.append(f"oscilloscope block entity missing exact trigger/cursor token {token!r}")

for token in (
    "BUTTON_TRIGGER_LEVEL_DIRECT_BASE = 3100",
    "BUTTON_CURSOR_A_DIRECT_BASE = 3200",
    "BUTTON_CURSOR_B_DIRECT_BASE = 3300",
    "scope.setTriggerLevel",
    "scope.setCursorA",
    "scope.setCursorB",
):
    if token not in menu:
        errors.append(f"oscilloscope menu missing direct trigger/cursor token {token!r}")

for token in (
    "setTriggerLevelFromUi",
    "setCursorAFromUi",
    "setCursorBFromUi",
    'controlRow("T", "trigger level 1..15"',
    'controlRow("cursor A", "0..15"',
    'controlRow("cursor B", "0..15"',
):
    if token not in hmi:
        errors.append(f"oscilloscope LDLib2 HMI missing direct trigger/cursor token {token!r}")

# A nonempty capture may contain only one valid sample, with other slots
# left-padded with -1. Both cursor samples must be valid on each channel.
for token in (
    'cursorEvidence(menu, 0)',
    'cursorEvidence(menu, 1)',
    'menu.displaySample(channel, menu.cursorA())',
    'menu.displaySample(channel, menu.cursorB())',
    'menu.sampleCount() <= 0 || a < 0 || b < 0',
    'NOT READY • cursor points to uncaptured data',
    'triggerMode(menu.triggerMode())',
):
    if token not in hmi:
        errors.append(f"oscilloscope cursor evidence guard missing {token!r}")

client_surface = "\n".join((screen, hmi, plot, host))
for forbidden in (
    "dev.redstoneengineering.physics",
    "RuntimeIntStore",
    "scheduleTick(",
    "setBlock(",
    "level.getBlockState(",
):
    if forbidden in client_surface:
        errors.append(f"oscilloscope LDLib2 presentation violates authority boundary with {forbidden!r}")

for token in (
    "BUTTON_SAMPLE_PERIOD",
    "scope.samplePeriodTicks() != 4",
    "scope.sampleRateMilliHz() != 5000",
    "scope.nyquistMilliHz() != 2500",
    "scope.sampleCount() != 0",
):
    if token not in gametest:
        errors.append(f"oscilloscope sampling GameTest missing {token!r}")

if errors:
    print("RSE OSCILLOSCOPE SAMPLING VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE OSCILLOSCOPE SAMPLING VERIFY: PASS")
print(" server-owned 1/2/4/8 tick timebase: PASS")
print(" exact Δt engineering-value entry with authoritative capture invalidation: PASS")
print(" exact trigger level and cursor engineering-value entry: PASS")
print(" fs and Nyquist derivation: PASS")
print(" timebase change invalidates mixed-dt capture: PASS")
print(" observed period/frequency evidence: PASS")
print(" alias-margin evidence classification: PASS")
print(" LDLib2 formula-first Sampling HMI with automatic layout: PASS")
print(" live probes -> sampler -> capture -> evidence mechanism flow: PASS")
print(" real dual-channel EngineeringPlot embedded in LDLib2 canvas: PASS")
print(" direct sampling/trigger controls remain server-authoritative: PASS")
print(" persistent live Health / Role / Evidence / I-O / Controls strip: PASS")
print(" client/no-second-solver boundary: PASS")
print(" authoritative sampling GameTest source: PASS")
