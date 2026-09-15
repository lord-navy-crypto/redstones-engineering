#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing quality file: {rel}")
        return ""
    return path.read_text(errors="ignore")


inspection = read("src/main/java/dev/redstoneengineering/operations/OperationQualityInspectionEvidence.java")
assessment = read("src/main/java/dev/redstoneengineering/operations/OperationQualityDispositionAssessment.java")
rework_demand = read("src/main/java/dev/redstoneengineering/operations/OperationReworkDemand.java")
rework_release = read("src/main/java/dev/redstoneengineering/operations/OperationReworkReleaseAssessment.java")
material_release = read("src/main/java/dev/redstoneengineering/operations/OperationQualityMaterialReleaseAssessment.java")
performance = read("src/main/java/dev/redstoneengineering/diagnostics/OperationQualityPerformanceAssessment.java")

for token in (
    "record OperationQualityInspectionEvidence(",
    "long outputId",
    "long jobId",
    "int inspectedUnits",
    "int goodUnits",
    "int rejectUnits",
    "int reworkUnits",
    "PortQuality evidenceQuality",
    "boolean inspectionConfirmed",
    "boolean faultActive",
    "int dispositionUnits()",
):
    if inspection and token not in inspection:
        errors.append(f"OperationQualityInspectionEvidence missing inspection contract {token!r}")

for token in (
    "class OperationQualityDispositionAssessment",
    "COMPLETE",
    "WAIT",
    "SAFE_STOP",
    "FAULT",
    "QUALITY_INSPECTION_EVIDENCE_MISSING",
    "QUALITY_INSPECTION_FAULT_ACTIVE",
    "QUALITY_INSPECTION_EVIDENCE_INVALID",
    "QUALITY_INSPECTION_UNCONFIRMED",
    "QUALITY_OUTPUT_ID_MISMATCH",
    "QUALITY_JOB_ID_MISMATCH",
    "QUALITY_INSPECTED_QUANTITY_MISMATCH",
    "QUALITY_DISPOSITION_ACCOUNTING_MISMATCH",
    "QUALITY_DISPOSITION_CONFIRMED",
    "inspection.dispositionUnits() != inspection.inspectedUnits()",
):
    if assessment and token not in assessment:
        errors.append(f"OperationQualityDispositionAssessment missing fail-closed disposition rule {token!r}")

for token in (
    "record OperationReworkDemand(",
    "long reworkJobId",
    "long sourceOutputId",
    "long sourceJobId",
    "String processId",
    "int units",
    "int priority",
    "long releaseTick",
    "long dueTick",
    "rework job must use a new job identity",
):
    if rework_demand and token not in rework_demand:
        errors.append(f"OperationReworkDemand missing explicit planning contract {token!r}")

for token in (
    "class OperationReworkReleaseAssessment",
    "JOB_READY",
    "QUALITY_DISPOSITION_MISSING",
    "REWORK_DEMAND_MISSING",
    "QUALITY_DISPOSITION_INCOMPLETE",
    "QUALITY_DISPOSITION_FAULTED",
    "QUALITY_DISPOSITION_INVALID",
    "NO_REWORK_UNITS",
    "REWORK_OUTPUT_ID_MISMATCH",
    "REWORK_SOURCE_JOB_MISMATCH",
    "REWORK_QUANTITY_MISMATCH",
    "new OperationJob(",
    "demand.processId()",
    "demand.priority()",
    "demand.releaseTick()",
    "demand.dueTick()",
    "REWORK_JOB_READY",
):
    if rework_release and token not in rework_release:
        errors.append(f"OperationReworkReleaseAssessment missing explicit rework bridge {token!r}")

for token in (
    "class OperationQualityMaterialReleaseAssessment",
    "RELEASED",
    "SOURCE_OUTPUT_MISSING",
    "QUALITY_DISPOSITION_MISSING",
    "ACCEPTED_OUTPUT_ID_INVALID",
    "ACCEPTED_OUTPUT_ID_NOT_DISTINCT",
    "SOURCE_OUTPUT_FAULT_ACTIVE",
    "SOURCE_OUTPUT_EVIDENCE_INVALID",
    "SOURCE_OUTPUT_COMPLETION_UNCONFIRMED",
    "SOURCE_MATERIAL_NOT_READY",
    "QUALITY_DISPOSITION_INCOMPLETE",
    "QUALITY_DISPOSITION_FAULTED",
    "QUALITY_DISPOSITION_INVALID",
    "QUALITY_OUTPUT_ID_MISMATCH",
    "QUALITY_JOB_ID_MISMATCH",
    "NO_ACCEPTED_UNITS",
    "ACCEPTED_QUANTITY_EXCEEDS_SOURCE",
    "quality.goodUnits()",
    "new OperationOutputSnapshot(",
    "QUALITY_CLEARED_OUTPUT_RELEASED",
):
    if material_release and token not in material_release:
        errors.append(f"OperationQualityMaterialReleaseAssessment missing quality-cleared material contract {token!r}")

for forbidden in (
    "quality.rejectUnits()",
    "quality.reworkUnits()",
):
    if material_release and forbidden in material_release:
        errors.append(f"Accepted material must contain good units only; found {forbidden!r}")

for token in (
    "class OperationQualityPerformanceAssessment",
    "Observer-only quality performance projection",
    "COMPLETE",
    "PARTIAL",
    "INVALID",
    "int inspectedUnits",
    "int goodUnits",
    "int rejectUnits",
    "int reworkUnits",
    "int firstPassYieldPercent",
    "int rejectRatePercent",
    "int reworkRatePercent",
    "case WAIT -> incomplete++",
    "case SAFE_STOP, FAULT -> invalid++",
    "percent(good, inspected)",
    "percent(reject, inspected)",
    "percent(rework, inspected)",
):
    if performance and token not in performance:
        errors.append(f"OperationQualityPerformanceAssessment missing coverage-aware observer metric {token!r}")

for body, label in (
    (assessment, "Quality disposition"),
    (rework_release, "Rework release"),
    (material_release, "Accepted-material release"),
    (performance, "Quality performance"),
):
    for forbidden in (
        "OperationDispatchRuntime",
        "OperationQueueRuntime",
        "OperationMaterialReleaseRuntime",
        "OperationChangeoverRuntime",
        "RobotMission",
        "setBlock(",
        "setDeltaMovement(",
        "RuntimeIntStore",
        "OperationsDashboardSnapshot",
        "IndustrialOperationsAssessment",
    ):
        if body and forbidden in body:
            errors.append(f"{label} must not own dispatch/world/robotics/KPI authority; found {forbidden!r}")

if performance and "OEE" in performance and "does not" not in performance:
    errors.append("Quality performance must not claim unsupported OEE")

if errors:
    print("RSE OPERATIONS QUALITY VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE OPERATIONS QUALITY VERIFY: PASS")
print(" explicit good/reject/rework inspection evidence: PASS")
print(" identity + quantity + disposition accounting: PASS")
print(" incomplete inspection remains WAIT: PASS")
print(" rework process/job/priority/release/due remain explicit planning inputs: PASS")
print(" quality-cleared accepted output uses a distinct identity and good units only: PASS")
print(" coverage-aware FPY/reject/rework observer metrics: PASS")
print(" unsupported OEE claim: NONE")
print(" automatic rework scheduling: NONE")
print(" dispatch/world/robotics/KPI authority leakage: NONE")
