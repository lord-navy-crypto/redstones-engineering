#!/usr/bin/env python3
"""Fail-closed automated gate for the Alpha 2 second-pass verification stage.

The 122-block audit remains the exhaustive per-block evidence matrix. This verifier adds a
horizontal destructive-runtime layer without pretending that one GameTest server run proves
real-client visuals, multiplayer behavior, process restart persistence, or chunk unload/reload.
"""
from __future__ import annotations

from pathlib import Path
import json
import sys

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
AUDIT = ROOT / ".rse-audit/rse-122-block-audit.json"
OUT = ROOT / ".rse-audit/rse-alpha2-second-pass.md"
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
    FAILED.append("122-block audit JSON is missing; total audit must run before second-pass gate")
    report = {}
else:
    try:
        report = json.loads(AUDIT.read_text(encoding="utf-8"))
    except Exception as exc:
        FAILED.append(f"unable to parse 122-block audit JSON: {exc}")
        report = {}

for key in (
    "registered_blocks",
    "ledger_unique_blocks",
    "direct_gametest_blocks",
    "direct_verifier_blocks",
    "port_contract_blocks",
    "domain_contract_blocks",
    "snapshot_blocks",
):
    if report.get(key) != 122:
        FAILED.append(f"second-pass prerequisite {key}: expected 122, observed {report.get(key)}")

if report.get("stateful_blocks") != 62 or report.get("stateful_with_cleanup") != 62:
    FAILED.append(
        "second-pass prerequisite stateful cleanup: expected 62/62, observed "
        f"{report.get('stateful_with_cleanup')}/{report.get('stateful_blocks')}"
    )
if report.get("hard_errors") not in ([], None):
    FAILED.append(f"122-block audit contains hard errors: {report.get('hard_errors')}")
if report.get("unbatched") not in ([], None):
    FAILED.append(f"122-block audit contains unbatched blocks: {report.get('unbatched')}")
if report.get("stale_ledger") not in ([], None):
    FAILED.append(f"122-block audit contains stale ledger entries: {report.get('stale_ledger')}")

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
    FAILED.append("second pass must preserve exactly 122 core + 5 systems-extension blocks")

suite = require(
    "src/main/java/dev/redstoneengineering/gametest/RseAlpha2SecondPassGameTests.java",
    "rapidBoundaryToggleConvergesWithoutStaleState",
    "instrumentCableLinkLifecycleRebuildsSymmetrically",
    "faultInjectorRuntimeCleanupSurvivesRemovalAndReinsert",
    "faultInjectorToAlarmChainClearsAfterHealthyReset",
    "vanillaOverlayBurstHistoryIsBoundedAndObserverOnly",
    "PortQuality.VALID",
    "FaultInjectorBlock.activationCount",
    "AlarmProcessorBlock.latched",
    "VanillaRedstoneTargetHistory.DISPLAY_SAMPLES",
    "level.updateNeighborsAt(absoluteWire, Blocks.REDSTONE_WIRE)",
    "Blocks.AIR.defaultBlockState()",
)
if suite.count("@GameTest(") != 5:
    FAILED.append(f"second-pass suite must contain exactly 5 destructive GameTests, observed {suite.count('@GameTest(')}")
if "for (int i = 0; i < 64; i++)" not in suite:
    FAILED.append("VEO burst second-pass test must retain a 64-observation stress loop")
if "history.count() != VanillaRedstoneTargetHistory.DISPLAY_SAMPLES" not in suite:
    FAILED.append("VEO burst test must prove presentation history remains bounded to DISPLAY_SAMPLES")

require(
    "src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java",
    "event.register(RseAlpha2SecondPassGameTests.class);",
)

workflow = require(
    ".github/workflows/build.yml",
    "tools/rse_alpha2_finishing_verify.py",
    "tools/rse_alpha2_second_pass_verify.py",
    "test_count < 192",
    "Expected at least 192 GameTests",
    "Too many chained neighbor updates",
    "runGameTestServer",
    "sha256sum *.jar",
)

# The destructive tests may deliberately mutate their isolated test world; presentation and VEO
# observer code may not. Preserve the previously frozen authority boundary.
for rel in (
    "src/main/java/dev/redstoneengineering/diagnostics/redstone/VanillaRedstoneTargetHistory.java",
    "src/main/java/dev/redstoneengineering/diagnostics/redstone/VanillaRedstoneRuntimeTelemetry.java",
):
    forbid(rel, "setBlock(", "scheduleTick(", "updateNeighborsAt(", "neighborChanged(")

for rel in (
    "src/main/java/dev/redstoneengineering/client/ui/OscilloscopeScreen.java",
    "src/main/java/dev/redstoneengineering/client/ui/LogicAnalyzerScreen.java",
    "src/main/java/dev/redstoneengineering/client/ui/SignalAnalyzerScreen.java",
    "src/main/java/dev/redstoneengineering/client/ui/PidControllerScreen.java",
    "src/main/java/dev/redstoneengineering/client/ui/TopologyDebuggerScreen.java",
):
    forbid(rel, "getBlockState(", "getSignal(", "scheduleTick(", "updateNeighborsAt(", "neighborChanged(")

require(
    "docs/ALPHA2_SECOND_PASS_VERIFICATION.md",
    "AUTOMATED SECOND-PASS GATE",
    "122/122",
    "62/62",
    "192",
    "MANUAL / PROCESS-BOUND CHECKS",
    "world restart",
    "multiplayer",
    "chunk unload/reload",
    "FEATURE FREEZE",
)

OUT.parent.mkdir(parents=True, exist_ok=True)
lines = [
    "# RSE Alpha 2 Automated Second-Pass Report",
    "",
    "## Automated evidence",
    "",
    f"- Per-block audit matrix: **{report.get('registered_blocks', '?')}/122** core blocks",
    f"- Direct GameTest evidence matrix: **{report.get('direct_gametest_blocks', '?')}/122**",
    f"- Direct verifier evidence matrix: **{report.get('direct_verifier_blocks', '?')}/122**",
    f"- Port/domain/snapshot closure: **{report.get('port_contract_blocks', '?')}/122 / {report.get('domain_contract_blocks', '?')}/122 / {report.get('snapshot_blocks', '?')}/122**",
    f"- Stateful cleanup evidence: **{report.get('stateful_with_cleanup', '?')}/{report.get('stateful_blocks', '?')}**",
    "- Architecture: **122 core + 5 systems extension = 127**",
    "- New destructive runtime checks: **5**",
    "- Required GameTest floor after this stage: **192**",
    "- Neighbor-update safety cutoff remains mandatory",
    "- VEO observer classes remain non-mutating",
    "- Client chart screens remain synchronized presentation only",
    "",
    "## Not claimed by this automated gate",
    "",
    "Real-client visual inspection, multiplayer interaction, full process/world restart persistence,",
    "and true chunk unload/reload remain separate manual/process-bound checks.",
]
OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")

if FAILED:
    print("RSE ALPHA 2 AUTOMATED SECOND-PASS GATE: FAIL")
    for item in FAILED:
        print(" -", item)
    raise SystemExit(1)

print("RSE ALPHA 2 AUTOMATED SECOND-PASS GATE: PASS")
print("  core matrix: 122/122 registration/resources/ports/domains/snapshots/evidence")
print("  stateful lifecycle evidence: 62/62")
print("  architecture: 122 core + 5 systems = 127")
print("  destructive runtime suite: 5 tests; required GameTest floor = 192")
print("  stress: rapid 0<->15, cable rebuild, runtime cleanup, fault->alarm recovery, VEO burst")
print("  authority: VEO non-mutating; chart clients do not scan world state")
print("  manual/process-bound checks remain explicitly pending")
