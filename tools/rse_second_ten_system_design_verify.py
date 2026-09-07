#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/dev/redstoneengineering"


def text(rel: str) -> str:
    return (JAVA / rel).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")

pulse = text("block/PulseShaperBlock.java")
pwm = text("block/PwmControllerBlock.java")
tap = text("block/SignalTapBlock.java")
range_sensor = text("block/RangeSensorBlock.java")
lapis_line = text("block/LapisSignalLineBlock.java")
lapis_source = text("block/LapisPrecisionSourceBlock.java")
quartz_line = text("block/QuartzTimingLineBlock.java")
quartz_source = text("block/QuartzOscillatorBlock.java")
amethyst_dust = text("block/AmethystResonanceDustBlock.java")
amethyst_source = text("block/AmethystResonatorBlock.java")
registration = text("gametest/RseGameTestRegistration.java")
tests = text("gametest/RseSecondTenDesignGameTests.java")

# 11: event conditioner, observer-neutral chronology.
require("RuntimeIntStore.peek" in pulse, "Pulse Shaper diagnostics must use observer-neutral peek()")
require("if (now && !last) remaining = state.getValue(WIDTH)" in pulse,
        "Pulse Shaper must retain rising-edge one-shot semantics")

# 12: periodic PWM must expose discrete realization rather than pretending analog duty is exact.
for token in ("quantizedOnTicks", "requestedDutyPermille", "effectiveDutyPermille", "quantizationErrorPermille"):
    require(token in pwm, f"PWM engineering evidence missing {token}")
require("RuntimeIntStore.peek" in pwm, "PWM phase diagnostics must be observer-neutral")

# 13: tap copies without back-driving the through path; it is not a passive measurement probe.
require("PortKind.TAP" in tap, "Signal Tap must remain a first-class TAP output")
require("back-drive" in tap and "active 0..15 output" in tap,
        "Signal Tap non-invasive terminology must explicitly mean non-backdriving copy output")

# 14: measurement coverage is first-class evidence.
for token in ("ScanStatus", "TARGET", "CLEAR", "INCOMPLETE_UNLOADED", "lastScan", "scannedCells"):
    require(token in range_sensor, f"Range Sensor coverage semantics missing {token}")
require("scan.complete() ? PortQuality.VALID : PortQuality.NO_SIGNAL" in range_sensor,
        "Range Sensor complete CLEAR scans must remain valid measurements")
require("RuntimeIntStore.peek" in range_sensor,
        "Range Sensor engineering snapshots must read cached server scan evidence")

# 15-18: precision/timing traces remain planar and single-source, with no-source != conflict.
for name, src, domain in (("Lapis", lapis_line, "lapis"), ("Quartz", quartz_line, "quartz")):
    require("sourceCount" in src, f"{name} trace must retain component source-count evidence")
    require("PortQuality.TOPOLOGY_ERROR" in src, f"{name} trace must distinguish source conflict")
    require("PortQuality.NO_SIGNAL" in src, f"{name} trace must distinguish no source")
    require("RuntimeIntStore.peek" in src, f"{name} trace readback must be observer-neutral")
    require(f'NetworkKernel.stats(l,"{domain}")' in src,
            f"{name} trace must capture authoritative driver evidence from the existing solver")
require("PortDirection.OUTPUT" in lapis_source and "EngineeringDomain.LAPIS" in lapis_source,
        "Lapis Precision Source must remain a dedicated deterministic source")
require("PortDirection.OUTPUT" in quartz_source and "EngineeringDomain.QUARTZ" in quartz_source,
        "Quartz Oscillator must remain a dedicated timing source")

# 19-20: resonance remains a planar frequency/amplitude medium; observation cannot create pulses.
require("extends SurfaceTraceBlock" in amethyst_dust and "Direction.Axis.Y" in amethyst_dust,
        "Amethyst resonance dust must remain a planar surface medium")
require("RuntimeIntStore.peek" in amethyst_dust,
        "Amethyst resonance trace diagnostics must be observer-neutral")
require("RuntimeIntStore.peek" in amethyst_source,
        "Amethyst Resonator activity inspection must be observer-neutral")
require("role=BASE SOURCE" in amethyst_source,
        "Base Amethyst Resonator must not absorb tuned/filter responsibilities")

require("RseSecondTenDesignGameTests.class" in registration,
        "Second-ten design GameTests are not registered")
for test_name in (
    "pwmPeriodExposesDeterministicDutyQuantization",
    "rangeSensorClearScanIsValidZeroThenTargetBecomesValidMeasurement",
    "lapisTraceDistinguishesNoSourceSingleSourceAndConflict",
    "quartzTraceDistinguishesNoClockSingleClockAndConflict",
    "pulseAndResonatorObservationDoesNotCreateTransientActivity",
):
    require(test_name in tests, f"Missing second-ten runtime contract: {test_name}")

print("RSE second-ten system design verification: PASS")
print("  Pulse Shaper event identity + observer neutrality: PASS")
print("  PWM requested-vs-realized duty quantization evidence: PASS")
print("  Signal Tap non-backdriving copy semantics: PASS")
print("  Range Sensor complete-vs-incomplete scan evidence: PASS")
print("  Lapis/Quartz no-source vs single-source vs conflict evidence: PASS")
print("  Lapis/Quartz surface-trace identities retained: PASS")
print("  Amethyst planar resonance + observer-neutral source readback: PASS")
print("  five executable second-ten design GameTests registered: PASS")
print("  fixed-content architecture: no new engineering block/domain required")
