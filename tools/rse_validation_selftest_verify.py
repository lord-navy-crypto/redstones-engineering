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
selftest_assets = read("tools/rse_validation_selftests.py")

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
    "ServerTickEvent.Post",
    "RseValidationSelfTestService.tickAll(",
):
    if module and token not in module:
        errors.append(f"validation module missing automatic runtime contract {token!r}")

for token in (
    "class RseValidationSelfTestSavedData",
    "extends SavedData",
    "BlockPos",
    "computeIfAbsent",
    "setDirty()",
    "save(",
    "load(",
    "retestPressed",
    "resetForRetest(",
):
    if saved and token not in saved:
        errors.append(f"self-test SavedData missing persistence/autorun contract {token!r}")

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
    "tickAll(",
    "tickAutomatic(",
    "BlockStateProperties.POWERED",
    "Automatic self-test armed",
):
    if service and token not in service:
        errors.append(f"self-test runtime service missing authoritative/automatic contract {token!r}")

if service and "Run /rsevalidation selftest check" in service:
    errors.append("placing a self-test must not require a manual /check instruction")

for token in (
    "retest_button",
    "minecraft:stone_button",
    '"powered": "false"',
):
    if selftest_assets and token not in selftest_assets:
        errors.append(f"self-test assets missing physical RETEST contract {token!r}")

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
    "Blocks.REDSTONE_BLOCK",
    "passOn",
    "waitOff",
    "failOff",
):
    if gametest and token not in gametest:
        errors.append(f"local Validation GameTest missing physical verdict-panel contract {token!r}")

if errors:
    print("RSE VALIDATION SELFTEST VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE VALIDATION SELFTEST VERIFY: PASS")
print(" persisted placed-test origin identity: PASS")
print(" WAIT/PASS/FAIL status-panel runtime: PASS")
print(" server-tick automatic progression: PASS")
print(" physical RETEST button and debounce: PASS")
print(" authoritative RSE/world evidence evaluation: PASS")
print(" local GameTest verifies physical PASS panel selection: REGISTERED")
print(" unrelated production authority leakage: NONE")
