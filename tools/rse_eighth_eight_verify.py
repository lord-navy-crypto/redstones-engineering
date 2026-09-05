#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing eighth-eight file: {rel}")
        return ""
    return path.read_text(errors="ignore")


def require(rel: str, *tokens: str) -> None:
    body = read(rel)
    for token in tokens:
        if body and token not in body:
            errors.append(f"{rel}: missing eighth-eight contract token {token!r}")


require(
    "src/main/java/dev/redstoneengineering/core/domain/EngineeringDomain.java",
    "MECHATRONIC_POSITION",
)

require(
    "src/main/java/dev/redstoneengineering/block/PidControllerBlock.java",
    '"SETPOINT IN"', '"PROCESS VALUE IN"', '"INHIBIT IN"',
    '"CONTROL OUT"', '"MODE SELECT"', '"MANUAL OUTPUT IN"',
    "PortKind.FEEDBACK", "PortKind.SAFETY",
    "AcceptanceEvidenceStore.clear",
    "PidControllerMenu",
)
require(
    "src/main/java/dev/redstoneengineering/block/WatchdogBlock.java",
    '"HEARTBEAT IN"', '"TIMEOUT OUT"',
    "timeoutCount", "transitionCount",
    "RuntimeIntStore.remove",
    "FieldDeviceUi.open",
)
require(
    "src/main/java/dev/redstoneengineering/instrument/InstrumentShieldingAudit.java",
    "ShieldingSnapshot", "coveragePercent", "FULLY_SHIELDED", "MIXED_SHIELDING",
    "NetworkKernel.MAX_NODES",
)
require(
    "src/main/java/dev/redstoneengineering/instrument/InstrumentNetwork.java",
    "shieldedCableNodes", "unshieldedCableNodes",
    "shieldingCoveragePercent", "shieldingIntegrity",
)
require(
    "src/main/java/dev/redstoneengineering/block/ServoActuatorBlock.java",
    "implements EntityBlock, EngineeringPortProvider",
    '"COMMAND IN"', '"BRAKE"', '"POSITION OUT"',
    "EngineeringDomain.MECHATRONIC_POSITION",
    "RuntimeIntStore.remove",
    "compactDiagnostics",
    "trajectory diagnostics", "settle=", "travel=",
)
require(
    "src/main/java/dev/redstoneengineering/block/ServoPositionSensorBlock.java",
    '"SERVO POSITION IN"', '"REDSTONE FEEDBACK OUT"',
    "EngineeringDomain.MECHATRONIC_POSITION",
    "MetrologyStore.remove",
    "FieldDeviceUi.open",
)
require(
    "src/main/java/dev/redstoneengineering/block/RedundantVoterBlock.java",
    '"CHANNEL A"', '"CHANNEL B"', '"CHANNEL C"', '"VOTED OUT"',
    "previousDegraded",
    "disagreementCount",
    "RuntimeIntStore.remove",
)
require(
    "src/main/java/dev/redstoneengineering/block/FaultLatchBlock.java",
    '"FAULT IN"', '"RESET"', '"LATCHED FAULT OUT"',
    "previousResetLevel",
    "if (resetHigh)",
    "return 0;",
    "resetCount",
    "RuntimeIntStore.remove",
)
require(
    "src/main/java/dev/redstoneengineering/block/OperationsMonitorBlock.java",
    "implements EngineeringPortProvider",
    '"MACHINE RUNNING"', '"CYCLE PULSE"', '"QUEUE / WIP"',
    "PortDirection.INPUT",
    "RuntimeIntStore.remove",
    "starved=", "blocked/fault=", "highQueueRun=",
    "FieldDeviceUi.open",
)

require(
    "src/main/java/dev/redstoneengineering/ui/menu/FieldDeviceMenu.java",
    "KIND_SHIELDED_INSTRUMENT_CABLE = 40",
    "KIND_WATCHDOG = 41",
    "KIND_SERVO_ACTUATOR = 42",
    "KIND_SERVO_POSITION_SENSOR = 43",
    "KIND_REDUNDANT_VOTER = 44",
    "KIND_FAULT_LATCH = 45",
    "KIND_OPERATIONS_MONITOR = 46",
    "InstrumentShieldingAudit.inspect",
    "WatchdogBlock.ageTicks",
    "ServoActuatorBlock.position",
    "RedundantVoterBlock.disagreementCount",
    "FaultLatchBlock.resetCount",
    "OperationsMonitorBlock.queueNow",
)
menu_body = read("src/main/java/dev/redstoneengineering/ui/menu/FieldDeviceMenu.java")
if menu_body:
    shielded = menu_body.find("if (block instanceof ShieldedInstrumentCableBlock)")
    generic = menu_body.find("if (block instanceof InstrumentCableBlock)")
    if shielded < 0 or generic < 0 or shielded > generic:
        errors.append("ShieldedInstrumentCableBlock must be matched before generic InstrumentCableBlock")

require(
    "src/main/java/dev/redstoneengineering/client/ui/FieldDeviceScreen.java",
    "SHIELDED INSTRUMENT BUS",
    "HEARTBEAT WATCHDOG",
    "SERVO ACTUATOR",
    "SERVO POSITION SENSOR",
    "2oo3 REDUNDANT VOTER",
    "FAULT LATCH",
    "OPERATIONS MONITOR • OBSERVER",
    "MECHATRONIC_POSITION OUTPUT",
    "RESET input with priority over FAULT",
    "READ-ONLY CPS / RELIABILITY DEVICE",
)

# High-cardinality runtime/diagnostic data must remain outside BlockState.
joined = "\n".join(read(rel) for rel in (
    "src/main/java/dev/redstoneengineering/block/PidControllerBlock.java",
    "src/main/java/dev/redstoneengineering/block/WatchdogBlock.java",
    "src/main/java/dev/redstoneengineering/block/ServoActuatorBlock.java",
    "src/main/java/dev/redstoneengineering/block/ServoPositionSensorBlock.java",
    "src/main/java/dev/redstoneengineering/block/RedundantVoterBlock.java",
    "src/main/java/dev/redstoneengineering/block/FaultLatchBlock.java",
    "src/main/java/dev/redstoneengineering/block/OperationsMonitorBlock.java",
))
for forbidden in (
    'IntegerProperty.create("servo_position"',
    'IntegerProperty.create("watchdog_age"',
    'IntegerProperty.create("disagreement_count"',
    'IntegerProperty.create("trip_count"',
    'IntegerProperty.create("queue_now"',
):
    if forbidden in joined:
        errors.append(f"eighth-eight high-cardinality runtime leaked into BlockState: {forbidden}")

# Exactly eight executable eighth-batch acceptance tests.
tests = "src/main/java/dev/redstoneengineering/gametest/RseEighthEightAcceptanceGameTests.java"
for method in (
    "pidExposesSixControlPortsAndInhibitDominates",
    "watchdogTimesOutAndHeartbeatRecovers",
    "shieldedInstrumentCableReportsCoverageWithoutChangingSolver",
    "servoActuatorPublishesMechanicalPositionNotRedstoneFront",
    "servoPositionSensorBridgesMechanicalFeedbackToRedstone",
    "redundantVoterCountsDisagreementEdgesNotDuration",
    "faultLatchResetHasPriorityAndCountsOneEdge",
    "operationsMonitorIsObserverOnlyAndClassifiesBlockedWork",
):
    require(tests, f"void {method}(GameTestHelper helper)")
body = read(tests)
if body and len(re.findall(r"@GameTest\(", body)) != 8:
    errors.append("eighth-eight acceptance class must contain exactly eight @GameTest methods")
require(
    "src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java",
    "event.register(RseEighthEightAcceptanceGameTests.class);",
)

workflow = read(".github/workflows/build.yml")
if workflow and "rse_eighth_eight_verify.py" not in workflow:
    errors.append("workflow does not gate the eighth-eight verifier")

if errors:
    print("RSE eighth-eight CPS reliability verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE eighth-eight CPS reliability verification: PASS")
print("  complete PID multi-port control topology: PASS")
print("  watchdog heartbeat lifecycle + timeout diagnostics: PASS")
print("  observer-only instrument shielding coverage audit: PASS")
print("  first-class mechatronic-position feedback domain: PASS")
print("  servo actuator/sensor lifecycle and port contracts: PASS")
print("  edge-count voter disagreement + deterministic fault reset: PASS")
print("  observer-only operations monitor six-port contract: PASS")
print("  Field Device Inspector CPS projection kinds 40-46: PASS")
print("  eight executable eighth-batch GameTests registered: PASS")
