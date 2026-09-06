#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing sixth-eight file: {rel}")
        return ""
    return path.read_text(errors="ignore")


def require(rel: str, *tokens: str) -> None:
    body = read(rel)
    for token in tokens:
        if body and token not in body:
            errors.append(f"{rel}: missing sixth-eight contract token {token!r}")


require(
    "src/main/java/dev/redstoneengineering/core/domain/EngineeringDomain.java",
    'AMETHYST("AMETHYST_RESONANCE")',
    'MECHANICAL_VIBRATION("MECHANICAL_VIBRATION")',
    "public String label()",
)
require(
    "src/main/java/dev/redstoneengineering/physics/VibrationNetwork.java",
    "arrivalSide",
    "Direction... outputSides",
    'InformationRuntime.write(level, "mech_wave"',
    "expectedInput",
)

contracts = {
    "src/main/java/dev/redstoneengineering/block/AmethystResonatorBlock.java": (
        "implements EngineeringPortProvider",
        "EngineeringDomain.AMETHYST",
        '"RESONANCE OUT"',
        "DomainNetwork.recomputeAmethyst",
    ),
    "src/main/java/dev/redstoneengineering/block/AmethystResonanceDustBlock.java": (
        "implements EngineeringPortProvider",
        "EngineeringDomain.AMETHYST",
        "PortKind.BUS",
        "PortDirection.BIDIRECTIONAL",
        "SurfaceTraceBlock.connected",
        "setResonance",
        "FieldDeviceUi.open",
    ),
    "src/main/java/dev/redstoneengineering/block/AmethystFrequencyFilterBlock.java": (
        "implements EngineeringPortProvider",
        "EngineeringDomain.AMETHYST",
        '"RESONANCE IN"',
        '"FILTERED OUT"',
        "input.frequency() == state.getValue(TARGET)",
    ),
    "src/main/java/dev/redstoneengineering/block/AmethystTunedResonatorBlock.java": (
        "implements EngineeringPortProvider",
        "EngineeringDomain.AMETHYST",
        '"RESONANCE IN"',
        '"RESONANT OUT"',
        "EngineeringMath.clamp",
    ),
    "src/main/java/dev/redstoneengineering/block/AmethystSpectrumAnalyzerBlock.java": (
        "implements EngineeringPortProvider",
        "PortKind.MEASUREMENT",
        "RuntimeIntStore.peek",
        "SAMPLE_PERIOD_TICKS",
        "FieldDeviceUi.open",
    ),
    "src/main/java/dev/redstoneengineering/block/MechanicalExciterBlock.java": (
        "implements EngineeringPortProvider",
        "EngineeringDomain.MECHANICAL_VIBRATION",
        '"DRIVE IN", Direction.DOWN',
        "neighborPos.equals(pos.below())",
        "VibrationNetwork.propagate",
        "FieldDeviceUi.open",
    ),
    "src/main/java/dev/redstoneengineering/block/SlimeVibrationConduitBlock.java": (
        "implements EngineeringPortProvider",
        "EngineeringDomain.MECHANICAL_VIBRATION",
        "PACKET_TTL_TICKS",
        'InformationRuntime.clear(level, "mech_wave"',
    ),
    "src/main/java/dev/redstoneengineering/block/MechanicalVibrationReceiverBlock.java": (
        "EngineeringDomain.MECHANICAL_VIBRATION",
        '"VIBRATION IN"',
        '"REDSTONE OUT"',
        "VibrationNetwork.sample",
        "FieldDeviceUi.open",
    ),
}
for rel, tokens in contracts.items():
    require(rel, *tokens)

require(
    "src/main/java/dev/redstoneengineering/ui/menu/FieldDeviceMenu.java",
    "KIND_AMETHYST_RESONATOR",
    "KIND_AMETHYST_DUST",
    "KIND_AMETHYST_FILTER",
    "KIND_AMETHYST_TUNED",
    "KIND_AMETHYST_SPECTRUM",
    "KIND_MECHANICAL_EXCITER",
    "KIND_SLIME_VIBRATION",
    "KIND_MECHANICAL_RECEIVER",
    "AmethystSpectrumAnalyzerBlock.spectrum",
    "VibrationNetwork.sample",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/FieldDeviceScreen.java",
    "AMETHYST RESONATOR",
    "RESONANCE BUS",
    "AMETHYST FREQUENCY FILTER",
    "TUNED AMETHYST RESONATOR",
    "SPECTRUM ANALYZER • OBSERVER",
    "MECHANICAL EXCITER",
    "SLIME VIBRATION CONDUIT",
    "MECHANICAL VIBRATION RECEIVER",
)

sixth_tests = "src/main/java/dev/redstoneengineering/gametest/RseSixthEightAcceptanceGameTests.java"
for method in (
    "amethystResonatorExcitesConfiguredFrequency",
    "amethystResonanceDustPropagatesWithoutGain",
    "amethystFrequencyFilterPassesTargetAndRejectsOffBand",
    "amethystTunedResonatorAmplifiesOnResonance",
    "amethystSpectrumAnalyzerObservesWithoutDriving",
    "mechanicalExciterUsesDownDriveAndPublishesWave",
    "slimeVibrationConduitExpiresTransientPacket",
    "mechanicalVibrationReceiverAcceptsBackAndRejectsWrongSide",
):
    require(sixth_tests, f"void {method}(GameTestHelper helper)")
require(
    "src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java",
    "event.register(RseSixthEightAcceptanceGameTests.class);",
)

joined = "\n".join(read(rel) for rel in contracts)
for forbidden in (
    'IntegerProperty.create("mech_wave"',
    'IntegerProperty.create("spectrum"',
    'IntegerProperty.create("wave_amplitude"',
    'IntegerProperty.create("measured_frequency"',
):
    if forbidden in joined:
        errors.append(f"sixth-eight transient diagnostics leaked into BlockState: {forbidden}")

workflow = read(".github/workflows/build.yml")
if workflow and "rse_sixth_eight_verify.py" not in workflow:
    errors.append("workflow does not gate the sixth-eight verifier")

if errors:
    print("RSE sixth-eight wave/frequency verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE sixth-eight wave/frequency verification: PASS")
print("  first-class mechanical-vibration engineering domain: PASS")
print("  directional excitation and reception contracts: PASS")
print("  transient mechanical-wave lifetime: PASS")
print("  amethyst resonance/filter/tuning contracts: PASS")
print("  observer-only server spectrum snapshot: PASS")
print("  Field Device Inspector wave projection: PASS")
print("  eight executable sixth-batch GameTests registered: PASS")
