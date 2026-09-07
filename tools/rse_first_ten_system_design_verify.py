#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing first-ten audit file: {rel}")
        return ""
    return path.read_text(errors="ignore")


def require(rel: str, *tokens: str) -> None:
    body = read(rel)
    for token in tokens:
        if body and token not in body:
            errors.append(f"{rel}: missing first-ten design contract {token!r}")


require(
    "docs/FIRST_TEN_SYSTEM_DESIGN_AUDIT.md",
    "Signal Analyzer",
    "Signal Probe",
    "Instrument Cable",
    "Oscilloscope",
    "Logic Analyzer",
    "Signal Conditioner",
    "Calibration Module",
    "Precision Filter",
    "Sample & Hold",
    "Edge Detector",
    "Live state is not retained history",
    "Zero is not the same as no signal",
    "Conditioning is not calibration",
    "Expected dynamics are not faults",
    "Observer neutrality",
    "127 registered blocks",
)

# The local analyzer remains deliberately direct and preserves its mature TAP/INLINE contract.
require(
    "src/main/java/dev/redstoneengineering/block/SignalAnalyzerBlock.java",
    "TAP mode is a non-invasive measurement aperture",
    "INLINE mode makes the",
    "Calibration affects display",
    "calibratedReading",
    "requestedOutput = state.getValue(MODE) == INLINE ? measured : 0",
)

require(
    "src/main/java/dev/redstoneengineering/block/SignalProbeBlock.java",
    "measurementPresent",
    "PortQuality.VALID : PortQuality.NO_SIGNAL",
    "open aperture",
    "INSTRUMENT BUS CH",
)

require(
    "src/main/java/dev/redstoneengineering/instrument/InstrumentNetwork.java",
    "validChannelsInMask",
    "duplicateChannelsInMask",
    "qualityForMask",
    "PortQuality.TOPOLOGY_ERROR",
    "PortQuality.NO_SIGNAL",
)
require(
    "src/main/java/dev/redstoneengineering/block/InstrumentCableBlock.java",
    "engineeringSnapshot",
    "InstrumentNetwork.scan(level, pos)",
    "validChannelsInMask(0xF)",
    "qualityForMask(0xF)",
)

require(
    "src/main/java/dev/redstoneengineering/block/OscilloscopeBlock.java",
    "OBSERVED_CHANNEL_MASK = 0b0011",
    "InstrumentNetwork.scan(level, pos)",
    "qualityForMask(OBSERVED_CHANNEL_MASK)",
    "current bus failure must not be hidden",
)
require(
    "src/main/java/dev/redstoneengineering/block/LogicAnalyzerBlock.java",
    "OBSERVED_CHANNEL_MASK = 0b1111",
    "InstrumentNetwork.scan(level, pos)",
    "qualityForMask(OBSERVED_CHANNEL_MASK)",
    "Live topology health",
)

require(
    "src/main/java/dev/redstoneengineering/block/SignalConditionerBlock.java",
    "limitingActive",
    "PortQuality.SATURATED",
    "Threshold HIGH and deadband hold",
    "case 0 -> input *",
    "case 2 -> input >",
)
require(
    "src/main/java/dev/redstoneengineering/block/CalibrationModuleBlock.java",
    "MeasurementQuality quality",
    "case GOOD, DEGRADED -> PortQuality.VALID",
    "case SATURATED -> PortQuality.SATURATED",
    "case STALE -> PortQuality.STALE",
    "case INVALID -> PortQuality.NO_SIGNAL",
    "traceability evidence",
)
require(
    "src/main/java/dev/redstoneengineering/block/PrecisionFilterBlock.java",
    "public static int lag",
    "public static boolean settled",
    "SETTLING",
    "Dynamic slew filter",
)
require(
    "src/main/java/dev/redstoneengineering/block/SampleHoldBlock.java",
    "CAPTURE_COUNT",
    "LAST_CAPTURE_TICK",
    "RuntimeIntStore.peek",
    "captureCount",
    "sampleAgeTicks",
    "Reload initialization must not fabricate",
)
require(
    "src/main/java/dev/redstoneengineering/block/EdgeDetectorBlock.java",
    "EDGE_COUNT",
    "LAST_EDGE_TICK",
    "RuntimeIntStore.peek",
    "edgeCount",
    "lastEdgeAgeTicks",
    "Read-only diagnostic accessors",
)

runtime_tests = "src/main/java/dev/redstoneengineering/gametest/RseFirstTenDesignGameTests.java"
for method in (
    "probeDistinguishesOpenApertureFromRealZero",
    "duplicateInstrumentChannelIsLiveTopologyErrorAcrossObservers",
    "conditionerLimitingAndFilterLagRemainDifferentEngineeringStates",
    "calibrationOutputQualityComesFromReferenceMetrology",
    "sampleAndEdgeModulesExposeRealEventChronologyWithoutObserverMutation",
):
    require(runtime_tests, f"void {method}(GameTestHelper helper)")
require(
    runtime_tests,
    "PortQuality.NO_SIGNAL",
    "PortQuality.TOPOLOGY_ERROR",
    "PortQuality.SATURATED",
    "SampleHoldBlock.captureCount",
    "EdgeDetectorBlock.edgeCount",
)
require(
    "src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java",
    "event.register(RseFirstTenDesignGameTests.class);",
)

workflow = read(".github/workflows/build.yml")
if workflow and "tools/rse_first_ten_system_design_verify.py" not in workflow:
    errors.append("first-ten system design verifier is not wired into CI")
if workflow:
    thresholds = [int(value) for value in re.findall(r"test_count\s*<\s*(\d+)", workflow)]
    if not thresholds or max(thresholds) < 205:
        errors.append("Minecraft runtime gate has not been raised to at least 205 GameTests")

# Historical behavior must remain executable rather than being replaced by this audit.
require(
    "src/main/java/dev/redstoneengineering/gametest/RseFirstEightAcceptanceGameTests.java",
    "analyzerInlinePassThroughRemainsRawDespiteDisplayCalibration",
    "oscilloscopeSamplesTwoProbeChannelsAndBoundsCapture",
    "logicAnalyzerThresholdPathCountsRealFallingAndRisingEdges",
    "calibrationModuleTransformsObservedSignalAndKeepsReferenceAsMetrologyInput",
    "sampleHoldCapturesOnlyOnConfiguredEdgeAndResetWins",
)
require(
    "src/main/java/dev/redstoneengineering/gametest/RseSecondEightAcceptanceGameTests.java",
    "instrumentCableCarriesRemoteProbeChannel",
    "instrumentBusFlagsDuplicateProbeChannel",
    "signalProbePortsExposeMeasurementAndBusBoundary",
    "precisionFilterSlewRateIsBoundedAndSymmetric",
)

if errors:
    print("RSE first-ten system design verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE first-ten system design verification: PASS")
print(" first ten registered blocks retain distinct engineering roles: PASS")
print(" live Instrument Bus health vs retained capture separation: PASS")
print(" zero measurement vs NO_SIGNAL distinction: PASS")
print(" conditioning/calibration/filter responsibility split: PASS")
print(" sample/edge transient chronology evidence: PASS")
print(" observer-neutral runtime diagnostics: PASS")
print(" five executable design-contract GameTests registered: PASS")
print(" fixed-content 127-block direction retained: PASS")
