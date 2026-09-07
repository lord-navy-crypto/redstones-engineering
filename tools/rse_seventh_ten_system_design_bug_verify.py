#!/usr/bin/env python3
"""Static contracts for the seventh 10-block design + bug audit (registered blocks 61-70)."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/dev/redstoneengineering"


def text(rel: str) -> str:
    path = JAVA / rel
    if not path.is_file():
        raise SystemExit(f"missing seventh-ten file: {rel}")
    return path.read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")


def method_body(source: str, signature: str) -> str:
    start = source.find(signature)
    require(start >= 0, f"missing method signature {signature}")
    brace = source.find("{", start)
    depth = 0
    for i in range(brace, len(source)):
        if source[i] == "{":
            depth += 1
        elif source[i] == "}":
            depth -= 1
            if depth == 0:
                return source[brace:i + 1]
    raise SystemExit(f"FAIL: unterminated method {signature}")


entity = text("block/EntityDensitySensorBlock.java")
indicator = text("block/AnalogIndicatorBlock.java")
junction = text("block/RedstoneCableJunctionBlock.java")
optical_junction = text("block/OpticalFiberJunctionBlock.java")
copper_junction = text("block/CopperCableJunctionBlock.java")
transducer = text("block/AbstractLapisTransducerBlock.java")
temperature = text("block/LapisTemperatureTransducerBlock.java")
magnetic = text("block/LapisMagneticTransducerBlock.java")
optical = text("block/LapisOpticalTransducerBlock.java")
voltage = text("block/LapisVoltageTransducerBlock.java")
range_sensor = text("block/LapisPrecisionRangeSensorBlock.java")
registration = text("gametest/RseGameTestRegistration.java")
tests = text("gametest/RseSeventhTenDesignBugGameTests.java")
workflow = (ROOT / ".github/workflows/build.yml").read_text(encoding="utf-8")

# 61 — occupancy count is trustworthy only with complete chunk coverage.
for token in ("record DensitySample", "level.hasChunkAt(cursor)", "PortQuality.STALE", "if (!sample.complete())", "last trustworthy output"):
    require(token in entity, f"Entity Density Sensor coverage contract missing {token}")

# 62 — display value and source evidence are independent; a configured zero source is real.
for token in ("record InputObservation", "PortQuality.STALE", "PortQuality.NO_SIGNAL", "EngineeringPortProvider", "port.direction() != PortDirection.INPUT"):
    require(token in indicator, f"Analog Indicator source-evidence contract missing {token}")
snapshot_body = method_body(indicator, "public Optional<EngineeringPortSnapshot> engineeringSnapshot")
require("inputObservation" in snapshot_body and "PortQuality.VALID" not in snapshot_body,
        "Analog Indicator snapshot still fabricates unconditional VALID")

# 63 — every advertised information-medium junction port must have a diagnostic snapshot.
for token in ('case INSTRUMENT', 'case DATA_BUS_8', 'case SERIAL', 'case DIFFERENTIAL', "InformationRuntime.snapshot"):
    require(token in junction, f"Signal Junction non-redstone snapshot contract missing {token}")
require(
    "PortQuality.STALE" in junction
    or all(token in junction for token in (
        "DataBusNetwork.quality(level, pos)",
        "SerialNetwork.quality(level, pos)",
        "DifferentialNetwork.quality(level, pos)",
    )),
    "Signal Junction non-redstone snapshot contract lost explicit stale/quality projection",
)

# 64 — opening an optical service splice is a hard isolation boundary, including retained runtime.
service_body = method_body(optical_junction, "public void setServiceOpen")
require("RuntimeIntStore.remove(level, KEY, pos)" in service_body,
        "Optical service-open path leaves retained carrier runtime")
require("SERVICE_OPEN" in optical_junction and "DomainNetwork.recomputeOpticalAround" in service_body,
        "Optical service splice isolation/recompute contract regressed")

# 65 — Copper junction readback remains observer-neutral and keeps source-count semantics separate from voltage.
for token in ("RuntimeIntStore.peek", "DRIVER_COUNT_INDEX", "drivers > 1", "drivers == 1 ? PortQuality.VALID : PortQuality.NO_SIGNAL"):
    require(token in copper_junction, f"Copper Junction evidence contract missing {token}")

# 66-70 share one quality-aware, observer-neutral transducer runtime contract.
for token in ("record Measurement(int normalized, PortQuality quality", "RuntimeIntStore.peek", "outputQuality", "PortQuality.STALE", "invalidateOutput"):
    require(token in transducer, f"Shared Lapis transducer contract missing {token}")
for signature in ("public int output", "public PortQuality outputQuality", "public boolean valid"):
    body = method_body(transducer, signature)
    require("RuntimeIntStore.get" not in body, f"{signature} still allocates runtime during observation")

# 66 — missing thermal probe coverage is unknown, not ambient-by-assumption.
require("level.hasChunkAt(probe)" in temperature and "PortQuality.STALE" in temperature,
        "Temperature transducer does not surface missing probe coverage")

# 67 — magnetic scan coverage must survive transduction.
require("MagneticPhysics.fieldSample" in magnetic and "sample.complete() ? PortQuality.VALID : PortQuality.STALE" in magnetic,
        "Magnetic transducer still discards field scan coverage")
require("MagneticPhysics.fieldAt" not in magnetic,
        "Magnetic transducer still uses coverage-blind fieldAt")

# 68 — topology/conflict evidence must survive optical -> Lapis conversion.
require("OpticalObservationSupport.observe" in optical and "observation.quality()" in optical,
        "Optical transducer collapses carrier quality to a boolean")

# 69 — numerical Copper voltage and Copper source/topology evidence have different owners.
require("DomainNetwork.sampleCopperVoltage" in voltage and "CopperObservationSupport.measure" in voltage and "observation.quality()" in voltage,
        "Voltage transducer does not separate numeric voltage from source quality")

# 70 — no target in complete coverage and unknown unloaded coverage are distinct outcomes.
for token in ("record RangeSample", "new RangeSample(-1, max, false)", "PortQuality.STALE", "PortQuality.NO_SIGNAL"):
    require(token in range_sensor, f"Precision Range Sensor coverage contract missing {token}")

methods = (
    "entityDensityIncompleteCoverageIsStaleInsteadOfLowCount",
    "analogIndicatorDistinguishesDrivenZeroFromEmptyInput",
    "signalJunctionNonRedstonePortHasObserverNeutralSnapshot",
    "opticalServiceOpenClearsRetainedCarrierEvidence",
    "copperJunctionSeparatesNoSourceValidZeroAndConflict",
    "temperatureTransducerInspectionIsNeutralBeforeFirstSample",
    "magneticTransducerPropagatesFieldCoverageQuality",
    "opticalTransducerPreservesUpstreamTopologyConflict",
    "voltageTransducerSeparatesUndrivenWireFromValidZeroSource",
    "precisionRangeSeparatesNoTargetFromUnknownCoverage",
)
for method in methods:
    require(f"void {method}(GameTestHelper helper)" in tests, f"Missing seventh-ten GameTest {method}")
require(tests.count("@GameTest(") == 10,
        f"expected 10 seventh-ten GameTests, found {tests.count('@GameTest(')}")
require("event.register(RseSeventhTenDesignBugGameTests.class);" in registration,
        "Seventh-ten GameTests are not registered")
require("event.register(RseSixthTenDesignBugGameTests.class);" in registration,
        "Sixth-ten regression registration was accidentally dropped")
require("tools/rse_seventh_ten_system_design_bug_verify.py" in workflow,
        "Workflow does not gate the seventh-ten verifier")
match = re.search(r"test_count < (\d+)", workflow)
require(match is not None and int(match.group(1)) >= 259,
        "CI GameTest gate must be at least 259 after ten seventh-ten regressions")

print("RSE seventh-ten system design + bug verification: PASS")
print("  entity-density aperture coverage and trusted-count retention: PASS")
print("  analog indicator driven-zero/source evidence: PASS")
print("  unified signal-junction cross-medium diagnostics: PASS")
print("  optical service-isolation runtime lifecycle: PASS")
print("  copper junction zero/source/conflict semantics: PASS")
print("  Lapis transducer observer-neutral quality runtime: PASS")
print("  thermal/magnetic coverage propagation: PASS")
print("  optical/copper upstream quality propagation: PASS")
print("  precision-range no-target versus unknown coverage: PASS")
print("  ten executable seventh-ten GameTests registered: PASS")
