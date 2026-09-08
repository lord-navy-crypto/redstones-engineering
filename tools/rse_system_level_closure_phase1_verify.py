#!/usr/bin/env python3
"""Fail-closed verifier for System-Level Closure Phase 1."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.exists():
        errors.append(f"missing {rel}")
        return ""
    return path.read_text(encoding="utf-8")


def need(src: str, needle: str, label: str) -> None:
    if needle not in src:
        errors.append(f"{label}: missing {needle!r}")


test_rel = "src/main/java/dev/redstoneengineering/gametest/RseSystemLevelClosurePhase1GameTests.java"
tests = read(test_rel)
registration = read("src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java")
workflow = read(".github/workflows/build.yml")
doc = read("docs/SYSTEM_LEVEL_CLOSURE.md")

required_tests = (
    "conditionedSignalChainConvergesAcrossRapidBoundaryChanges",
    "servoActuationMeasurementChainCrossesDomainsAndSettles",
    "servoCommandLossBrakesPlantAndRecoveryResumesMeasurementChain",
    "injectedFaultAlarmLifecycleRecoversWithoutGhostRelatch",
    "instrumentCableRemovalAndReplacementRecoversSymmetricTopology",
)
for name in required_tests:
    need(tests, name, test_rel)

if len(re.findall(r"@GameTest\s*\(", tests)) != 5:
    errors.append(f"{test_rel}: expected exactly 5 Phase 1 system-chain GameTests")

need(registration, "event.register(RseSystemLevelClosurePhase1GameTests.class);", "RseGameTestRegistration.java")
need(workflow, "tools/rse_system_level_closure_phase1_verify.py", "build.yml")
minimum_match = re.search(r"test_count < ([0-9]+)", workflow)
if minimum_match is None or int(minimum_match.group(1)) < 322:
    errors.append("build.yml: GameTest floor must be at least 322 for System-Level Closure Phase 1")

for phrase in (
    "Block-Level Closure",
    "System-Level Closure",
    "Phase 1 — Deterministic chains and recovery",
    "Phase 2 — Closed-loop control",
    "Phase 3 — Safety, protection and incident closure",
    "Phase 4 — Lifecycle, soak and process-bound closure",
    "122 / 122",
    "317 to 322",
    "same checks pass again on `main` after merge",
):
    need(doc, phrase, "SYSTEM_LEVEL_CLOSURE.md")

# Phase 1 is a closure/verification phase, not a content expansion phase.
for forbidden in (
    "DeferredRegister",
    "BLOCKS.register(",
    "registerBlock(",
    "new Block(",
):
    if forbidden in tests:
        errors.append(f"{test_rel}: Phase 1 must compose existing blocks, found content-registration token {forbidden}")

# Require representative cross-layer evidence rather than five renamed single-block tests.
for token in (
    "SIGNAL_CONDITIONER",
    "ANALOG_INDICATOR",
    "SERVO_ACTUATOR",
    "SERVO_POSITION_SENSOR",
    "FAULT_INJECTOR",
    "ALARM_PROCESSOR",
    "INSTRUMENT_CABLE",
    "ServoActuatorBlock.braking",
    "ServoPositionSensorBlock.sourceQuality",
    "ConnectedCableBlock.connected",
    "PortQuality.VALID",
):
    need(tests, token, test_rel)

if errors:
    print("RSE SYSTEM-LEVEL CLOSURE PHASE 1 VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    sys.exit(1)

print("RSE SYSTEM-LEVEL CLOSURE PHASE 1 VERIFY: PASS")
print("  phase: deterministic chains and recovery")
print("  cross-system GameTests: 5")
print("  gameplay blocks added by test suite: 0")
print("  covered chains: signal conditioning / servo actuation+measurement / command-loss recovery / fault+alarm / cable lifecycle")
print("  required GameTest floor: >=322")
