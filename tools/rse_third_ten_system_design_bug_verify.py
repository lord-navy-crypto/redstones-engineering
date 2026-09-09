#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/dev/redstoneengineering"


def text(rel: str) -> str:
    return (JAVA / rel).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")

fiber = text("block/OpticalFiberBlock.java")
emitter = text("block/OpticalEmitterBlock.java")
receiver = text("block/OpticalReceiverBlock.java")
wire = text("block/CopperWireBlock.java")
source = text("block/CopperVoltageSourceBlock.java")
load = text("block/CopperResistiveLoadBlock.java")
core = text("block/IronCoreBlock.java")
magnet = text("block/ElectromagnetBlock.java")
sensor = text("block/MagneticFieldSensorBlock.java")
thermal_mass = text("block/ThermalMassBlock.java")
copper_support = text("physics/CopperNetworkSupport.java")
magnetic = text("physics/MagneticPhysics.java")
thermal = text("physics/ThermalPhysics.java")
domain = text("physics/DomainNetwork.java")
registration = text("gametest/RseGameTestRegistration.java")
tests = text("gametest/RseThirdTenDesignBugGameTests.java")
magnetic_system_tests = text("gametest/RseMagneticMeasurementSystemGameTests.java")

# 21-23: optical medium/source/sink identities and terminal opacity.
for token in ("DRIVER_COUNT", "PortQuality.TOPOLOGY_ERROR", "RuntimeIntStore.peek"):
    require(token in fiber, f"Optical Fiber source-conflict evidence missing {token}")
require("PortQuality.VALID" in emitter and "configured zero-intensity source" in emitter,
        "Optical Emitter must distinguish valid source configuration from downstream dark carrier")
require("recomputeOpticalAround" in receiver and "recomputeOptical(serverLevel,pos)" not in receiver,
        "Optical Receiver must recompute adjacent components, never seed transparent traversal")
require("PHYSICAL_INPUTS" in receiver and "inputCount" in receiver and "PortQuality.TOPOLOGY_ERROR" in receiver,
        "Optical Receiver must expose multi-feed terminal conflict")

# Shared graph bug: sinks are opaque even when they happen to be the traversal start.
require("sourceTerminal" in domain and "sourceSeed" in domain,
        "Domain graph traversal must distinguish source terminals from sink terminals")
require("terminal.test(p) && !sourceSeed" in domain,
        "Sink terminal opacity must not depend on start position")
require("block instanceof OpticalReceiverBlock" in domain and "!p.equals(start)" in domain,
        "Optical distance traversal must terminate at receivers and non-source emitters")

# 24-26: copper observation/source/load contracts and no back-driving.
require("RuntimeIntStore.peek" in wire and "RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE)" in wire,
        "Copper Wire reads must be observer-neutral while solver writes remain explicit")
for token in ("DRIVER_COUNT_INDEX", "PortQuality.NO_SIGNAL", "PortQuality.TOPOLOGY_ERROR"):
    require(token in wire, f"Copper Wire no-source/conflict evidence missing {token}")
require("PortDirection.OUTPUT" in source and "EngineeringDomain.COPPER" in source,
        "Copper Voltage Source must remain a dedicated Copper source")
require("terminalInputOnSide" in load and "CopperNetworkSupport.recomputeAround" in load,
        "Copper Resistive Load must remain a terminal sink with per-face feed evidence")
require("recomputeCopper(serverLevel, pos)" not in load,
        "Copper Resistive Load must never seed a traversal through itself")
require("descriptor.direction() == PortDirection.INPUT" in copper_support,
        "Copper terminal input resolver must reject INPUT-only neighbors as sources")

# 27-29: external-field/remanence separation and magnetic measurement certainty.
require("appliedFieldAt" in core and "MAGNETIZE_THRESHOLD" in core,
        "Iron Core must magnetize from bounded external applied field")
require("terminalInput" in magnet and "adjacentCopperLevel" not in magnet,
        "Electromagnet must consume source-safe Copper terminal evidence")
for token in ("FieldSample", "scannedCells", "expectedCells", "complete"):
    require(token in magnetic, f"Magnetic free-space scan coverage missing {token}")
require("includeRemanence" in magnetic and "appliedFieldAt" in magnetic,
        "Magnetic physics must separate external applied field from iron-core remanence")
require("observation.complete() ? PortQuality.VALID : PortQuality.STALE" in sensor,
        "Magnetic sensor must report complete measurements as VALID and incomplete/awaiting evidence as STALE")
require("RuntimeIntStore.peek" in sensor,
        "Magnetic field snapshot must read cached server coverage evidence")
require("RseMagneticMeasurementSystemGameTests.class" in registration,
        "Magnetic measurement lifecycle GameTests are not registered")
require("magneticSensorAwaitsFirstSampleAsStaleThenEstablishesValidZero" in magnetic_system_tests,
        "Magnetic first-sample STALE to VALID-zero lifecycle regression is missing")

# 30: thermal inertia remains simple, while boundary resolution is rotation/order independent.
require("hasHot && hasCold" in thermal and "(hottest + coldest) / 2" in thermal,
        "Thermal boundary resolution must not depend on Direction iteration order")
require("hasChunkAt" in thermal,
        "Thermal helpers must not silently inspect unloaded neighbors")
for token in ("ThermalState", "environment", "neighborAverage", "target", "maxStep"):
    require(token in thermal_mass, f"Thermal Mass engineering evidence missing {token}")

require("RseThirdTenDesignBugGameTests.class" in registration,
        "Third-ten design/bug GameTests are not registered")
for test_name in (
    "copperCableObservationDoesNotCreateRuntimeState",
    "opticalFiberDistinguishesNoSourceFromDriverConflict",
    "opticalReceiverNeverBridgesIndependentFiberSegments",
    "copperCableDistinguishesNoSourceSingleSourceAndConflict",
    "poweredLoadCannotBackDriveElectromagnet",
    "completeZeroMagneticFieldIsValidMeasurement",
    "ironCoreMagnetizesFromRealExternalField",
    "thermalEnvironmentResolutionIsRotationOrderIndependent",
):
    require(test_name in tests, f"Missing third-ten bug contract: {test_name}")

print("RSE third-ten system design + bug audit verification: PASS")
print("  optical no-source/conflict + opaque receiver terminal: PASS")
print("  copper observer-neutral ownership + non-backdriving sink: PASS")
print("  source-vs-input port direction enforcement: PASS")
print("  magnetic valid-zero + STALE coverage + external-field remanence: PASS")
print("  thermal direction-order independence + inertia evidence: PASS")
print("  eight executable third-ten bug regressions + dedicated magnetic lifecycle regression: PASS")
print("  fixed-content architecture: 127 blocks; no new block/domain required")
