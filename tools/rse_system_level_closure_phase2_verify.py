#!/usr/bin/env python3
"""Fail-closed static guard for System-Level Closure Phase 2 acceptance coverage."""
from pathlib import Path

import rse_validation_factory as factory

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


def require_block(
    by_pos: dict[tuple[int, int, int], factory.BlockSpec],
    pos: tuple[int, int, int],
    expected: str,
    label: str,
) -> None:
    actual = block_id(by_pos[pos]) if pos in by_pos else "<missing>"
    if actual != expected:
        errors.append(f"{label}: {pos} expected {expected}, got {actual}")


def require_props(
    by_pos: dict[tuple[int, int, int], factory.BlockSpec],
    pos: tuple[int, int, int],
    expected: dict[str, str],
    label: str,
) -> None:
    if pos not in by_pos:
        errors.append(f"{label}: {pos} missing")
        return
    actual = props(by_pos[pos])
    for key, value in expected.items():
        if actual.get(key) != value:
            errors.append(f"{label}: {pos} expected {key}={value}, got {actual}")


def require_terminal(
    by_pos: dict[tuple[int, int, int], factory.BlockSpec],
    pos: tuple[int, int, int],
    facing: str,
    output_mode: bool,
    label: str,
) -> None:
    require_block(by_pos, pos, "redstoneengineering:redstone_cable_terminal", label)
    require_props(
        by_pos,
        pos,
        {"facing": facing, "output_mode": "true" if output_mode else "false"},
        label,
    )


tests = text("src/main/java/dev/redstoneengineering/gametest/RseSystemLevelClosurePhase2ProbeGameTests.java")
registration = text("src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java")
pid = text("src/main/java/dev/redstoneengineering/block/PidControllerBlock.java")
commissioning = text("src/main/java/dev/redstoneengineering/diagnostics/ClosedLoopCommissioning.java")
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

# Validate the actual structure that the production generator writes, not the pre-repair visual layout.
cell_e = dict(factory._mega_structure("mega_cell_e_comms")[1])
require_block(cell_e, (1, 1, 12), "redstoneengineering:redstone_reference_source", "heartbeat PWM command source")
require_props(cell_e, (1, 1, 12), {"facing": "east", "power": "8"}, "heartbeat PWM command source")
require_block(cell_e, (2, 1, 12), "redstoneengineering:pwm_controller", "heartbeat PWM generator")
require_props(
    cell_e,
    (2, 1, 12),
    {"facing": "east", "input_facing": "west", "period_mode": "0", "invert": "false"},
    "heartbeat PWM generator",
)
require_terminal(cell_e, (22, 1, 12), "west", False, "D24-to-watchdog cable ingress")
for x in range(23, 26):
    require_block(cell_e, (x, 1, 12), "redstoneengineering:redstone_signal_cable", "D24-to-watchdog insulated run")
require_terminal(cell_e, (26, 1, 12), "east", True, "watchdog cable egress")

cell_h = dict(factory._mega_structure("mega_cell_h_control")[1])
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

require(mega_evaluator, "case 25 -> evaluateWatchdog", "watchdog dedicated evaluator")
require(mega_evaluator, "WatchdogBlock.transitionCount", "watchdog transition evidence")
require(mega_evaluator, "WatchdogBlock.ageTicks", "watchdog freshness evidence")
require(mega_evaluator, "WATCHDOG_TIMEOUT", "watchdog timeout rejection")

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
print("  Mega physical PWM heartbeat retained: PASS")
print("  Mega watchdog transition/freshness acceptance retained: PASS")