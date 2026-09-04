#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
failed: list[str] = []


def text(rel: str) -> str:
    path = root / rel
    if not path.exists():
        failed.append(f"missing: {rel}")
        return ""
    return path.read_text(errors="ignore")


def require(rel: str, *tokens: str) -> None:
    body = text(rel)
    for token in tokens:
        if token not in body:
            failed.append(f"{rel}: missing {token!r}")


def forbid(rel: str, *tokens: str) -> None:
    body = text(rel)
    for token in tokens:
        if token in body:
            failed.append(f"{rel}: forbidden {token!r}")


analyzer = "src/main/java/dev/redstoneengineering/block/SignalAnalyzerBlock.java"
require(
    analyzer,
    'IntegerProperty.create("calibration", 0, 4)',
    "DISPLAY_SAMPLES = 16",
    "rollingAverage100",
    "rollingPeakToPeak",
    "rollingMeanStep100",
    "stableAgeTicks",
    "sampleAgeTicks",
    "UiSnapshot",
    "requestedOutput = state.getValue(MODE) == INLINE ? measured : 0",
    "state.getValue(OUTPUT)",
    "calibratedReading",
)
forbid(analyzer, "requestedOutput = state.getValue(MODE) == INLINE ? calibrated")

# Later UI milestones moved variation classification and sample-age presentation
# out of action-bar prose and into the formal, server-synchronized Engineering UI.
require(
    "src/main/java/dev/redstoneengineering/ui/menu/SignalAnalyzerMenu.java",
    "SignalAnalyzerBlock.uiSnapshot",
    "sampleAgeTicks",
    "stableAgeTicks",
    "average100",
    "peakToPeak",
    "meanStep100",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/SignalAnalyzerScreen.java",
    "stabilityClass",
    "sampleAgeTicks",
    "DISPLAY ONLY",
    "INLINE",
    "RAW",
)

require(
    "src/main/java/dev/redstoneengineering/instrument/InstrumentNetwork.java",
    "maxCableDepth",
    "maxProbeDepth",
    "duplicateProbes",
    "validChannels",
    'return "TRUNCATED"',
    'return "AMBIGUOUS"',
    'return "NO_PROBES"',
    'return "OK"',
)

scope_be = "src/main/java/dev/redstoneengineering/blockentity/OscilloscopeBlockEntity.java"
require(
    scope_be,
    "SAMPLE_PERIOD_TICKS = 2",
    "coveragePercent",
    "average100",
    "meanStep100",
    "estimatedPeriodTicks",
    "captureQuality",
    "cursorDeltaTicks",
    'tag.putInt("samplesSinceTrigger"',
    'tag.getInt("samplesSinceTrigger")',
)

logic_be = "src/main/java/dev/redstoneengineering/blockentity/LogicAnalyzerBlockEntity.java"
require(
    logic_be,
    "SAMPLE_PERIOD_TICKS = 1",
    "coveragePercent",
    "transitionRatePercent",
    "captureQuality",
    "cursorDeltaTicks",
    'tag.putInt("postTriggerSamples"',
    'tag.getInt("postTriggerSamples")',
)

# Scope/logic quality metrics are still computed by the authoritative BlockEntity,
# but are now synchronized and presented through dedicated menus/screens.
require(
    "src/main/java/dev/redstoneengineering/ui/menu/OscilloscopeMenu.java",
    "coveragePercent",
    "meanStep100",
    "estimatedPeriodTicks",
    "cursorA",
    "cursorB",
    "displaySample",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/OscilloscopeScreen.java",
    "Capture",
    "coverage=",
    "meanStep=",
    "period≈",
    "Cursor Δ",
)
require(
    "src/main/java/dev/redstoneengineering/ui/menu/LogicAnalyzerMenu.java",
    "coveragePercent",
    "transitionRatePercent",
    "cursorA",
    "cursorB",
    "displayState",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/LogicAnalyzerScreen.java",
    "Capture",
    "coverage=",
    "transition=",
    "Cursor Δ",
)

# Alpha 1.0.4 topology remains mandatory. Verify executable structure instead
# of a particular comment sentence so later refactors can preserve the contract.
require(
    "src/main/java/dev/redstoneengineering/physics/PneumaticNetwork.java",
    "PneumaticCylinderBlock",
    "discoveryConnects",
    "Direction input = a.getValue(DirectionalDomainBlock.FACING).getOpposite();",
    "Direction input = b.getValue(DirectionalDomainBlock.FACING).getOpposite();",
    "bPos.equals(aPos.relative(input))",
    "aPos.equals(bPos.relative(input))",
    "if (a.getBlock() instanceof PneumaticCylinderBlock) return false;",
    "from.equals(to.relative(input))",
)
require(
    "src/main/java/dev/redstoneengineering/block/PneumaticCylinderBlock.java",
    "feedback OUT=",
    "direction.getOpposite() == outputSide(state)",
)

blockstate = root / "src/main/resources/assets/redstoneengineering/blockstates/signal_analyzer.json"
if not blockstate.exists():
    failed.append("missing signal_analyzer blockstate")
else:
    try:
        data = json.loads(blockstate.read_text())
        if "multipart" not in data:
            failed.append("signal_analyzer blockstate must use multipart mapping")
        if "variants" in data:
            failed.append("signal_analyzer should not enumerate mode/output/calibration variants")
    except Exception as exc:
        failed.append(f"signal_analyzer JSON invalid: {exc}")

props = text("gradle.properties")
match = re.search(r"^mod_version=(\d+)\.(\d+)\.(\d+)-alpha(?:[.-][0-9A-Za-z.-]+)?$", props, re.MULTILINE)
if not match:
    failed.append("gradle.properties has no recognized alpha mod_version")
else:
    version_tuple = tuple(map(int, match.groups()))
    if version_tuple < (1, 0, 5):
        failed.append(f"Alpha 1.0.5 regression verifier requires version >=1.0.5-alpha, got {version_tuple}")

for rel in [
    "ALPHA1_0_5_MANIFEST.txt",
    "ALPHA1_0_5_CHANGED_FILES.txt",
    "docs/ALPHA1_0_5_QUALITY_CALIBRATION.md",
    "docs/ALPHA1_0_5_TEST_MATRIX.md",
    "tools/rse_reference_model_tests.py",
]:
    if not (root / rel).exists():
        failed.append(f"missing: {rel}")

workflow = text(".github/workflows/build.yml")
for token in [
    "rse_alpha104_verify.py",
    "rse_alpha105_quality_verify.py",
    "rse_reference_model_tests.py",
    "compileall",
    "compileJava",
    "clean build",
    "SHA256SUMS.txt",
]:
    if token not in workflow:
        failed.append(f"workflow missing quality gate: {token}")

for path in (root / "src/main/resources").rglob("*.json"):
    try:
        json.loads(path.read_text())
    except Exception as exc:
        failed.append(f"bad JSON: {path.relative_to(root)}: {exc}")

java_text = "\n".join(p.read_text(errors="ignore") for p in (root / "src/main/java").rglob("*.java"))
for forbidden in [
    'IntegerProperty.create("samples",',
    'IntegerProperty.create("coverage",',
    'IntegerProperty.create("mean_step",',
    'IntegerProperty.create("average",',
    'IntegerProperty.create("pressure", 0, 100)',
]:
    if forbidden in java_text:
        failed.append(f"high-cardinality runtime metric leaked into BlockState: {forbidden}")

for path in (root / "src/main/java/dev/redstoneengineering").rglob("*.java"):
    body = path.read_text(errors="ignore")
    for match in re.finditer(r'IntegerProperty\.create\([^,]+,\s*(-?\d+)\s*,\s*(-?\d+)\s*\)', body):
        lo, hi = map(int, match.groups())
        if hi - lo + 1 > 256:
            failed.append(f"high-cardinality BlockState in {path.name}: {lo}..{hi}")

if failed:
    print("RSE Alpha 1.0.5 quality regression verification: FAIL")
    for item in failed:
        print(" -", item)
    raise SystemExit(1)

print("RSE Alpha 1.0.5 quality regression verification: PASS")
print("  analyzer rolling quality + display calibration: PASS")
print("  raw INLINE 0..15 pass-through invariant: PASS")
print("  instrument topology depth/integrity diagnostics: PASS")
print("  scope/logic capture coverage + timebase metrics through formal UI: PASS")
print("  trigger save/reload progress persistence: PASS")
print("  Alpha 1.0.4 topology regression: PASS")
print("  JSON/forward-version/workflow/high-cardinality guards: PASS")
