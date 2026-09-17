#!/usr/bin/env python3
"""Fail-closed static guard for System-Level Closure Phase 2 acceptance coverage."""
from pathlib import Path

from tools import rse_mega_factory as mega
from tools import rse_validation_factory as factory

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []


def text(path: str) -> str:
    return (ROOT / path).read_text()


def require(body: str, needle: str, label: str) -> None:
    if needle not in body:
        errors.append(f"{label}: missing {needle!r}")


def block_id(spec: factory.BlockSpec) -> str:
    return factory._normalize_block_spec(spec)[0]


def props(spec: factory.BlockSpec) -> dict[str, str]:
    return dict(factory._normalize_block_spec(spec)[1])


def require_block(by_pos: dict[tuple[int, int, int], factory.BlockSpec], pos: tuple[int, int, int], expected: str, label: str) -> None:
    actual = block_id(by_pos[pos]) if pos in by_pos else "<missing>"
    if actual != expected:
        errors.append(f"{label}: {pos} expected {expected}, got {actual}")


def require_terminal(by_pos: dict[tuple[int, int, int], factory.BlockSpec], pos: tuple[int, int, int], facing: str, output_mode: bool, label: str) -> None:
    require_block(by_pos, pos, "redstoneengineering:redstone_cable_terminal", label)
    if pos not in by_pos:
        return
    actual = props(by_pos[pos])
    expected_mode = "true" if output_mode else "false"
    if actual.get("facing") != facing or actual.get("output_mode") != expected_mode:
        errors.append(f"{label}: {pos} expected facing={facing} output_mode={expected_mode}, got {actual}")


tests = text("src/main/java/dev/redstoneengineering/gametest/RseSystemLevelClosurePhase2ProbeGameTests.java")
registration = text("src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java")
pid = text("src/main/java/dev/redstoneengineering/block/PidControllerBlock.java")
commissioning = text("src/main/java/dev/redstoneengineering/diagnostics/ClosedLoopCommissioning.java")
mega_service = text("src/main/java/dev/redstoneengineering/validation/RseMegaValidationService.java")
mega_evaluator = text("src/main/java/dev/redstoneengineering/validation/RseMegaStationEvaluator.java")

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

cell_e = dict(mega.MEGA_STRUCTURES["mega_cell_e_comms"]()[1])
require_terminal(cell_e, (22, 1, 12), "west", False, "D24-to-watchdog cable ingress")
for x in range(23, 26):
    require_block(cell_e, (x, 1, 12), "redstoneengineering:redstone_signal_cable", "D24-to-watchdog insulated run")
require_terminal(cell_e, (26, 1, 12), "east", True, "watchdog cable egress")

cell_h = dict(mega.MEGA_STRUCTURES["mega_cell_h_control"]()[1])
require_terminal(cell_h, (4, 1, 12), "west", False, "PID command cable ingress")
for x in range(5, 8):
    require_block(cell_h, (x, 1, 12), "redstoneengineering:redstone_signal_cable", "PID command insulated run")
require_terminal(cell_h, (8, 1, 12), "east", True, "servo command cable egress")
require_terminal(cell_h, (10, 1, 11), "south", False, "position feedback cable ingress")
require_block(cell_h, (10, 1, 10), "redstoneengineering:redstone_signal_cable", "position feedback insulated run")
require_block(cell_h, (9, 1, 10), "redstoneengineering:redstone_signal_cable", "position feedback insulated run")
require_terminal(cell_h, (8, 1, 10), "west", True, "fault injector input boundary")
require_terminal(cell_h, (6, 1, 10), "east", False, "fault injector output boundary")
for x in range(3, 6):
    require_block(cell_h, (x, 1, 10), "redstoneengineering:redstone_signal_cable", "PID feedback insulated run")
require_terminal(cell_h, (3, 1, 11), "south", True, "PID process-value cable egress")

require(mega_service, "E_HEARTBEAT_SOURCE", "Mega heartbeat fixture")
require(mega_service, "phaseAge / 8L", "Mega heartbeat transitions")
require(mega_service, "phase == Phase.STRUCTURE_PRECHECK && phaseAge >= 8L && phaseAge < 12L", "alarm startup reset pulse")
require(mega_service, "case STRUCTURE_PRECHECK -> 16;", "startup commissioning settle window")
require(mega_evaluator, "case 25 -> evaluateWatchdog", "watchdog dedicated evaluator")
require(mega_evaluator, "WatchdogBlock.transitionCount", "watchdog transition evidence")

if errors:
    print("RSE System-Level Closure Phase 2 verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE System-Level Closure Phase 2 verification: PASS")
print("  real sampled feedback path retained: PASS")
print("  settling evidence acceptance retained: PASS")
print("  feedback-loss fail-safe acceptance retained: PASS")
print("  Mega physical terminal boundaries retained: PASS")
print("  Mega watchdog heartbeat evidence retained: PASS")
print("  Mega alarm startup reset retained: PASS")
