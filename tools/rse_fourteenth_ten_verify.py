#!/usr/bin/env python3
"""Static gate for the fourteenth ten-block thermal/Lapis/Quartz/sensor deep audit."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        raise SystemExit(f"missing fourteenth-ten file: {rel}")
    return path.read_text(encoding="utf-8")


def require(rel: str, *needles: str) -> None:
    body = read(rel)
    missing = [needle for needle in needles if needle not in body]
    if missing:
        raise SystemExit(f"{rel}: missing fourteenth-ten contract tokens {missing}")


contracts = {
    "src/main/java/dev/redstoneengineering/block/ThermalRadiatorBlock.java": (
        "implements EngineeringPortProvider",
        '"THERMAL SINK"',
        "EngineeringDomain.THERMAL",
        "PortKind.ACTUATOR",
        "PortDirection.INPUT",
        "neighborChanged",
        "ThermalPhysics.AMBIENT",
    ),
    "src/main/java/dev/redstoneengineering/block/ThermalCalorimeterBlock.java": (
        "implements EngineeringPortProvider",
        '"THERMAL MEASURE"',
        "PortKind.MEASUREMENT",
        "PortDirection.INPUT",
        "RuntimeIntStore.remove",
        "neighborChanged",
    ),
    "src/main/java/dev/redstoneengineering/block/TemperatureSensorBlock.java": (
        "implements EngineeringPortProvider",
        '"THERMAL SENSE"',
        "EngineeringDomain.THERMAL",
        "PortKind.SENSOR",
        "PortDirection.INPUT",
        "neighborChanged",
    ),
    "src/main/java/dev/redstoneengineering/block/IronCoreBlock.java": (
        "implements EngineeringPortProvider",
        '"MAGNETIC COUPLING "',
        "EngineeringDomain.IRON_MAGNETIC",
        "PortKind.AUXILIARY",
        "PortDirection.BIDIRECTIONAL",
        'false, "remanence"',
        "free-space magnetic material",
        "remanent",
        "neighborChanged",
    ),
    "src/main/java/dev/redstoneengineering/block/LapisNoiseSourceBlock.java": (
        "implements EngineeringPortProvider",
        '"LAPIS NOISE OUT"',
        "EngineeringDomain.LAPIS",
        "PortDirection.OUTPUT",
        "DomainNetwork.recomputeLapisAround",
        "RuntimeIntStore.remove",
    ),
    "src/main/java/dev/redstoneengineering/block/LapisLowPassFilterBlock.java": (
        "implements EngineeringPortProvider",
        '"LAPIS FILTER IN"',
        '"LAPIS FILTER OUT"',
        "PortDirection.INPUT",
        "PortDirection.OUTPUT",
        "DomainNetwork.driveLapis",
        "DomainNetwork.recomputeLapisAround",
        "neighborChanged",
    ),
    "src/main/java/dev/redstoneengineering/block/LapisPrecisionMeterBlock.java": (
        "implements EngineeringPortProvider",
        '"LAPIS MEASURE"',
        "PortKind.MEASUREMENT",
        "PortDirection.INPUT",
        "sampledValue",
        "observer-neutral",
    ),
    "src/main/java/dev/redstoneengineering/block/QuartzLabOscillatorBlock.java": (
        "implements EngineeringPortProvider",
        '"QUARTZ LAB CLOCK OUT"',
        "EngineeringDomain.QUARTZ",
        "PortDirection.OUTPUT",
        "DomainNetwork.recomputeQuartzAround",
    ),
    "src/main/java/dev/redstoneengineering/block/QuartzPhaseDelayBlock.java": (
        "implements EngineeringPortProvider",
        '"QUARTZ DELAY IN"',
        '"QUARTZ DELAY OUT"',
        "PortDirection.INPUT",
        "PortDirection.OUTPUT",
        "DomainNetwork.driveQuartz",
        "DomainNetwork.recomputeQuartzAround",
        "neighborChanged",
    ),
    "src/main/java/dev/redstoneengineering/block/EngineeringLightSensorBlock.java": (
        '"LIGHT APERTURE"',
        "Direction.UP",
        "EngineeringDomain.OPTICAL",
        '"SENSOR OUT"',
        "EngineeringDomain.REDSTONE",
        "PortDirection.OUTPUT",
        "neighborChanged",
    ),
}
for rel, needles in contracts.items():
    require(rel, *needles)

# Observer devices must not silently become Lapis drivers or thermal actuators.
meter_body = read("src/main/java/dev/redstoneengineering/block/LapisPrecisionMeterBlock.java")
if "driveLapis" in meter_body:
    raise SystemExit("Lapis precision meter must remain observer-only and must not drive the Lapis network")
calorimeter_body = read("src/main/java/dev/redstoneengineering/block/ThermalCalorimeterBlock.java")
if "setValue(ThermalMassBlock.TEMPERATURE" in calorimeter_body:
    raise SystemExit("Thermal calorimeter must remain observer-only and must not mutate ThermalMass temperature")

# High-rate Lapis/Quartz samples stay in runtime stores, not new high-cardinality BlockState properties.
for rel in (
    "src/main/java/dev/redstoneengineering/block/LapisNoiseSourceBlock.java",
    "src/main/java/dev/redstoneengineering/block/LapisLowPassFilterBlock.java",
    "src/main/java/dev/redstoneengineering/block/QuartzPhaseDelayBlock.java",
):
    body = read(rel)
    for forbidden in (
        'IntegerProperty.create("runtime_output"',
        'IntegerProperty.create("runtime_sample"',
        'IntegerProperty.create("pending_ticks"',
    ):
        if forbidden in body:
            raise SystemExit(f"{rel}: high-cardinality runtime leaked into BlockState: {forbidden}")

# Exactly ten executable tests: one scene per audited block.
tests_rel = "src/main/java/dev/redstoneengineering/gametest/RseFourteenthTenAcceptanceGameTests.java"
tests = read(tests_rel)
methods = (
    "thermalRadiatorPublishesSixPassiveSinkPorts",
    "thermalCalorimeterIsSixFaceObserver",
    "temperatureSensorAveragesAdjacentThermalBodies",
    "ironCoreRemanenceIsFreeSpaceNotWired",
    "lapisNoiseSourcePublishesFourOutputsAndClearsOnRemoval",
    "lapisLowPassFilterIsDirectionalAndReleasesOutput",
    "lapisPrecisionMeterObservesWithoutBridging",
    "quartzLabOscillatorPublishesFourOutputsAndClearsOnRemoval",
    "quartzPhaseDelayIsDirectionalAndReleasesOutput",
    "engineeringLightSensorSeparatesOpticalApertureAndRedstoneOutput",
)
for method in methods:
    if f"void {method}(GameTestHelper helper)" not in tests:
        raise SystemExit(f"{tests_rel}: missing GameTest method {method}")
if tests.count("@GameTest(") != 10:
    raise SystemExit(f"expected 10 fourteenth GameTests, found {tests.count('@GameTest(')}")

registration = read("src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java")
if "event.register(RseFourteenthTenAcceptanceGameTests.class);" not in registration:
    raise SystemExit("fourteenth-ten GameTests are not registered")
if "event.register(RseThirteenthTenAcceptanceGameTests.class);" not in registration:
    raise SystemExit("thirteenth-ten regression GameTests were accidentally dropped")

workflow = read(".github/workflows/build.yml")
if "rse_fourteenth_ten_verify.py" not in workflow:
    raise SystemExit("workflow does not gate the fourteenth-ten verifier")
if "runGameTestServer" not in workflow:
    raise SystemExit("workflow no longer runs Minecraft GameTests")

print("RSE fourteenth-ten thermal/Lapis/Quartz/sensor verification: PASS")
print("  passive thermal sink and observer boundaries: PASS")
print("  temperature sensing and inspectable non-wired magnetic free-space semantics: PASS")
print("  Lapis source/filter/meter lifecycle and observer contracts: PASS")
print("  Quartz source/delay lifecycle and timing ports: PASS")
print("  optical-aperture to redstone-output sensor boundary: PASS")
print("  ten executable fourteenth-batch GameTests registered: PASS")
