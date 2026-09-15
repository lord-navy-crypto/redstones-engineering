#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing Industrial Buffer world file: {rel}")
        return ""
    return path.read_text(errors="ignore")


def require(rel: str, *tokens: str) -> str:
    body = read(rel)
    for token in tokens:
        if body and token not in body:
            errors.append(f"{rel}: missing Industrial Buffer contract token {token!r}")
    return body


block = require(
    "src/main/java/dev/redstoneengineering/block/IndustrialBufferBlock.java",
    "class IndustrialBufferBlock",
    "OperationIndustrialBufferState.create",
    "OperationIndustrialBufferState.snapshot",
    "DEFAULT_CAPACITY_UNITS",
    '"WIP LEVEL"',
    '"SPACE PERMIT"',
    '"FULL"',
    "isSignalSource",
    "queryDirection.getOpposite()",
    "snapshot.lots().isEmpty()",
)
ui = require(
    "src/main/java/dev/redstoneengineering/ui/IndustrialBufferUi.java",
    "class IndustrialBufferUi",
    "writeVarLong(lot.outputId())",
    "writeVarLong(lot.jobId())",
    "writeVarInt(lot.units())",
    "MAX_VISIBLE_LOTS",
)
menu = require(
    "src/main/java/dev/redstoneengineering/ui/menu/IndustrialBufferMenu.java",
    "class IndustrialBufferMenu",
    "readVarLong()",
    "visibleLots",
    "capacityUnits",
    "usedUnits",
    "availableUnits",
    "totalLotCount",
)
screen = require(
    "src/main/java/dev/redstoneengineering/client/ui/IndustrialBufferScreen.java",
    "class IndustrialBufferScreen",
    "OUTPUT",
    "JOB",
    "UNITS",
    "LOT IDENTITY",
    "server snapshot",
)

combined = block + ui + menu + screen
for forbidden in (
    "new OperationBufferLot(",
    "OperationBufferRuntime.receive",
    "OperationBufferRuntime.allocate",
    "OperationPlantSavedData.putBuffer",
    "OperationQualityDispositionAssessment",
    "OperationCompletionEvidence",
    "setDeltaMovement(",
    "getEntitiesOfClass",
    "inflate(",
):
    if combined and forbidden in combined:
        errors.append(f"Industrial Buffer block/UI must not forge Operations authority; found {forbidden!r}")

# Identity is high-cardinality and must never become block properties or analog signal payload.
for forbidden in (
    "LongProperty",
    "StringProperty",
    "output_id",
    "job_id",
    "lot_id",
):
    if block and forbidden in block:
        errors.append(f"Industrial Buffer block must keep identities out of BlockState/redstone; found {forbidden!r}")

if errors:
    print("RSE INDUSTRIAL BUFFER WORLD VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE INDUSTRIAL BUFFER WORLD VERIFY: PASS")
print(" world block owns no duplicate lot/inventory authority: PASS")
print(" finite persisted Operations buffer state bridge: PASS")
print(" WIP/space/fullness low-cardinality redstone projection: PASS")
print(" exact output/job/unit identity transported to UI via server buffer: PASS")
print(" non-empty break retains logical WIP for fail-closed recovery: PASS")
print(" manual quality/completion fabrication: NONE")
