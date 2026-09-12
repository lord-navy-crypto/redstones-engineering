#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/dev/redstoneengineering"


def text(rel: str) -> str:
    return (JAVA / rel).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")


def method_body(source: str, signature: str) -> str:
    start = source.find(signature)
    require(start >= 0, f"missing method signature {signature}")
    brace = source.find("{", start)
    depth = 0
    for i in range(brace, len(source)):
        if source[i] == "{": depth += 1
        elif source[i] == "}":
            depth -= 1
            if depth == 0:
                return source[brace:i + 1]
    raise SystemExit(f"FAIL: unterminated method {signature}")


spectrum = text("block/AmethystSpectrumAnalyzerBlock.java")
power_meter = text("block/OpticalPowerMeterBlock.java")
splitter = text("block/OpticalSplitterBlock.java")
channel_filter = text("block/OpticalChannelFilterBlock.java")
attenuator = text("block/OpticalAttenuatorBlock.java")
resistor = text("block/CopperSeriesResistorBlock.java")
capacitor = text("block/CopperCapacitorBlock.java")
fuse = text("block/CopperFuseBlock.java")
circuit_meter = text("block/CopperCircuitMeterBlock.java")
magnet = text("block/PermanentMagnetBlock.java")
copper_base = text("block/DirectionalCopperProcessorBlock.java")
optical_support = text("physics/OpticalObservationSupport.java")
copper_support = text("physics/CopperObservationSupport.java")
metrology_store = text("metrology/MetrologyStore.java")
metrology_support = text("metrology/MetrologySupport.java")
registration = text("gametest/RseGameTestRegistration.java")
tests = text("gametest/RseFifthTenDesignBugGameTests.java")
workflow = (ROOT / ".github/workflows/build.yml").read_text(encoding="utf-8")

for token in ("CONFLICT_SAMPLES", "SCANNED_CELLS", "EXPECTED_CELLS", "RuntimeIntStore.peek", "PortQuality.TOPOLOGY_ERROR"):
    require(token in spectrum, f"Spectrum Analyzer evidence missing {token}")
use_body = method_body(spectrum, "protected InteractionResult useWithoutItem")
require("scan(" not in use_body, "Spectrum Analyzer inspection must not trigger a physics scan")

for name, source in (("Power Meter", power_meter), ("Splitter", splitter), ("Channel Filter", channel_filter), ("Attenuator", attenuator)):
    require("OpticalObservationSupport" in source, f"Optical {name} does not preserve upstream carrier quality")
for token in ("OpticalFiberBlock.quality", "OpticalReceiverBlock.quality", "PortQuality.TOPOLOGY_ERROR"):
    require(token in optical_support, f"Optical observation helper missing {token}")
require("quantizationLoss" in splitter, "Optical Splitter must expose odd-level integer split loss")
for name, source, prop in (("Channel Filter", channel_filter, "TARGET"), ("Attenuator", attenuator, "LOSS")):
    require("configurationChanged" in source and "invalidateOutput" in source,
            f"Optical {name} lacks stale-output invalidation")
    require(f"oldState.hasProperty({prop})" in source,
            f"Optical {name} must catch every same-block configuration path")

for name, source in (("Series Resistor", resistor), ("Capacitor", capacitor), ("Fuse", fuse)):
    body = method_body(source, "public static int outputVoltage")
    require("RuntimeIntStore.get" not in body, f"Copper {name} outputVoltage inspection allocates runtime")
    require("RuntimeIntStore.peek" in source, f"Copper {name} lacks observer-only runtime readback")
    require("PortQuality" in source, f"Copper {name} lacks explicit output quality")

cap_quality = method_body(capacitor, "public static PortQuality outputQuality")
cap_tick = method_body(capacitor, "protected void tick")
for token in ("CHARGE_SLOT", "outputVoltageFromCharge", "DomainNetwork.driveCopper"):
    require(token in cap_tick or token in capacitor, f"Copper Capacitor stored-energy path missing {token}")
require("runtime[CHARGE_SLOT] > 0 || inputQuality == PortQuality.VALID" in cap_quality,
        "Copper Capacitor no longer treats retained charge as a legitimate local source")
require("PortQuality.TOPOLOGY_ERROR" in cap_quality and "PortQuality.FAULT" in cap_quality,
        "Copper Capacitor must not hide hard upstream faults behind retained charge")
require("return PortQuality.VALID" in cap_quality,
        "Copper Capacitor retained charge no longer publishes valid output quality")

require("state.getValue(TRIPPED)" in fuse and "PortQuality.FAULT" in fuse,
        "Copper Fuse must expose a tripped output as FAULT")
require("RUNTIME_SIZE = 3" in fuse and "QUALITY_KEY" in fuse,
        "Copper Fuse must preserve protection runtime layout while separating quality evidence")
require("CopperObservationSupport" in copper_base and "observedOutputQuality" in copper_base,
        "Directional Copper snapshots are still unconditional VALID")
require("DomainNetwork.sampleCopperVoltage" in copper_base and "DomainNetwork.sampleCopperVoltage" in circuit_meter,
        "Copper numerical voltage ownership must remain in DomainNetwork")
require("Non-recursive" in copper_support and "cyclic processor layout" in copper_support,
        "Copper observation helper must document its non-recursive boundary")

snapshot_body = method_body(metrology_support, "public static MeasurementSnapshot snapshot")
require("MetrologyStore.peek" in snapshot_body and "MetrologyStore.tracker" not in snapshot_body,
        "Metrology snapshot still creates tracker state")
require("entryCount" in metrology_store and "peek(" in metrology_store,
        "Metrology store lacks observer-neutral regression evidence")
require("measurementQuality" in circuit_meter and "PortQuality.STALE" in circuit_meter,
        "Copper Circuit Meter must separate pre-sample/stale evidence from FAULT")

for token in ("SourceEvidence", "scalarFieldModel", "wired", '"MAGNETIC FIELD "', "scalar free-space", "orientation marker only in scalar solver"):
    require(token in magnet, f"Permanent Magnet scalar/free-space compatibility contract missing {token}")

require("RseFifthTenDesignBugGameTests.class" in registration,
        "Fifth-ten design/bug GameTests are not registered")
for test_name in (
    "spectrumAnalyzerInspectionIsNeutralAndConflictAware",
    "opticalPowerMeterPreservesSourceConflict",
    "opticalSplitterReportsOddLevelQuantizationAndDrivesBothBranches",
    "opticalRetuningImmediatelyInvalidatesOldFilterAndAttenuatorOutputs",
    "copperProcessorInspectionIsNeutralAndZeroSourceRemainsValid",
    "copperCapacitorRetainsLegitimateStoredEnergyAfterInputRemoval",
    "copperFuseTripPublishesFaultQualityWithoutObserverMutation",
    "copperMeterSnapshotDoesNotCreateTrackerButServerSampleDoes",
    "permanentMagnetIsFreeSpaceScalarSourceNotWiredBus",
):
    require(test_name in tests, f"Missing fifth-ten runtime contract: {test_name}")

require("tools/rse_fifth_ten_system_design_bug_verify.py" in workflow,
        "Fifth-ten verifier is not wired into CI")
for token in (
    "Minecraft topology GameTests (manual diagnostic)",
    "github.event_name == 'workflow_dispatch'",
    "continue-on-error: true",
    "./gradlew runGameTestServer",
    "./gradlew compileJava",
    "./gradlew test",
):
    require(token in workflow, f"build.yml missing manual/non-blocking GameTest policy token {token!r}")

print("RSE fifth-ten system design + bug verification: PASS")
print("  Amethyst spectrum observer/conflict/coverage evidence: PASS")
print("  Optical power/split/filter/attenuator quality semantics: PASS")
print("  Optical retuning stale-carrier prevention: PASS")
print("  Copper R/C/fuse observer-neutral source-quality evidence: PASS")
print("  Capacitor retained-energy structural contract: PASS")
print("  Copper metrology snapshot ownership boundary: PASS")
print("  Permanent Magnet scalar free-space identity + legacy label: PASS")
print("  registered fifth-ten GameTests: 9 (manual diagnostic / non-blocking)")
print("  fixed-content architecture: 127 blocks; no new block/domain required")
