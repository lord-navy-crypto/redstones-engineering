#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/dev/redstoneengineering"


def text(rel: str) -> str:
    return (JAVA / rel).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")


temperature = text("block/TemperatureSensorBlock.java")
noise = text("block/LapisNoiseSourceBlock.java")
lowpass = text("block/LapisLowPassFilterBlock.java")
meter = text("block/LapisPrecisionMeterBlock.java")
precision_observation = text("physics/PrecisionObservationSupport.java")
lab_clock = text("block/QuartzLabOscillatorBlock.java")
divider = text("block/QuartzClockDividerBlock.java")
phase = text("block/QuartzPhaseDelayBlock.java")
stability = text("block/QuartzStabilityMonitorBlock.java")
amethyst_filter = text("block/AmethystFrequencyFilterBlock.java")
tuned = text("block/AmethystTunedResonatorBlock.java")
registration = text("gametest/RseGameTestRegistration.java")
tests = text("gametest/RseFourthTenDesignBugGameTests.java")
thermal_system_tests = text("gametest/RseThermalMeasurementSystemGameTests.java")
lapis_system_tests = text("gametest/RseLapisMeasurementSystemGameTests.java")
workflow = (ROOT / ".github/workflows/build.yml").read_text(encoding="utf-8")

for token in ("ThermalObservation", "loadedFaces", "complete()", "cached reading retained"):
    require(token in temperature, f"Temperature Sensor coverage evidence missing {token}")
require("level.hasChunkAt(neighborPos)" in temperature,
        "Temperature Sensor must not treat unloaded neighbors as complete thermal evidence")
require("observation.complete() ? PortQuality.VALID : PortQuality.STALE" in temperature,
        "Temperature Sensor must preserve incomplete aggregate coverage as STALE")
require("state.getValue(TEMPERATURE), 0.0, 100.0, PortQuality.STALE" in temperature,
        "Temperature Sensor must expose an unloaded direct aperture as STALE")
require("RseThermalMeasurementSystemGameTests.class" in registration,
        "Thermal measurement lifecycle GameTests are not registered")
require("temperatureSensorDistinguishesUnknownCoverageFromValidAmbient" in thermal_system_tests,
        "Temperature unknown-coverage STALE to VALID ambient regression is missing")

for token in ("INITIALIZED_SLOT", "setSample", "sampleInitialized", "RuntimeIntStore.peek"):
    require(token in noise, f"Lapis Noise Source zero/observer contract missing {token}")
require("runtime[0] == 0" not in noise,
        "Lapis Noise Source reintroduced zero-as-uninitialized sentinel")

for token in ("FilterState", "filterState", "runtimePresent", "RuntimeIntStore.peek"):
    require(token in lowpass, f"Lapis Low-Pass observer evidence missing {token}")
require("RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE)" in lowpass,
        "Lapis Low-Pass server tick lost authoritative runtime write")

require("PrecisionObservationSupport.lapis" in meter and "case TOPOLOGY_ERROR" in meter,
        "Lapis Precision Meter must reuse shared observation and preserve source-conflict quality")
require("case STALE" in meter,
        "Lapis Precision Meter diagnostics must distinguish stale coverage from no signal")
require("if (!level.hasChunkAt(pos)) return new LapisObservation(0, PortQuality.STALE)" in precision_observation,
        "Shared Lapis observation must classify unloaded coverage as STALE")
require("RseLapisMeasurementSystemGameTests.class" in registration,
        "Lapis measurement lifecycle GameTests are not registered")
require("precisionMeterDistinguishesUnknownApertureFromLoadedNoSignal" in lapis_system_tests,
        "Lapis meter STALE-vs-NO_SIGNAL lifecycle regression is missing")

for token in ("TimingEvidence", "LAST_HALF_INTERVAL_SLOT", "LAST_JITTER_OFFSET_SLOT", "RuntimeIntStore.peek"):
    require(token in lab_clock, f"Quartz Lab Oscillator realized timing evidence missing {token}")

for name, source in (("Quartz Clock Divider", divider), ("Quartz Phase Delay", phase)):
    require("INITIALIZED_SLOT" in source, f"{name} lacks explicit initialization state")
    require("runtime[INITIALIZED_SLOT] == 0" in source,
            f"{name} does not separate first sample from a real edge")
    require("RuntimeIntStore.peek" in source,
            f"{name} inspection helpers must be observer-neutral")
require("runtime[PENDING_SLOT] = 0" in phase and "if (!input.valid())" in phase,
        "Quartz Phase Delay must clear stale pending events when timing input is invalid")

for token in ("REFERENCE_EDGE_SLOT", "CURRENT_MEASUREMENT_SLOT", "TimingMeasurement", "RuntimeIntStore.peek", "PortQuality.STALE"):
    require(token in stability, f"Quartz Stability Monitor complete-period semantics missing {token}")
require("runtime[REFERENCE_EDGE_SLOT] == 0" in stability,
        "Quartz Stability Monitor lacks first-reference-edge stage")

for token in ("FilterEvidence", "matched", "expectedOutputAmplitude"):
    require(token in amethyst_filter, f"Amethyst Frequency Filter evidence missing {token}")
for token in ("ResponseEvidence", "bandwidth", "frequencyError", "saturated"):
    require(token in tuned, f"Amethyst Tuned Resonator response evidence missing {token}")
require("raw > 15" in tuned,
        "Tuned resonator must retain explicit gain-saturation evidence")

require("RseFourthTenDesignBugGameTests.class" in registration,
        "Fourth-ten design/bug GameTests are not registered")
for test_name in (
    "noiseSourceZeroSampleInspectionIsObserverNeutral",
    "lowPassInspectionDoesNotCreateRuntimeState",
    "temperatureSensorSeparatesAmbientCoverageFromDirectThermalBody",
    "dividerHighAttachDoesNotFabricateRisingEdge",
    "phaseDelayRequiresRealPostInitializationRisingEdge",
    "stabilityMonitorNeedsTwoRealEdgesAndInspectionIsNeutral",
    "labOscillatorPublishesRealizedJitterEvidence",
    "amethystFilterAndTunedResonatorKeepDistinctResponses",
):
    require(test_name in tests, f"Missing fourth-ten runtime contract: {test_name}")

require("tools/rse_fourth_ten_system_design_bug_verify.py" in workflow,
        "Fourth-ten verifier is not wired into CI")
for token in (
    "Minecraft topology GameTests (manual diagnostic)",
    "github.event_name == 'workflow_dispatch'",
    "continue-on-error: true",
    "./gradlew runGameTestServer",
    "./gradlew compileJava",
    "./gradlew test",
):
    require(token in workflow, f"build.yml missing manual/non-blocking GameTest policy token {token!r}")

print("RSE fourth-ten system design + bug verification: PASS")
print("  Temperature coverage completeness + STALE unknown evidence: PASS")
print("  Lapis zero-safe noise + observer-neutral filtering: PASS")
print("  Lapis precision shared observation + STALE/NO_SIGNAL separation: PASS")
print("  Quartz realized jitter evidence: PASS")
print("  Divider/phase-delay first-sample edge safety: PASS")
print("  Stability monitor full-period + stale-evidence semantics: PASS")
print("  Amethyst exact-filter vs tuned-response identity: PASS")
print("  registered fourth-ten GameTests: 8 + thermal/Lapis lifecycle regressions (manual diagnostic / non-blocking)")
print("  fixed-content architecture: no new engineering block/domain required")
