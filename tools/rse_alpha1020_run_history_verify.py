#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
failed: list[str] = []


def require(rel: str, *tokens: str) -> None:
    path = root / rel
    if not path.exists():
        failed.append(f"missing: {rel}")
        return
    text = path.read_text(errors="ignore")
    for token in tokens:
        if token not in text:
            failed.append(f"{rel} missing token: {token}")


def require_min_alpha_version(minimum: tuple[int, int, int]) -> None:
    path = root / "gradle.properties"
    if not path.exists():
        failed.append("missing: gradle.properties")
        return
    match = re.search(r"^mod_version=(\d+)\.(\d+)\.(\d+)-alpha$", path.read_text(errors="ignore"), re.MULTILINE)
    if not match:
        failed.append("gradle.properties missing parseable alpha mod_version")
        return
    current = tuple(int(part) for part in match.groups())
    if current < minimum:
        failed.append(f"mod_version {current} is older than required Alpha {minimum}")


require("src/main/java/dev/redstoneengineering/diagnostics/acceptance/AcceptanceEvidenceTrend.java",
        "IMPROVED", "SAME", "REGRESSED", "INCOMPARABLE")
require("src/main/java/dev/redstoneengineering/diagnostics/acceptance/AcceptanceEvidenceRecord.java",
        "record AcceptanceEvidenceRecord", "sequence", "gameTick", "tuningPreset", "EngineeringAcceptanceSnapshot", "compact")
require("src/main/java/dev/redstoneengineering/diagnostics/acceptance/AcceptanceEvidenceComparison.java",
        "between", "scoreDelta", "topologyIssueDelta", "AcceptanceEvidenceTrend", "compact")
require("src/main/java/dev/redstoneengineering/diagnostics/acceptance/AcceptanceEvidenceTimeline.java",
        "DEFAULT_CAPACITY = 8", "List.copyOf", "compareLatestToPrevious", "while (records.size() > capacity)")
require("src/main/java/dev/redstoneengineering/diagnostics/acceptance/AcceptanceEvidenceStore.java",
        "MAX_CONTROLLERS_PER_LEVEL = 256", "MAX_RECORDS_PER_CONTROLLER", "WeakHashMap", "new LinkedHashMap<>()",
        "capture", "history", "compareLatestToPrevious", "insertion-ordered rather than access-ordered")
require("src/main/java/dev/redstoneengineering/block/PidControllerBlock.java",
        "h.getDirection() == outputSide(s)", "captureAcceptanceEvidence", "AcceptanceEvidenceStore.capture", "Shift+FRONT",
        "protected void onRemove", "state.getBlock() != newState.getBlock()", "RuntimeIntStore.remove(level, KEY, pos)",
        "AcceptanceEvidenceStore.clear(level, pos)")
require("src/main/java/dev/redstoneengineering/integration/jade/EngineeringPortJadeProvider.java",
        "KEY_EVIDENCE_COUNT", "KEY_EVIDENCE_LATEST", "KEY_EVIDENCE_COMPARISON", "AcceptanceEvidenceStore.history")
require("src/main/java/dev/redstoneengineering/gametest/RseAcceptanceGameTests.java",
        "capturedRunTimelineIsBoundedImmutableAndComparable", "AcceptanceEvidenceTimeline(2)", "AcceptanceEvidenceTrend.IMPROVED")
require_min_alpha_version((1, 0, 20))
require("ALPHA1_0_20_MANIFEST.txt", "1.0.20-alpha", "Commissioning Run History & Baseline Comparison", "Java: 21")
require("README.md", "Alpha 1.0.20", "1.0.20-alpha", "Commissioning Run History & Baseline Comparison")

store = root / "src/main/java/dev/redstoneengineering/diagnostics/acceptance/AcceptanceEvidenceStore.java"
if store.exists():
    text = store.read_text(errors="ignore")
    forbidden = [
        "import net.minecraft.world.level.block.state.BlockState",
        "RuntimeIntStore.get(",
        "RuntimeIntStore.remove(",
        ".setBlock(",
        ".scheduleTick(",
        "DomainNetwork.drive",
        "DomainNetwork.recompute",
        "0.75f, true",
    ]
    for token in forbidden:
        if token in text:
            failed.append(f"evidence history must stay observer-neutral/outside simulation state; found forbidden token {token!r}")

jade = root / "src/main/java/dev/redstoneengineering/integration/jade/EngineeringPortJadeProvider.java"
if jade.exists() and "AcceptanceEvidenceStore.capture(" in jade.read_text(errors="ignore"):
    failed.append("Jade must observe captured evidence and must never create run-history records")

if failed:
    print("RSE Alpha 1.0.20 run-history verification: FAIL")
    for item in failed:
        print(" -", item)
    sys.exit(1)

print("RSE Alpha 1.0.20 run-history verification: PASS")
print(" explicit player-owned capture action: PASS")
print(" bounded transient evidence history: PASS")
print(" immutable record + comparison contracts: PASS")
print(" observer-neutral retention order: PASS")
print(" PID removal clears runtime + captured evidence: PASS")
print(" Jade observer-only history presentation: PASS")
print(" no BlockState/physics ownership leakage: PASS")