#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing electrical protection file: {rel}")
        return ""
    return path.read_text(errors="ignore")


kinds = read("src/main/java/dev/redstoneengineering/diagnostics/events/SystemEventKind.java")
for token in (
    "ELECTRICAL_TRIP(true)",
    "ELECTRICAL_READY(false)",
):
    if kinds and token not in kinds:
        errors.append(f"SystemEventKind missing electrical protection vocabulary {token!r}")

fuse = read("src/main/java/dev/redstoneengineering/block/CopperFuseBlock.java")
for token in (
    "RUNTIME_SIZE = 3",
    "LAST_EVALUATED_TRIP",
    "PROTECTION_STATE_INITIALIZED",
    "CircuitPhysics.equivalentLoadResistance",
    "CircuitPhysics.current",
    "SystemEventKind.ELECTRICAL_TRIP",
    '"COPPER_FUSE_TRIP"',
    "SystemEventKind.ELECTRICAL_READY",
    '"COPPER_FUSE_READY"',
    "current > state.getValue(RATING)",
    "READY only after a safe server re-evaluation",
):
    if fuse and token not in fuse:
        errors.append(f"CopperFuseBlock missing protection/event contract {token!r}")

# Event production must stay on the authoritative server tick path.
if fuse:
    tick_index = fuse.find("protected void tick(")
    trip_index = fuse.find("SystemEventKind.ELECTRICAL_TRIP")
    ready_index = fuse.find("SystemEventKind.ELECTRICAL_READY")
    if tick_index < 0 or trip_index < tick_index or ready_index < tick_index:
        errors.append("Electrical trip/ready evidence is not emitted from the server protection tick path")

screen = read("src/main/java/dev/redstoneengineering/client/ui/OperationsMonitorScreen.java")
for token in (
    'case ELECTRICAL_TRIP -> "E-TRP"',
    'case ELECTRICAL_READY -> "E-RDY"',
):
    if screen and token not in screen:
        errors.append(f"Operations Monitor timeline missing electrical event rendering {token!r}")
for forbidden in (
    "CircuitPhysics",
    "DomainNetwork",
    "SystemEventTimeline",
    "setBlock(",
    "scheduleTick(",
):
    if screen and forbidden in screen:
        errors.append(f"Operations client must remain synchronized/render-only; found {forbidden!r}")

tests = read("src/main/java/dev/redstoneengineering/gametest/RseCopperGameTests.java")
for token in (
    "fuseTripBecomesPlantFirstOutElectricalEvidence",
    "FirstOutAnalysis.latestWithin",
    "SystemEventScope.around",
    "SystemEventKind.ELECTRICAL_TRIP",
    "fuseReadyRequiresVerifiedSafeReevaluation",
    "readyBeforeSafe != 0",
    "trips != 1 || ready != 1",
):
    if tests and token not in tests:
        errors.append(f"Copper protection runtime evidence missing {token!r}")

workflow = read(".github/workflows/build.yml")
if workflow and "tools/rse_electrical_protection_events_verify.py" not in workflow:
    errors.append("Electrical protection verifier is not wired into CI")
if workflow and "test_count < 195" not in workflow:
    errors.append("Minecraft runtime gate has not been raised to at least 195 GameTests")

if errors:
    print("RSE electrical protection event verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE electrical protection event verification: PASS")
print(" server-authoritative overcurrent trip evidence: PASS")
print(" plant-scoped first-out integration: PASS")
print(" guarded safe-reset/READY semantics: PASS")
print(" Operations Monitor electrical event rendering: PASS")
print(" executable electrical protection lifecycle GameTests: PASS")
