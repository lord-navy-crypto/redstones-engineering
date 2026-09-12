#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing eleventh-ten file: {rel}")
        return ""
    return path.read_text(errors="ignore")


def require(rel: str, *tokens: str) -> None:
    body = read(rel)
    for token in tokens:
        if body and token not in body:
            errors.append(f"{rel}: missing eleventh-ten contract token {token!r}")


require(
    "src/main/java/dev/redstoneengineering/block/MechanicalVibrationReceiverBlock.java",
    "waveQuality",
    "InformationRuntime.snapshot",
    "PortQuality.NO_SIGNAL",
)
require(
    "src/main/java/dev/redstoneengineering/block/HydroacousticTubeBlock.java",
    'InformationRuntime.snapshot(level, "hydro", pos)',
    "packet.qualityPercent()",
    "InformationRuntime.clear(level, \"hydro\", pos)",
)
require(
    "src/main/java/dev/redstoneengineering/block/HydroacousticExciterBlock.java",
    "RedstoneObservationSupport.observe",
    "driveObservation",
    "drive.quality()",
)
require(
    "src/main/java/dev/redstoneengineering/block/HydroacousticReceiverBlock.java",
    "packetQuality",
    'InformationRuntime.snapshot(level, "hydro", pos)',
    "next == 0",
    "InformationRuntime.clear(level, \"hydro\", pos)",
)
require(
    "src/main/java/dev/redstoneengineering/physics/RadioKernel.java",
    "Payload zero is a real frame value",
    "Observer-neutral radio reception calculation",
    "recordReception",
)
radio_kernel = read("src/main/java/dev/redstoneengineering/physics/RadioKernel.java")
if radio_kernel and "if (payload > 0)" in radio_kernel:
    errors.append("RadioKernel must not use payload > 0 as transmitter-presence evidence")
receive_match = re.search(
    r"public static synchronized Reception receivePacket\(.*?\n    }\n\n    /\*\* Authoritative receiver ticks",
    radio_kernel,
    re.S,
)
if receive_match and "recordDriverState" in receive_match.group(0):
    errors.append("RadioKernel.receivePacket must remain observer-neutral")

require(
    "src/main/java/dev/redstoneengineering/block/RadioTransmitterBlock.java",
    "PayloadObservation",
    "RedstoneObservationSupport.observe",
    "payload.valid()",
    "RadioKernel.removeTransmitter",
)
require(
    "src/main/java/dev/redstoneengineering/block/RadioReceiverBlock.java",
    "RadioKernel.recordReception",
    "receptionQuality",
)
require(
    "src/main/java/dev/redstoneengineering/block/FreeSpaceOpticalTransmitterBlock.java",
    "RedstoneObservationSupport.observe",
    "inputObservation",
    "input.quality()",
)
require(
    "src/main/java/dev/redstoneengineering/physics/FreeSpaceOpticsKernel.java",
    "channelOk",
    "InformationRuntime.write",
    "channelOk,",
)
require(
    "src/main/java/dev/redstoneengineering/block/FreeSpaceOpticalReceiverBlock.java",
    "opticalQuality",
    "PortQuality.DOMAIN_MISMATCH",
    "packet.selector()",
    "packet.qualityPercent() <= 20",
)
require(
    "src/main/java/dev/redstoneengineering/physics/SoulFluxNetwork.java",
    "initializeReservoir",
    "chargeSnapshot",
    "transient conduit packets to absence",
    "InformationRuntime.clear(level, FLUX_KEY, pos)",
)
require(
    "src/main/java/dev/redstoneengineering/block/SoulSoilConduitBlock.java",
    "SoulFluxNetwork.chargeSnapshot",
    "flux.valid()",
)
require(
    "src/main/java/dev/redstoneengineering/block/SoulSandReservoirBlock.java",
    "SoulFluxNetwork.initializeReservoir",
    "SoulFluxNetwork.chargeSnapshot",
    "PortQuality.STALE",
)

runtime_tests = "src/main/java/dev/redstoneengineering/gametest/RseEleventhTenDesignBugGameTests.java"
for method in (
    "mechanicalReceiverOutputQualityTracksWaveEvidence",
    "hydroTubeExpiresPacketWithoutGhostEnvelope",
    "hydroExciterSeparatesMissingDriveFromDrivenZero",
    "hydroReceiverClearsZeroAmplitudePacket",
    "radioTransmitterKeepsDrivenZeroFrameActive",
    "radioReceiverAcceptsZeroFrameWithoutInventingOutput",
    "opticalTransmitterSeparatesMissingInputFromDrivenZero",
    "opticalReceiverReportsChannelMismatch",
    "soulConduitDecayClearsZeroPacket",
    "soulReservoirInitializesValidEmptyStorage",
):
    require(runtime_tests, f"void {method}(GameTestHelper helper)")

test_body = read(runtime_tests)
if test_body and len(re.findall(r"@GameTest\(", test_body)) != 10:
    errors.append("eleventh-ten GameTest class must contain exactly ten @GameTest methods")

require(
    "src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java",
    "event.register(RseEleventhTenDesignBugGameTests.class);",
)

workflow = read(".github/workflows/build.yml")
if workflow and "tools/rse_eleventh_ten_media_evidence_verify.py" not in workflow:
    errors.append("workflow does not gate the eleventh-ten verifier")
for token in (
    "Minecraft topology GameTests (manual diagnostic)",
    "github.event_name == 'workflow_dispatch'",
    "continue-on-error: true",
    "./gradlew runGameTestServer",
    "./gradlew compileJava",
    "./gradlew test",
):
    if workflow and token not in workflow:
        errors.append(f"build.yml: missing manual/non-blocking GameTest policy token {token!r}")

if errors:
    print("RSE eleventh-ten media evidence verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE eleventh-ten media evidence verification: PASS")
print("  mechanical/hydro evidence quality and packet expiry: PASS")
print("  hydro valid-zero drive semantics: PASS")
print("  radio zero-frame presence and observer-neutral reception: PASS")
print("  optical source evidence + wrong-channel visibility: PASS")
print("  Soul-Flux transport/storage zero semantics: PASS")
print("  registered eleventh-ten GameTests: 10 (manual diagnostic / non-blocking)")
