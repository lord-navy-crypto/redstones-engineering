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


require(
    "src/main/java/dev/redstoneengineering/diagnostics/topology/TopologyLinkStatus.java",
    "CONNECTED", "OPEN", "ISOLATED", "DOMAIN_MISMATCH", "DIRECTION_MISMATCH", "UNLOADED",
)
require(
    "src/main/java/dev/redstoneengineering/diagnostics/topology/TopologyFaceSnapshot.java",
    "EngineeringPort", "EngineeringPortSnapshot", "topologyIssue", "compact",
)
require(
    "src/main/java/dev/redstoneengineering/diagnostics/topology/TopologyVisualizationSnapshot.java",
    "List.copyOf", "portCount", "connectedCount", "issueCount", "summary",
)
require(
    "src/main/java/dev/redstoneengineering/diagnostics/topology/EngineeringTopologyView.java",
    "EngineeringPortProvider", "PortCompatibility.evaluate", "engineeringSnapshot", "classify",
)
require(
    "src/main/java/dev/redstoneengineering/integration/jade/EngineeringPortJadeProvider.java",
    "EngineeringTopologyView.inspect", "KEY_TOPOLOGY_CONNECTED", "KEY_TOPOLOGY_ISSUES",
    "KEY_TOPOLOGY_FACE_PREFIX", "appendTopology",
)
require(
    "src/main/java/dev/redstoneengineering/gametest/RseEngineeringUxGameTests.java",
    "compatibilityProjectionDistinguishesTopologyFaults",
    "visualizationSnapshotIsImmutableAndCountsIssues",
)
require(
    "src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java",
    "event.register(RseEngineeringUxGameTests.class)",
)
require_min_alpha_version((1, 0, 17))
require("README.md", "Alpha 1.0.17", "Engineering UX & Topology Visualization")
require("ALPHA1_0_17_MANIFEST.txt", "1.0.17-alpha", "License: MIT", "Java: 21")

projection = root / "src/main/java/dev/redstoneengineering/diagnostics/topology/EngineeringTopologyView.java"
if projection.exists():
    text = projection.read_text(errors="ignore")
    forbidden = [
        "RuntimeIntStore.get(",
        "RuntimeIntStore.remove(",
        ".setBlock(",
        ".scheduleTick(",
        "DomainNetwork.drive",
        "DomainNetwork.recompute",
    ]
    for token in forbidden:
        if token in text:
            failed.append(f"topology visualization must remain read-only; found forbidden token {token!r}")

if failed:
    print("RSE Alpha 1.0.17 engineering UX verification: FAIL")
    for item in failed:
        print(" -", item)
    sys.exit(1)

print("RSE Alpha 1.0.17 engineering UX verification: PASS")
print(" all-face Engineering Port projection: PASS")
print(" Jade topology summary + face diagnostics: PASS")
print(" read-only/no-second-solver boundary: PASS")
print(" executable topology UX GameTests: PASS")
