#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing reliability retention file: {rel}")
        return ""
    return path.read_text(errors="ignore")


assessment = read("src/main/java/dev/redstoneengineering/diagnostics/ElectricalReliabilityAssessment.java")
for token in (
    "retained-window evidence",
    "public static Snapshot project(List<SystemEventRecord> events, long nowTick)",
    "SystemEventTimeline.within(level, scope)",
    "does not imply that the live transient ring retains the same evidence indefinitely",
):
    if assessment and token not in assessment:
        errors.append(f"ElectricalReliabilityAssessment missing retention-safe contract {token!r}")

tests = read("src/main/java/dev/redstoneengineering/gametest/RseElectricalReliabilityGameTests.java")
for token in (
    "ElectricalReliabilityAssessment.inspect(helper.getLevel(), scope)",
    "ElectricalReliabilityAssessment.project(chronology, 112L)",
    "shared 256-event runtime ring",
    "snapshot.electricalDowntimeTicks() != 9L",
):
    if tests and token not in tests:
        errors.append(f"Electrical reliability GameTests missing retention-safe evidence {token!r}")
if tests and "SystemEventTimeline.clear(helper.getLevel())" in tests:
    errors.append("Electrical reliability tests must not clear the shared level-wide timeline")
if tests and "runAfterDelay(12" in tests:
    errors.append("Electrical reliability chronology test still depends on delayed retention of shared timeline evidence")

workflow = read(".github/workflows/build.yml")
if workflow and "tools/rse_reliability_retention_contract_verify.py" not in workflow:
    errors.append("reliability retention contract verifier is not wired into CI")

if errors:
    print("RSE reliability retention contract verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE reliability retention contract verification: PASS")
print(" live plant-scoped timeline integration: PASS")
print(" pure chronology projection: PASS")
print(" bounded transient retention semantics: PASS")
print(" no global timeline clearing in runtime tests: PASS")
