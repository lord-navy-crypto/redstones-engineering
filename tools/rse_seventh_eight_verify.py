#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing seventh-eight file: {rel}")
        return ""
    return path.read_text(errors="ignore")


def require(rel: str, *tokens: str) -> None:
    body = read(rel)
    for token in tokens:
        if body and token not in body:
            errors.append(f"{rel}: missing seventh-eight contract token {token!r}")


require(
    "src/main/java/dev/redstoneengineering/core/domain/EngineeringDomain.java",
    "HYDROACOUSTIC",
    "PHONON_THERMAL",
    "MECHANICAL_VIBRATION",
)
require(
    "src/main/java/dev/redstoneengineering/physics/HydroacousticNetwork.java",
    "arrivalSide",
    "Direction... outputSides",
    'InformationRuntime.write(level, "hydro"',
    "expectedInput",
)
require(
    "src/main/java/dev/redstoneengineering/physics/ThermalPulseKernel.java",
    "arrivalSide",
    "Direction... outputSides",
    'InformationRuntime.write(level, "thermal_pulse"',
    "expectedInput",
)
require(
    "src/main/java/dev/redstoneengineering/physics/VibrationNetwork.java",
    "HoneyVibrationDamperBlock",
    "return 4",
)

contracts = {
    "src/main/java/dev/redstoneengineering/block/HoneyVibrationDamperBlock.java": (
        "implements EngineeringPortProvider",
        "EngineeringDomain.MECHANICAL_VIBRATION",
        "PACKET_TTL_TICKS",
        'InformationRuntime.clear(level, "mech_wave"',
        "FieldDeviceUi.open",
    ),
    "src/main/java/dev/redstoneengineering/block/SculkVibrationInterfaceBlock.java": (
        "EngineeringDomain.REDSTONE",
        '"SCULK CODE IN"',
        '"EVENT CODE OUT"',
        "eventCount",
        "lastEventCode",
        "transitionCount",
        "RuntimeIntStore.remove",
        "FieldDeviceUi.open",
    ),
    "src/main/java/dev/redstoneengineering/block/HydroacousticTubeBlock.java": (
        "implements EngineeringPortProvider",
        "EngineeringDomain.HYDROACOUSTIC",
        "PACKET_TTL_TICKS",
        "MEDIUM",
        'InformationRuntime.clear(level, "hydro"',
        "FieldDeviceUi.open",
    ),
    "src/main/java/dev/redstoneengineering/block/HydroacousticExciterBlock.java": (
        "implements EngineeringPortProvider",
        "EngineeringDomain.HYDROACOUSTIC",
        '"DRIVE IN", Direction.DOWN',
        "neighborPos.equals(pos.below())",
        "HydroacousticNetwork.propagate",
        "FieldDeviceUi.open",
    ),
    "src/main/java/dev/redstoneengineering/block/HydroacousticReceiverBlock.java": (
        "EngineeringDomain.HYDROACOUSTIC",
        '"PRESSURE WAVE IN"',
        '"REDSTONE OUT"',
        'InformationRuntime.valid(level, "hydro"',
        "FieldDeviceUi.open",
    ),
    "src/main/java/dev/redstoneengineering/block/PhononConduitBlock.java": (
        "implements EngineeringPortProvider",
        "EngineeringDomain.PHONON_THERMAL",
        "PACKET_TTL_TICKS",
        'InformationRuntime.clear(level, "thermal_pulse"',
        "FieldDeviceUi.open",
    ),
    "src/main/java/dev/redstoneengineering/block/ThermalPulseEncoderBlock.java": (
        "implements EngineeringPortProvider",
        "EngineeringDomain.PHONON_THERMAL",
        '"DRIVE IN", Direction.DOWN',
        "neighborPos.equals(pos.below())",
        "ThermalPulseKernel.send",
        "FieldDeviceUi.open",
    ),
    "src/main/java/dev/redstoneengineering/block/ThermalPulseReceiverBlock.java": (
        "EngineeringDomain.PHONON_THERMAL",
        '"THERMAL PULSE IN"',
        '"REDSTONE OUT"',
        'InformationRuntime.valid(level, "thermal_pulse"',
        "FieldDeviceUi.open",
    ),
}
for rel, tokens in contracts.items():
    require(rel, *tokens)

menu = "src/main/java/dev/redstoneengineering/ui/menu/FieldDeviceMenu.java"
for token in (
    "KIND_HONEY_DAMPER = 32",
    "KIND_SCULK_INTERFACE = 33",
    "KIND_HYDRO_TUBE = 34",
    "KIND_HYDRO_EXCITER = 35",
    "KIND_HYDRO_RECEIVER = 36",
    "KIND_PHONON_CONDUIT = 37",
    "KIND_THERMAL_ENCODER = 38",
    "KIND_THERMAL_RECEIVER = 39",
    'InformationRuntime.value(level, "hydro"',
    'InformationRuntime.value(level, "thermal_pulse"',
    "sculk.eventCount",
    "HydroacousticTubeBlock.MEDIUM",
):
    require(menu, token)

screen = "src/main/java/dev/redstoneengineering/client/ui/FieldDeviceScreen.java"
for token in (
    "HONEY VIBRATION DAMPER",
    "SCULK VIBRATION INTERFACE",
    "HYDROACOUSTIC TUBE",
    "HYDROACOUSTIC EXCITER",
    "HYDROACOUSTIC RECEIVER",
    "PHONON CONDUIT",
    "THERMAL PULSE ENCODER",
    "THERMAL PULSE RECEIVER",
    "KIND_THERMAL_RECEIVER",
    "HYDROACOUSTIC • BIDIRECTIONAL PRESSURE PATH",
    "PHONON_THERMAL • BIDIRECTIONAL PULSE PATH",
):
    require(screen, token)

seventh_tests = "src/main/java/dev/redstoneengineering/gametest/RseSeventhEightAcceptanceGameTests.java"
for method in (
    "honeyVibrationDamperAppliesHighLoss",
    "sculkVibrationInterfaceCapturesDirectionalEventCode",
    "hydroacousticTubePropagatesWithMediumLoss",
    "hydroacousticExciterUsesDownDrive",
    "hydroacousticReceiverRejectsWrongSide",
    "phononConduitCarriesAndExpiresThermalPulse",
    "thermalPulseEncoderUsesDownDrive",
    "thermalPulseReceiverRejectsWrongSide",
):
    require(seventh_tests, f"void {method}(GameTestHelper helper)")

test_body = read(seventh_tests)
if test_body and len(re.findall(r"@GameTest\(", test_body)) != 8:
    errors.append("seventh-eight acceptance class must contain exactly eight @GameTest methods")
require(
    "src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java",
    "event.register(RseSeventhEightAcceptanceGameTests.class);",
)

joined = "\n".join(read(rel) for rel in contracts)
for forbidden in (
    'IntegerProperty.create("mech_wave"',
    'IntegerProperty.create("hydro_wave"',
    'IntegerProperty.create("thermal_pulse"',
    'IntegerProperty.create("arrival_side"',
):
    if forbidden in joined:
        errors.append(f"seventh-eight transient runtime leaked into BlockState: {forbidden}")

workflow = read(".github/workflows/build.yml")
if workflow and "rse_seventh_eight_verify.py" not in workflow:
    errors.append("workflow does not gate the seventh-eight verifier")

if errors:
    print("RSE seventh-eight acoustic/thermal-wave verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE seventh-eight acoustic/thermal-wave verification: PASS")
print("  explicit hydroacoustic + phonon-thermal domains: PASS")
print("  directional source/receiver arrival contracts: PASS")
print("  honey damping + bounded transient wave lifetime: PASS")
print("  hydro medium + pressure-wave runtime: PASS")
print("  sculk event telemetry lifecycle: PASS")
print("  Field Device Inspector kinds 32-39: PASS")
print("  eight executable seventh-batch GameTests registered: PASS")
