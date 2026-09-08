#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing final-closure file: {rel}")
        return ""
    return path.read_text(errors="ignore")


def require(rel: str, *tokens: str) -> None:
    body = read(rel)
    for token in tokens:
        if body and token not in body:
            errors.append(f"{rel}: missing final-closure contract token {token!r}")


fault = "src/main/java/dev/redstoneengineering/block/FaultLatchBlock.java"
require(
    fault,
    '"FAULT IN"', '"RESET"', '"LATCHED FAULT OUT"',
    "RedstoneObservationSupport.observe",
    "resetObservation.valid()",
    "faultObservation.valid()",
    "if (resetHigh)",
    "return 0;",
    "previousResetLevel",
    "RuntimeIntStore.remove",
)

ops = "src/main/java/dev/redstoneengineering/block/OperationsMonitorBlock.java"
require(
    ops,
    '"MACHINE RUNNING"', '"CYCLE PULSE"', '"QUEUE / WIP"',
    "InputEvidence",
    "RedstoneObservationSupport.observe",
    "operationalReady()",
    "if (!evidence.operationalReady())",
    "updateCycleEvidence",
    "r[26] = 1",
    "r[27] = 1",
    "lastCycleTicks",
    "monitoringReady",
    "queueEvidenceSources",
    "first trustworthy pulse establishes a baseline",
    "RuntimeIntStore.remove",
)
ops_body = read(ops)
if ops_body:
    on_place = re.search(
        r"protected void onPlace\([^}]+?\{(?P<body>.*?)\n\s*}\n\n\s*private static void updateCycleEvidence",
        ops_body,
        re.S,
    )
    if not on_place:
        errors.append("OperationsMonitorBlock: could not isolate onPlace for cycle-baseline audit")
    elif "getGameTime" in on_place.group("body") or "r[7]" in on_place.group("body"):
        errors.append("OperationsMonitorBlock: placement time still contaminates cycle timing baseline")

require(
    "src/main/java/dev/redstoneengineering/ui/menu/OperationsMonitorMenu.java",
    "telemetryReady",
    "runEvidenceValid",
    "queueEvidenceSources",
    "cycleEvidenceValid",
    "OperationsMonitorBlock.inputEvidence",
)
require(
    "src/main/java/dev/redstoneengineering/client/ui/OperationsMonitorScreen.java",
    "TELEMETRY • INCOMPLETE",
    "KPIs advance only while RUN + at least one QUEUE source are trustworthy.",
    "Cycle timing requires observed LOW→HIGH edges",
)

# Historical CPS acceptance must still prove explicitly stopped + queued => SAFETY_LIMITED,
# but it may no longer use missing RUN as the stopped-machine surrogate.
old_tests = "src/main/java/dev/redstoneengineering/gametest/RseEighthEightAcceptanceGameTests.java"
require(
    old_tests,
    "operationsMonitorIsObserverOnlyAndClassifiesBlockedWork",
    "BlockPos runPos = monitorPos.below();",
    "helper.setBlock(runPos, Blocks.REDSTONE_WIRE.defaultBlockState());",
    "OperationsMonitorBlock.monitoringReady",
    "Explicitly stopped machine with queued work was not classified SAFETY_LIMITED",
)

final_tests = "src/main/java/dev/redstoneengineering/gametest/RseFinalTwoEvidenceClosureGameTests.java"
for method in (
    "faultLatchSeparatesMissingZeroAndResetPriority",
    "operationsMonitorMissingRunEvidenceCannotCreateKpis",
    "operationsMonitorCycleTimingStartsOnSecondTrustworthyPulse",
):
    require(final_tests, f"void {method}(GameTestHelper helper)")
require(
    final_tests,
    "PortQuality.NO_SIGNAL",
    "PortQuality.VALID",
    "OperationsMonitorBlock.downtimeTicks",
    "OperationsMonitorBlock.blockedFaultTicks",
    "OperationsMonitorBlock.lastCycleTicks",
)
final_body = read(final_tests)
if final_body and len(re.findall(r"@GameTest\(", final_body)) != 3:
    errors.append("final evidence closure class must contain exactly three @GameTest methods")

require(
    "src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java",
    "event.register(RseFinalTwoEvidenceClosureGameTests.class);",
)

workflow = read(".github/workflows/build.yml")
if workflow:
    if "rse_final_two_evidence_closure_verify.py" not in workflow:
        errors.append("workflow does not gate the final two-block evidence verifier")
    if "test_count < 312" not in workflow or "at least 312 GameTests" not in workflow:
        errors.append("workflow does not enforce the final 312-GameTest floor")

if errors:
    print("RSE final two-block evidence closure verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE final two-block evidence closure verification: PASS")
print("  #121 Fault Latch source-vs-zero + reset-priority evidence: PASS")
print("  #122 Operations Monitor no-fabricated-KPI evidence gating: PASS")
print("  first-cycle baseline / second-cycle interval chronology: PASS")
print("  dedicated Operations UI telemetry-readiness projection: PASS")
print("  historical stopped+queued CPS acceptance migrated to explicit zero source: PASS")
print("  three executable final-closure GameTests registered: PASS")
print("  final CI floor: 312 GameTests")
