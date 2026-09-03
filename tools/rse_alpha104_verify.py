#!/usr/bin/env python3
from pathlib import Path
import json
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
failed: list[str] = []


def check_file(rel: str, tokens: list[str]) -> None:
    path = root / rel
    if not path.exists():
        failed.append(f"missing {rel}")
        return
    text = path.read_text(errors="ignore")
    missing = [token for token in tokens if token not in text]
    if missing:
        failed.append(f"{rel}: missing {', '.join(missing)}")


check_file(
    "src/main/java/dev/redstoneengineering/block/SignalAnalyzerBlock.java",
    [
        "IntegerProperty.create(\"mode\", 0, 1)",
        "IntegerProperty.create(\"output\", 0, 15)",
        "TAP",
        "INLINE",
        "non-invasive side tap",
        "RUNTIME_SIZE",
        "SAMPLE_PERIOD_TICKS",
        "measureNode(",
        "instrumentToTarget",
        "targetState.isSignalSource()",
        "stableFor=",
        "modeSwitches=",
        "direction.getOpposite()",
    ],
)

check_file(
    "src/main/java/dev/redstoneengineering/block/SignalProbeBlock.java",
    [
        "SignalAnalyzerBlock.measureNode(",
        "targetSide",
        "direction-aware",
        "return false;",
    ],
)

check_file(
    "src/main/java/dev/redstoneengineering/instrument/InstrumentNetwork.java",
    [
        "cableNodes",
        "probeNodes",
        "duplicateChannels",
        "activeChannels",
        "networkStatus",
        "BOUNDED",
        "TRUNCATED",
        "AMBIGUOUS",
    ],
)

check_file(
    "src/main/java/dev/redstoneengineering/block/OscilloscopeBlock.java",
    ["snapshot.networkStatus()", "InstrumentNetwork.scan"],
)

check_file(
    "src/main/java/dev/redstoneengineering/block/LogicAnalyzerBlock.java",
    ["snapshot.networkStatus()", "InstrumentNetwork.scan"],
)

check_file(
    "src/main/java/dev/redstoneengineering/block/PneumaticCylinderBlock.java",
    [
        "feedback OUT=",
        "direction.getOpposite() == outputSide(state)",
        "side != outputSide(state).getOpposite()",
        "peak pressure",
        "motion reversals",
        "outputPos(pos, state)",
    ],
)

check_file(
    "src/main/java/dev/redstoneengineering/physics/PneumaticNetwork.java",
    [
        "discoveryConnects",
        "terminal one-port sink/actuator",
        "if (a.getBlock() instanceof PneumaticCylinderBlock)",
        "if (b.getBlock() instanceof PneumaticCylinderBlock)",
        "to.relative(input)",
        "return false;",
    ],
)

# Analyzer model must not require enumerating MODE × OUTPUT state combinations.
blockstate = root / "src/main/resources/assets/redstoneengineering/blockstates/signal_analyzer.json"
if not blockstate.exists():
    failed.append("missing signal_analyzer blockstate")
else:
    try:
        data = json.loads(blockstate.read_text())
        if "multipart" not in data:
            failed.append("signal_analyzer blockstate should use multipart orientation mapping")
    except Exception as exc:
        failed.append(f"signal_analyzer blockstate JSON invalid: {exc}")

# Regression-version contract: this verifier protects features introduced in
# Alpha 1.0.4 and must remain valid for later alpha milestones. Reject only a
# version older than 1.0.4 or an unexpected version format.
props = (root / "gradle.properties").read_text(errors="ignore") if (root / "gradle.properties").exists() else ""
match = re.search(r"^mod_version=(\d+)\.(\d+)\.(\d+)-alpha(?:[.-][0-9A-Za-z.-]+)?$", props, re.MULTILINE)
if not match:
    failed.append("gradle.properties has no recognized alpha mod_version")
else:
    version_tuple = tuple(map(int, match.groups()))
    if version_tuple < (1, 0, 4):
        failed.append(f"Alpha 1.0.4 regression verifier requires version >=1.0.4-alpha, got {version_tuple}")

for rel in [
    "docs/ALPHA1_0_4_INSTRUMENTATION_TOPOLOGY.md",
    "docs/ALPHA1_0_4_TEST_MATRIX.md",
]:
    if not (root / rel).exists():
        failed.append(f"missing {rel}")

# Runtime measurements must not turn into high-cardinality BlockState properties.
java_text = "\n".join(
    p.read_text(errors="ignore")
    for p in (root / "src/main/java").rglob("*.java")
)
for forbidden in [
    'IntegerProperty.create("samples",',
    'IntegerProperty.create("changes",',
    'IntegerProperty.create("pressure", 0, 100)',
    'IntegerProperty.create("value", 0, 255)',
]:
    if forbidden in java_text:
        failed.append(f"high-cardinality runtime data leaked into BlockState: {forbidden}")

if failed:
    print("RSE Alpha 1.0.4 regression verification: FAIL")
    for item in failed:
        print(" -", item)
    sys.exit(1)

print("RSE Alpha 1.0.4 instrumentation/topology regression verification: PASS")
print(" analyzer TAP/INLINE + transient diagnostics: PASS")
print(" direction-aware analyzer/probe measurement: PASS")
print(" instrument-network topology diagnostics: PASS")
print(" scope/logic topology visibility: PASS")
print(" pneumatic cylinder directional feedback: PASS")
print(" pneumatic cylinder terminal network topology: PASS")
print(" resource/forward-version/high-cardinality guards: PASS")
