#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing Operations binding tool file: {rel}")
        return ""
    return path.read_text(errors="ignore")


item = read("src/main/java/dev/redstoneengineering/item/OperationsBindingToolItem.java")
module = read("src/main/java/dev/redstoneengineering/EngineeringSystemsModule.java")
lang = read("src/main/resources/assets/redstoneengineering/lang/en_us.json")

for token in (
    "class OperationsBindingToolItem extends Item",
    "useOn(UseOnContext",
    "DataComponents.CUSTOM_DATA",
    "CustomData.update",
    "OperationWorldResourceResolver.resolve",
    "IndustrialBufferBlock.bufferId",
    "WorkcellControllerBlock.bindResource",
    "OperationWorkcellStore.bindBuffers",
    "level.dimension().location()",
    "target_dimension",
    "target_position",
    "input_buffer_id",
    "output_buffer_id",
    "player.isShiftKeyDown()",
    "displayClientMessage",
):
    if item and token not in item:
        errors.append(f"OperationsBindingToolItem missing explicit binding workflow token {token!r}")

for token in (
    "OPERATIONS_BINDING_TOOL_ITEM",
    'ITEMS.register("operations_binding_tool"',
    "event.accept(OPERATIONS_BINDING_TOOL_ITEM);",
):
    if module and token not in module:
        errors.append(f"EngineeringSystemsModule missing binding tool registration {token!r}")

if lang and '"item.redstoneengineering.operations_binding_tool"' not in lang:
    errors.append("en_us.json missing Operations Binding Tool translation")

for forbidden in (
    "getEntitiesOfClass",
    "inflate(",
    "closerThan",
    "nearest",
    "OperationDispatchRuntime",
    "OperationBufferRuntime",
    "OperationQualityDispositionAssessment",
    "OperationCompletionEvidence",
    "new OperationBufferLot(",
    "setBlock(",
    "setDeltaMovement(",
):
    if item and forbidden in item:
        errors.append(f"Operations Binding Tool must remain explicit configuration only; found {forbidden!r}")

if errors:
    print("RSE OPERATIONS BINDING TOOL VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE OPERATIONS BINDING TOOL VERIFY: PASS")
print(" explicit resource target capture: PASS")
print(" explicit input/output buffer capture: PASS")
print(" controller submission delegates to existing workcell binding authority: PASS")
print(" dimension + block-position identity retained in item custom data: PASS")
print(" proximity discovery / scheduling / lot / quality authority leakage: NONE")
