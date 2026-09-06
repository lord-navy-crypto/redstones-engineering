#!/usr/bin/env python3
"""Fail-closed Alpha 2 finishing gate.

This is intentionally a closure verifier, not a new feature verifier. It consumes the existing
122-block deep-audit artifact, promotes the remaining observability/lifecycle expectations to hard
release gates, and checks that the Phase-5 presentation layer stays server-authoritative and
observer-only.
"""
from __future__ import annotations

from pathlib import Path
import json
import sys

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
AUDIT = ROOT / ".rse-audit/rse-122-block-audit.json"
OUT = ROOT / ".rse-audit/rse-alpha2-finishing-gate.md"
FAILED: list[str] = []


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        FAILED.append(f"missing {rel}")
        return ""
    return path.read_text(encoding="utf-8", errors="ignore")


def require(rel: str, *tokens: str) -> str:
    body = read(rel)
    for token in tokens:
        if token not in body:
            FAILED.append(f"{rel}: missing {token!r}")
    return body


def forbid(rel: str, *tokens: str) -> None:
    body = read(rel)
    for token in tokens:
        if token in body:
            FAILED.append(f"{rel}: forbidden {token!r}")


if not AUDIT.is_file():
    FAILED.append("122-block audit JSON is missing; rse_122_block_total_audit.py must run first")
    report = {}
else:
    try:
        report = json.loads(AUDIT.read_text(encoding="utf-8"))
    except Exception as exc:
        FAILED.append(f"unable to parse 122-block audit JSON: {exc}")
        report = {}

expected_counts = {
    "registered_blocks": 122,
    "ledger_unique_blocks": 122,
    "direct_gametest_blocks": 122,
    "direct_verifier_blocks": 122,
    "port_contract_blocks": 122,
    "domain_contract_blocks": 122,
    "snapshot_blocks": 122,
}
for key, expected in expected_counts.items():
    observed = report.get(key)
    if observed != expected:
        FAILED.append(f"122-block closure {key}: expected {expected}, observed {observed}")

if report.get("hard_errors") not in ([], None):
    FAILED.append(f"122-block audit contains hard errors: {report.get('hard_errors')}")
if report.get("unbatched") not in ([], None):
    FAILED.append(f"122-block audit has unbatched blocks: {report.get('unbatched')}")
if report.get("stale_ledger") not in ([], None):
    FAILED.append(f"122-block audit has stale ledger entries: {report.get('stale_ledger')}")
if report.get("stateful_blocks") != report.get("stateful_with_cleanup"):
    FAILED.append(
        "stateful lifecycle closure incomplete: "
        f"{report.get('stateful_with_cleanup')}/{report.get('stateful_blocks')}"
    )

blocks = report.get("blocks", [])
if len(blocks) != 122:
    FAILED.append(f"expected 122 per-block audit rows, observed {len(blocks)}")
else:
    for row in blocks:
        block_id = row.get("id", "<unknown>")
        for key in ("resources_ok", "class_found", "ports", "domain", "snapshot", "gametest", "verifier"):
            if row.get(key) is not True:
                FAILED.append(f"{block_id}: Alpha 2 closure requires {key}=true")
        if row.get("stateful") and not row.get("cleanup"):
            FAILED.append(f"{block_id}: stateful block lacks cleanup evidence")

systems = require(
    "src/main/java/dev/redstoneengineering/EngineeringSystemsModule.java",
    "SYSTEM_BLOCK_COUNT = 5",
    'registerBlock("sequence_controller"',
    'registerBlock("safety_interlock"',
    'registerBlock("fault_injector"',
    'registerBlock("alarm_processor"',
    'registerBlock("topology_debugger"',
)
if systems.count("registerBlock(") != 5:
    FAILED.append("systems extension must remain exactly 5 blocks for the 122 + 5 = 127 closure")

instrument = require(
    "src/main/java/dev/redstoneengineering/block/InstrumentCableBlock.java",
    "engineeringSnapshot(",
    "EngineeringPortSnapshot",
    "PortQuality.STALE",
    "PortQuality.TOPOLOGY_ERROR",
    "PortQuality.VALID",
    'PortDirection.BIDIRECTIONAL, false, "link"',
)
if "InstrumentNetwork.scan(" in instrument:
    FAILED.append("InstrumentCable snapshot must remain O(1) local observability, not a full network scan")
for token in ("setBlock(", "scheduleTick(", "updateNeighborsAt("):
    if token in instrument:
        FAILED.append(f"InstrumentCable observability must not mutate simulation via {token}")
require(
    "src/main/java/dev/redstoneengineering/block/ShieldedInstrumentCableBlock.java",
    "extends InstrumentCableBlock",
)

menu = require(
    "src/main/java/dev/redstoneengineering/ui/menu/TopologyDebuggerMenu.java",
    "timelineLatestTimeLow",
    "timelineLatestTimeHigh",
    "VanillaRedstoneRuntimeTelemetry.inspect",
    "VanillaRedstoneTargetHistory.inspect",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/TopologyDebuggerScreen.java",
    "VANILLA BEHAVIOR IMMUTABILITY",
    "EngineeringChartRenderer.drawWaveform",
    "EngineeringChartRenderer.drawChangeMarkers",
    "EngineeringChartRenderer.drawGameTimeAxis",
    "Exact target-source observations only; gaps mean no retained target event.",
    "Timebase: logical-server gameTime. No sub-tick or causal-order claim.",
)
require(
    "src/main/java/dev/redstoneengineering/diagnostics/redstone/VanillaRedstoneTargetHistory.java",
    "MAX_EVENTS_PER_LEVEL = 2048",
    "DISPLAY_SAMPLES = 24",
    "sample.source().equals(target)",
)
require(
    "src/main/java/dev/redstoneengineering/diagnostics/redstone/VanillaRedstoneRuntimeTelemetry.java",
    "MAX_EVENTS_PER_LEVEL = 4096",
    "MAX_PREVIOUS_STATES_PER_LEVEL = 1024",
)

chart_screens = (
    "src/main/java/dev/redstoneengineering/client/ui/OscilloscopeScreen.java",
    "src/main/java/dev/redstoneengineering/client/ui/LogicAnalyzerScreen.java",
    "src/main/java/dev/redstoneengineering/client/ui/SignalAnalyzerScreen.java",
    "src/main/java/dev/redstoneengineering/client/ui/PidControllerScreen.java",
    "src/main/java/dev/redstoneengineering/client/ui/TopologyDebuggerScreen.java",
)
for rel in chart_screens:
    require(rel, "EngineeringChartRenderer.")
    forbid(rel, "getBlockState(", "getSignal(", "scheduleTick(", "updateNeighborsAt(", "neighborChanged(")

require(
    "src/main/java/dev/redstoneengineering/client/ui/EngineeringChartRenderer.java",
    "presentation-only",
    "server-authoritative samples",
    "gameTimeAt",
)

workflow = require(
    ".github/workflows/build.yml",
    "./gradlew compileJava",
    "./gradlew test",
    "./gradlew runGameTestServer",
    "All [0-9]+ required tests passed",
    "sha256sum *.jar",
    "actions/upload-artifact@v6",
)
if "rse_alpha2_finishing_verify.py" not in workflow:
    FAILED.append("build workflow does not execute rse_alpha2_finishing_verify.py")

require(
    "docs/ALPHA2_FINISHING_GATE.md",
    "FEATURE FREEZE",
    "122/122",
    "127",
    "SECOND-PASS VERIFICATION",
    "VANILLA BEHAVIOR IMMUTABILITY",
)

OUT.parent.mkdir(parents=True, exist_ok=True)
stateful = report.get("stateful_blocks", "?")
cleanup = report.get("stateful_with_cleanup", "?")
lines = [
    "# RSE Alpha 2 Finishing Gate",
    "",
    f"- Core registration/resources/contracts: **{report.get('registered_blocks', '?')}/122**",
    f"- Snapshot/metrology surfaces: **{report.get('snapshot_blocks', '?')}/122**",
    f"- Direct GameTest evidence: **{report.get('direct_gametest_blocks', '?')}/122**",
    f"- Direct verifier evidence: **{report.get('direct_verifier_blocks', '?')}/122**",
    f"- Stateful cleanup evidence: **{cleanup}/{stateful}**",
    "- Systems extension: **5 blocks**",
    "- Aggregate architecture target: **127 blocks**",
    "- Phase 5 Vanilla Engineering Overlay: **diagnostics-only / server-authoritative**",
    "- Instrument cable observability: **local O(1) link snapshots; no full-network HUD scan**",
    "",
    "This gate marks feature-freeze readiness only. Runtime second-pass verification remains the next stage.",
]
OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")

if FAILED:
    print("RSE ALPHA 2 FINISHING GATE: FAIL")
    for item in FAILED:
        print(" -", item)
    raise SystemExit(1)

print("RSE ALPHA 2 FINISHING GATE: PASS")
print("  core observability: 122/122 snapshot/metrology surfaces")
print(f"  stateful lifecycle cleanup: {cleanup}/{stateful}")
print("  systems architecture: 122 core + 5 extension = 127")
print("  chart clients: synchronized presentation only; no world scanning")
print("  instrument cables: local link quality snapshot; no full-network HUD scan")
print("  Phase 5 VEO: VANILLA BEHAVIOR IMMUTABILITY retained")
print("  next stage after green build: SECOND-PASS VERIFICATION")
