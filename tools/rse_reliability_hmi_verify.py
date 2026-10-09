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
screen = read("src/main/java/dev/redstoneengineering/ui/ldlib/ReliabilitySystemLdUi.java")

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
        "runtime[0] = 0;",
        "runtime[2]++;",
        "updateOutput(level, pos, state, 0);",
        "manualReset(level, pos);")

require(menu, "ReliabilitySystemMenu.java",
        "BUTTON_ACTION = 8",
        "id == BUTTON_ACTION",
        "runMaintenanceAction(block)",
        "watchdog.resetDiagnostics(level, blockPos)",
        "servo.homeAndReset(level, blockPos)",
        "sensor.resetMetrology(level, blockPos)",
        "voter.resetDiagnostics(level, blockPos)",
        "latch.manualReset(level, blockPos)",
        "refreshAuthoritativeSnapshot(); broadcastChanges();")

require(screen, "ReliabilitySystemLdUi.java",
        "Maintenance action",
        '"Reset watchdog diagnostics"',
        '"Home / reset trajectory"',
        '"Reset position metrology"',
        '"Reset voter diagnostics"',
        '"Manual reset latch"',
        "RseLdUiComponents.authorityFooter()",
        '"Routing stays on Route; maintenance actions use the same server methods as Shift-right-click."')
require(menu, "ReliabilitySystemMenu.java",
        "runMaintenance()",
        "clickMenuButton(playerInventory.player, BUTTON_ACTION)",
        "runMaintenanceAction(block)",
        "watchdog.resetDiagnostics(level, blockPos)",
        "servo.homeAndReset(level, blockPos)",
        "sensor.resetMetrology(level, blockPos)",
        "voter.resetDiagnostics(level, blockPos)",
        "latch.manualReset(level, blockPos)")

# Safety interlock must fail closed during reset and expose genuine retained evidence.
interlock = read("src/main/java/dev/redstoneengineering/block/SafetyInterlockBlock.java")
universal_menu = read("src/main/java/dev/redstoneengineering/ui/menu/UniversalFieldDeviceMenu.java")
universal_hmi = read("src/main/java/dev/redstoneengineering/ui/ldlib/UniversalFieldDeviceLdUi.java")
require(interlock, "SafetyInterlockBlock.java",
        "failedMask(level, pos) < 0 ? PortQuality.STALE : PortQuality.VALID",
        "RuntimeIntStore.remove(level, KEY, pos);",
        "updateOutput(level, pos, state, 0);",
        "server.scheduleTick(pos, this, 1);",
        "int blockedTicks(Level level, BlockPos pos)",
        "int permittedTicks(Level level, BlockPos pos)",
        "int transitionCount(Level level, BlockPos pos)")
require(universal_menu, "UniversalFieldDeviceMenu.java",
        "interlockBlockedTicks.set(SafetyInterlockBlock.blockedTicks(level, blockPos))",
        "interlockPermittedTicks.set(SafetyInterlockBlock.permittedTicks(level, blockPos))",
        "interlockTransitions.set(SafetyInterlockBlock.transitionCount(level, blockPos))")
require(universal_hmi, "UniversalFieldDeviceLdUi.java",
        "interlockInputs(menu.configPrimary())",
        "menu.interlockBlockedTicks()",
        "menu.interlockPermittedTicks()",
        "menu.interlockTransitions()",
        "NOT EVALUATED")
require(screen, "ReliabilitySystemLdUi.java",
        "LIVE RELIABILITY MECHANISM",
        "DEVICE-SPECIFIC RELIABILITY EVIDENCE",
        "quorum=",
        "DEGRADED VOTE",
        "max spread=",
        "disagreement events=")
for forbidden in ('"events A"', '"events B"', '"HEALTHY"'):
    if forbidden in screen:
        errors.append(f"Reliability HMI contains generic/misleading event or health token {forbidden}")

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
print(" fault latch: explicit manual reset preserves reset-event semantics")
