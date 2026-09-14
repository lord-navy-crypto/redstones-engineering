#!/usr/bin/env python3
"""Static regression gate for the first Industrial Operations foundation layer."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
ASSESSMENT = ROOT / "src/main/java/dev/redstoneengineering/diagnostics/IndustrialOperationsAssessment.java"
MENU = ROOT / "src/main/java/dev/redstoneengineering/ui/menu/OperationsMonitorMenu.java"
BLOCK = ROOT / "src/main/java/dev/redstoneengineering/block/OperationsMonitorBlock.java"

errors = []

def read(path):
    if not path.exists():
        errors.append(f"missing {path.relative_to(ROOT)}")
        return ""
    return path.read_text(encoding="utf-8")

def req(text, needle, label):
    if needle not in text:
        errors.append(f"{label}: missing {needle!r}")

assessment = read(ASSESSMENT)
menu = read(MENU)
block = read(BLOCK)

for needle in (
    "enum MachineState",
    "UNAVAILABLE",
    "IDLE",
    "STARVED",
    "RUNNING",
    "BLOCKED",
    "FAULTED",
    "if (!ready) return MachineState.UNAVAILABLE;",
    "if (systemState == OperationsMonitorBlock.SystemState.FAILED) return MachineState.FAULTED;",
    "if (running && q == 0) return MachineState.STARVED;",
    "if (running) return MachineState.RUNNING;",
    "if (q > 0) return MachineState.BLOCKED;",
    "return MachineState.IDLE;",
    "OperationsMonitorBlock.lastCycleTicks(level, pos)",
    "OperationsMonitorBlock.monitoringReady(level, pos)",
    "OperationsMonitorBlock.running(level, pos)",
):
    req(assessment, needle, "IndustrialOperationsAssessment.java")

for forbidden in (
    "MachineState.MAINTENANCE",
    "MAINTENANCE,",
    "RuntimeIntStore.get(",
    "setBlock(",
    "scheduleTick(",
):
    if forbidden in assessment:
        errors.append(f"IndustrialOperationsAssessment.java must remain evidence-only; unexpected {forbidden!r}")

for needle in (
    "machineState.set(operations.machineState().ordinal())",
    "lastCycleTicks.set(operations.lastCycleTicks())",
    "public IndustrialOperationsAssessment.MachineState machineState()",
    "public int lastCycleTicks()",
):
    req(menu, needle, "OperationsMonitorMenu.java")

# Existing evidence boundaries must remain explicit: missing run/queue coverage may not advance KPIs.
for needle in (
    "if (!evidence.operationalReady())",
    "if (run == 1 && queue == 0) r[20]++;",
    "if (run == 0 && queue > 0) r[21]++;",
    "public static boolean monitoringReady(Level level, BlockPos pos)",
):
    req(block, needle, "OperationsMonitorBlock.java")

if errors:
    print("RSE INDUSTRIAL OPERATIONS FOUNDATION VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    sys.exit(1)

print("RSE INDUSTRIAL OPERATIONS FOUNDATION VERIFY: PASS")
print("  machine state: UNAVAILABLE / IDLE / STARVED / RUNNING / BLOCKED / FAULTED")
print("  missing telemetry never masquerades as idle")
print("  state derives from authoritative RUN + QUEUE/WIP + process failure evidence")
print("  throughput / cycle / WIP / downtime remain observer-only operations evidence")
print("  MAINTENANCE intentionally withheld until an explicit maintenance contract exists")
