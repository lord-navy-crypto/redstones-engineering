#!/usr/bin/env python3
"""Static gate for plant-scoped incident views and explicit systems persistence semantics."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
errors = []

def read(rel: str) -> str:
    path = ROOT / rel
    if not path.exists():
        errors.append(f"missing {rel}")
        return ""
    return path.read_text(encoding="utf-8")

def need(src: str, needle: str, label: str) -> None:
    if needle not in src:
        errors.append(f"{label}: missing {needle!r}")

scope = read("src/main/java/dev/redstoneengineering/diagnostics/events/SystemEventScope.java")
timeline = read("src/main/java/dev/redstoneengineering/diagnostics/events/SystemEventTimeline.java")
firstout = read("src/main/java/dev/redstoneengineering/diagnostics/events/FirstOutAnalysis.java")
dashboard = read("src/main/java/dev/redstoneengineering/diagnostics/OperationsDashboardSnapshot.java")
rootcause = read("src/main/java/dev/redstoneengineering/diagnostics/events/RootCauseEvidenceTrace.java")
contract = read("src/main/java/dev/redstoneengineering/diagnostics/lifecycle/RuntimePersistenceContract.java")
module = read("src/main/java/dev/redstoneengineering/EngineeringSystemsModule.java")
gt = read("src/main/java/dev/redstoneengineering/gametest/RsePlantScopeLifecycleGameTests.java")
doc = read("docs/PERSISTENCE_AND_PLANT_SCOPE_CONTRACT.md")
workflow = read(".github/workflows/build.yml")

for needle in ("DEFAULT_PLANT_RADIUS = 32", "MAX_RADIUS = 128", "boolean contains", "dx * dx + dy * dy + dz * dz"):
    need(scope, needle, "SystemEventScope.java")
for needle in ("within(Level level, SystemEventScope scope)", "scope.contains(event.source())", "recentWithin"):
    need(timeline, needle, "SystemEventTimeline.java")
for needle in ("latestWithin(Level level, SystemEventScope scope)", "SystemEventTimeline.within(level, scope)"):
    need(firstout, needle, "FirstOutAnalysis.java")
for needle in ("SystemEventScope.around(operationsMonitorPos)", "SystemEventTimeline.within(level, scope)", "FirstOutAnalysis.latestWithin(level, scope)"):
    need(dashboard, needle, "OperationsDashboardSnapshot.java")
need(rootcause, "latestWithin(Level level, SystemEventScope scope)", "RootCauseEvidenceTrace.java")

for needle in (
    '"SequenceController"', '"AlarmProcessor"', '"OperationsMonitor"', '"FaultInjector"',
    '"SystemEventTimeline"', '"AcceptanceEvidenceStore"', '"PidController"',
    "BLOCK_STATE_DURABLE", "LEVEL_RUNTIME_TRANSIENT", "BLOCK_RUNTIME_TRANSIENT",
    "CONSIDER_DURABLE_RECORDER", "SPLIT_ROLLING_AND_LIFETIME_METRICS",
):
    need(contract, needle, "RuntimePersistenceContract.java")

for needle in (
    "plantScopeExcludesUnrelatedRemoteIncident",
    "persistenceContractKeepsSafetyCriticalBoundariesExplicit",
    "REMOTE_FIRE_ALARM",
    "LOCAL_PRESSURE_LOW",
):
    need(gt, needle, "RsePlantScopeLifecycleGameTests.java")
need(module, "event.register(RsePlantScopeLifecycleGameTests.class);", "EngineeringSystemsModule.java")
need(doc, "Persistence is not a universal upgrade.", "PERSISTENCE_AND_PLANT_SCOPE_CONTRACT.md")
need(workflow, "tools/rse_plant_scope_persistence_verify.py", "build.yml")
need(workflow, "test_count <", "build.yml")
need(workflow, "All [0-9]+ required tests passed", "build.yml")

# No plant/event projection may become a second simulator.
for label, src in (("SystemEventScope.java", scope), ("OperationsDashboardSnapshot.java", dashboard), ("RuntimePersistenceContract.java", contract)):
    for forbidden in ("setBlock(", "scheduleTick(", "updateNeighborsAt("):
        if forbidden in src:
            errors.append(f"{label}: observer/contract layer must not mutate simulation; found {forbidden}")

if errors:
    print("RSE PLANT SCOPE + PERSISTENCE VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    sys.exit(1)

print("RSE PLANT SCOPE + PERSISTENCE VERIFY: PASS")
print("  default plant incident radius: 32 blocks")
print("  hard scope radius cap: 128 blocks")
print("  operations dashboard first-out isolation: PASS")
print("  runtime persistence contract: explicit")
print("  registered lifecycle GameTests: 2")
