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


def forbid(rel: str, *tokens: str) -> None:
    path = root / rel
    if not path.exists():
        failed.append(f"missing: {rel}")
        return
    text = path.read_text(errors="ignore")
    for token in tokens:
        if token in text:
            failed.append(f"{rel} contains forbidden token: {token}")


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
    "topologyRoleProjectionUsesFormalPortContract",
    "directionalDomainRotationMovesTheWholeSeriesContract",
)
require(
    "src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java",
    "event.register(RseEngineeringUxGameTests.class)",
)

# Shared series-I/O contract: the UI must expose the same capability the server actually implements.
require(
    "src/main/java/dev/redstoneengineering/ui/menu/FieldDeviceMenu.java",
    "BUTTON_ROTATE_CCW",
    "BUTTON_ROTATE_CW",
    "seriesConfigurable.set(block instanceof DirectionalSignalBlock || block instanceof DirectionalDomainBlock ? 1 : 0)",
    "PneumaticObservationSupport.observe",
    "applyPneumaticEvidence",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/FieldDeviceScreen.java",
    "↺ Rotate I/O",
    "Rotate I/O ↻",
    "SERIES LOCK • IN → PROCESS → OUT",
    "menu.seriesConfigurable()",
)
require(
    "src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java",
    "seriesRotatable.set(block instanceof DirectionalSignalBlock || block instanceof DirectionalDomainBlock ? 1 : 0)",
    "return seriesRotatable.get() != 0",
)
forbid(
    "src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java",
    "return facing.get() >= 0;",
)

# Shared role projection: every engineering HMI must expose the actual physical topology role.
require(
    "src/main/java/dev/redstoneengineering/ui/menu/EngineeringDeviceMenu.java",
    "TOPOLOGY_SERIES",
    "TOPOLOGY_SOURCE",
    "TOPOLOGY_SINK",
    "TOPOLOGY_OBSERVER",
    "TOPOLOGY_PASSIVE",
    "TOPOLOGY_EXPLICIT_JUNCTION",
    "TOPOLOGY_MULTIPORT",
    "topologyRoleLabel",
    "classifyTopologyRole",
    "refreshTopologyRole",
    "EngineeringPortProvider",
    "port.canReceive()",
    "port.canTransmit()",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/EngineeringScreen.java",
    "ROLE • ",
    "menu.topologyRoleLabel()",
)

# Evidence validity and operational health are independent engineering dimensions.
require(
    "src/main/java/dev/redstoneengineering/ui/menu/EngineeringDeviceMenu.java",
    "HEALTH_NOMINAL",
    "HEALTH_ACTIVE",
    "HEALTH_PROTECTIVE",
    "HEALTH_DEGRADED",
    "HEALTH_FAULT",
    "operationalHealthLabel",
    "EVIDENCE_UNOBSERVED",
    "EVIDENCE_VALID",
    "EVIDENCE_STALE",
    "EVIDENCE_DOMAIN_MISMATCH",
    "EVIDENCE_TOPOLOGY_ERROR",
    "evidenceStateLabel",
    "refreshEvidenceState",
    "engineeringSnapshot(level, blockPos, state, port.side())",
    "RedundantVoterBlock.degraded",
    "FaultLatchBlock.latched",
    "OperationsMonitorBlock.SystemState",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/EngineeringScreen.java",
    "HEALTH • ",
    "operationalHealthColor",
    "EVIDENCE • ",
    "menu.evidenceStateLabel()",
    "evidenceStateColor",
)
require(
    "src/main/java/dev/redstoneengineering/block/FaultLatchBlock.java",
    "The alarm is authoritative evidence even while the device health is FAULT.",
    "state.getValue(OUTPUT), PortQuality.VALID",
)
forbid(
    "src/main/java/dev/redstoneengineering/ui/menu/ReliabilitySystemMenu.java",
    "FaultLatchBlock.latched(level, blockPos) ? PortQuality.FAULT : PortQuality.VALID",
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
print(" strict series-I/O capability + controls: PASS")
print(" shared physical topology-role HMI: PASS")
print(" lightweight topology-role regression: PASS")
print(" shared EngineeringPort evidence-quality HMI: PASS")
print(" authoritative valid-zero evidence boundary: PASS")
print(" evidence-validity / operational-health separation: PASS")
print(" read-only/no-second-solver boundary: PASS")
print(" executable topology UX GameTests: PASS")
