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
    "PneumaticReceiverBlock",
    "PneumaticValveBlock",
    "PneumaticCheckValveBlock",
    "PneumaticFlowMeterBlock",
    "PneumaticProportionalValveBlock",
    "PneumaticReliefValveBlock",
    "directionalForward",
    "directionalBackwardEntry",
    "recomputeAround",
    "PneumaticReliefValveBlock.recordVent",
    "PneumaticReliefValveBlock.clearVenting",
    '"pneumatic_relief"',
)

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
require(
    "src/main/java/dev/redstoneengineering/block/PneumaticProportionalValveBlock.java",
    "EntityBlock, EngineeringPortProvider",
    '"PNEUMATIC IN"',
    '"PNEUMATIC OUT"',
    '"OPENING COMMAND"',
    "EngineeringDomain.PNEUMATIC",
    "EngineeringDomain.REDSTONE",
    "direction.getOpposite() == Direction.UP",
    "PneumaticNetwork.recomputeAround",
    "FieldDeviceUi.open",
)
require(
    "src/main/java/dev/redstoneengineering/block/PneumaticReliefValveBlock.java",
    "implements EngineeringPortProvider",
    '"PNEUMATIC IN"',
    '"LIMITED OUT"',
    "DIAG_SIZE = 4",
    "recordVent",
    "clearVenting",
    "if (diag[3] == 0) diag[0]++",
    "RuntimeIntStore.remove",
    "PneumaticNetwork.recomputeAround",
    "FieldDeviceUi.open",
)

# Supporting lifecycle regression: source removal must not leave a ghost pressure driver.
require(
    "src/main/java/dev/redstoneengineering/block/AirCompressorBlock.java",
    'InformationRuntime.clear(level, "pneumatic", pos)',
    "PneumaticNetwork.recomputeAround",
)

# Runtime pressure/flow/counters are high-cardinality and must stay outside BlockState.
joined = "\n".join(read(rel) for rel in (
    "src/main/java/dev/redstoneengineering/block/PneumaticPipeBlock.java",
    "src/main/java/dev/redstoneengineering/block/AirReservoirBlock.java",
    "src/main/java/dev/redstoneengineering/block/PneumaticReceiverBlock.java",
    "src/main/java/dev/redstoneengineering/block/PneumaticValveBlock.java",
    "src/main/java/dev/redstoneengineering/block/PneumaticCheckValveBlock.java",
    "src/main/java/dev/redstoneengineering/block/PneumaticFlowMeterBlock.java",
    "src/main/java/dev/redstoneengineering/block/PneumaticProportionalValveBlock.java",
    "src/main/java/dev/redstoneengineering/block/PneumaticReliefValveBlock.java",
))
for forbidden in (
    'IntegerProperty.create("pressure",',
    'IntegerProperty.create("stored_pressure",',
    'IntegerProperty.create("flow",',
    'IntegerProperty.create("vent_events",',
):
    if forbidden in joined:
        errors.append(f"ninth-eight high-cardinality runtime leaked into BlockState: {forbidden}")

# Exactly eight executable ninth-batch acceptance tests.
tests = "src/main/java/dev/redstoneengineering/gametest/RseNinthEightAcceptanceGameTests.java"
for method in (
    "pneumaticPipeBreakRecomputesSeparatedIsland",
    "airReservoirStoresAndClearsTransientPressure",
    "pneumaticReceiverIsTerminalConverterNotBridge",
    "manualValveUsesAxialPortsAndClosedStateSplitsFlow",
    "checkValveAllowsBackToFrontAndRejectsReverse",
    "flowMeterReportsDirectionalDropAndClearsRuntime",
    "proportionalValveUsesUpCommandAndAxialPneumaticPorts",
    "reliefValveClampsAndCountsVentEdges",
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
    print("RSE ninth-eight pneumatic verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE ninth-eight pneumatic verification: PASS")
print("  physical pneumatic edge topology + terminal isolation: PASS")
print("  six-way pipe/reservoir contracts + split-network recomputation: PASS")
print("  receiver PNEUMATIC-to-REDSTONE domain isolation: PASS")
print("  axial manual/check/flow/proportional/relief contracts: PASS")
print("  flow metrology + transient lifecycle cleanup: PASS")
print("  compressor ghost-source cleanup: PASS")
print("  relief overpressure edge-count diagnostics: PASS")
print("  eight executable ninth-batch GameTests registered: PASS")
