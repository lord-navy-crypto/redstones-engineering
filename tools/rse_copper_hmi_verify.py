#!/usr/bin/env python3
"""Fail-closed guard for the dedicated server-authoritative Copper electrical HMI."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []


def text(path: str) -> str:
    return (ROOT / path).read_text()


def require(body: str, needle: str, label: str) -> None:
    if needle not in body:
        errors.append(f"{label}: missing {needle!r}")


def forbid(body: str, needle: str, label: str) -> None:
    if needle in body:
        errors.append(f"{label}: forbidden {needle!r}")


block = text("src/main/java/dev/redstoneengineering/block/CopperCircuitMeterBlock.java")
menu = text("src/main/java/dev/redstoneengineering/ui/menu/CopperCircuitMeterMenu.java")
screen = text("src/main/java/dev/redstoneengineering/client/ui/CopperCircuitMeterScreen.java")
assessment = text("src/main/java/dev/redstoneengineering/core/diagnostic/CopperCommissioningAssessment.java")
evidence_assessment = text("src/main/java/dev/redstoneengineering/diagnostics/CopperEvidenceAssessment.java")
operations_menu = text("src/main/java/dev/redstoneengineering/ui/menu/OperationsMonitorMenu.java")
kinds = text("src/main/java/dev/redstoneengineering/diagnostics/events/SystemEventKind.java")
operations = text("src/main/java/dev/redstoneengineering/client/ui/OperationsMonitorScreen.java")
reliability = text("src/main/java/dev/redstoneengineering/diagnostics/ElectricalReliabilityAssessment.java")
registration = text("src/main/java/dev/redstoneengineering/ui/EngineeringUiRegistration.java")
client_registration = text("src/main/java/dev/redstoneengineering/client/ui/EngineeringUiClientRegistration.java")
openers = text("src/main/java/dev/redstoneengineering/ui/FieldDeviceUi.java")

require(block, "record ElectricalDiagnostics", "shared electrical evidence")
require(block, "CircuitPhysics.equivalentLoadResistance", "server load model")
require(block, "CircuitPhysics.current", "server current model")
require(block, "CircuitPhysics.power", "server power model")
require(block, "FieldDeviceUi.open(serverPlayer, pos)", "dedicated HMI opener")

require(assessment, "CopperCommissioningAssessment", "shared commissioning classifier")
require(assessment, "case VALID -> voltage > 0 ? CommissioningStatus.PASS : CommissioningStatus.MARGINAL", "energized commissioning rule")
require(menu, "CopperCommissioningAssessment.assess", "menu shared commissioning evidence")
require(menu, "commissioningStatus.set", "server commissioning synchronization")
require(menu, "rotateMeasurementFace", "real measurement-face routing")
for token in (
    "MeasurementSnapshot measurement = CopperCircuitMeterBlock.measurement",
    "meterReadingCenti",
    "repeatabilityCenti",
    "biasCenti",
    "driftCenti",
    "noiseCenti",
    "uncertaintyCenti",
    "sampleAgeTicks",
    "sampleCount",
    "measurementQuality",
    "measurement.saturated()",
):
    require(menu, token, "server metrology synchronization")

require(screen, "COPPER POWER / LOAD NETWORK", "Copper medium identity")
require(screen, "SERVER-SYNCHRONIZED OBSERVER", "observer authority")
require(screen, "V, Req, I and P", "electrical telemetry explanation")
require(screen, "commissioningStatus()", "commissioning presentation")
for token in (
    "METROLOGY + COMMISSIONING EVIDENCE",
    "Measurement quality",
    "Conditioned reading",
    "Repeatability",
    "Bias / drift",
    "Noise",
    "Uncertainty proxy",
    "Sample count / age",
    "diagnostic metrology proxies",
):
    require(screen, token, "retained metrology presentation")
# UI prose may name the authoritative server model. What is forbidden is importing or invoking it client-side.
forbid(screen, "import dev.redstoneengineering.physics.CircuitPhysics", "client must not import circuit solver")
forbid(screen, "CircuitPhysics.", "client must not invoke circuit solver")
forbid(screen, "import dev.redstoneengineering.physics.DomainNetwork", "client must not import network solver")
forbid(screen, "DomainNetwork.", "client must not sample network physics")

for token in (
    "COMMISSIONING_EVENT_INITIALIZED",
    "LAST_COMMISSIONING_STATUS",
    "publishCommissioningTransition",
    "SystemEventTimeline.record",
    "SystemEventKind.ELECTRICAL_EVIDENCE_DEGRADED",
    "SystemEventKind.ELECTRICAL_EVIDENCE_FAILED",
    "SystemEventKind.ELECTRICAL_EVIDENCE_RESTORED",
    "RuntimeIntStore.remove(level, COMMISSIONING_EVENT_KEY, pos)",
):
    require(block, token, "Copper operations transition contract")
for token in (
    "ELECTRICAL_EVIDENCE_DEGRADED(false)",
    "ELECTRICAL_EVIDENCE_FAILED(true)",
    "ELECTRICAL_EVIDENCE_RESTORED(false)",
):
    require(kinds, token, "system event vocabulary")
for token in (
    'case ELECTRICAL_EVIDENCE_DEGRADED -> "E-DEG"',
    'case ELECTRICAL_EVIDENCE_FAILED -> "E-FAIL"',
    'case ELECTRICAL_EVIDENCE_RESTORED -> "E-OK"',
):
    require(operations, token, "Operations Copper event rendering")
for forbidden in ("CircuitPhysics", "DomainNetwork", "SystemEventTimeline.record"):
    forbid(operations, forbidden, "Operations client remains render-only")

for token in (
    "class CopperEvidenceAssessment",
    "degradedTransitions",
    "failedTransitions",
    "restoredTransitions",
    "activeDegradedSources",
    "activeFailedSources",
    "SystemEventTimeline.within(level, scope)",
):
    require(evidence_assessment, token, "plant-scoped Copper evidence projection")
require(operations_menu, "CopperEvidenceAssessment.inspect(level, dashboard.eventScope())", "Operations evidence projection")
require(operations_menu, "copperEvidenceActiveFailed", "synchronized active Copper failure")
require(operations_menu, "copperEvidenceActiveDegraded", "synchronized active Copper degradation")
require(operations, "COPPER EVIDENCE FAILURE", "distinct Copper evidence diagnosis")
require(operations, "COPPER EVIDENCE DEGRADED", "distinct Copper degradation diagnosis")
require(operations, "without treating degradation as protection downtime", "semantic separation guidance")

# Fuse protection reliability must remain based only on trip/ready lifecycle evidence.
for forbidden in (
    "ELECTRICAL_EVIDENCE_DEGRADED",
    "ELECTRICAL_EVIDENCE_FAILED",
    "ELECTRICAL_EVIDENCE_RESTORED",
    "CopperEvidenceAssessment",
):
    forbid(reliability, forbidden, "protection reliability must exclude meter evidence readiness")

require(registration, "COPPER_CIRCUIT_METER", "menu registration")
require(client_registration, "CopperCircuitMeterScreen::new", "screen registration")
require(openers, "new CopperCircuitMeterMenu", "dedicated opener routing")

if errors:
    print("RSE Copper HMI verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE Copper HMI verification: PASS")
print("  server-authoritative V/Req/I/P evidence: PASS")
print("  dedicated power/load identity: PASS")
print("  commissioning synchronization: PASS")
print("  retained server metrology synchronization/presentation: PASS")
print("  transition-only plant timeline integration: PASS")
print("  protection reliability / evidence readiness separation: PASS")
print("  client physics isolation: PASS")
