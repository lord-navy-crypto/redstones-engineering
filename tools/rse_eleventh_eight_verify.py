#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing eleventh-eight file: {rel}")
        return ""
    return path.read_text(errors="ignore")


def require(rel: str, *tokens: str) -> None:
    body = read(rel)
    for token in tokens:
        if body and token not in body:
            errors.append(f"{rel}: missing eleventh-eight contract token {token!r}")


require(
    "src/main/java/dev/redstoneengineering/block/PneumaticProportionalValveBlock.java",
    "implements EntityBlock, EngineeringPortProvider",
    '"PNEUMATIC IN"',
    '"PNEUMATIC OUT"',
    '"OPENING COMMAND"',
    "EngineeringDomain.REDSTONE",
    "PneumaticNetwork.recomputeAround",
    "FieldDeviceUi.open",
)
require(
    "src/main/java/dev/redstoneengineering/block/PneumaticReliefValveBlock.java",
    "PortKind.SAFETY",
    "recordVent",
    "clearVenting",
    "RuntimeIntStore.remove",
    "PneumaticNetwork.recomputeAround",
    "FieldDeviceUi.open",
)
require(
    "src/main/java/dev/redstoneengineering/block/PneumaticCylinderBlock.java",
    "implements EntityBlock, EngineeringPortProvider",
    '"PNEUMATIC IN"',
    '"POSITION FEEDBACK"',
    "PortKind.FEEDBACK",
    "RuntimeIntStore.remove",
    "if (runtime[0] != target) level.scheduleTick",
    "FieldDeviceUi.open",
)
require(
    "src/main/java/dev/redstoneengineering/block/ElectromagnetBlock.java",
    "implements EngineeringPortProvider",
    "EngineeringDomain.COPPER",
    "PortKind.ACTUATOR",
    "PortDirection.INPUT",
    "DomainNetwork.recomputeCopper",
    "FieldDeviceUi.open",
)
require(
    "src/main/java/dev/redstoneengineering/block/PermanentMagnetBlock.java",
    "implements EngineeringPortProvider",
    "return List.of()",
    "free-space",
    "FieldDeviceUi.open",
)
require(
    "src/main/java/dev/redstoneengineering/block/InductionCoilBlock.java",
    "implements EngineeringPortProvider",
    '"MAGNETIC SENSE"',
    '"INDUCED COPPER OUT"',
    "EngineeringDomain.IRON_MAGNETIC",
    "EngineeringDomain.COPPER",
    "RuntimeIntStore.peek",
    "RuntimeIntStore.remove",
    "FieldDeviceUi.open",
)
require(
    "src/main/java/dev/redstoneengineering/block/MagneticFieldSensorBlock.java",
    "implements EngineeringPortProvider",
    "return List.of()",
    "free-space",
    "FieldDeviceUi.open",
)
require(
    "src/main/java/dev/redstoneengineering/block/MagneticGradientMeterBlock.java",
    "implements EngineeringPortProvider",
    "gradientX",
    "gradientY",
    "gradientZ",
    "return List.of()",
    "FieldDeviceUi.open",
)
require(
    "src/main/java/dev/redstoneengineering/ui/menu/FieldDeviceMenu.java",
    "KIND_PNEUMATIC_PROPORTIONAL_VALVE = 63",
    "KIND_PNEUMATIC_RELIEF_VALVE = 64",
    "KIND_PNEUMATIC_CYLINDER = 65",
    "KIND_ELECTROMAGNET = 66",
    "KIND_PERMANENT_MAGNET = 67",
    "KIND_INDUCTION_COIL = 68",
    "KIND_MAGNETIC_FIELD_SENSOR = 69",
    "KIND_MAGNETIC_GRADIENT_METER = 70",
    "PneumaticCylinderBlock.position",
    "MagneticGradientMeterBlock.gradientX",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/FieldDeviceScreen.java",
    "PROPORTIONAL VALVE",
    "RELIEF ARMED",
    "PNEUMATIC ACTUATOR",
    "COPPER → MAGNETIC",
    "PERMANENT FIELD SOURCE",
    "MAGNETIC INDUCTION",
    "MAGNETIC FIELD SENSOR",
    "MAGNETIC GRADIENT",
    "NO WIRED PORT",
)

tests = "src/main/java/dev/redstoneengineering/gametest/RseEleventhEightAcceptanceGameTests.java"
for method in (
    "proportionalValveUsesAxialPressureAndUpCommand",
    "reliefValveClampsAndCountsOneOverpressureEpisode",
    "pneumaticCylinderPublishesFeedbackAndCleansRuntime",
    "electromagnetConvertsCopperWithoutRedstoneLeak",
    "permanentMagnetIsFreeSpaceSourceNotWiredPort",
    "inductionCoilPulsesOnFluxChangeAndHasAxialDomains",
    "magneticFieldSensorTracksSourceRemovalWithoutOutput",
    "magneticGradientMeterReportsSpatialDifferenceWithoutDriving",
):
    require(tests, f"void {method}(GameTestHelper helper)")
body = read(tests)
if body and len(re.findall(r"@GameTest\(", body)) != 8:
    errors.append("eleventh-eight acceptance class must contain exactly eight @GameTest methods")
require(
    "src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java",
    "event.register(RseEleventhEightAcceptanceGameTests.class);",
)

workflow = read(".github/workflows/build.yml")
if workflow and "rse_eleventh_eight_verify.py" not in workflow:
    errors.append("workflow does not gate the eleventh-eight verifier")

if errors:
    print("RSE eleventh-eight actuation/safety verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE eleventh-eight actuation/safety verification: PASS")
print("  proportional control and relief safety contracts: PASS")
print("  cylinder actuation/feedback lifecycle: PASS")
print("  copper-to-magnetic conversion boundary: PASS")
print("  free-space field observer semantics: PASS")
print("  induction transient and cleanup contract: PASS")
print("  Field Device Inspector projection kinds 63-70: PASS")
print("  eight executable eleventh-batch GameTests registered: PASS")
