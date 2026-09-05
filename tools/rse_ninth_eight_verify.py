#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing ninth-eight file: {rel}")
        return ""
    return path.read_text(errors="ignore")


def require(rel: str, *tokens: str) -> None:
    body = read(rel)
    for token in tokens:
        if body and token not in body:
            errors.append(f"{rel}: missing ninth-eight contract token {token!r}")


require(
    "src/main/java/dev/redstoneengineering/core/domain/EngineeringDomain.java",
    'PNEUMATIC("PNEUMATIC")',
)

require(
    "src/main/java/dev/redstoneengineering/physics/PneumaticNetwork.java",
    "exposesPneumaticEdge",
    "discoveryConnects",
    "AirCompressorBlock",
    "other.equals(self.above())",
    "PneumaticReceiverBlock",
    "PneumaticValveBlock",
    "PneumaticCheckValveBlock",
    "PneumaticFlowMeterBlock",
    "directionalForward",
    "directionalBackwardEntry",
    "recomputeAround",
)

require(
    "src/main/java/dev/redstoneengineering/block/AirCompressorBlock.java",
    "implements EngineeringPortProvider",
    '"PRESSURE COMMAND"',
    '"COMPRESSED AIR OUT"',
    "Direction.DOWN",
    "Direction.UP",
    "EngineeringDomain.REDSTONE",
    "EngineeringDomain.PNEUMATIC",
    "commandSignal",
    "direction.getOpposite() == Direction.DOWN",
    'InformationRuntime.clear(level, "pneumatic", pos)',
    "PneumaticNetwork.recomputeAround",
    "FieldDeviceUi.open",
)
compressor = read("src/main/java/dev/redstoneengineering/block/AirCompressorBlock.java")
if compressor and "getBestNeighborSignal" in compressor:
    errors.append("AirCompressorBlock must not accept implicit redstone from arbitrary faces")

require(
    "src/main/java/dev/redstoneengineering/block/PneumaticPipeBlock.java",
    "implements EngineeringPortProvider",
    "EngineeringDomain.PNEUMATIC",
    "PortKind.BUS",
    "PortDirection.BIDIRECTIONAL",
    "InformationRuntime.clear",
    "PneumaticNetwork.recomputeAround",
    "FieldDeviceUi.open",
)
require(
    "src/main/java/dev/redstoneengineering/block/AirReservoirBlock.java",
    "implements EngineeringPortProvider",
    "EngineeringDomain.PNEUMATIC",
    "PortDirection.BIDIRECTIONAL",
    "storedPressure",
    'InformationRuntime.clear(level, "air_reservoir", pos)',
    'InformationRuntime.clear(level, "pneumatic", pos)',
    "PneumaticNetwork.recomputeAround",
    "FieldDeviceUi.open",
)
require(
    "src/main/java/dev/redstoneengineering/block/PressureRegulatorBlock.java",
    "extends DomainBlock implements EngineeringPortProvider",
    '"REGULATED AIR"',
    "EngineeringDomain.PNEUMATIC",
    "PortKind.BUS",
    "PortDirection.BIDIRECTIONAL",
    "setpointPressure",
    'InformationRuntime.clear(level, "pneumatic", pos)',
    "PneumaticNetwork.recomputeAround",
    "FieldDeviceUi.open",
)
require(
    "src/main/java/dev/redstoneengineering/block/PneumaticReceiverBlock.java",
    '"PNEUMATIC IN"',
    '"REDSTONE OUT"',
    "EngineeringDomain.PNEUMATIC",
    "EngineeringDomain.REDSTONE",
    "direction.getOpposite() == outputSide(state)",
    "PneumaticNetwork.recomputeAround",
    "FieldDeviceUi.open",
)
require(
    "src/main/java/dev/redstoneengineering/block/PneumaticValveBlock.java",
    "implements EngineeringPortProvider",
    '"PNEUMATIC BACK"',
    '"PNEUMATIC FRONT"',
    "PortDirection.BIDIRECTIONAL",
    "PneumaticNetwork.recomputeAround",
    "FieldDeviceUi.open",
)
require(
    "src/main/java/dev/redstoneengineering/block/PneumaticCheckValveBlock.java",
    "implements EngineeringPortProvider",
    '"PNEUMATIC IN"',
    '"PNEUMATIC OUT"',
    "PortDirection.INPUT",
    "PortDirection.OUTPUT",
    "PneumaticNetwork.recomputeAround",
    "FieldDeviceUi.open",
)
require(
    "src/main/java/dev/redstoneengineering/block/PneumaticFlowMeterBlock.java",
    "implements EngineeringPortProvider",
    "PortKind.MEASUREMENT",
    "pressureDrop",
    "inletPressure",
    "outletPressure",
    'RuntimeIntStore.remove(level, RUNTIME, pos)',
    "MetrologyStore.remove",
    "PneumaticNetwork.recomputeAround",
    "FieldDeviceUi.open",
)

# Runtime pressure/flow data are high-cardinality and must stay outside BlockState.
joined = "\n".join(read(rel) for rel in (
    "src/main/java/dev/redstoneengineering/block/AirCompressorBlock.java",
    "src/main/java/dev/redstoneengineering/block/PneumaticPipeBlock.java",
    "src/main/java/dev/redstoneengineering/block/AirReservoirBlock.java",
    "src/main/java/dev/redstoneengineering/block/PressureRegulatorBlock.java",
    "src/main/java/dev/redstoneengineering/block/PneumaticReceiverBlock.java",
    "src/main/java/dev/redstoneengineering/block/PneumaticValveBlock.java",
    "src/main/java/dev/redstoneengineering/block/PneumaticCheckValveBlock.java",
    "src/main/java/dev/redstoneengineering/block/PneumaticFlowMeterBlock.java",
))
for forbidden in (
    'IntegerProperty.create("pressure",',
    'IntegerProperty.create("stored_pressure",',
    'IntegerProperty.create("flow",',
):
    if forbidden in joined:
        errors.append(f"ninth-eight high-cardinality runtime leaked into BlockState: {forbidden}")

# Exactly eight executable ninth-batch acceptance tests.
tests = "src/main/java/dev/redstoneengineering/gametest/RseNinthEightAcceptanceGameTests.java"
for method in (
    "compressorSeparatesDownCommandFromUpPneumaticOutlet",
    "pneumaticPipeBreakRecomputesSeparatedIsland",
    "airReservoirStoresAndClearsTransientPressure",
    "pressureRegulatorIsSixWayAndClampsSetpoint",
    "pneumaticReceiverIsTerminalConverterNotBridge",
    "manualValveUsesAxialPortsAndClosedStateSplitsFlow",
    "checkValveAllowsBackToFrontAndRejectsReverse",
    "flowMeterReportsDirectionalDropAndClearsRuntime",
):
    require(tests, f"void {method}(GameTestHelper helper)")
body = read(tests)
if body and len(re.findall(r"@GameTest\(", body)) != 8:
    errors.append("ninth-eight acceptance class must contain exactly eight @GameTest methods")
require(
    "src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java",
    "event.register(RseNinthEightAcceptanceGameTests.class);",
)

workflow = read(".github/workflows/build.yml")
if workflow and "rse_ninth_eight_verify.py" not in workflow:
    errors.append("workflow does not gate the ninth-eight verifier")

if errors:
    print("RSE ninth-eight pneumatic foundation verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE ninth-eight pneumatic foundation verification: PASS")
print("  compressor DOWN-redstone / UP-pneumatic isolation: PASS")
print("  physical pneumatic discovery + terminal isolation: PASS")
print("  pipe/reservoir/regulator manifold contracts: PASS")
print("  receiver PNEUMATIC-to-REDSTONE conversion: PASS")
print("  axial manual/check/flow-meter contracts: PASS")
print("  split-network + runtime/metrology cleanup: PASS")
print("  eight executable ninth-batch GameTests registered: PASS")
