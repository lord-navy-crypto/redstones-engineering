#!/usr/bin/env python3
"""Static guard for the executable functional-correctness program.

The GameTests themselves prove runtime behavior. This verifier makes sure the
high-value tests stay registered, the common redstone processor keeps the
world-facing 0..15 ownership contract, and AMR safety state never invents a
false diagnostic cause.
"""

from __future__ import annotations

import sys
from pathlib import Path

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(relative: str) -> str:
    path = root / relative
    if not path.is_file():
        errors.append(f"missing: {relative}")
        return ""
    return path.read_text(errors="ignore")


tests = read("src/main/java/dev/redstoneengineering/gametest/RseFunctionalCorrectnessGameTests.java")
registration = read("src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java")
directional = read("src/main/java/dev/redstoneengineering/block/DirectionalSignalBlock.java")
conditioner = read("src/main/java/dev/redstoneengineering/block/SignalConditionerBlock.java")
conditioner_logic = read("src/main/java/dev/redstoneengineering/signal/SignalConditionerLogic.java")
robot_entity = read("src/main/java/dev/redstoneengineering/entity/EngineeringMobileRobotEntity.java")
robot_safety = read("src/main/java/dev/redstoneengineering/robotics/RobotSafetyAssessment.java")
robot_state_machine = read("src/main/java/dev/redstoneengineering/robotics/RobotStateMachine.java")
workflow = read(".github/workflows/build.yml")
matrix = read("docs/FUNCTIONAL_CORRECTNESS_MATRIX.md")

for method in (
    "conditionerChainProducesExpectedWorldOutput",
    "sourceRemovalClearsDownstreamState",
    "conditionerGainSaturatesAtVanillaBoundary",
    "conditionerRejectsSideFeed",
    "conditionerOffsetAndThresholdModesMatchConfiguredSemantics",
    "conditionerDeadbandRetainsAndReleasesOutputDeterministically",
):
    if f"void {method}(GameTestHelper helper)" not in tests:
        errors.append(f"functional GameTest missing: {method}")

if "event.register(RseFunctionalCorrectnessGameTests.class);" not in registration:
    errors.append("functional correctness GameTests are not registered")

for token in (
    "EngineeringSignal.clamp(requestedOutput)",
    "direction == outputSide(state).getOpposite() ? state.getValue(OUTPUT) : 0",
    "side == inputSide(state) || side == outputSide(state)",
):
    if token not in directional:
        errors.append(f"DirectionalSignalBlock lost core directional/boundary contract: {token}")

for token in (
    "SignalConditionerLogic.apply(",
    "SignalConditionerLogic.limiting(",
):
    if token not in conditioner:
        errors.append(f"SignalConditioner block no longer delegates to pure transfer authority: {token}")

for token in (
    "case MODE_SCALE -> EngineeringSignal.clamp",
    "case MODE_OFFSET -> EngineeringSignal.clamp",
    "case MODE_CLAMP -> Math.min",
    "case MODE_THRESHOLD -> x >= p ? x : 0",
    "case MODE_DEADBAND -> Math.abs(x - yPrevious) >= p ? x : yPrevious",
    "case MODE_ATTENUATE -> EngineeringSignal.clamp",
):
    if token not in conditioner_logic:
        errors.append(f"SignalConditioner pure mode contract missing: {token}")

for token in (
    'new Snapshot(Verdict.SAFE_STOP, false, "E_STOP_ACTIVE")',
    'new Snapshot(Verdict.FAULT, false, "DRIVE_NOT_READY")',
    'new Snapshot(Verdict.SAFE_STOP, false, "LOCALIZATION_LOST")',
    'new Snapshot(Verdict.SAFE_STOP, false, "LOCALIZATION_STALE")',
    'new Snapshot(Verdict.DEGRADED_HOLD, false, "LOCALIZATION_DEGRADED")',
    'new Snapshot(Verdict.SAFE_STOP, false, "OBSTACLE_UNSAFE")',
):
    if token not in robot_safety:
        errors.append(f"AMR safety evidence contract missing: {token}")

for token in (
    "SAFETY_STOP_REQUESTED",
    "event == Event.LOCALIZATION_LOST || event == Event.SAFETY_STOP_REQUESTED",
    "event == Event.SENSOR_DEGRADED",
):
    if token not in robot_state_machine:
        errors.append(f"AMR state-machine safety contract missing: {token}")

for token in (
    "applySafetyHold(safety);",
    '"LOCALIZATION_LOST".equals(safety.primaryReason())',
    '"OBSTACLE_UNSAFE".equals(safety.primaryReason())',
    "RobotStateMachine.Event.SAFETY_STOP_REQUESTED",
    "RobotStateMachine.Event.SENSOR_DEGRADED",
    "RobotStateMachine.Event.CRITICAL_FAULT",
):
    if token not in robot_entity:
        errors.append(f"AMR authoritative safety routing missing: {token}")

# A generic safety hold must never be relabeled as localization loss. The only
# LOCALIZATION_LOST transition in the entity must sit behind the matching
# authoritative safety reason.
if robot_entity.count("RobotStateMachine.Event.LOCALIZATION_LOST") != 1:
    errors.append("AMR entity must have exactly one explicit LOCALIZATION_LOST transition")

for token in (
    "A — In-world end-to-end",
    "Signal Conditioner",
    "Redstone → Lapis → Redstone placed-world round trip",
    "unloaded-chunk/no-force-load behavior",
):
    if token not in matrix:
        errors.append(f"functional correctness matrix missing: {token}")

for token in (
    "rse_functional_correctness_verify.py",
    "runGameTestServer",
):
    if token not in workflow:
        errors.append(f"workflow missing functional correctness gate: {token}")

if errors:
    print("RSE functional correctness verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE functional correctness verification: PASS")
print("  six in-world redstone processing correctness tests: registered")
print("  shared 0..15 + FRONT/BACK contract: guarded")
print("  AMR authoritative safety reason/state routing: guarded")
print("  correctness evidence matrix: present")
print("  Minecraft GameTest execution gate: present")
