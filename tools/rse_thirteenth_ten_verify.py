#!/usr/bin/env python3
"""Static gate for the thirteenth ten-block copper/electrothermal deep audit."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        raise SystemExit(f"missing thirteenth-ten file: {rel}")
    return path.read_text(encoding="utf-8")


def require(rel: str, *needles: str) -> None:
    body = read(rel)
    missing = [needle for needle in needles if needle not in body]
    if missing:
        raise SystemExit(f"{rel}: missing thirteenth-ten contract tokens {missing}")


require(
    "src/main/java/dev/redstoneengineering/physics/CopperNetworkSupport.java",
    "recomputeAround",
    "Direction.values()",
    "level.hasChunkAt(neighbor)",
    "DomainNetwork.recomputeCopper(level, neighbor)",
)

contracts = {
    "src/main/java/dev/redstoneengineering/block/CopperWireBlock.java": (
        "implements EngineeringPortProvider",
        "EngineeringDomain.COPPER",
        "PortKind.BUS",
        "PortDirection.BIDIRECTIONAL",
        "PortQuality.TOPOLOGY_ERROR",
        "CopperNetworkSupport.recomputeAround",
    ),
    "src/main/java/dev/redstoneengineering/block/CopperCableJunctionBlock.java": (
        "implements EngineeringPortProvider",
        "EngineeringDomain.COPPER",
        "PortKind.BUS",
        "PortDirection.BIDIRECTIONAL",
        "CopperNetworkSupport.recomputeAround",
    ),
    "src/main/java/dev/redstoneengineering/block/CopperVoltageSourceBlock.java": (
        "implements EngineeringPortProvider",
        '"COPPER SOURCE"',
        "PortDirection.OUTPUT",
        "CopperNetworkSupport.recomputeAround",
    ),
    "src/main/java/dev/redstoneengineering/block/CopperResistiveLoadBlock.java": (
        "implements EngineeringPortProvider",
        '"COPPER LOAD"',
        "PortDirection.INPUT",
        "CopperNetworkSupport.recomputeAround",
    ),
    "src/main/java/dev/redstoneengineering/block/CopperSeriesResistorBlock.java": (
        "extends DirectionalCopperProcessorBlock",
        "DomainNetwork.driveCopper",
        "RuntimeIntStore.remove",
        "CopperNetworkSupport.recomputeAround",
        "level.scheduleTick(pos, this, 1)",
    ),
    "src/main/java/dev/redstoneengineering/block/CopperCapacitorBlock.java": (
        "extends DirectionalCopperProcessorBlock",
        "DomainNetwork.driveCopper",
        "RuntimeIntStore.remove",
        "CopperNetworkSupport.recomputeAround",
        "level.scheduleTick(pos, this, 1)",
    ),
    "src/main/java/dev/redstoneengineering/block/CopperFuseBlock.java": (
        "extends DirectionalCopperProcessorBlock",
        "TRIPPED",
        "DomainNetwork.driveCopper",
        "CopperNetworkSupport.recomputeAround",
        "protection re-evaluates next tick",
    ),
    "src/main/java/dev/redstoneengineering/block/CopperCircuitMeterBlock.java": (
        "implements EngineeringPortProvider",
        '"COPPER MEASURE"',
        "PortKind.MEASUREMENT",
        "sampledVoltage",
        "neighborChanged",
        "observer-only",
    ),
    "src/main/java/dev/redstoneengineering/block/ThermalHeaterBlock.java": (
        "implements EngineeringPortProvider",
        '"COPPER POWER IN"',
        "EngineeringDomain.COPPER",
        "PortKind.CONVERTER",
        "PortDirection.INPUT",
        "CopperNetworkSupport.recomputeAround",
    ),
    "src/main/java/dev/redstoneengineering/block/ThermalMassBlock.java": (
        "implements EngineeringPortProvider",
        '"THERMAL BODY"',
        "EngineeringDomain.THERMAL",
        "PortKind.BUS",
        "PortDirection.BIDIRECTIONAL",
        "neighborChanged",
    ),
}
for rel, needles in contracts.items():
    require(rel, *needles)

# Preserve the earlier Alpha 1.0.13 axial copper contract while deepening lifecycle behavior.
require(
    "src/main/java/dev/redstoneengineering/block/DirectionalCopperProcessorBlock.java",
    "EngineeringDomain.COPPER",
    "PortDirection.INPUT",
    "PortDirection.OUTPUT",
    "observedOutputVoltage",
)
require(
    "src/main/java/dev/redstoneengineering/block/TransmissionTopology.java",
    "CopperWireBlock",
    "CopperCableJunctionBlock",
    "CopperVoltageSourceBlock",
    "CopperResistiveLoadBlock",
    "ThermalHeaterBlock",
    "CopperCircuitMeterBlock",
)

# Runtime data that change every tick must stay out of high-cardinality BlockState properties.
for rel in (
    "src/main/java/dev/redstoneengineering/block/CopperWireBlock.java",
    "src/main/java/dev/redstoneengineering/block/CopperCableJunctionBlock.java",
    "src/main/java/dev/redstoneengineering/block/CopperSeriesResistorBlock.java",
    "src/main/java/dev/redstoneengineering/block/CopperCapacitorBlock.java",
    "src/main/java/dev/redstoneengineering/block/CopperFuseBlock.java",
):
    body = read(rel)
    for forbidden in (
        'IntegerProperty.create("runtime_voltage"',
        'IntegerProperty.create("charge_percent"',
        'IntegerProperty.create("output_voltage"',
    ):
        if forbidden in body:
            raise SystemExit(f"{rel}: high-cardinality runtime leaked into BlockState: {forbidden}")

# Exactly ten executable tests: one acceptance scene for each audited block.
tests_rel = "src/main/java/dev/redstoneengineering/gametest/RseThirteenthTenAcceptanceGameTests.java"
tests = read(tests_rel)
methods = (
    "copperWirePublishesConnectedBusPortsAndClearsSplitIsland",
    "copperJunctionRemovalClearsEveryFormerBranch",
    "voltageSourceRemovalClearsPoweredCableAndLoad",
    "resistiveLoadIsSixFaceInputButNeverTransparentConductor",
    "seriesResistorRemovalReleasesProtectedOutputDriver",
    "capacitorRemovalClearsStoredOutputIsland",
    "fuseRemovalClearsProtectedOutputIsland",
    "circuitMeterObservesFacingCopperWithoutBridgingThrough",
    "thermalHeaterExposesSixCopperConverterInputs",
    "thermalMassPublishesSixFaceThermalBodyState",
)
for method in methods:
    if f"void {method}(GameTestHelper helper)" not in tests:
        raise SystemExit(f"{tests_rel}: missing GameTest method {method}")
if tests.count("@GameTest(") != 10:
    raise SystemExit(f"expected 10 thirteenth GameTests, found {tests.count('@GameTest(')}")

registration = read("src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java")
if "event.register(RseThirteenthTenAcceptanceGameTests.class);" not in registration:
    raise SystemExit("thirteenth-ten GameTests are not registered")
if "event.register(RseCopperGameTests.class);" not in registration:
    raise SystemExit("legacy copper regression GameTests were accidentally dropped")

workflow = read(".github/workflows/build.yml")
if "rse_thirteenth_ten_verify.py" not in workflow:
    raise SystemExit("workflow does not gate the thirteenth-ten verifier")
if "runGameTestServer" not in workflow:
    raise SystemExit("workflow no longer runs Minecraft GameTests")

print("RSE thirteenth-ten copper/electrothermal verification: PASS")
print("  connected copper bus port contracts: PASS")
print("  six-neighbor split-network recomputation: PASS")
print("  source/load/processor lifecycle cleanup: PASS")
print("  capacitor/fuse driver release: PASS")
print("  observer-only copper metrology boundary: PASS")
print("  electrothermal converter + thermal-body ports: PASS")
print("  ten executable thirteenth-batch GameTests registered: PASS")
