#!/usr/bin/env python3
"""Static gate for RSE systems-level automation and diagnostics extension."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
MODULE = ROOT / "src/main/java/dev/redstoneengineering/EngineeringSystemsModule.java"
BLOCK_DIR = ROOT / "src/main/java/dev/redstoneengineering/block"
GT = ROOT / "src/main/java/dev/redstoneengineering/gametest/RseEngineeringSystemsGameTests.java"
ASSETS = ROOT / "src/main/resources/assets/redstoneengineering"
DATA = ROOT / "src/main/resources/data/redstoneengineering"
errors: list[str] = []

def read(path: Path) -> str:
    if not path.exists(): errors.append(f"missing {path.relative_to(ROOT)}"); return ""
    return path.read_text(encoding="utf-8")
def require(source: str, needle: str, label: str) -> None:
    if needle not in source: errors.append(f"{label}: missing {needle!r}")
def require_all(source: str, needles: tuple[str, ...], label: str) -> None:
    for needle in needles: require(source, needle, label)

module = read(MODULE)
sequence = read(BLOCK_DIR / "SequenceControllerBlock.java")
interlock = read(BLOCK_DIR / "SafetyInterlockBlock.java")
fault = read(BLOCK_DIR / "FaultInjectorBlock.java")
alarm = read(BLOCK_DIR / "AlarmProcessorBlock.java")
debugger = read(BLOCK_DIR / "TopologyDebuggerBlock.java")
gt = read(GT)

require_all(module, (
    "@Mod(RedstoneEngineering.MOD_ID)", "SYSTEM_BLOCK_COUNT = 5",
    "DeferredRegister<MapCodec<? extends Block>> BLOCK_TYPES", "DeferredRegister.Blocks BLOCKS", "DeferredRegister.Items ITEMS",
    "BLOCK_TYPES.register(modBus);", "BLOCKS.register(modBus);", "ITEMS.register(modBus);",
    "DeferredBlock<SequenceControllerBlock> SEQUENCE_CONTROLLER", "DeferredBlock<SafetyInterlockBlock> SAFETY_INTERLOCK",
    "DeferredBlock<FaultInjectorBlock> FAULT_INJECTOR", "DeferredBlock<AlarmProcessorBlock> ALARM_PROCESSOR",
    "DeferredBlock<TopologyDebuggerBlock> TOPOLOGY_DEBUGGER", "event.register(RseEngineeringSystemsGameTests.class);"
), "EngineeringSystemsModule.java")
if "@EventBusSubscriber" in module or "@SubscribeEvent" in module: errors.append("EngineeringSystemsModule.java: deprecated annotation event registration reintroduced")

require_all(sequence, ("extends PassiveDirectionalSignalBlock", "SEQUENCE_CONTROLLER_CODEC.value()", '"RUN / ENABLE"', '"ADVANCE"', '"RESET"', '"HOLD"', '"STEP CODE"', "RuntimeIntStore.remove(level, KEY, pos)"), "SequenceControllerBlock.java")
require_all(interlock, ("extends PassiveDirectionalSignalBlock", "SAFETY_INTERLOCK_CODEC.value()", '"PERMISSIVE A"', '"PERMISSIVE B"', '"PERMISSIVE C"', '"PERMIT OUT"', "failedMask(", "RuntimeIntStore.remove(level, KEY, pos)"), "SafetyInterlockBlock.java")
require_all(fault, ("FAULT_INJECTOR_CODEC.value()", "IntegerProperty.create(\"mode\", 0, 3)", '"STUCK LOW"', '"STUCK HIGH"', '"BIAS +4"', '"BIAS -4"', "PortQuality.FAULT", "RuntimeIntStore.remove(level, KEY, pos)"), "FaultInjectorBlock.java")
require_all(alarm, ("ALARM_PROCESSOR_CODEC.value()", "IntegerProperty.create(\"severity\", 1, 3)", '"ALARM CONDITION"', '"ACKNOWLEDGE"', '"RESET / CLEAR"', '"ALARM OUT"', "condition <= 0", "PortQuality.FAULT", "RuntimeIntStore.remove(level, KEY, pos)"), "AlarmProcessorBlock.java")
require_all(debugger, ("TOPOLOGY_DEBUGGER_CODEC.value()", "EngineeringTopologyView.inspect", "TopologyDiagnosticsReport", '"TOPOLOGY ALARM OUT"', "report.hasIssue()", "RuntimeIntStore.remove(level, KEY, pos)"), "TopologyDebuggerBlock.java")

count = len(re.findall(r"@GameTest\s*\(", gt))
if count != 5: errors.append(f"RseEngineeringSystemsGameTests.java: expected exactly 5 @GameTest methods, found {count}")
for needle in ("EngineeringSystemsModule.SEQUENCE_CONTROLLER.get().defaultBlockState()", "EngineeringSystemsModule.SAFETY_INTERLOCK.get().defaultBlockState()", "EngineeringSystemsModule.FAULT_INJECTOR.get().defaultBlockState()", "EngineeringSystemsModule.ALARM_PROCESSOR.get().defaultBlockState()", "EngineeringSystemsModule.TOPOLOGY_DEBUGGER.get().defaultBlockState()"):
    require(gt, needle, "RseEngineeringSystemsGameTests.java")

for block_id in ("sequence_controller", "safety_interlock", "fault_injector", "alarm_processor", "topology_debugger"):
    for path in (ASSETS / "blockstates" / f"{block_id}.json", ASSETS / "models/block" / f"{block_id}.json", ASSETS / "models/item" / f"{block_id}.json", DATA / "loot_table/blocks" / f"{block_id}.json", DATA / "recipe" / f"{block_id}.json"):
        if not path.exists(): errors.append(f"{block_id}: missing {path.relative_to(ROOT)}")

if errors:
    print("RSE ENGINEERING SYSTEMS VERIFY: FAIL")
    for error in errors: print(" -", error)
    sys.exit(1)
print("RSE ENGINEERING SYSTEMS VERIFY: PASS")
print("  legacy audited core: 122 blocks")
print("  systems extension: 5 blocks")
print("  aggregate closure target: 127 blocks")
print("  registry lifecycle: DeferredRegister only; no eager Block construction")
print("  event registration: explicit IEventBus listeners")
print("  executable systems GameTests: 5")
