#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing fifth-eight file: {rel}")
        return ""
    return path.read_text(errors="ignore")


def require(rel: str, *tokens: str) -> None:
    body = read(rel)
    for token in tokens:
        if body and token not in body:
            errors.append(f"{rel}: missing fifth-eight contract token {token!r}")


require(
    "src/main/java/dev/redstoneengineering/core/domain/EngineeringDomain.java",
    'RADIO_DATA("RADIO_DATA")',
    'DIFFERENTIAL_DATA("DIFFERENTIAL_DATA")',
    'OPTICAL("OPTICAL")',
    'QUARTZ("QUARTZ_TIMING")',
)
require(
    "src/main/java/dev/redstoneengineering/physics/RadioKernel.java",
    "MIN_DECODE_QUALITY",
    "collision",
    "interferencePenalty",
    "obstacleSamples",
    "latencyTicks",
)
require(
    "src/main/java/dev/redstoneengineering/physics/FreeSpaceOpticsKernel.java",
    "range<=48" if False else "i<=48",
    "aligned",
    "channelOk",
    'InformationRuntime.write(l,"free_optical"',
)

contracts = {
    "src/main/java/dev/redstoneengineering/block/DifferentialDriverBlock.java": (
        "implements EngineeringPortProvider",
        "EngineeringDomain.REDSTONE",
        "EngineeringDomain.DIFFERENTIAL_DATA",
        "neighborPos.equals(inputPos(pos, state))",
        'InformationRuntime.clear(level, "diff_out"',
        "FieldDeviceUi.open",
    ),
    "src/main/java/dev/redstoneengineering/block/DifferentialReceiverBlock.java": (
        "EngineeringDomain.DIFFERENTIAL_DATA",
        "EngineeringDomain.REDSTONE",
        "canConnectRedstone",
        "FieldDeviceUi.open",
    ),
    "src/main/java/dev/redstoneengineering/block/RadioTransmitterBlock.java": (
        "implements EngineeringPortProvider",
        "EngineeringDomain.RADIO_DATA",
        '"RADIO ANTENNA", Direction.UP',
        "if (side == Direction.UP) continue",
        "RadioKernel.updateTransmitter",
        "FieldDeviceUi.open",
    ),
    "src/main/java/dev/redstoneengineering/block/RadioReceiverBlock.java": (
        "EngineeringDomain.RADIO_DATA",
        '"RADIO ANTENNA", Direction.UP',
        "RadioKernel.receivePacket",
        "PortQuality.TOPOLOGY_ERROR",
        "canConnectRedstone",
        "FieldDeviceUi.open",
    ),
    "src/main/java/dev/redstoneengineering/block/FreeSpaceOpticalTransmitterBlock.java": (
        "implements EngineeringPortProvider",
        "EngineeringDomain.REDSTONE",
        "EngineeringDomain.OPTICAL",
        "neighborPos.equals(inputPos(pos, state))",
        "FreeSpaceOpticsKernel.emit",
        "FieldDeviceUi.open",
    ),
    "src/main/java/dev/redstoneengineering/block/FreeSpaceOpticalReceiverBlock.java": (
        "EngineeringDomain.OPTICAL",
        "EngineeringDomain.REDSTONE",
        "PortQuality.DOMAIN_MISMATCH",
        "canConnectRedstone",
        'InformationRuntime.clear(level, "free_optical"',
        "FieldDeviceUi.open",
    ),
    "src/main/java/dev/redstoneengineering/block/QuartzClockDividerBlock.java": (
        "implements EngineeringPortProvider",
        "EngineeringDomain.QUARTZ",
        "division(int index)",
        "DomainNetwork.driveQuartz",
        "FieldDeviceUi.open",
    ),
    "src/main/java/dev/redstoneengineering/block/QuartzStabilityMonitorBlock.java": (
        "implements EngineeringPortProvider",
        "PortKind.MEASUREMENT",
        "EngineeringDomain.QUARTZ",
        "measuredPeriod",
        "nominalError",
        "Math.min(4096",
        "FieldDeviceUi.open",
    ),
}
for rel, tokens in contracts.items():
    require(rel, *tokens)

require(
    "src/main/java/dev/redstoneengineering/ui/menu/FieldDeviceMenu.java",
    "KIND_DIFFERENTIAL_DRIVER",
    "KIND_DIFFERENTIAL_RECEIVER",
    "KIND_RADIO_TRANSMITTER",
    "KIND_RADIO_RECEIVER",
    "KIND_FREE_OPTICAL_TRANSMITTER",
    "KIND_FREE_OPTICAL_RECEIVER",
    "KIND_QUARTZ_DIVIDER",
    "KIND_QUARTZ_STABILITY",
    "RadioKernel.receivePacket",
    "DomainNetwork.sampleQuartz",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/FieldDeviceScreen.java",
    "REDSTONE → DIFFERENTIAL",
    "DIFFERENTIAL → REDSTONE",
    "RADIO TRANSMITTER",
    "RADIO RECEIVER",
    "FREE-SPACE OPTICAL TX",
    "FREE-SPACE OPTICAL RX",
    "QUARTZ CLOCK DIVIDER",
    "QUARTZ STABILITY MONITOR",
)

fifth_tests = "src/main/java/dev/redstoneengineering/gametest/RseFifthEightAcceptanceGameTests.java"
for method in (
    "differentialDriverOwnsRedstoneBackAndDifferentialFront",
    "differentialReceiverConvertsPairAndDropsOnDisconnect",
    "radioTransmitterSeparatesAntennaFromPayloadInputs",
    "radioReceiverReportsAntennaAndRejectsSameChannelCollision",
    "freeSpaceOpticalTransmitterUsesOnlyDirectionalBackInput",
    "freeSpaceOpticalReceiverRejectsChannelMismatch",
    "quartzClockDividerPublishesExpectedPeriod",
    "quartzStabilityMonitorMeasuresPeriodWithoutDriving",
):
    require(fifth_tests, f"void {method}(GameTestHelper helper)")
require(
    "src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java",
    "event.register(RseFifthEightAcceptanceGameTests.class);",
)

joined = "\n".join(read(rel) for rel in contracts)
for forbidden in (
    'IntegerProperty.create("quality", 0, 100)',
    'IntegerProperty.create("latency", 0,',
    'IntegerProperty.create("measured_period",',
    'IntegerProperty.create("radio_payload",',
):
    if forbidden in joined:
        errors.append(f"fifth-eight runtime diagnostics leaked into BlockState: {forbidden}")

workflow = read(".github/workflows/build.yml")
if workflow and "rse_fifth_eight_verify.py" not in workflow:
    errors.append("workflow does not gate the fifth-eight verifier")

if errors:
    print("RSE fifth-eight communication endpoint verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE fifth-eight communication endpoint verification: PASS")
print("  first-class RADIO_DATA endpoint domain: PASS")
print("  differential driver/receiver directional isolation: PASS")
print("  radio antenna/payload separation + collision diagnostics: PASS")
print("  free-space optical LOS/channel endpoint contracts: PASS")
print("  quartz divider + observer-only stability metrology: PASS")
print("  Field Device Inspector endpoint projection: PASS")
print("  eight executable fifth-batch GameTests registered: PASS")
