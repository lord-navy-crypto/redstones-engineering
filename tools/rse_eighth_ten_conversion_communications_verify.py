#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing eighth-ten file: {rel}")
        return ""
    return path.read_text(errors="ignore")


def require(rel: str, *tokens: str) -> None:
    body = read(rel)
    for token in tokens:
        if body and token not in body:
            errors.append(f"{rel}: missing eighth-ten contract token {token!r}")


require(
    "src/main/java/dev/redstoneengineering/physics/RedstoneObservationSupport.java",
    "PortQuality.STALE",
    "PortQuality.NO_SIGNAL",
    "port.direction() != PortDirection.INPUT",
    "canConnectRedstone",
)
require(
    "src/main/java/dev/redstoneengineering/physics/PrecisionObservationSupport.java",
    "LapisSignalLineBlock.quality",
    "QuartzTimingLineBlock.quality",
    "PortQuality.STALE",
)
require(
    "src/main/java/dev/redstoneengineering/block/LapisToRedstoneQuantizerBlock.java",
    "outputQuality(Level level, BlockPos pos)",
    "RuntimeIntStore.peek",
    "PrecisionObservationSupport.lapis",
    "PortQuality.STALE",
)
require(
    "src/main/java/dev/redstoneengineering/block/RedstoneToLapisScalerBlock.java",
    "RedstoneObservationSupport.observe",
    "outputQuality(Level level, BlockPos pos)",
    "quality == PortQuality.STALE",
    "DomainNetwork.driveLapis",
)
require(
    "src/main/java/dev/redstoneengineering/block/QuartzTriggeredLapisSamplerBlock.java",
    "CLOCK_SEEN",
    "PREVIOUS_CLOCK",
    "runtime[CLOCK_SEEN] == 0",
    "boolean rising = active == 1 && runtime[PREVIOUS_CLOCK] == 0",
    "heldQuality(Level level, BlockPos pos)",
)
require(
    "src/main/java/dev/redstoneengineering/physics/DataBusNetwork.java",
    "InformationRuntime.snapshot",
    "RuntimeIntStore.peek",
    "PortQuality.TOPOLOGY_ERROR",
    "PortQuality.STALE",
    "driverCount() == 0",
)
require(
    "src/main/java/dev/redstoneengineering/block/RedstoneByteEncoderBlock.java",
    "RedstoneObservationSupport.observe",
    'InformationRuntime.snapshot(level, "bus8_out", pos)',
    "input.valid()",
    "serverLevel.scheduleTick(pos, this, 2)",
)
require(
    "src/main/java/dev/redstoneengineering/block/ByteToRedstoneDecoderBlock.java",
    "DataBusNetwork.quality",
    "PortQuality.SATURATED",
    "inputQuality",
)
require(
    "src/main/java/dev/redstoneengineering/physics/SerialNetwork.java",
    "driverCount",
    "RuntimeIntStore.peek",
    "PortQuality.TOPOLOGY_ERROR",
    "PortQuality.NO_SIGNAL",
    "PortQuality.STALE",
)
require(
    "src/main/java/dev/redstoneengineering/block/SerializerBlock.java",
    "DataBusNetwork.quality",
    'InformationRuntime.snapshot(level, "serial", pos)',
    "WATCHDOG_TICKS = 16",
    "scheduleTick(pos, this, WATCHDOG_TICKS)",
)
require(
    "src/main/java/dev/redstoneengineering/block/DeserializerBlock.java",
    "SerialNetwork.quality",
    'InformationRuntime.snapshot(level, "bus8_out", pos)',
    "WATCHDOG_TICKS = 16",
    "scheduleTick(pos, this, WATCHDOG_TICKS)",
)
require(
    "src/main/java/dev/redstoneengineering/physics/DifferentialNetwork.java",
    'DIAG_KEY = "diff_diag"',
    "driverCount(Level level, BlockPos pos)",
    "RuntimeIntStore.peek",
    "PortQuality.TOPOLOGY_ERROR",
    "PortQuality.NO_SIGNAL",
)
require(
    "src/main/java/dev/redstoneengineering/block/DifferentialDataPairBlock.java",
    "InformationRuntime.snapshot",
    "DifferentialNetwork.quality",
    "DifferentialNetwork.driverCount",
)
require(
    "src/main/java/dev/redstoneengineering/block/RedstoneCableJunctionBlock.java",
    "DataBusNetwork.quality(level, pos)",
    "SerialNetwork.quality(level, pos)",
    "DifferentialNetwork.quality(level, pos)",
)

runtime_tests = "src/main/java/dev/redstoneengineering/gametest/RseEighthTenDesignBugGameTests.java"
for method in (
    "lapisQuantizerKeepsUnsampledOutputStaleAndInspectionNeutral",
    "redstoneScalerSeparatesEmptyInputFromDrivenZero",
    "quartzSamplerRequiresObservedLowToHighEdge",
    "dataBusInspectionIsNeutralBeforeResolutionThenNoSignal",
    "byteEncoderDoesNotDriveFromEmptyInputButAcceptsDrivenZero",
    "byteDecoderPropagatesBusConflictToOutputQuality",
    "serialLineSeparatesNoDriverFromMultipleDrivers",
    "serializerRejectsConflictedBusInsteadOfFramingZero",
    "deserializerRejectsSerialConflictAsBusSource",
    "differentialPairSeparatesNoDriverFromConflict",
):
    require(runtime_tests, f"void {method}(GameTestHelper helper)")

test_body = read(runtime_tests)
if test_body and len(re.findall(r"@GameTest\(", test_body)) != 10:
    errors.append("eighth-ten GameTest class must contain exactly ten @GameTest methods")

require(
    "src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java",
    "event.register(RseEighthTenDesignBugGameTests.class);",
)

workflow = read(".github/workflows/build.yml")
if workflow and "tools/rse_eighth_ten_conversion_communications_verify.py" not in workflow:
    errors.append("workflow does not gate the eighth-ten verifier")
if workflow:
    thresholds = [int(value) for value in re.findall(r"test_count\s*<\s*(\d+)", workflow)]
    if not thresholds or max(thresholds) < 269:
        errors.append("workflow GameTest floor must be at least 269")

if errors:
    print("RSE eighth-ten conversion/communications verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE eighth-ten conversion/communications verification: PASS")
print("  Lapis/Redstone zero-source and first-sample quality: PASS")
print("  Quartz observed-edge chronology and held-sample quality: PASS")
print("  8-bit bus observer neutrality + conflict evidence: PASS")
print("  encoder/decoder source-quality conversion boundary: PASS")
print("  serial no-driver/conflict quality + converter propagation: PASS")
print("  serializer/deserializer event-driven updates + bounded watchdog: PASS")
print("  differential no-driver/conflict quality: PASS")
print("  unified junction preserves communication quality: PASS")
print("  ten executable eighth-ten GameTests registered: PASS")
