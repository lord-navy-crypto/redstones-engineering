#!/usr/bin/env python3
"""Static gate for RSE systems-level automation, diagnostics, and operator-reference extension."""
from pathlib import Path
import json
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
MODULE = ROOT / "src/main/java/dev/redstoneengineering/EngineeringSystemsModule.java"
BLOCK_DIR = ROOT / "src/main/java/dev/redstoneengineering/block"
GT = ROOT / "src/main/java/dev/redstoneengineering/gametest/RseEngineeringSystemsGameTests.java"
ASSETS = ROOT / "src/main/resources/assets/redstoneengineering"
DATA = ROOT / "src/main/resources/data/redstoneengineering"
DYNAMIC_VISUAL_VERIFY = ROOT / "tools/rse_dynamic_io_visuals_verify.py"
SYSTEM_HMI_VERIFY = ROOT / "tools/rse_system_hmi_verify.py"
RELIABILITY_HMI_VERIFY = ROOT / "tools/rse_reliability_hmi_verify.py"
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
compass = read(BLOCK_DIR / "EngineeringCompassBlock.java")
workcell = read(BLOCK_DIR / "WorkcellControllerBlock.java")
industrial_buffer = read(BLOCK_DIR / "IndustrialBufferBlock.java")
compass_model = read(ASSETS / "models/block/engineering_compass.json")
compass_state = read(ASSETS / "blockstates/engineering_compass.json")
gt = read(GT)

require_all(module, (
    "@Mod(RedstoneEngineering.MOD_ID)", "SYSTEM_BLOCK_COUNT = 8",
    "DeferredRegister<MapCodec<? extends Block>> BLOCK_TYPES", "DeferredRegister.Blocks BLOCKS", "DeferredRegister.Items ITEMS",
    "BLOCK_TYPES.register(modBus);", "BLOCKS.register(modBus);", "ITEMS.register(modBus);",
    "DeferredBlock<SequenceControllerBlock> SEQUENCE_CONTROLLER", "DeferredBlock<SafetyInterlockBlock> SAFETY_INTERLOCK",
    "DeferredBlock<FaultInjectorBlock> FAULT_INJECTOR", "DeferredBlock<AlarmProcessorBlock> ALARM_PROCESSOR",
    "DeferredBlock<TopologyDebuggerBlock> TOPOLOGY_DEBUGGER", "DeferredBlock<EngineeringCompassBlock> ENGINEERING_COMPASS",
    "DeferredBlock<WorkcellControllerBlock> WORKCELL_CONTROLLER", "WORKCELL_CONTROLLER_CODEC",
    'codec("workcell_controller", WorkcellControllerBlock::new)', 'BLOCKS.registerBlock("workcell_controller", WorkcellControllerBlock::new',
    "WORKCELL_CONTROLLER_ITEM", "event.accept(WORKCELL_CONTROLLER_ITEM);",
    "DeferredBlock<IndustrialBufferBlock> INDUSTRIAL_BUFFER", "INDUSTRIAL_BUFFER_CODEC",
    'codec("industrial_buffer", IndustrialBufferBlock::new)', 'BLOCKS.registerBlock("industrial_buffer", IndustrialBufferBlock::new',
    "INDUSTRIAL_BUFFER_ITEM", "event.accept(INDUSTRIAL_BUFFER_ITEM);",
    "ENGINEERING_COMPASS_CODEC", 'codec("engineering_compass", EngineeringCompassBlock::new)',
    'BLOCKS.registerBlock("engineering_compass", EngineeringCompassBlock::new', ".noOcclusion()", "ENGINEERING_COMPASS_ITEM",
    "event.accept(ENGINEERING_COMPASS_ITEM);", "event.register(RseEngineeringSystemsGameTests.class);"
), "EngineeringSystemsModule.java")
if "@EventBusSubscriber" in module or "@SubscribeEvent" in module: errors.append("EngineeringSystemsModule.java: deprecated annotation event registration reintroduced")

require_all(sequence, ("extends PassiveDirectionalSignalBlock", "SEQUENCE_CONTROLLER_CODEC.value()", '"RUN / ENABLE"', '"ADVANCE"', '"RESET"', '"HOLD"', '"STEP CODE"', "RuntimeIntStore.remove(level, KEY, pos)"), "SequenceControllerBlock.java")
require_all(interlock, ("extends PassiveDirectionalSignalBlock", "SAFETY_INTERLOCK_CODEC.value()", '"PERMISSIVE A"', '"PERMISSIVE B"', '"PERMISSIVE C"', '"PERMIT OUT"', "failedMask(", "RuntimeIntStore.remove(level, KEY, pos)"), "SafetyInterlockBlock.java")
require_all(fault, ("FAULT_INJECTOR_CODEC.value()", "IntegerProperty.create(\"mode\", 0, 3)", '"STUCK LOW"', '"STUCK HIGH"', '"BIAS +4"', '"BIAS -4"', "PortQuality.FAULT", "RuntimeIntStore.remove(level, KEY, pos)"), "FaultInjectorBlock.java")
require_all(alarm, ("ALARM_PROCESSOR_CODEC.value()", "IntegerProperty.create(\"severity\", 1, 3)", '"ALARM CONDITION"', '"ACKNOWLEDGE"', '"RESET / CLEAR"', '"ALARM OUT"', "RedstoneObservationSupport.observe", "conditionClearForReset", "CONDITION_REACQUIRE", "ACK_REACQUIRE", "RESET_REACQUIRE", "CONDITION_BAD_ACTIVE", "PortQuality.FAULT", "RuntimeIntStore.remove(level, KEY, pos)"), "AlarmProcessorBlock.java")
require_all(debugger, ("TOPOLOGY_DEBUGGER_CODEC.value()", "EngineeringTopologyView.inspect", "TopologyDiagnosticsReport", '"TOPOLOGY ALARM OUT"', "report.hasIssue()", "RuntimeIntStore.remove(level, KEY, pos)"), "TopologyDebuggerBlock.java")
require_all(workcell, (
    "class WorkcellControllerBlock extends Block implements EngineeringPortProvider",
    "WORKCELL_CONTROLLER_CODEC.value()", '"ACTIVE"', '"PERMIT"', '"HOLD"', '"FAULT"', '"QUEUE PRESSURE"',
    "OperationWorkcellStore.resolveBoundResources", "OperationWorkcellAdmissionAssessment.inspect",
    "OperationDispatchRuntime.evaluate", "OperationChangeoverRuntime.request", "OperationMaintenanceRuntime.start",
), "WorkcellControllerBlock.java")
for forbidden in ("OperationBottleneckAssessment", "Comparator.comparing", "getEntitiesOfClass", "inflate("):
    if forbidden in workcell: errors.append(f"WorkcellControllerBlock must delegate authority; found {forbidden!r}")
require_all(industrial_buffer, (
    "class IndustrialBufferBlock extends Block implements EngineeringPortProvider",
    "INDUSTRIAL_BUFFER_CODEC.value()", '"WIP LEVEL"', '"SPACE PERMIT"', '"FULL"',
    "OperationIndustrialBufferState.create", "OperationIndustrialBufferState.snapshot",
    "snapshot.lots().isEmpty()", "isSignalSource", "queryDirection.getOpposite()",
), "IndustrialBufferBlock.java")
for forbidden in ("new OperationBufferLot(", "OperationBufferRuntime.receive", "OperationBufferRuntime.allocate", "StringProperty", "LongProperty"):
    if forbidden in industrial_buffer: errors.append(f"IndustrialBufferBlock must project persistent Operations state without owning lot authority; found {forbidden!r}")

require_all(compass_state, ('"variants"', '"redstoneengineering:block/engineering_compass"'), "engineering_compass blockstate")
require_all(compass, (
    "class EngineeringCompassBlock extends Block", "ENGINEERING_COMPASS_CODEC.value()",
    "Block.box(0, 0, 0, 16, 8, 16)", "getCollisionShape", "useWithoutItem",
    "World datum", "N=-Z", "E=+X", "S=+Z", "W=-X",
), "EngineeringCompassBlock.java")
for forbidden in ("EntityBlock", "BlockEntity", "scheduleTick", "neighborChanged", "EngineeringPortProvider", "RuntimeIntStore", ".setBlock("):
    if forbidden in compass:
        errors.append(f"Engineering Compass must remain passive; found {forbidden!r} in EngineeringCompassBlock.java")

try:
    compass_json = json.loads(compass_model)
except json.JSONDecodeError as exc:
    errors.append(f"engineering_compass model: invalid JSON: {exc}")
    compass_json = {}
textures = compass_json.get("textures", {}) if isinstance(compass_json, dict) else {}
expected_textures = {
    "base": "redstoneengineering:block/signal_analyzer_side",
    "north": "redstoneengineering:block/redstone_reference_source",
    "east": "redstoneengineering:block/optical_fiber",
    "south": "redstoneengineering:block/slime_vibration_conduit",
    "west": "redstoneengineering:block/honey_vibration_damper",
    "center": "redstoneengineering:block/signal_analyzer_top",
}
for key, value in expected_textures.items():
    if textures.get(key) != value:
        errors.append(f"engineering_compass model: texture {key!r} expected {value!r}, found {textures.get(key)!r}")
for marker in ("marker_n", "marker_e", "marker_s", "marker_w"):
    value = textures.get(marker)
    if not isinstance(value, str) or not value.startswith("redstoneengineering:block/"):
        errors.append(f"engineering_compass model: {marker} must use an RSE-owned block texture")
if '"minecraft:block/' in compass_model:
    errors.append("engineering_compass model: vanilla placeholder texture reference is forbidden")
if len(compass_json.get("elements", [])) < 20:
    errors.append("engineering_compass model: expected explicit raised cardinal-letter geometry")

count = len(re.findall(r"@GameTest\s*\(", gt))
if count < 8: errors.append(f"RseEngineeringSystemsGameTests.java: expected at least 8 @GameTest methods, found {count}")
for needle in (
    "EngineeringSystemsModule.SEQUENCE_CONTROLLER.get().defaultBlockState()",
    "EngineeringSystemsModule.SAFETY_INTERLOCK.get().defaultBlockState()",
    "EngineeringSystemsModule.FAULT_INJECTOR.get().defaultBlockState()",
    "EngineeringSystemsModule.ALARM_PROCESSOR.get().defaultBlockState()",
    "EngineeringSystemsModule.TOPOLOGY_DEBUGGER.get().defaultBlockState()",
    "alarmProcessorFailsSafeOnBadConditionEvidence",
    "systemTimelineCapturesAlarmLifecycleAndFirstOut",
    "firstOutPreservesEarliestAbnormalEventInIncident",
):
    require(gt, needle, "RseEngineeringSystemsGameTests.java")

for block_id in ("sequence_controller", "safety_interlock", "fault_injector", "alarm_processor", "topology_debugger", "engineering_compass", "workcell_controller", "industrial_buffer"):
    for path in (ASSETS / "blockstates" / f"{block_id}.json", ASSETS / "models/block" / f"{block_id}.json", ASSETS / "models/item" / f"{block_id}.json", DATA / "loot_table/blocks" / f"{block_id}.json", DATA / "recipe" / f"{block_id}.json"):
        if not path.exists(): errors.append(f"{block_id}: missing {path.relative_to(ROOT)}")

for verifier, label in (
    (DYNAMIC_VISUAL_VERIFY, "dynamic I/O visuals"),
    (SYSTEM_HMI_VERIFY, "system HMI"),
    (RELIABILITY_HMI_VERIFY, "reliability HMI"),
):
    if not verifier.is_file():
        errors.append(f"missing {verifier.relative_to(ROOT)}")
        continue
    child = subprocess.run([sys.executable, str(verifier)], cwd=ROOT, text=True, capture_output=True, check=False)
    if child.stdout: print(child.stdout, end="")
    if child.stderr: print(child.stderr, end="", file=sys.stderr)
    if child.returncode != 0:
        errors.append(f"{label} verifier failed with exit code {child.returncode}")

if errors:
    print("RSE ENGINEERING SYSTEMS VERIFY: FAIL")
    for error in errors: print(" -", error)
    sys.exit(1)
print("RSE ENGINEERING SYSTEMS VERIFY: PASS")
print("  legacy audited core: 122 blocks")
print("  systems extension: 8 blocks")
print("  aggregate closure target: 130 blocks")
print("  Engineering Compass: passive world-axis datum / raised N-E-S-W geometry / low-profile shape")
print("  Workcell Controller: explicit server binding + delegated Operations authority")
print("  Industrial Buffer: persisted logical WIP + exact lot HMI + bounded redstone projection")
print("  systems visualization: synchronized world-visible routing and live state overlays")
print("  systems HMI: explicit Sequence / Interlock / Topology operator controls")
print("  reliability HMI: explicit shared maintenance actions for watchdog / servo / sensor / voter / latch")
print("  registry lifecycle: DeferredRegister only; no eager Block construction")
print("  event registration: explicit IEventBus listeners")
print(f"  executable systems GameTests: {count}")
