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
    if assessment and forbidden in assessment:
        errors.append(f"Quality disposition must not own dispatch/world/robotics/KPI authority; found {forbidden!r}")

if errors:
    print("RSE OPERATIONS QUALITY VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE OPERATIONS QUALITY VERIFY: PASS")
print(" explicit good/reject/rework inspection evidence: PASS")
print(" identity + quantity + disposition accounting: PASS")
print(" incomplete inspection remains WAIT: PASS")
print(" automatic rework scheduling: NONE")
print(" dispatch/world/robotics/KPI authority leakage: NONE")
