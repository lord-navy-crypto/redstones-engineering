#!/usr/bin/env python3
"""Fail-closed static guard for System-Level Closure Phase 2 acceptance coverage."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []


def text(path: str) -> str:
    return (ROOT / path).read_text()


def require(body: str, needle: str, label: str) -> None:
    if needle not in body:
        errors.append(f"{label}: missing {needle!r}")


tests = text("src/main/java/dev/redstoneengineering/gametest/RseSystemLevelClosurePhase2ProbeGameTests.java")
registration = text("src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java")
pid = text("src/main/java/dev/redstoneengineering/block/PidControllerBlock.java")
commissioning = text("src/main/java/dev/redstoneengineering/diagnostics/ClosedLoopCommissioning.java")

require(tests, "sampledPidLoopBoundsLongDynamicFeedback", "sample-boundary isolation probe")
require(tests, "sampledPidLoopSettlesAndFailsSafeOnFeedbackLoss", "closed-loop acceptance probe")
require(tests, "ClosedLoopCommissioning.inspectPid", "settling evidence readback")
require(tests, "snapshot.settlingTicks() <= 0", "positive settling requirement")
require(tests, "helper.setBlock(sampleHold, Blocks.AIR.defaultBlockState())", "feedback-loss injection")
require(tests, "failSafeOut != 0", "feedback-loss fail-safe assertion")
require(registration, "RseSystemLevelClosurePhase2ProbeGameTests.class", "Phase 2 GameTest registration")
require(pid, "requestedMode == AUTO_MODE && (!usable(setpointObservation) || !usable(processObservation))", "AUTO missing-evidence fail-safe")
require(pid, "return 0;", "PID fail-safe zero output")
require(commissioning, "settlingTicks", "commissioning settling contract")
require(commissioning, "overshoot", "commissioning overshoot contract")

if errors:
    print("RSE System-Level Closure Phase 2 verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE System-Level Closure Phase 2 verification: PASS")
print("  real sampled feedback path retained: PASS")
print("  settling evidence acceptance retained: PASS")
print("  feedback-loss fail-safe acceptance retained: PASS")
