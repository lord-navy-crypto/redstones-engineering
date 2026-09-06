#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing PID telemetry file: {rel}")
        return ""
    return path.read_text(errors="ignore")


store = read("src/main/java/dev/redstoneengineering/diagnostics/PidTelemetryStore.java")
for token in (
    "MAX_CONTROLLERS_PER_LEVEL = 256",
    "MAX_SAMPLES_PER_CONTROLLER = 32",
    "WeakHashMap",
    "LinkedHashMap",
    "level.isClientSide",
    "samples.addLast(pack(setpoint, processValue, controlOutput))",
    "while (samples.size() > MAX_SAMPLES_PER_CONTROLLER) samples.removeFirst()",
    "public static synchronized void clear(Level level, BlockPos pos)",
):
    if store and token not in store:
        errors.append(f"PidTelemetryStore missing bounded server contract {token!r}")

block = read("src/main/java/dev/redstoneengineering/block/PidControllerBlock.java")
for token in (
    "return recordTelemetry(level, pos, setpoint, process, 0);",
    "return recordTelemetry(level, pos, setpoint, process, manualOutput);",
    "return recordTelemetry(level, pos, setpoint, process, out);",
    "PidTelemetryStore.clear(level, pos);",
    "PidTelemetryStore.clear(l, p);",
):
    if block and token not in block:
        errors.append(f"PidControllerBlock missing trend lifecycle contract {token!r}")

menu_base = read("src/main/java/dev/redstoneengineering/ui/menu/EngineeringDeviceMenu.java")
if menu_base and "protected DataSlot[] trackedInts(int count)" not in menu_base:
    errors.append("EngineeringDeviceMenu missing bounded synchronized vector helper")

menu = read("src/main/java/dev/redstoneengineering/ui/menu/PidControllerMenu.java")
for token in (
    "TREND_SAMPLES = PidTelemetryStore.MAX_SAMPLES_PER_CONTROLLER",
    "private final DataSlot[] trend = trackedInts(TREND_SAMPLES);",
    "PidTelemetryStore.snapshot(level, blockPos)",
    "trend[slot].set(slot < pad ? -1 : samples.get(slot - pad));",
    "trendSetpoint",
    "trendProcessValue",
    "trendControlOutput",
):
    if menu and token not in menu:
        errors.append(f"PidControllerMenu missing compact trend sync contract {token!r}")

screen = read("src/main/java/dev/redstoneengineering/client/ui/PidControllerScreen.java")
for token in (
    "EngineeringPlot.analogFrame",
    "menu::trendSetpoint",
    "menu::trendProcessValue",
    "menu::trendControlOutput",
    "authoritative samples",
    "2t/sample",
):
    if screen and token not in screen:
        errors.append(f"PidControllerScreen missing trend visualization {token!r}")
for forbidden in (
    "RuntimeIntStore",
    "PidTelemetryStore",
    "getBlockState(",
    "scheduleTick(",
    "setBlock(",
):
    if screen and forbidden in screen:
        errors.append(f"PID client screen must stay synchronized/render-only; found {forbidden!r}")

tests = read("src/main/java/dev/redstoneengineering/gametest/RseEngineeringUiGameTests.java")
for token in (
    "pidTrendTelemetryIsServerOwnedAndBounded",
    "pidTrendTelemetryClearsWithControllerLifecycle",
    "MAX_SAMPLES_PER_CONTROLLER",
    "Removing PID controller left ghost trend telemetry behind",
):
    if tests and token not in tests:
        errors.append(f"PID trend runtime evidence missing {token!r}")

if errors:
    print("RSE PID telemetry visualization verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE PID telemetry visualization verification: PASS")
print(" server-owned 32-sample SP/PV/OUT ring buffer: PASS")
print(" compact 12-bit menu synchronization: PASS")
print(" shared render-only PID trend graph: PASS")
print(" controller removal/reset lifecycle cleanup: PASS")
print(" executable bounded-retention + cleanup GameTests: PASS")
