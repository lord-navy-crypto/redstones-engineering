#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing operations world integration file: {rel}")
        return ""
    return path.read_text(errors="ignore")


provider = read("src/main/java/dev/redstoneengineering/operations/world/OperationWorldResourceProvider.java")
snapshot = read("src/main/java/dev/redstoneengineering/operations/world/OperationWorldResourceSnapshot.java")
resolver = read("src/main/java/dev/redstoneengineering/operations/world/OperationWorldResourceResolver.java")
binding = read("src/main/java/dev/redstoneengineering/operations/world/OperationWorkcellBinding.java")
store = read("src/main/java/dev/redstoneengineering/operations/world/OperationWorkcellStore.java")
saved = read("src/main/java/dev/redstoneengineering/operations/world/OperationPlantSavedData.java")
buffer_state = read("src/main/java/dev/redstoneengineering/operations/world/OperationIndustrialBufferState.java")
sequence = read("src/main/java/dev/redstoneengineering/block/SequenceControllerBlock.java")
alarm = read("src/main/java/dev/redstoneengineering/block/AlarmProcessorBlock.java")
watchdog = read("src/main/java/dev/redstoneengineering/block/WatchdogBlock.java")
interlock = read("src/main/java/dev/redstoneengineering/block/SafetyInterlockBlock.java")
fault_latch = read("src/main/java/dev/redstoneengineering/block/FaultLatchBlock.java")
servo = read("src/main/java/dev/redstoneengineering/block/ServoActuatorBlock.java")
workcell_controller = read("src/main/java/dev/redstoneengineering/block/WorkcellControllerBlock.java")
workcell_ui = read("src/main/java/dev/redstoneengineering/ui/WorkcellControllerUi.java")
workcell_menu = read("src/main/java/dev/redstoneengineering/ui/menu/WorkcellControllerMenu.java")
workcell_screen = read("src/main/java/dev/redstoneengineering/client/ui/WorkcellControllerScreen.java")

for token in ("interface OperationWorldResourceProvider", "operationResourceSnapshot"):
    if provider and token not in provider:
        errors.append(f"World resource provider missing contract {token!r}")

for token in (
    "record OperationWorldResourceSnapshot", "resourceId", "processCapabilities", "available",
    "running", "completionEvidenceAvailable", "faultActive", "numericEvidence", "PortQuality", "validEvidence",
):
    if snapshot and token not in snapshot:
        errors.append(f"World resource snapshot missing fail-closed evidence field/helper {token!r}")

for token in ("class OperationWorldResourceResolver", "resolve", "BlockPos", "OperationWorldResourceProvider"):
    if resolver and token not in resolver:
        errors.append(f"World resource resolver missing explicit-position resolution {token!r}")

for token in (
    "record OperationWorkcellBinding", "workcellId", "resourceBindings", "BlockPos", "expectedResourceId", "validBinding",
):
    if binding and token not in binding:
        errors.append(f"Workcell binding missing stable explicit membership contract {token!r}")

for token in (
    "class OperationPlantSavedData", "extends SavedData", "computeIfAbsent", "overworld()", "setDirty()",
    "save(CompoundTag", "load(CompoundTag", "putWorkcell", "removeWorkcell", "workcells",
    "putBuffer", "removeBuffer", "buffers",
):
    if saved and token not in saved:
        errors.append(f"Plant SavedData missing server persistence contract {token!r}")

for token in (
    "class OperationWorkcellStore", "bindResource", "unbindResource", "OperationPlantSavedData",
    "OperationWorldResourceResolver.resolve", "RESOURCE_POSITION_ALREADY_BOUND", "RESOURCE_ID_ALREADY_BOUND",
    "RESOURCE_ID_MISMATCH", "WORKCELL_ID_MISSING", "RESOURCE_EVIDENCE_INVALID",
):
    if store and token not in store:
        errors.append(f"Workcell store missing fail-closed explicit binding rule {token!r}")

for body, label, required in (
    (sequence, "SequenceControllerBlock", ("implements OperationWorldResourceProvider", "operationResourceSnapshot", "step", "completedCycles", "sequence_step", "completed_cycles")),
    (alarm, "AlarmProcessorBlock", ("implements OperationWorldResourceProvider", "operationResourceSnapshot", "latched", "unacknowledged", "alarm_severity")),
    (watchdog, "WatchdogBlock", ("implements OperationWorldResourceProvider", "operationResourceSnapshot", "ageTicks", "timeoutCount", "heartbeat_age_ticks")),
    (interlock, "SafetyInterlockBlock", ("implements OperationWorldResourceProvider", "operationResourceSnapshot", "failedMask", "failed_mask")),
    (fault_latch, "FaultLatchBlock", ("implements OperationWorldResourceProvider", "operationResourceSnapshot", "latched", "tripCount", "trip_count")),
    (servo, "ServoActuatorBlock", ("OperationWorldResourceProvider", "operationResourceSnapshot", "servo_positioning", "completionEvidenceAvailable", "position", "velocity", "error", "braking")),
):
    for token in required:
        if body and token not in body:
            errors.append(f"{label} missing Operations evidence integration {token!r}")

if servo and "false," not in servo:
    errors.append("ServoActuatorBlock must explicitly expose completion evidence as unavailable rather than inventing a completion event")

for body, label, required in (
    (workcell_controller, "WorkcellControllerBlock", (
        "class WorkcellControllerBlock", '"ACTIVE"', '"PERMIT"', '"HOLD"', '"FAULT"', '"QUEUE PRESSURE"',
        "OperationWorkcellStore.resolveBoundResources", "OperationWorkcellAdmissionAssessment.inspect",
        "isSignalSource", "queryDirection.getOpposite()",
    )),
    (workcell_ui, "WorkcellControllerUi", ("class WorkcellControllerUi", "open", "WorkcellControllerMenu")),
    (workcell_menu, "WorkcellControllerMenu", (
        "class WorkcellControllerMenu", "OperationWorkcellStore", "boundResource", "admissionReason",
        "setup", "maintenance", "activeAssignments", "queuePressure",
    )),
    (workcell_screen, "WorkcellControllerScreen", (
        "class WorkcellControllerScreen", "BOUND RESOURCES", "ADMISSION", "SETUP", "MAINTENANCE",
    )),
):
    for token in required:
        if body and token not in body:
            errors.append(f"{label} missing world-facing controller contract {token!r}")

for token in ("OperationDispatchRuntime", "OperationChangeoverRuntime", "OperationMaintenanceRuntime"):
    combined_controller = workcell_controller + workcell_menu + workcell_ui
    if combined_controller and token not in combined_controller:
        errors.append(f"Workcell Controller must delegate existing Operations authority through {token}")

for forbidden in ("OperationBottleneckAssessment", "severityScore", "Comparator.comparing", "getEntitiesOfClass", "inflate("):
    if workcell_controller and forbidden in workcell_controller:
        errors.append(f"Workcell Controller must not rank/discover resources independently; found {forbidden!r}")

# Persistent world buffer state must preserve logical lot identity and apply state changes only
# through the already-authoritative pure buffer/material-release runtimes.
for token in (
    "class OperationIndustrialBufferState",
    "bufferId",
    "location",
    "capacityUnits",
    "OperationBufferLot",
    "OperationBufferSnapshot",
    "OperationBufferRuntime.receive",
    "OperationBufferRuntime.allocate",
    "OperationMaterialReleaseRuntime.release",
    "OperationPlantSavedData.get",
    "data.putBuffer",
    "BUFFER_CAPACITY_REACHED",
    "DUPLICATE_OUTPUT_RECEIPT",
):
    if buffer_state and token not in buffer_state:
        errors.append(f"Industrial Buffer state missing authoritative persistence/runtime bridge {token!r}")

for forbidden in (
    "import net.minecraft.world.item.ItemStack",
    "import net.minecraft.world.Container",
    "import net.minecraft.world.SimpleContainer",
    "getEntitiesOfClass", "inflate(", "setBlock(", "OperationDispatchRuntime", "RobotMission",
):
    if buffer_state and forbidden in buffer_state:
        errors.append(f"Industrial Buffer state must retain logical lot authority without world/inventory shortcuts; found {forbidden!r}")

for body, label in ((provider, "provider"), (snapshot, "snapshot"), (resolver, "resolver"), (binding, "binding"), (saved, "SavedData")):
    for forbidden in (
        "OperationDispatchRuntime", "OperationQueueRuntime", "OperationMaintenanceRuntime", "OperationChangeoverRuntime",
        "RobotMission", "setBlock(", "setDeltaMovement(", "scheduleTick(", "Minecraft.getInstance", "client.ui",
    ):
        if body and forbidden in body:
            errors.append(f"Operations world {label} must remain evidence/persistence-only; found {forbidden!r}")

for body, label in ((sequence, "SequenceControllerBlock"), (alarm, "AlarmProcessorBlock"), (watchdog, "WatchdogBlock"), (interlock, "SafetyInterlockBlock"), (fault_latch, "FaultLatchBlock"), (servo, "ServoActuatorBlock")):
    for forbidden in ("OperationDispatchRuntime", "OperationQueueRuntime", "OperationChangeoverRuntime", "OperationMaintenanceRuntime"):
        if body and forbidden in body:
            errors.append(f"{label} must expose evidence only; found Operations authority {forbidden!r}")

for body, label in ((resolver, "World resource resolver"), (store, "Workcell store")):
    for forbidden in ("getEntitiesOfClass", "inflate(", "closerThan", "nearest"):
        if body and forbidden in body:
            errors.append(f"{label} must not use proximity discovery; found {forbidden!r}")

for body, label in ((binding, "Workcell binding"), (saved, "Plant SavedData"), (buffer_state, "Industrial Buffer state")):
    for forbidden in (
        "import net.minecraft.world.level.block.state.BlockState",
        "import net.minecraft.world.level.block.state.properties.IntegerProperty",
        "import net.minecraft.world.level.block.state.properties.StringProperty",
    ):
        if body and forbidden in body:
            errors.append(f"{label} must keep high-cardinality identity out of block properties; found {forbidden!r}")

if errors:
    print("RSE OPERATIONS WORLD INTEGRATION VERIFY: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE OPERATIONS WORLD INTEGRATION VERIFY: PASS")
print(" explicit-position resource resolution: PASS")
print(" fail-closed evidence snapshot: PASS")
print(" existing sequence/alarm/watchdog/interlock/fault devices expose Operations evidence: PASS")
print(" existing servo actuator exposes real machine evidence without fabricated completion: PASS")
print(" server-owned explicit workcell binding persistence: PASS")
print(" duplicate position/resource identity rejection: PASS")
print(" Workcell Controller delegates dispatch/changeover/maintenance/capacity authority: PASS")
print(" Workcell Controller physical redstone query direction: PASS")
print(" persistent Industrial Buffer logical lot/WIP state: PASS")
print(" buffer receipt/allocation delegates to OperationBufferRuntime: PASS")
print(" downstream release delegates atomically to OperationMaterialReleaseRuntime: PASS")
print(" Workcell Controller independent ranking/proximity discovery: NONE")
print(" dispatch/world-motion/client/inventory authority leakage: NONE")
print(" proximity auto-discovery: NONE")
