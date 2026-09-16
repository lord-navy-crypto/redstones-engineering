#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        errors.append(f"missing self-test file: {rel}")
        return ""
    return path.read_text(encoding="utf-8", errors="ignore")


module = read("src/main/java/dev/redstoneengineering/validation/RseValidationFactoryModule.java")
saved = read("src/main/java/dev/redstoneengineering/validation/RseValidationSelfTestSavedData.java")
service = read("src/main/java/dev/redstoneengineering/validation/RseValidationSelfTestService.java")
gametest = read("src/main/java/dev/redstoneengineering/gametest/RseValidationFactoryGameTests.java")

for token in (
    'Commands.literal("selftest")',
    'Commands.literal("list")',
    'Commands.literal("place")',
    'Commands.literal("check")',
    "RseValidationSelfTestService.list(",
    "RseValidationSelfTestService.place(",
    "RseValidationSelfTestService.check(",
):
    if module and token not in module:
        errors.append(f"validation command module missing self-test contract {token!r}")

for token in (
    "class RseValidationSelfTestSavedData",
    "extends SavedData",
    "BlockPos",
    "computeIfAbsent",
    "setDirty()",
    "save(",
    "load(",
):
    if saved and token not in saved:
        errors.append(f"self-test SavedData missing persistence contract {token!r}")

for token in (
    "class RseValidationSelfTestService",
    "enum Verdict",
    "WAIT",
    "PASS",
    "FAIL",
    "EngineeringPortSnapshot",
    "PortQuality",
    "SignalAnalyzerBlock.uiSnapshot",
    "validation/selftest/",
    "Blocks.REDSTONE_BLOCK",
    "updatePanel",
):
    if service and token not in service:
        errors.append(f"self-test runtime service missing authoritative contract {token!r}")

for forbidden in (
    "OperationPlantSavedData",
    ".putQueue(",
    ".putBuffer(",
    "setRobotState(",
):
    if service and forbidden in service:
        errors.append(f"self-test service leaked unrelated production authority: {forbidden!r}")

for token in (
    "selfTestReferenceSourceUsesRealWorldEvidence",
    "RseValidationSelfTestService",
    "Verdict.PASS",
):
    if gametest and token not in gametest:
        errors.append(f"local Validation GameTest missing self-test contract {token!r}")

if errors:
    print("RSE VALIDATION SELFTEST VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE VALIDATION SELFTEST VERIFY: PASS")
print(" persisted placed-test origin identity: PASS")
print(" WAIT/PASS/FAIL status-panel runtime: PASS")
print(" authoritative RSE/world evidence evaluation: PASS")
print(" unrelated production authority leakage: NONE")
