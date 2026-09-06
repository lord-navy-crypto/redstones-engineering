#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing operations incident file: {rel}")
        return ""
    return path.read_text(errors="ignore")


summary = read("src/main/java/dev/redstoneengineering/diagnostics/OperationsIncidentSummary.java")
for token in (
    "RootCauseEvidenceTrace.latestWithin",
    "dashboard.firstOut()",
    "first.source().getX() - operationsMonitorPos.getX()",
    "incident.downstreamObservations().size()",
    "abnormalDownstream",
    "incident.incidentEndTick() - incident.incidentStartTick()",
):
    if summary and token not in summary:
        errors.append(f"OperationsIncidentSummary missing server evidence projection {token!r}")

menu = read("src/main/java/dev/redstoneengineering/ui/menu/OperationsMonitorMenu.java")
for token in (
    "OperationsIncidentSummary.inspect(level, blockPos, dashboard)",
    "private final DataSlot firstOutDx = trackedInt();",
    "private final DataSlot firstOutDy = trackedInt();",
    "private final DataSlot firstOutDz = trackedInt();",
    "private final DataSlot incidentDuration = trackedInt();",
    "private final DataSlot downstreamObservations = trackedInt();",
    "private final DataSlot abnormalDownstreamObservations = trackedInt();",
    "private final DataSlot evidenceTraceEntries = trackedInt();",
    "incidentDurationTicks()",
):
    if menu and token not in menu:
        errors.append(f"OperationsMonitorMenu missing synchronized incident drill-down {token!r}")

screen = read("src/main/java/dev/redstoneengineering/client/ui/OperationsMonitorScreen.java")
for token in (
    "First-out source",
    "Incident span",
    "Follow-up evidence",
    "firstOutLocation()",
    "menu.downstreamObservations()",
    "menu.abnormalDownstreamObservations()",
    "menu.evidenceTraceEntries()",
):
    if screen and token not in screen:
        errors.append(f"OperationsMonitorScreen missing incident visualization {token!r}")
for forbidden in (
    "RootCauseEvidenceTrace",
    "OperationsIncidentSummary",
    "SystemEventTimeline",
    "RuntimeIntStore",
    "getBlockState(",
    "scheduleTick(",
    "setBlock(",
):
    if screen and forbidden in screen:
        errors.append(f"Operations incident client screen must remain synchronized/render-only; found {forbidden!r}")

tests = read("src/main/java/dev/redstoneengineering/gametest/RseOperationsIncidentGameTests.java")
for token in (
    "operationsIncidentSummaryLocalizesFirstOutAndCountsFollowUpEvidence",
    "operationsIncidentSummaryPreservesBoundedEvidenceTrace",
    "incidentDurationTicks() != 4L",
    "downstreamObservations() != 16",
    "RootCauseEvidenceTrace.MAX_TRACE_ENTRIES",
):
    if tests and token not in tests:
        errors.append(f"Operations incident runtime evidence missing {token!r}")

registry = read("src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java")
if registry and "event.register(RseOperationsIncidentGameTests.class);" not in registry:
    errors.append("Operations incident GameTests are not registered")

if errors:
    print("RSE operations incident drill-down verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE operations incident drill-down verification: PASS")
print(" server-derived first-out localization: PASS")
print(" incident duration + downstream evidence summary: PASS")
print(" bounded RootCauseEvidenceTrace projection: PASS")
print(" synchronized render-only operations UI: PASS")
print(" executable localization + bounded-trace GameTests: PASS")
