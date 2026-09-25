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
        "boolean resetDiagnostics(Level level, BlockPos pos)",
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
        "Combined operational evidence is intentionally distinct from the FRONT alarm output quality")

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
        '"SERVER SYNCHRONIZED • FRONT ALARM OUTPUT VALID"')

if "quality.set(snapshotQuality(latch, state, out).ordinal())" in menu:
    errors.append("Fault Latch HMI regressed to reporting authoritative alarm-output quality as device evidence")

if errors:
    print("RSE RELIABILITY HMI VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE RELIABILITY HMI VERIFY: PASS")
print(" watchdog: explicit diagnostics reset shares Shift-right-click server method")
print(" servo: explicit home/trajectory reset shares renderer-safe server method")
print(" position sensor: explicit metrology reset shares server method")
print(" voter: explicit diagnostics reset shares server method")
print(" fault latch: reset is permissive-gated, edge-safe, and shared with HMI maintenance action")
print(" fault latch: FAULT/RESET input qualities are synchronized separately from authoritative alarm output")
