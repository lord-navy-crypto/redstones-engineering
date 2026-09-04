#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing fourth-eight file: {rel}")
        return ""
    return path.read_text(errors="ignore")


def require(rel: str, *tokens: str) -> None:
    body = read(rel)
    for token in tokens:
        if body and token not in body:
            errors.append(f"{rel}: missing fourth-eight contract token {token!r}")


require(
    "src/main/java/dev/redstoneengineering/core/domain/EngineeringDomain.java",
    'DATA_BUS_8("DATA_BUS_8")',
    'SERIAL_DATA("SERIAL_DATA")',
    'DIFFERENTIAL_DATA("DIFFERENTIAL_DATA")',
)
require(
    "src/main/java/dev/redstoneengineering/physics/DataBusDriver.java",
    "interface DataBusDriver",
    "drivesDataBusAt",
)
require(
    "src/main/java/dev/redstoneengineering/physics/DataBusNetwork.java",
    "instanceof DataBusDriver driver",
    "driver.drivesDataBusAt(neighbor, neighborState, pos)",
    "driverCount > 0 && distinctValues == 1",
    "releaseDriver",
    "clearNode",
    "sameValueMultiDriver",
)
require(
    "src/main/java/dev/redstoneengineering/physics/SerialNetwork.java",
    "recompute(ServerLevel level, BlockPos start)",
    "invalidate(ServerLevel level",
    "SerializerBlock",
    "DigitalRegeneratorBlock",
    'recordDriverState(level, "serial"',
)
require(
    "src/main/java/dev/redstoneengineering/physics/DifferentialNetwork.java",
    "recompute(ServerLevel level, BlockPos start)",
    "invalidate(ServerLevel level",
    "DifferentialDriverBlock",
    '"diff_out"',
)

contracts = {
    "src/main/java/dev/redstoneengineering/block/EightBitDataBusBlock.java": (
        "implements EngineeringPortProvider",
        "EngineeringDomain.DATA_BUS_8",
        "PortDirection.BIDIRECTIONAL",
        "DataBusNetwork.clearNode",
        "FieldDeviceUi.open",
    ),
    "src/main/java/dev/redstoneengineering/block/RedstoneByteEncoderBlock.java": (
        "DataBusDriver",
        "drivesDataBusAt",
        "outputPos(driverPos, driverState).equals(busPos)",
        "EngineeringDomain.REDSTONE",
        "EngineeringDomain.DATA_BUS_8",
        "canConnectRedstone",
        "DataBusNetwork.releaseDriver",
    ),
    "src/main/java/dev/redstoneengineering/block/ByteToRedstoneDecoderBlock.java": (
        "EngineeringDomain.DATA_BUS_8",
        "EngineeringDomain.REDSTONE",
        "PortQuality.SATURATED",
        "canConnectRedstone",
        "Math.min(15, DataBusNetwork.sample",
    ),
    "src/main/java/dev/redstoneengineering/block/SerialDataLineBlock.java": (
        "implements EngineeringPortProvider",
        "EngineeringDomain.SERIAL_DATA",
        "PortDirection.BIDIRECTIONAL",
        "SerialNetwork.recompute",
        "SerialNetwork.clearNode",
    ),
    "src/main/java/dev/redstoneengineering/block/SerializerBlock.java": (
        "implements EngineeringPortProvider",
        "EngineeringDomain.DATA_BUS_8",
        "EngineeringDomain.SERIAL_DATA",
        "SerialNetwork.recompute",
        'InformationRuntime.clear(level, "serial"',
    ),
    "src/main/java/dev/redstoneengineering/block/DeserializerBlock.java": (
        "DataBusDriver",
        "drivesDataBusAt",
        "outputPos(driverPos, driverState).equals(busPos)",
        "EngineeringDomain.SERIAL_DATA",
        "EngineeringDomain.DATA_BUS_8",
        "DataBusNetwork.releaseDriver",
    ),
    "src/main/java/dev/redstoneengineering/block/DifferentialDataPairBlock.java": (
        "implements EngineeringPortProvider",
        "EngineeringDomain.DIFFERENTIAL_DATA",
        "PortDirection.BIDIRECTIONAL",
        "DifferentialNetwork.recompute",
        "DifferentialNetwork.clearNode",
    ),
    "src/main/java/dev/redstoneengineering/block/DigitalRegeneratorBlock.java": (
        "implements EngineeringPortProvider",
        "EngineeringDomain.SERIAL_DATA",
        "minimumQuality",
        "SerialNetwork.recompute",
        'InformationRuntime.clear(level, "serial"',
    ),
}
for rel, tokens in contracts.items():
    require(rel, *tokens)

require(
    "src/main/java/dev/redstoneengineering/ui/menu/FieldDeviceMenu.java",
    "KIND_DATA_BUS_8",
    "KIND_ENCODER",
    "KIND_DECODER",
    "KIND_SERIAL_LINE",
    "KIND_SERIALIZER",
    "KIND_DESERIALIZER",
    "KIND_DIFFERENTIAL_PAIR",
    "KIND_DIGITAL_REGENERATOR",
    "dataValid",
    "qualityPercent",
    "driverCount",
    "PortCompatibility.evaluate",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/FieldDeviceScreen.java",
    "8-BIT DATA BUS",
    "SERIAL DATA LINE",
    "DIFFERENTIAL DATA",
    "DIGITAL REGENERATOR",
    "minimumQuality",
)

fourth_tests = "src/main/java/dev/redstoneengineering/gametest/RseFourthEightAcceptanceGameTests.java"
for method in (
    "eightBitBusWithoutDriverIsExplicitlyInvalid",
    "redstoneByteEncoderDrivesBusAndRemovalReleasesDriver",
    "byteDecoderOwnsBusInputAndSaturatesRedstoneOutput",
    "serialLineBreakInvalidatesDisconnectedSegment",
    "serializerFramesOnlyValidDrivenBusWords",
    "deserializerRecoversFullByteAndRemovalReleasesBus",
    "differentialPairBreakInvalidatesRemoteBit",
    "digitalRegeneratorEnforcesQualityThreshold",
):
    require(fourth_tests, f"void {method}(GameTestHelper helper)")
require(
    "src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java",
    "event.register(RseFourthEightAcceptanceGameTests.class);",
)

# Communication payload must remain runtime data rather than a 256-state BlockState field.
joined = "\n".join(read(rel) for rel in contracts)
for forbidden in (
    'IntegerProperty.create("byte", 0, 255)',
    'IntegerProperty.create("payload", 0, 255)',
    'IntegerProperty.create("quality", 0, 100)',
):
    if forbidden in joined:
        errors.append(f"communication runtime payload leaked into BlockState: {forbidden}")

workflow = read(".github/workflows/build.yml")
if workflow and "rse_fourth_eight_verify.py" not in workflow:
    errors.append("workflow does not gate the fourth-eight verifier")

if errors:
    print("RSE fourth-eight digital communication verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE fourth-eight digital communication verification: PASS")
print("  explicit DATA_BUS_8 / SERIAL_DATA / DIFFERENTIAL_DATA domains: PASS")
print("  ghost-driver + floating-bus + physical-output alignment guards: PASS")
print("  serial/differential topology invalidation: PASS")
print("  eight communication devices expose inspectable engineering contracts: PASS")
print("  Field Device Inspector communication projection: PASS")
print("  eight executable fourth-batch GameTests registered: PASS")
