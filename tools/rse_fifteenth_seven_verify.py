#!/usr/bin/env python3
"""Static gate for the fifteenth seven-block tail sensor/Soul-domain deep audit."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        raise SystemExit(f"missing fifteenth-seven file: {rel}")
    return path.read_text(encoding="utf-8")


def require(rel: str, *needles: str) -> None:
    body = read(rel)
    missing = [needle for needle in needles if needle not in body]
    if missing:
        raise SystemExit(f"{rel}: missing fifteenth-seven contract tokens {missing}")


require(
    "src/main/java/dev/redstoneengineering/core/domain/EngineeringDomain.java",
    'SOUL_FLUX("SOUL_FLUX")',
)
require(
    "src/main/java/dev/redstoneengineering/physics/SoulFluxNetwork.java",
    "NetworkKernel.MAX_NODES",
    "isNode",
    "public static void clear",
    'InformationRuntime.clear(level, FLUX_KEY, pos)',
    'InformationRuntime.clear(level, STORE_KEY, pos)',
)

contracts = {
    "src/main/java/dev/redstoneengineering/block/TankLevelSensorBlock.java": (
        '"TANK COLUMN"',
        "Direction.UP",
        "EngineeringDomain.GENERIC",
        "PortKind.SENSOR",
        "PortDirection.INPUT",
        '"SENSOR OUT"',
        "EngineeringDomain.REDSTONE",
        "PortDirection.OUTPUT",
        "physicalCount",
        "neighborChanged",
    ),
    "src/main/java/dev/redstoneengineering/block/EntityDensitySensorBlock.java": (
        '"OCCUPANCY FIELD"',
        "Direction.UP",
        "EngineeringDomain.GENERIC",
        "PortDirection.INPUT",
        '"SENSOR OUT"',
        "EngineeringDomain.REDSTONE",
        "PortDirection.OUTPUT",
        "physicalCount",
    ),
    "src/main/java/dev/redstoneengineering/block/SoulSoilConduitBlock.java": (
        "implements EngineeringPortProvider",
        '"SOUL FLUX BUS"',
        "EngineeringDomain.SOUL_FLUX",
        "PortKind.BUS",
        "PortDirection.BIDIRECTIONAL",
        "SoulFluxNetwork.decay",
        "SoulFluxNetwork.clear",
    ),
    "src/main/java/dev/redstoneengineering/block/SoulSandReservoirBlock.java": (
        "implements EngineeringPortProvider",
        '"SOUL RESERVOIR"',
        "EngineeringDomain.SOUL_FLUX",
        "PortKind.BUS",
        "PortDirection.BIDIRECTIONAL",
        "SoulFluxNetwork.decay",
        "SoulFluxNetwork.clear",
    ),
    "src/main/java/dev/redstoneengineering/block/SoulFluxInjectorBlock.java": (
        "implements EngineeringPortProvider",
        '"REDSTONE COMMAND"',
        "Direction.UP",
        "EngineeringDomain.REDSTONE",
        "PortKind.CONTROL",
        "PortDirection.INPUT",
        '"SOUL FLUX OUT"',
        "EngineeringDomain.SOUL_FLUX",
        "PortKind.CONVERTER",
        "PortDirection.OUTPUT",
        "commandSignal",
        "SoulFluxNetwork.inject",
    ),
    "src/main/java/dev/redstoneengineering/block/SoulFluxMeterBlock.java": (
        '"SOUL FLUX MEASURE"',
        "EngineeringDomain.SOUL_FLUX",
        "PortKind.MEASUREMENT",
        "PortDirection.INPUT",
        '"REDSTONE READOUT"',
        "EngineeringDomain.REDSTONE",
        "PortKind.REDSTONE_ANALOG",
        "PortDirection.OUTPUT",
        "canConnectRedstone",
        "inputCharge",
    ),
    "src/main/java/dev/redstoneengineering/block/MolecularCloudReceiverBlock.java": (
        '"MOLECULAR FIELD"',
        "Direction.UP",
        "EngineeringDomain.GENERIC",
        "PortKind.SENSOR",
        "PortDirection.INPUT",
        '"REDSTONE READOUT"',
        "EngineeringDomain.REDSTONE",
        "PortDirection.OUTPUT",
        "canConnectRedstone",
        "RuntimeIntStore.remove",
        "AreaEffectCloud",
    ),
}
for rel, needles in contracts.items():
    require(rel, *needles)

# The injector now has one physical redstone command face rather than hidden any-face power semantics.
injector = read("src/main/java/dev/redstoneengineering/block/SoulFluxInjectorBlock.java")
if "getBestNeighborSignal" in injector:
    raise SystemExit("Soul Flux injector must not retain hidden any-face redstone command sampling")
if "direction.getOpposite() == Direction.UP" not in injector:
    raise SystemExit("Soul Flux injector must expose only the dedicated UP redstone connection")

# The meter is an observer/converter; BACK must not remain a vanilla-redstone input inherited from the base class.
meter = read("src/main/java/dev/redstoneengineering/block/SoulFluxMeterBlock.java")
if "direction.getOpposite() == outputSide(state)" not in meter:
    raise SystemExit("Soul Flux meter must allow vanilla redstone only on its FRONT readout face")

# Sensor runtime/history must stay out of high-cardinality BlockState.
molecular = read("src/main/java/dev/redstoneengineering/block/MolecularCloudReceiverBlock.java")
for forbidden in (
    'IntegerProperty.create("filtered"',
    'IntegerProperty.create("raw"',
    'IntegerProperty.create("peak"',
):
    if forbidden in molecular:
        raise SystemExit(f"Molecular receiver leaked runtime history into BlockState: {forbidden}")

# Exactly seven executable tests: one scene per remaining tail block.
tests_rel = "src/main/java/dev/redstoneengineering/gametest/RseFifteenthSevenAcceptanceGameTests.java"
tests = read(tests_rel)
methods = (
    "tankLevelSensorPublishesPhysicalApertureAndFrontReadout",
    "entityDensitySensorSeparatesFreeSpaceSenseFromRedstone",
    "soulSoilConduitCarriesAndClearsTransientFlux",
    "soulReservoirStoresAndDecaysCharge",
    "soulInjectorConvertsRedstoneCommandIntoFlux",
    "soulFluxMeterIsSoulInputRedstoneOutputObserver",
    "molecularReceiverPublishesAmbientApertureAndClearsRuntime",
)
for method in methods:
    if f"void {method}(GameTestHelper helper)" not in tests:
        raise SystemExit(f"{tests_rel}: missing GameTest method {method}")
if tests.count("@GameTest(") != 7:
    raise SystemExit(f"expected 7 fifteenth GameTests, found {tests.count('@GameTest(')}")

registration = read("src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java")
if "event.register(RseFifteenthSevenAcceptanceGameTests.class);" not in registration:
    raise SystemExit("fifteenth-seven GameTests are not registered")
if "event.register(RseFourteenthTenAcceptanceGameTests.class);" not in registration:
    raise SystemExit("fourteenth-ten regression GameTests were accidentally dropped")

workflow = read(".github/workflows/build.yml")
if "rse_fifteenth_seven_verify.py" not in workflow:
    raise SystemExit("workflow does not gate the fifteenth-seven verifier")
if "runGameTestServer" not in workflow:
    raise SystemExit("workflow no longer runs Minecraft GameTests")

print("RSE fifteenth-seven tail sensor/Soul-domain verification: PASS")
print("  physical environmental sensing apertures: PASS")
print("  first-class SOUL_FLUX domain: PASS")
print("  conduit/reservoir lifecycle and decay: PASS")
print("  dedicated redstone-to-Soul converter boundary: PASS")
print("  Soul observer-to-redstone meter boundary: PASS")
print("  molecular runtime cleanup and free-space aperture: PASS")
print("  seven executable fifteenth-batch GameTests registered: PASS")
