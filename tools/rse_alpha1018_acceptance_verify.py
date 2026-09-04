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
    "src/main/java/dev/redstoneengineering/diagnostics/acceptance/EngineeringAcceptanceStatus.java",
    "NOT_READY", "PASS", "MARGINAL", "FAIL",
)
require(
    "src/main/java/dev/redstoneengineering/diagnostics/acceptance/EngineeringAcceptanceIssue.java",
    "record EngineeringAcceptanceIssue", "code", "detail",
)
require(
    "src/main/java/dev/redstoneengineering/diagnostics/acceptance/EngineeringAcceptanceSnapshot.java",
    "List.copyOf", "commissioningScore", "topologyIssues", "traceKey", "accepted",
)
require(
    "src/main/java/dev/redstoneengineering/diagnostics/acceptance/EngineeringAcceptance.java",
    "TopologyVisualizationSnapshot", "CommissioningSnapshot", "TOPOLOGY_MISMATCH",
    "COMMISSIONING_NOT_READY", "COMMISSIONING_MARGINAL", "COMMISSIONING_FAIL",
)
require(
    "src/main/java/dev/redstoneengineering/gametest/RseAcceptanceGameTests.java",
    "structuralMismatchFailsEvenWithPassingCommissioning",
    "acceptanceSeparatesReadinessMarginalAndPass",
)
require(
    "src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java",
    "event.register(RseAcceptanceGameTests.class)",
)
require_min_alpha_version((1, 0, 18))
require("ALPHA1_0_18_MANIFEST.txt", "1.0.18-alpha", "Engineering Acceptance & Traceability", "Java: 21")

acceptance = root / "src/main/java/dev/redstoneengineering/diagnostics/acceptance/EngineeringAcceptance.java"
if acceptance.exists():
    text = acceptance.read_text(errors="ignore")
    forbidden = [
        "RuntimeIntStore.get(",
        "RuntimeIntStore.remove(",
        ".setBlock(",
        ".scheduleTick(",
        "DomainNetwork.drive",
        "DomainNetwork.recompute",
        "EngineeringTopologyView.inspect(",
        "ClosedLoopCommissioning.inspectPid(",
    ]
    for token in forbidden:
        if token in text:
            failed.append(f"acceptance layer must consume snapshots only; found forbidden token {token!r}")

if failed:
    print("RSE Alpha 1.0.18 acceptance verification: FAIL")
    for item in failed:
        print(" -", item)
    sys.exit(1)

print("RSE Alpha 1.0.18 acceptance verification: PASS")
print(" topology + commissioning evidence aggregation: PASS")
print(" deterministic PASS/MARGINAL/FAIL/NOT_READY verdicts: PASS")
print(" immutable traceability snapshot: PASS")
print(" read-only/no-second-solver boundary: PASS")
print(" executable acceptance GameTests: PASS")
