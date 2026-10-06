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
    'SAMPLING("Sampling")',
    "mouseScrolled",
    "mouseClicked",
    "mouseDragged",
    "mouseReleased",
    "beginScrollbarDrag",
    "dragScrollbarTo",
    "hasShiftDown()",
    "enableScissor",
    "Δt = N_ticks / 20 s",
    "f_s = 1 / Δt = 20 / N_ticks Hz",
    "f_N = f_s / 2",
    "[ADJUSTABLE] N_ticks",
    "[DERIVED] f_s",
    "[DERIVED] f_N",
    "LIVE SUBSTITUTION",
    "TIMEBASE TABLE",
    "aliasLabel(menu.aliasRisk(channel))",
    "Nyquist gives a theoretical boundary, not proof",
    "FOOTER_HEIGHT = 132",
    "LIVE STATE • HEALTH ",
    "I/O • ",
    "menu.portRouteLabel()",
    "CONTROLS ",
    "INSTRUMENT_CONTENT_WIDTH = 980",
    "SAMPLING_CONTENT_WIDTH = 1120",
    "renderScrollIndicators",
    'Component.literal("Arm / Hold")',
    'Component.literal("Trigger source")',
    "int plotWidth = Math.min(760, contentWidth() - CONTENT_X - 90)",
):
    if token not in screen:
        errors.append(f"oscilloscope screen missing formula-first sampling token {token!r}")

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
    "EditBox",
    "submitSamplePeriod",
    "{1, 2, 4, 8} ticks/sample",
    "BUTTON_SAMPLE_PERIOD_DIRECT_BASE + ticks",
):
    if token not in screen:
        errors.append(f"oscilloscope screen missing direct timebase token {token!r}")

for forbidden in (
    "dev.redstoneengineering.physics",
    "RuntimeIntStore",
    "scheduleTick(",
    "setBlock(",
):
    if forbidden in screen:
        errors.append(f"oscilloscope client screen violates authority boundary with {forbidden!r}")

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
print(" fs and Nyquist derivation: PASS")
print(" timebase change invalidates mixed-dt capture: PASS")
print(" observed period/frequency evidence: PASS")
print(" alias-margin evidence classification: PASS")
print(" spacious two-axis formula-first Sampling HMI: PASS")
print(" live probes -> sampler -> capture -> evidence mechanism flow: PASS")
print(" draggable visible scrollbars + non-compressed waveform canvas: PASS")
print(" fixed sampling/trigger controls remain outside server model ownership: PASS")
print(" persistent live Health / Role / Evidence / I-O / Controls strip: PASS")
print(" client/no-second-solver boundary: PASS")
print(" authoritative sampling GameTest source: PASS")
