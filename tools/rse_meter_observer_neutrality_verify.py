#!/usr/bin/env python3
"""Fourth-wave source contracts: observer-only metrology and unloaded coverage.

Source gates are not executable Minecraft GameTest runtime acceptance.
"""
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors = []
def source(rel):
    path = root / rel
    if not path.is_file():
        errors.append(f"missing file: {rel}")
        return ""
    return path.read_text(encoding="utf-8")
prefix = "src/main/java/dev/redstoneengineering/"
analyzer = source(prefix+"block/SignalAnalyzerBlock.java")
copper = source(prefix+"block/CopperCircuitMeterBlock.java")
optical = source(prefix+"block/OpticalPowerMeterBlock.java")
tests = source(prefix+"gametest/RseMeterObserverNeutralityGameTests.java")
register = source(prefix+"gametest/RseGameTestRegistration.java")
workflow = source(".github/workflows/build.yml")
def require(condition, message):
    if not condition: errors.append(message)
def method_body(text, start_marker, end_marker):
    start = text.find(start_marker)
    end = text.find(end_marker, start + 1)
    if start < 0 or end < 0:
        errors.append(f"cannot isolate method from {start_marker}")
        return ""
    return text[start:end]

snapshot = method_body(analyzer, "public static UiSnapshot uiSnapshot(", "public static boolean rotateMeasurementAxis(")
tick = method_body(analyzer, "protected void tick(BlockState state,", "protected void onRemove(BlockState state,")
require("RuntimeIntStore.peek(level, KEY, pos)" in snapshot,
        "analyzer HMI must use peek for read-only snapshot history")
require("RuntimeIntStore.get(" not in snapshot and "recordSample(" not in snapshot,
        "analyzer HMI may not allocate runtime or run a sampler")
require("new int[RUNTIME_SIZE]" in snapshot and "r[8] == 0 ? -1" in snapshot,
        "uncaptured history must render without pretending a sample exists")
require("level.hasChunkAt(pos.relative(testSide(state)))" in snapshot
        and "PortQuality.STALE" in snapshot,
        "analyzer HMI must distinguish unloaded target from observed zero")
require("if (covered) recordSample(level, pos, measured, present);" in tick,
        "unloaded target must never append a fabricated zero measurement")
require("int requestedOutput = state.getValue(MODE) == INLINE ? measured : 0;" in tick,
        "INLINE output still fails closed to zero on missing target")
require("if (!level.hasChunkAt(targetPos)) return 0;" in analyzer
        and "if (!level.hasChunkAt(targetPos)) return false;" in analyzer,
        "analyzer source reads must not force-load an uncovered target")
require("if (!covered ? PortQuality.STALE" not in analyzer,
        "guard against inverted coverage quality")
require("PortQuality quality = !covered ? PortQuality.STALE" in analyzer,
        "analyzer port snapshot must expose missing coverage quality")

for code, marker in (
    (copper, "if (!level.hasChunkAt(target))"),
    (copper, "return new CopperObservationSupport.Observation(0, PortQuality.STALE);"),
    (copper, "return new ElectricalDiagnostics(0, 0.0, 0.0, 0.0, PortQuality.STALE);"),
    (copper, "target.quality() == PortQuality.STALE ? 0.0"),
    (optical, "if (!level.hasChunkAt(target))"),
    (optical, "return new Measurement(0, 0, PortQuality.STALE);"),
    (optical, "DomainNetwork.sampleOptical(level, target)"),
    (optical, "OpticalObservationSupport.observe(level, target)"),
):
    require(marker in code, "meter lost safe covered-only reading contract: "+marker)
for method in ("void analyzerUiSnapshotDoesNotInventSampleHistory(",
               "void opticalAndCopperMetersRejectEmptyMeasurementTargets("):
    require(method in tests, "GameTest missing: "+method)
require(tests.count("@GameTest(") == 2, "exactly 2 new metrology regression cases expected")
require("RuntimeIntStore.entryCount" in tests and "snapshot.totalSamples() != 0" in tests,
        "analyzer observer neutrality GameTest must check runtime allocation + sampled history")
require("event.register(RseMeterObserverNeutralityGameTests.class);" in register,
        "meter runtime GameTests not registered")
require("tools/rse_meter_observer_neutrality_verify.py" in workflow,
        "metrology source gate not included in CI")

if errors:
    print("RSE METER OBSERVER NEUTRALITY: FAIL")
    for error in errors: print(" -", error)
    raise SystemExit(1)
print("RSE METER OBSERVER NEUTRALITY: PASS")
print(" Analyzer HMI read-only, missing coverage does not fabricate zero samples")
print(" Copper and optical probes fail closed on unloaded targets")
print(" Two registered GameTests (runtime execution still required)")
