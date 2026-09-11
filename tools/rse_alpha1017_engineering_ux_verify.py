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


def braced_region(text: str, start: int) -> str:
    brace = text.find("{", start)
    if brace < 0:
        return ""
    depth = 0
    for index in range(brace, len(text)):
        char = text[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return text[brace:index + 1]
    return ""


def engineering_ports_method(text: str) -> str:
    marker = "List<EngineeringPort> engineeringPorts"
    start = text.find(marker)
    return "" if start < 0 else braced_region(text, start)


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
    "RedstoneEngineering.PRESSURE_REGULATOR",
    "RedstoneEngineering.PNEUMATIC_VALVE",
)
require(
    "src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java",
    "event.register(RseEngineeringUxGameTests.class)",
)

# Shared configurable one-input/one-output route contract.
for rel in (
    "src/main/java/dev/redstoneengineering/block/DirectionalSignalBlock.java",
    "src/main/java/dev/redstoneengineering/block/DirectionalDomainBlock.java",
):
    require(
        rel,
        'DirectionProperty.create("input_facing", Direction.Plane.HORIZONTAL)',
        "INPUT_FACING",
        "seriesInputSide",
        "seriesOutputSide",
        "rotateSeriesOutput",
        "rotateWholeRoute",
        "return rotateSeriesOutput(level, pos, clockwise)",
        "newOutput == input",
    )

# Existing field HMI buttons now call rotateSeriesAxis(), whose compatibility entry point routes OUTPUT only.
require(
    "src/main/java/dev/redstoneengineering/ui/menu/FieldDeviceMenu.java",
    "BUTTON_ROTATE_CCW",
    "BUTTON_ROTATE_CW",
    "DirectionalSignalBlock.rotateSeriesAxis(level, blockPos, clockwise)",
    "DirectionalDomainBlock.rotateSeriesAxis(level, blockPos, clockwise)",
    "seriesConfigurable.set(block instanceof DirectionalSignalBlock || block instanceof DirectionalDomainBlock ? 1 : 0)",
    "PneumaticObservationSupport.observe",
    "applyPneumaticEvidence",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/FieldDeviceScreen.java",
    "↺ Rotate I/O",
    "Rotate I/O ↻",
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

# Serial-first, role-aware policy:
# all-face SOURCE/SINK terminals remain explicit exceptions, while ordinary processors may not
# silently aggregate multiple process inputs or create hidden fan-out.
block_dir = root / "src/main/java/dev/redstoneengineering/block"
all_face_loop = re.compile(r"for\s*\(\s*Direction\s+\w+\s*:\s*Direction\.values\(\)\s*\)")
if block_dir.is_dir():
    for source in sorted(block_dir.glob("*.java")):
        text = source.read_text(errors="ignore")
        ports_method = engineering_ports_method(text)
        if not ports_method:
            continue
        explicit_branch = "Junction" in source.stem or "Splitter" in source.stem
        if explicit_branch:
            continue

        for loop in all_face_loop.finditer(ports_method):
            loop_body = braced_region(ports_method, loop.end())
            if not loop_body:
                continue
            loop_input = "PortDirection.INPUT" in loop_body
            loop_output = "PortDirection.OUTPUT" in loop_body
            method_input = "PortDirection.INPUT" in ports_method
            method_output = "PortDirection.OUTPUT" in ports_method

            if loop_input and loop_output:
                failed.append(
                    f"{source.name}: all-face loop declares mixed INPUT/OUTPUT; "
                    "serial-first policy requires strict endpoints or explicit Junction/Splitter topology"
                )
                continue

            if loop_input and method_output:
                failed.append(
                    f"{source.name}: all-face INPUT plus separate OUTPUT creates implicit multi-input processing; "
                    "use one explicit process input or an explicit aggregation device"
                )
                continue

            if loop_output and method_input:
                fixed_prefix = ports_method[:loop.start()]
                controlled_source = "PortKind.CONTROL" in fixed_prefix and "PortDirection.INPUT" in fixed_prefix
                if not controlled_source:
                    failed.append(
                        f"{source.name}: all-face OUTPUT plus non-control INPUT is implicit fan-out processing; "
                        "use a controlled-source contract or explicit Junction/Splitter topology"
                    )

# Representative role exceptions are intentional and must stay explicit.
require(
    "src/main/java/dev/redstoneengineering/block/OpticalEmitterBlock.java",
    "Six-face optical source",
    "PortDirection.OUTPUT",
)
require(
    "src/main/java/dev/redstoneengineering/block/OpticalReceiverBlock.java",
    "Six-face optical receiver terminal",
    "PortDirection.INPUT",
    "inputs <= 1",
)
require(
    "src/main/java/dev/redstoneengineering/block/PneumaticFlowMeterBlock.java",
    "extends DirectionalDomainBlock",
    "inputSide(state)",
    "outputSide(state)",
)
require(
    "src/main/java/dev/redstoneengineering/block/PneumaticProportionalValveBlock.java",
    "extends DirectionalDomainBlock",
    '"PNEUMATIC IN", inputSide(state)',
    '"PNEUMATIC OUT", outputSide(state)',
)
require(
    "src/main/java/dev/redstoneengineering/block/PressureRegulatorBlock.java",
    "extends DirectionalDomainBlock",
    '"PNEUMATIC IN", inputSide(state)',
    '"REGULATED OUT", outputSide(state)',
)
forbid(
    "src/main/java/dev/redstoneengineering/block/PressureRegulatorBlock.java",
    "Six-way pneumatic pressure-limiting node",
    "PortDirection.BIDIRECTIONAL",
)
require(
    "src/main/java/dev/redstoneengineering/physics/PneumaticNetwork.java",
    "directionalInput(BlockState state)",
    "directionalOutput(BlockState state)",
    "DirectionalDomainBlock.seriesInputSide(state)",
    "DirectionalDomainBlock.seriesOutputSide(state)",
    "Direction input = directionalInput(state)",
    "Direction output = directionalOutput(state)",
)
forbid(
    "src/main/java/dev/redstoneengineering/physics/PneumaticNetwork.java",
    "state.getValue(DirectionalDomainBlock.FACING).getOpposite()",
)
require(
    "src/main/java/dev/redstoneengineering/block/OpticalChannelFilterBlock.java",
    "Direction inputSide = seriesInputSide(state)",
    "outputPos(pos, state)",
)
require(
    "src/main/java/dev/redstoneengineering/block/OpticalAttenuatorBlock.java",
    "Direction inputSide = seriesInputSide(state)",
    "outputPos(pos, state)",
)
require(
    "src/main/java/dev/redstoneengineering/block/RedstoneReferenceSourceBlock.java",
    "rotateOutput(level, pos, true)",
    "Reference output →",
)
require(
    "src/main/java/dev/redstoneengineering/block/DirectionalRedstoneEndpointBlock.java",
    "rotateOutput(Level level, BlockPos pos, boolean clockwise)",
)
require(
    "src/main/java/dev/redstoneengineering/block/SignalAnalyzerBlock.java",
    "TAP mode is a non-invasive measurement aperture",
    "TEST IN",
    "INLINE OUT",
)
require(
    "src/main/java/dev/redstoneengineering/block/SoulFluxInjectorBlock.java",
    "UP is the dedicated command input; the other five faces are Soul-Flux outputs",
    "PortKind.CONTROL",
    "SOUL FLUX OUT",
)

# Shared role + route projection: every engineering HMI exposes actual formal port faces.
require(
    "src/main/java/dev/redstoneengineering/ui/menu/EngineeringDeviceMenu.java",
    "TOPOLOGY_SERIES",
    "TOPOLOGY_SOURCE",
    "TOPOLOGY_SINK",
    "TOPOLOGY_OBSERVER",
    "TOPOLOGY_PASSIVE",
    "TOPOLOGY_EXPLICIT_JUNCTION",
    "TOPOLOGY_MULTIPORT",
    "TOPOLOGY_CONTROLLED_SOURCE",
    "TOPOLOGY_CONTROLLED_SERIES",
    "TOPOLOGY_PASSIVE_SERIES",
    "CONTROLLED SOURCE",
    "CONTROLLED SERIES",
    "PASSIVE SERIES",
    "topologyRoleLabel",
    "classifyTopologyRole",
    "refreshTopologyRole",
    "refreshPortRoute",
    "receivePortMask",
    "transmitPortMask",
    "portRouteLabel",
    "port.canReceive()",
    "port.canTransmit()",
    "block instanceof DirectionalDomainBlock",
    "controlReceivers == 1",
    "ports.size() == 2 ? TOPOLOGY_PASSIVE_SERIES : TOPOLOGY_PASSIVE",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/EngineeringScreen.java",
    "ROLE • ",
    "menu.topologyRoleLabel()",
    "SIGNAL ROUTE",
    "menu.receivePortFacesLabel()",
    "menu.transmitPortFacesLabel()",
    "this.imageHeight = 270",
    "routeNode",
    "drawRouteLink",
    "compactRole",
    "normalizeLegacyPresentation",
    '"PNEUMATIC • " + menu.portRouteLabel()',
    'new PresentationLine("DOWN", "REDSTONE PAYLOAD INPUT")',
    'new PresentationLine(label, menu.topologyRoleLabel())',
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
print(" configurable one-input/one-output route contract: PASS")
print(" output-only HMI routing with whole-route maintenance rotation: PASS")
print(" serial-first / explicit-branch topology policy: PASS")
print(" controlled-series / controlled-source role projection: PASS")
print(" passive-series / passive-bus role projection: PASS")
print(" pneumatic explicit-route solver contract: PASS")
print(" optical configurable-route sampling contract: PASS")
print(" shared physical topology-role HMI: PASS")
print(" shared RX/ROLE/TX route schematic: PASS")
print(" expanded anti-crowding HMI shell: PASS")
print(" reference-source adjustable output: PASS")
print(" shared EngineeringPort evidence-quality HMI: PASS")
print(" authoritative valid-zero evidence boundary: PASS")
print(" evidence-validity / operational-health separation: PASS")
print(" read-only/no-second-solver boundary: PASS")
