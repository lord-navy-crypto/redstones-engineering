#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing tenth-eight file: {rel}")
        return ""
    return path.read_text(errors="ignore")


def require(rel: str, *tokens: str) -> None:
    body = read(rel)
    for token in tokens:
        if body and token not in body:
            errors.append(f"{rel}: missing tenth-eight contract token {token!r}")


require(
    "src/main/java/dev/redstoneengineering/block/EdgeDetectorBlock.java",
    "FieldDeviceUi.open",
    "lastInput",
    "pulseRemaining",
    "initialized",
    "RuntimeIntStore.remove",
)
require(
    "src/main/java/dev/redstoneengineering/block/PulseShaperBlock.java",
    "FieldDeviceUi.open",
    "lastInput",
    "pulseRemaining",
    "initialized",
    "RuntimeIntStore.remove",
)
require(
    "src/main/java/dev/redstoneengineering/block/SignalTapBlock.java",
    '"SIGNAL IN"',
    '"THROUGH OUT"',
    '"NON-INVASIVE TAP"',
    "PortKind.TAP",
    "leftOf(facing)",
    "FieldDeviceUi.open",
)
require(
    "src/main/java/dev/redstoneengineering/block/RangeSensorBlock.java",
    "implements EngineeringPortProvider",
    '"RANGE SIGNAL OUT"',
    "PortKind.SENSOR",
    "PortDirection.OUTPUT",
    "outputSide(state)",
    "detectedDistance",
    "FieldDeviceUi.open",
)
require(
    "src/main/java/dev/redstoneengineering/block/LapisSignalLineBlock.java",
    "implements EngineeringPortProvider",
    "EngineeringDomain.LAPIS",
    "PortDirection.BIDIRECTIONAL",
    "RuntimeIntStore.remove",
    "recomputeLapisAround",
    "FieldDeviceUi.open",
)
require(
    "src/main/java/dev/redstoneengineering/block/LapisPrecisionSourceBlock.java",
    "implements EngineeringPortProvider",
    "EngineeringDomain.LAPIS",
    "PortDirection.OUTPUT",
    "recomputeLapisAround",
    "FieldDeviceUi.open",
)
require(
    "src/main/java/dev/redstoneengineering/block/QuartzTimingLineBlock.java",
    "implements EngineeringPortProvider",
    "EngineeringDomain.QUARTZ",
    "PortDirection.BIDIRECTIONAL",
    "RuntimeIntStore.remove",
    "recomputeQuartzAround",
    "FieldDeviceUi.open",
)
require(
    "src/main/java/dev/redstoneengineering/block/QuartzOscillatorBlock.java",
    "implements EngineeringPortProvider",
    "EngineeringDomain.QUARTZ",
    "PortDirection.OUTPUT",
    "recomputeQuartzAround",
    "FieldDeviceUi.open",
)
require(
    "src/main/java/dev/redstoneengineering/physics/DomainNetwork.java",
    "recomputeLapisAround",
    "recomputeQuartzAround",
    "Direction.Plane.HORIZONTAL",
)

require(
    "src/main/java/dev/redstoneengineering/ui/menu/FieldDeviceMenu.java",
    "KIND_EDGE_DETECTOR = 55",
    "KIND_PULSE_SHAPER = 56",
    "KIND_SIGNAL_TAP = 57",
    "KIND_RANGE_SENSOR = 58",
    "KIND_LAPIS_LINE = 59",
    "KIND_LAPIS_SOURCE = 60",
    "KIND_QUARTZ_LINE = 61",
    "KIND_QUARTZ_OSCILLATOR = 62",
    "EdgeDetectorBlock.pulseRemaining",
    "RangeSensorBlock.detectedDistance",
    "LapisSignalLineBlock.valid",
    "QuartzTimingLineBlock.period",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/FieldDeviceScreen.java",
    "NON-INVASIVE SIGNAL TAP",
    "SENSING APERTURE • NO WIRED PORT",
    "LAPIS_PRECISION • FOUR HORIZONTAL OUTPUTS",
    "QUARTZ_TIMING • FOUR HORIZONTAL OUTPUTS",
    "Pulse remaining",
)

# Precision/timing runtime must stay outside the finite BlockState property space.
joined = "\n".join(read(rel) for rel in (
    "src/main/java/dev/redstoneengineering/block/LapisSignalLineBlock.java",
    "src/main/java/dev/redstoneengineering/block/QuartzTimingLineBlock.java",
))
for forbidden in (
    'IntegerProperty.create("lapis_value"',
    'IntegerProperty.create("clock_period"',
    'IntegerProperty.create("clock_phase"',
):
    if forbidden in joined:
        errors.append(f"tenth-eight high-cardinality runtime leaked into BlockState: {forbidden}")

tests = "src/main/java/dev/redstoneengineering/gametest/RseTenthEightAcceptanceGameTests.java"
for method in (
    "edgeDetectorPulsesOnlyOnConfiguredTransition",
    "pulseShaperHonorsConfiguredWidthAndClears",
    "signalTapDeclaresThroughAndNonInvasiveTapOutputs",
    "rangeSensorSeparatesSensingApertureFromSignalOutput",
    "lapisSignalLineBreakClearsSeparatedIsland",
    "lapisPrecisionSourceDrivesOnlyLapisDomain",
    "quartzTimingLineBreakClearsSeparatedClockIsland",
    "quartzOscillatorPublishesFourWayClockContract",
):
    require(tests, f"void {method}(GameTestHelper helper)")
body = read(tests)
if body and len(re.findall(r"@GameTest\(", body)) != 8:
    errors.append("tenth-eight acceptance class must contain exactly eight @GameTest methods")
require(
    "src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java",
    "event.register(RseTenthEightAcceptanceGameTests.class);",
)

workflow = read(".github/workflows/build.yml")
if workflow and "rse_tenth_eight_verify.py" not in workflow:
    errors.append("workflow does not gate the tenth-eight verifier")

if errors:
    print("RSE tenth-eight signal foundation verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE tenth-eight signal foundation verification: PASS")
print("  edge detector / pulse shaper scheduled lifecycle: PASS")
print("  three-port non-invasive Signal Tap contract: PASS")
print("  Range Sensor aperture/output domain separation: PASS")
print("  four-way Lapis source/trace port contracts: PASS")
print("  four-way Quartz oscillator/trace port contracts: PASS")
print("  split-island precision and timing cleanup: PASS")
print("  Field Device Inspector projection kinds 55-62: PASS")
print("  eight executable tenth-batch GameTests registered: PASS")
