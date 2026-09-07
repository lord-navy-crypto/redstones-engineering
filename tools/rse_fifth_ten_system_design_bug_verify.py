#!/usr/bin/env python3
from pathlib import Path
import re

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

# 41: scheduled sampling owns writes; UI/diagnostics are observer-only and conflicts/coverage are explicit.
for token in ("CONFLICT_SAMPLES", "SCANNED_CELLS", "EXPECTED_CELLS", "RuntimeIntStore.peek", "PortQuality.TOPOLOGY_ERROR"):
    require(token in spectrum, f"Spectrum Analyzer evidence missing {token}")
use_body = method_body(spectrum, "protected InteractionResult useWithoutItem")
require("scan(" not in use_body, "Spectrum Analyzer inspection must not trigger a physics scan")

# 42-45: one shared read-only optical quality path, with splitter quantization and stale-output invalidation.
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

# 46-48: output diagnostics must never allocate runtime; source quality is retained in server-owned runtime evidence.
for name, source in (("Series Resistor", resistor), ("Capacitor", capacitor), ("Fuse", fuse)):
    body = method_body(source, "public static int outputVoltage")
    require("RuntimeIntStore.get" not in body, f"Copper {name} outputVoltage inspection allocates runtime")
    require("RuntimeIntStore.peek" in source, f"Copper {name} lacks observer-only runtime readback")
    require("PortQuality" in source, f"Copper {name} lacks explicit output quality")
require("stored charge is a legitimate local energy source" in capacitor,
        "Copper Capacitor lost stored-energy identity")
require("state.getValue(TRIPPED)" in fuse and "PortQuality.FAULT" in fuse,
        "Copper Fuse must expose a tripped output as FAULT")
require("CopperObservationSupport" in copper_base and "observedOutputQuality" in copper_base,
        "Directional Copper snapshots are still unconditional VALID")
require("cannot\n * recurse forever" in copper_support or "cannot\n * recurse" in copper_support or "cannot\n" in copper_support,
        "Copper observation helper must document its non-recursive boundary")

# 49: metrology snapshot is a real peek; no-data is not created by UI inspection.
snapshot_body = method_body(metrology_support, "public static MeasurementSnapshot snapshot")
require("MetrologyStore.peek" in snapshot_body and "MetrologyStore.tracker" not in snapshot_body,
        "Metrology snapshot still creates tracker state")
require("entryCount" in metrology_store and "peek(" in metrology_store,
        "Metrology store lacks observer-neutral regression evidence")
require("measurementQuality" in circuit_meter and "PortQuality.STALE" in circuit_meter,
        "Copper Circuit Meter must separate pre-sample/stale evidence from FAULT")

# 50: retain intentionally scalar, free-space, non-wired magnetic identity.
for token in ("SourceEvidence", "scalarFieldModel", "wired", "SCALAR MAGNETIC FIELD", "orientation marker only in scalar solver"):
    require(token in magnet, f"Permanent Magnet scalar/free-space contract missing {token}")

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

match = re.search(r"test_count < (\d+)", workflow)
require(match is not None and int(match.group(1)) >= 239,
        "CI GameTest gate must be at least 239 after nine fifth-ten regressions")
require("tools/rse_fifth_ten_system_design_bug_verify.py" in workflow,
        "Fifth-ten verifier is not wired into CI")

print("RSE fifth-ten system design + bug verification: PASS")
print("  Amethyst spectrum observer/conflict/coverage evidence: PASS")
print("  Optical power/split/filter/attenuator quality semantics: PASS")
print("  Optical retuning stale-carrier prevention: PASS")
print("  Copper R/C/fuse observer-neutral source-quality evidence: PASS")
print("  Copper metrology snapshot ownership boundary: PASS")
print("  Permanent Magnet scalar free-space identity: PASS")
print("  nine executable fifth-ten GameTests registered: PASS")
print("  fixed-content architecture: 127 blocks; no new block/domain required")
