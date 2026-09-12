#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]

checks = {
    "src/main/java/dev/redstoneengineering/diagnostics/events/SystemEventTimeline.java": [
        "MAX_EVENTS_PER_LEVEL = 256",
        "level.isClientSide",
        "while (timeline.size() > MAX_EVENTS_PER_LEVEL)",
        "List.copyOf",
    ],
    "src/main/java/dev/redstoneengineering/diagnostics/events/FirstOutAnalysis.java": [
        "INCIDENT_GAP_TICKS = 200L",
        "earliest recorded abnormal event",
        "downstream observations",
    ],
    "src/main/java/dev/redstoneengineering/diagnostics/events/RootCauseEvidenceTrace.java": [
        "evidence chain, not an automatic causal proof",
        "FIRST_OUT",
        "FOLLOW_UP",
    ],
    "src/main/java/dev/redstoneengineering/diagnostics/OperationsDashboardSnapshot.java": [
        "IndustrialOperationsAssessment.inspect",
        "SystemEventTimeline.within",
        "FirstOutAnalysis.latestWithin",
    ],
    "src/main/java/dev/redstoneengineering/block/AlarmProcessorBlock.java": [
        "SystemEventKind.ALARM_RAISED",
        "SystemEventKind.ALARM_ACKNOWLEDGED",
        "SystemEventKind.ALARM_CLEARED",
    ],
    "src/main/java/dev/redstoneengineering/block/SafetyInterlockBlock.java": [
        "SystemEventKind.INTERLOCK_TRIPPED",
        "SystemEventKind.INTERLOCK_READY",
    ],
    "src/main/java/dev/redstoneengineering/block/SequenceControllerBlock.java": [
        "SystemEventKind.SEQUENCE_STARTED",
        "SystemEventKind.SEQUENCE_STEP",
        "SystemEventKind.SEQUENCE_COMPLETED",
        "SystemEventKind.SEQUENCE_RESET",
    ],
    "src/main/java/dev/redstoneengineering/block/TopologyDebuggerBlock.java": [
        "SystemEventKind.TOPOLOGY_ISSUE",
        "SystemEventKind.TOPOLOGY_CLEAR",
    ],
    "src/main/java/dev/redstoneengineering/block/OperationsMonitorBlock.java": [
        "SystemEventKind.OPERATIONS_STATE_CHANGED",
        "OperationsDashboardSnapshot",
    ],
    "src/main/java/dev/redstoneengineering/gametest/RseEngineeringSystemsGameTests.java": [
        "systemTimelineCapturesAlarmLifecycleAndFirstOut",
        "firstOutPreservesEarliestAbnormalEventInIncident",
    ],
    ".github/workflows/build.yml": [
        "tools/rse_event_intelligence_verify.py",
        "Minecraft topology GameTests (manual diagnostic)",
        "github.event_name == 'workflow_dispatch'",
        "continue-on-error: true",
        "runGameTestServer",
        "GameTest diagnostic evidence",
    ],
}

errors = []
for rel, needles in checks.items():
    path = ROOT / rel
    if not path.exists():
        errors.append(f"missing file: {rel}")
        continue
    text = path.read_text(encoding="utf-8")
    for needle in needles:
        if needle not in text:
            errors.append(f"{rel}: missing evidence {needle!r}")

# Guard the architectural boundary: timeline is transient evidence, not simulation ownership.
timeline = (ROOT / "src/main/java/dev/redstoneengineering/diagnostics/events/SystemEventTimeline.java").read_text(encoding="utf-8")
for forbidden in ("setBlock(", "scheduleTick(", "updateNeighborsAt("):
    if forbidden in timeline:
        errors.append(f"SystemEventTimeline must remain observer/evidence-only; found {forbidden}")

if errors:
    print("RSE system event intelligence verification: FAIL")
    for error in errors:
        print(" -", error)
    sys.exit(1)

print("RSE system event intelligence verification: PASS")
print(" GameTest runtime evidence policy: manual diagnostic / non-blocking")
