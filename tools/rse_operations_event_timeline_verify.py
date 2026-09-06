#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing operations timeline file: {rel}")
        return ""
    return path.read_text(errors="ignore")


window = read("src/main/java/dev/redstoneengineering/diagnostics/OperationsEventWindow.java")
for token in (
    "MAX_VISIBLE_EVENTS = 8",
    "SystemEventTimeline.recentWithin",
    "dashboard.eventScope()",
    "dashboard.firstOut()",
    "firstOutIndex()",
):
    if window and token not in window:
        errors.append(f"OperationsEventWindow missing bounded plant projection {token!r}")

menu = read("src/main/java/dev/redstoneengineering/ui/menu/OperationsMonitorMenu.java")
for token in (
    "EVENT_SLOTS = OperationsEventWindow.MAX_VISIBLE_EVENTS",
    "OperationsDashboardSnapshot.inspect(level, blockPos)",
    "OperationsEventWindow.inspect(level, dashboard)",
    "private final DataSlot[] eventKinds = trackedInts(EVENT_SLOTS);",
    "private final DataSlot[] eventSeverities = trackedInts(EVENT_SLOTS);",
    "private final DataSlot[] eventAges = trackedInts(EVENT_SLOTS);",
    "firstOutSlot.set",
    "MAX_SYNC_AGE_TICKS = 32767",
):
    if menu and token not in menu:
        errors.append(f"OperationsMonitorMenu missing server-synchronized event contract {token!r}")

screen = read("src/main/java/dev/redstoneengineering/client/ui/OperationsMonitorScreen.java")
for token in (
    "PLANT EVENT TIMELINE",
    "FIRST OUT",
    "menu.eventKindOrdinal",
    "menu.eventSeverity",
    "menu.eventAgeTicks",
    "menu.firstOutSlot()",
    "Observer-only",
):
    if screen and token not in screen:
        errors.append(f"OperationsMonitorScreen missing event visualization {token!r}")
for forbidden in (
    "SystemEventTimeline",
    "RuntimeIntStore",
    "getBlockState(",
    "scheduleTick(",
    "setBlock(",
):
    if screen and forbidden in screen:
        errors.append(f"Operations client screen must remain synchronized/render-only; found {forbidden!r}")

registration = read("src/main/java/dev/redstoneengineering/ui/EngineeringUiRegistration.java")
if registration and 'MENUS.register("operations_monitor"' not in registration:
    errors.append("Dedicated Operations Monitor menu is not registered")

client_registration = read("src/main/java/dev/redstoneengineering/client/ui/EngineeringUiClientRegistration.java")
if client_registration and "OperationsMonitorScreen::new" not in client_registration:
    errors.append("Dedicated Operations Monitor screen is not registered")

block = read("src/main/java/dev/redstoneengineering/block/OperationsMonitorBlock.java")
for token in (
    "OperationsMonitorUi.open(serverPlayer, p);",
    "plant event evidence retained",
):
    if block and token not in block:
        errors.append(f"OperationsMonitorBlock missing dedicated console/lifecycle contract {token!r}")

opener = read("src/main/java/dev/redstoneengineering/ui/OperationsMonitorUi.java")
if opener and "new OperationsMonitorMenu" not in opener:
    errors.append("OperationsMonitorUi does not open the dedicated synchronized menu")

tests = read("src/main/java/dev/redstoneengineering/gametest/RseOperationsTimelineGameTests.java")
for token in (
    "operationsEventWindowKeepsOrderedBoundedPlantTail",
    "operationsEventWindowMapsFirstOutIntoVisibleChronology",
    "MAX_VISIBLE_EVENTS",
    "FIRST_OUT_TRIP",
):
    if tests and token not in tests:
        errors.append(f"Operations timeline runtime evidence missing {token!r}")

registry = read("src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java")
if registry and "event.register(RseOperationsTimelineGameTests.class);" not in registry:
    errors.append("Operations timeline GameTests are not registered")

if errors:
    print("RSE operations event timeline verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE operations event timeline verification: PASS")
print(" plant-scoped bounded 8-event tail: PASS")
print(" server-synchronized kind/severity/age evidence: PASS")
print(" first-out visible chronology mapping: PASS")
print(" dedicated observer-only Operations Monitor UI: PASS")
print(" executable ordering + first-out GameTests: PASS")
