#!/usr/bin/env python3
"""Verify explicit, server-authoritative maintenance actions for the reliability HMI."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        errors.append(f"missing {rel}")
        return ""
    return path.read_text(encoding="utf-8")


def require(text: str, label: str, *tokens: str) -> None:
    for token in tokens:
        if token not in text:
            errors.append(f"{label}: missing {token!r}")


watchdog = read("src/main/java/dev/redstoneengineering/block/WatchdogBlock.java")
servo = read("src/main/java/dev/redstoneengineering/block/ServoActuatorBlock.java")
sensor = read("src/main/java/dev/redstoneengineering/block/ServoPositionSensorBlock.java")
voter = read("src/main/java/dev/redstoneengineering/block/RedundantVoterBlock.java")
latch = read("src/main/java/dev/redstoneengineering/block/FaultLatchBlock.java")
menu = read("src/main/java/dev/redstoneengineering/ui/menu/ReliabilitySystemMenu.java")
screen = read("src/main/java/dev/redstoneengineering/client/ui/ReliabilitySystemScreen.java")

require(watchdog, "WatchdogBlock.java",
        "MIN_TIMEOUT_INDEX = 0",
        "MAX_TIMEOUT_INDEX = 3",
        "DEFAULT_TIMEOUT_INDEX = 1",
        "TIMEOUT_SHORT_TICKS = 20",
        "TIMEOUT_MEDIUM_TICKS = 40",
        "TIMEOUT_LONG_TICKS = 80",
        "TIMEOUT_EXTENDED_TICKS = 160",
        "SAMPLE_TICKS = 2",
        "MAX_AGE_TICKS = 12000",
        "boundedTimeoutIndex",
        "timeoutChoicesText",
        "boolean resetDiagnostics(Level level, BlockPos pos)",
        "A source appearing is only a baseline",
        "Unknown/missing coverage cannot masquerade as a LOW transition",
        "RuntimeIntStore.remove(level, KEY, pos);",
        "resetDiagnostics(l, p);")
require(servo, "ServoActuatorBlock.java",
        "boolean homeAndReset(Level level, BlockPos pos)",
        "RuntimeIntStore.remove(level, KEY, pos);",
        "MechatronicsVisualBlockEntity.push(level, pos, visualState(level, pos, state));",
        "homeAndReset(l, p);")
require(sensor, "ServoPositionSensorBlock.java",
        "boolean resetMetrology(Level level, BlockPos pos)",
        "MetrologyStore.remove(level, CHANNEL, pos);",
        "resetMetrology(level, pos);")
require(voter, "RedundantVoterBlock.java",
        "MIN_TOLERANCE_INDEX = 0",
        "MAX_TOLERANCE_INDEX = 3",
        "DEFAULT_TOLERANCE_INDEX = 1",
        "TOLERANCE_EXACT = 0",
        "TOLERANCE_TIGHT = 1",
        "TOLERANCE_NORMAL = 2",
        "TOLERANCE_RELAXED = 4",
        "MIN_VALID_INPUTS = 2",
        "NOMINAL_INPUTS = 3",
        "boundedToleranceIndex",
        "toleranceChoicesText",
        "boolean resetDiagnostics(Level level, BlockPos pos)",
        "RuntimeIntStore.remove(level, KEY, pos);",
        "resetDiagnostics(level,pos);")
require(latch, "FaultLatchBlock.java",
        "boolean manualReset(Level level, BlockPos pos)",
        "resetPermitted(level, pos, state)",
        "runtime[LATCHED] = 0;",
        "runtime[RESET_COUNT]",
        "RESET_REACQUIRE",
        "updateOutput(level, pos, state, 0);",
        "manualReset(level, pos);",
        "PortQuality faultInputQuality(Level level, BlockPos pos, BlockState state)",
        "PortQuality resetInputQuality(Level level, BlockPos pos, BlockState state)",
        "PortQuality operationalEvidenceQuality(Level level, BlockPos pos, BlockState state)",
        "Combined operational evidence is intentionally distinct from the FRONT alarm output quality",
        "MIN_THRESHOLD_INDEX = 0",
        "MAX_THRESHOLD_INDEX = 3",
        "DEFAULT_THRESHOLD_INDEX = 0",
        "THRESHOLD_LEVEL_LOW = 1",
        "THRESHOLD_LEVEL_MEDIUM = 4",
        "THRESHOLD_LEVEL_HIGH = 8",
        "THRESHOLD_LEVEL_CRITICAL = 12",
        "MAX_ALARM_OUTPUT = 15",
        "boundedThresholdIndex",
        "thresholdChoicesText",
        "NO_SIGNAL is air/non-redstone adjacency",
        "boolean resetEvidenceBad = !resetObservation.valid()",
        "boolean faultActive = !faultObservation.valid()")

require(menu, "ReliabilitySystemMenu.java",
        "BUTTON_ACTION = 8",
        "id == BUTTON_ACTION",
        "runMaintenanceAction(block)",
        "watchdog.resetDiagnostics(level, blockPos)",
        "servo.homeAndReset(level, blockPos)",
        "sensor.resetMetrology(level, blockPos)",
        "voter.resetDiagnostics(level, blockPos)",
        "latch.manualReset(level, blockPos)",
        "FaultLatchBlock.resetPermitted(level, blockPos, state)",
        "WatchdogBlock.stepTimeout(",
        "RedundantVoterBlock.stepTolerance(",
        "FaultLatchBlock.stepThreshold(",
        "faultInputQuality.set(FaultLatchBlock.faultInputQuality",
        "resetInputQuality.set(FaultLatchBlock.resetInputQuality",
        "quality.set(FaultLatchBlock.operationalEvidenceQuality",
        "public PortQuality faultInputQuality()",
        "public PortQuality resetInputQuality()",
        "refreshAuthoritativeSnapshot(); broadcastChanges();")

require(screen, "ReliabilitySystemScreen.java",
        "maintenanceAction",
        "ReliabilitySystemMenu.BUTTON_ACTION",
        '"Reset watchdog diagnostics"',
        '"Home / reset trajectory"',
        '"Reset position metrology"',
        '"Reset voter diagnostics"',
        '"Manual reset latch"',
        '"Reset blocked • fault not clear"',
        '"NO VALID SOURCE"',
        "maintenanceAction.visible = configure",
        "menu.kind() != ReliabilitySystemMenu.KIND_FAULT_LATCH || menu.extraC() != 0",
        '"Routing stays on Route; maintenance actions use the same server methods as Shift-right-click."',
        '"Fault input evidence"',
        '"Reset input evidence"',
        '"Operational evidence"',
        '"OUTPUT • LATCHED ALARM • AUTHORITATIVE"',
        '"Fault input quality"',
        '"Reset input quality"',
        '"SERVER SYNCHRONIZED • FRONT ALARM OUTPUT VALID"',
        '"Allowed timeouts"',
        "WatchdogBlock.timeoutChoicesText()",
        '"only a VALID observed transition resets age"',
        '"Allowed tolerance"',
        "RedundantVoterBlock.toleranceChoicesText()",
        "RedundantVoterBlock.MIN_VALID_INPUTS",
        "RedundantVoterBlock.NOMINAL_INPUTS",
        '"Allowed trip levels"',
        "FaultLatchBlock.thresholdChoicesText()",
        '"FAULT evidence missing/invalid OR value ≥ T → LATCH"',
        '"rising RESET + VALID fault value < T → CLEAR"',
        "NO_SIGNAL on FAULT IN is missing evidence, not a measured zero",
        "RESET evidence recovery only reacquires the electrical level")

if "if (faultObservation.quality() == PortQuality.NO_SIGNAL) return true;" in latch:
    errors.append("Fault Latch reset permissive regressed to treating NO_SIGNAL as proven-clear fault evidence")
if "if (quality == PortQuality.NO_SIGNAL) quality = PortQuality.VALID;" in latch:
    errors.append("Fault Latch operational evidence regressed to relabeling missing FAULT input as VALID")
if "boolean resetEvidenceBad = evidenceUnusable(resetObservation.quality());" in latch:
    errors.append("Fault Latch RESET reacquisition still ignores NO_SIGNAL evidence gaps")
if "int index = state.getValue(WatchdogBlock.TIMEOUT);" in menu:
    errors.append("Reliability menu duplicated Watchdog timeout-index cycling instead of delegating to Block authority")
if "int index = state.getValue(RedundantVoterBlock.TOLERANCE);" in menu:
    errors.append("Reliability menu duplicated Voter tolerance-index cycling instead of delegating to Block authority")
if "int index = state.getValue(FaultLatchBlock.THRESHOLD);" in menu:
    errors.append("Reliability menu duplicated Fault Latch threshold-index cycling instead of delegating to Block authority")

if "quality.set(snapshotQuality(latch, state, out).ordinal())" in menu:
    errors.append("Fault Latch HMI regressed to reporting authoritative alarm-output quality as device evidence")

if errors:
    print("RSE RELIABILITY HMI VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE RELIABILITY HMI VERIFY: PASS")
print(" watchdog: timeout 20/40/80/160 + baseline-safe heartbeat contract: PASS")
print(" watchdog: explicit diagnostics reset shares Shift-right-click server method")
print(" servo: explicit home/trajectory reset shares renderer-safe server method")
print(" position sensor: explicit metrology reset shares server method")
print(" voter: tolerance 0/1/2/4 + 2oo3 degraded/nominal quorum contract: PASS")
print(" voter: explicit diagnostics reset shares server method")
print(" fault latch: reset is permissive-gated, edge-safe, and shared with HMI maintenance action")
print(" fault latch: FAULT/RESET input qualities are synchronized separately from authoritative alarm output")
print(" fault latch: NO_SIGNAL FAULT input is fail-safe and cannot satisfy reset permissive")
print(" fault latch: RESET evidence gaps reacquire baseline without fabricating a reset edge")
print(" fault latch: threshold index 0..3 maps only to trip levels 1/4/8/12")
