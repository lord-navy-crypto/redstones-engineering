#!/usr/bin/env python3
"""Static contracts for the sixth 10-block design + bug audit (registered blocks 51-60)."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        raise SystemExit(f"missing sixth-ten file: {rel}")
    return path.read_text(encoding="utf-8")


def require(rel: str, *tokens: str) -> None:
    body = read(rel)
    missing = [token for token in tokens if token not in body]
    if missing:
        raise SystemExit(f"{rel}: missing sixth-ten contract tokens {missing}")


require(
    "src/main/java/dev/redstoneengineering/block/InductionCoilBlock.java",
    '"MAGNETIC SENSE"', '"INDUCED COPPER OUT"',
    "RuntimeIntStore.peek", "MagneticPhysics.fieldSample", "PortQuality.STALE",
    "delta * s.getValue(TURNS)",
)
require(
    "src/main/java/dev/redstoneengineering/block/MagneticGradientMeterBlock.java",
    "record GradientSample", "MagneticPhysics.fieldSample", "plus.complete() && minus.complete()",
    "sample.complete() ? PortQuality.VALID : PortQuality.STALE",
)
gradient = read("src/main/java/dev/redstoneengineering/block/MagneticGradientMeterBlock.java")
if "component == 0 ? PortQuality.NO_SIGNAL" in gradient:
    raise SystemExit("MagneticGradientMeterBlock still confuses a real zero gradient with NO_SIGNAL")

require(
    "src/main/java/dev/redstoneengineering/block/ThermalHeaterBlock.java",
    "CopperNetworkSupport.terminalInputOnSide", "input.quality()", "CircuitPhysics.power",
)
require(
    "src/main/java/dev/redstoneengineering/block/ThermalRadiatorBlock.java",
    "Passive heat sink", "Math.max(ThermalPhysics.AMBIENT, t - cooling)",
)
require(
    "src/main/java/dev/redstoneengineering/block/ThermalCalorimeterBlock.java",
    "record History", "RuntimeIntStore.peek", "STALE HISTORY", "RuntimeIntStore.remove",
)
calorimeter = read("src/main/java/dev/redstoneengineering/block/ThermalCalorimeterBlock.java")
use_body = calorimeter.split("useWithoutItem", 1)[1] if "useWithoutItem" in calorimeter else ""
if "RuntimeIntStore.get" in use_body:
    raise SystemExit("ThermalCalorimeter inspection still creates history runtime")

require(
    "src/main/java/dev/redstoneengineering/physics/RedstoneCableNetwork.java",
    "record SourceEvidence", 'EVIDENCE_KEY = "redstone_cable_source_evidence"',
    "sourceCount++", "sourceCount > 0 ? PortQuality.VALID : PortQuality.NO_SIGNAL",
    "PriorityQueue<Node>", "bestVoltage" if False else "best.put(pos, power)",
)
network = read("src/main/java/dev/redstoneengineering/physics/RedstoneCableNetwork.java")
if "sourceCount > 1 ? PortQuality.TOPOLOGY_ERROR" in network:
    raise SystemExit("Insulated redstone must retain Vanilla-like multi-source strongest-value semantics")

require(
    "src/main/java/dev/redstoneengineering/block/RedstoneSignalCableBlock.java",
    "RuntimeIntStore.peek", "RedstoneCableNetwork.sourceEvidence", "RedstoneCableNetwork.removeEvidence",
)
cable = read("src/main/java/dev/redstoneengineering/block/RedstoneSignalCableBlock.java")
power_body = cable.split("public static int power", 1)[1].split("@Override", 1)[0]
if "RuntimeIntStore.get" in power_body:
    raise SystemExit("RedstoneSignalCableBlock.power() is not observer-neutral")

require(
    "src/main/java/dev/redstoneengineering/block/RedstoneCableTerminalBlock.java",
    "state.getValue(OUTPUT_MODE) != oldState.getValue(OUTPUT_MODE)",
    ".setValue(POWER, 0)", "RedstoneCableNetwork.sourceEvidence", "RedstoneCableNetwork.removeEvidence",
)
require(
    "src/main/java/dev/redstoneengineering/block/RedstoneReferenceSourceBlock.java",
    'IntegerProperty.create("power", 0, 15)', '"REFERENCE OUT"',
    "PortDirection.OUTPUT", "PortQuality.VALID",
)
reference = read("src/main/java/dev/redstoneengineering/block/RedstoneReferenceSourceBlock.java")
if reference.count("new EngineeringPort(") != 1:
    raise SystemExit("Redstone Reference Source must remain a single-FRONT engineering source")

require(
    "src/main/java/dev/redstoneengineering/metrology/MetrologySupport.java",
    "measurement.sampleCount() == 0", "return PortQuality.STALE", "awaiting first sample",
)
require(
    "src/main/java/dev/redstoneengineering/block/EngineeringLightSensorBlock.java",
    "MetrologySupport.portQuality(sensorMeasurement(level, pos))", "conditionRedstone", "sampleMeasurement",
)
require(
    "src/main/java/dev/redstoneengineering/block/TankLevelSensorBlock.java",
    "record ColumnSample", "if (!level.hasChunkAt(sample))", "false);",
    "if (!column.complete())", "Retain the last trustworthy output/sample", "PortQuality.STALE",
)
tank = read("src/main/java/dev/redstoneengineering/block/TankLevelSensorBlock.java")
if "!level.hasChunkAt(sample) || level.getFluidState(sample).isEmpty()" in tank:
    raise SystemExit("Tank level sensor still collapses unloaded coverage into an empty-fluid boundary")

# Junction is shared infrastructure touched by the cable audit: redstone readback must also be observer-only.
require(
    "src/main/java/dev/redstoneengineering/block/RedstoneCableJunctionBlock.java",
    "RuntimeIntStore.peek", "RedstoneCableNetwork.sourceEvidence", "RedstoneCableNetwork.removeEvidence",
)

# Exactly ten executable regression scenes, one for each audited registered block.
tests_rel = "src/main/java/dev/redstoneengineering/gametest/RseSixthTenDesignBugGameTests.java"
tests = read(tests_rel)
methods = (
    "inductionInspectionIsNeutralAndStaticFieldDoesNotBecomeSustainedEmf",
    "magneticGradientZeroIsAValidCompleteMeasurement",
    "thermalHeaterDistinguishesValidZeroCopperFromAbsentFeed",
    "thermalRadiatorStopsAtAmbientFloor",
    "thermalCalorimeterInspectionDoesNotCreateHistory",
    "redstoneCableDistinguishesDrivenZeroFromUndrivenZeroWithoutObserverMutation",
    "redstoneTerminalModeChangeClearsCachedRoleValue",
    "redstoneReferenceZeroIsValidAndSingleEnded",
    "engineeringLightSensorIsStaleBeforeFirstSampleAndZeroCanBeValid",
    "tankColumnCoverageSeparatesUnknownFromLoadedEmptyZero",
)
for method in methods:
    if f"void {method}(GameTestHelper helper)" not in tests:
        raise SystemExit(f"{tests_rel}: missing GameTest method {method}")
if tests.count("@GameTest(") != 10:
    raise SystemExit(f"expected 10 sixth-ten GameTests, found {tests.count('@GameTest(')}")

registration = read("src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java")
if "event.register(RseSixthTenDesignBugGameTests.class);" not in registration:
    raise SystemExit("sixth-ten GameTests are not registered")
if "event.register(RseFifthTenDesignBugGameTests.class);" not in registration:
    raise SystemExit("fifth-ten regression GameTests were accidentally dropped")

workflow = read(".github/workflows/build.yml")
if "rse_sixth_ten_system_design_bug_verify.py" not in workflow:
    raise SystemExit("workflow does not gate the sixth-ten verifier")
if "test_count < 249" not in workflow:
    raise SystemExit("workflow GameTest floor was not raised to 249")

print("RSE sixth-ten system design + bug verification: PASS")
print("  induction transient/read-only evidence: PASS")
print("  magnetic zero-gradient + coverage semantics: PASS")
print("  heater/radiator/calorimeter role boundaries: PASS")
print("  insulated redstone value/source/terminal lifecycle: PASS")
print("  reference valid-zero and FRONT-only source: PASS")
print("  metrology first-sample STALE + tank coverage semantics: PASS")
print("  ten executable sixth-ten GameTests registered: PASS")
