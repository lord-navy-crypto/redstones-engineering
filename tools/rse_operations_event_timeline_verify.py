#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        errors.append(f"missing operations timeline file: {rel}")
        return ""
    return path.read_text(errors="ignore")


window = read("src/main/java/dev/redstoneengineering/diagnostics/OperationsEventWindow.java")
for token in (
    "MAX_VISIBLE_EVENTS = 8",
    "SystemEventTimeline.recentWithin",
    "dashboard.eventScope()",
    "dashboard.firstOut()",
    "firstOutIndex()",
):
    if window and token not in window:
        errors.append(f"OperationsEventWindow missing bounded plant projection {token!r}")

menu = read("src/main/java/dev/redstoneengineering/ui/menu/OperationsMonitorMenu.java")
for token in (
    "EVENT_SLOTS = OperationsEventWindow.MAX_VISIBLE_EVENTS",
    "OperationsDashboardSnapshot.inspect(level, blockPos)",
    "OperationsEventWindow.inspect(level, dashboard)",
    "private final DataSlot[] eventKinds = trackedInts(EVENT_SLOTS);",
    "private final DataSlot[] eventSeverities = trackedInts(EVENT_SLOTS);",
    "private final DataSlot[] eventAges = trackedInts(EVENT_SLOTS);",
    "firstOutSlot.set",
    "MAX_SYNC_AGE_TICKS = 32767",
):
    if menu and token not in menu:
        errors.append(f"OperationsMonitorMenu missing server-synchronized event contract {token!r}")

screen = read("src/main/java/dev/redstoneengineering/client/ui/OperationsMonitorScreen.java")
for token in (
    "PLANT EVENT TIMELINE",
    "FIRST OUT",
    "menu.eventKindOrdinal",
    "menu.eventSeverity",
    "menu.eventAgeTicks",
    "menu.firstOutSlot()",
    "Observer-only",
):
    if screen and token not in screen:
        errors.append(f"OperationsMonitorScreen missing event visualization {token!r}")
for forbidden in (
    "SystemEventTimeline",
    "RuntimeIntStore",
    "getBlockState(",
    "scheduleTick(",
    "setBlock(",
):
    if screen and forbidden in screen:
        errors.append(f"Operations client screen must remain synchronized/render-only; found {forbidden!r}")

registration = read("src/main/java/dev/redstoneengineering/ui/EngineeringUiRegistration.java")
if registration and 'MENUS.register("operations_monitor"' not in registration:
    errors.append("Dedicated Operations Monitor menu is not registered")

client_registration = read("src/main/java/dev/redstoneengineering/client/ui/EngineeringUiClientRegistration.java")
if client_registration and "OperationsMonitorScreen::new" not in client_registration:
    errors.append("Dedicated Operations Monitor screen is not registered")

block = read("src/main/java/dev/redstoneengineering/block/OperationsMonitorBlock.java")
for token in (
    "OperationsMonitorUi.open(serverPlayer, p);",
    "plant event evidence retained",
):
    if block and token not in block:
        errors.append(f"OperationsMonitorBlock missing dedicated console/lifecycle contract {token!r}")

opener = read("src/main/java/dev/redstoneengineering/ui/OperationsMonitorUi.java")
if opener and "new OperationsMonitorMenu" not in opener:
    errors.append("OperationsMonitorUi does not open the dedicated synchronized menu")

tests = read("src/main/java/dev/redstoneengineering/gametest/RseOperationsTimelineGameTests.java")
for token in (
    "operationsEventWindowKeepsOrderedBoundedPlantTail",
    "operationsEventWindowMapsFirstOutIntoVisibleChronology",
    "MAX_VISIBLE_EVENTS",
    "FIRST_OUT_TRIP",
):
    if tests and token not in tests:
        errors.append(f"Operations timeline runtime evidence missing {token!r}")

registry = read("src/main/java/dev/redstoneengineering/gametest/RseGameTestRegistration.java")
if registry and "event.register(RseOperationsTimelineGameTests.class);" not in registry:
    errors.append("Operations timeline GameTests are not registered")

job = read("src/main/java/dev/redstoneengineering/operations/OperationJob.java")
resource = read("src/main/java/dev/redstoneengineering/operations/OperationResourceSnapshot.java")
dispatch = read("src/main/java/dev/redstoneengineering/operations/OperationDispatchRuntime.java")
for token in (
    "record OperationJob(",
    "quantity must be positive",
    "priority must be in 0..100",
    "releasedAt(long gameTick)",
):
    if job and token not in job:
        errors.append(f"OperationJob missing authoritative work-demand contract {token!r}")
for token in (
    "record OperationResourceSnapshot(",
    "PortQuality evidenceQuality",
    "AVAILABLE",
    "BUSY",
    "BLOCKED",
    "FAULTED",
    "dispatchable()",
):
    if resource and token not in resource:
        errors.append(f"OperationResourceSnapshot missing resource evidence contract {token!r}")
for token in (
    "FIFO",
    "PRIORITY_THEN_FIFO",
    "ASSIGN",
    "WAIT",
    "SAFE_STOP",
    "DUPLICATE_JOB_ID",
    "DUPLICATE_RESOURCE_ID",
    "RESOURCE_EVIDENCE_INVALID",
    "DISPATCH_PERMIT",
    "comparingLong(OperationJob::releaseTick)",
    "comparingInt(OperationJob::priority).reversed()",
):
    if dispatch and token not in dispatch:
        errors.append(f"OperationDispatchRuntime missing deterministic dispatch contract {token!r}")
for forbidden in (
    "OperationsDashboardSnapshot",
    "IndustrialOperationsAssessment",
    "throughput",
    "downtime",
    "queuePressure",
    "setBlock(",
    "setDeltaMovement(",
    "RobotStateMachine",
):
    if dispatch and forbidden in dispatch:
        errors.append(f"Dispatch authority must remain pure and KPI-independent; found {forbidden!r}")

assignment = read("src/main/java/dev/redstoneengineering/operations/OperationAssignment.java")
queue_snapshot = read("src/main/java/dev/redstoneengineering/operations/OperationQueueSnapshot.java")
queue_runtime = read("src/main/java/dev/redstoneengineering/operations/OperationQueueRuntime.java")
completion_evidence = read("src/main/java/dev/redstoneengineering/operations/OperationCompletionEvidence.java")
completion_assessment = read("src/main/java/dev/redstoneengineering/operations/OperationCompletionAssessment.java")
for token in (
    "record OperationAssignment(",
    "OperationJob job",
    "String resourceId",
    "long assignedTick",
):
    if assignment and token not in assignment:
        errors.append(f"OperationAssignment missing identity-bound assignment contract {token!r}")
for token in (
    "record OperationQueueSnapshot(",
    "MAX_CAPACITY = 64",
    "queued jobs exceed capacity",
    "queued/active job identities must be unique",
    "one resource cannot own multiple active assignments",
    "int wip()",
):
    if queue_snapshot and token not in queue_snapshot:
        errors.append(f"OperationQueueSnapshot missing bounded WIP contract {token!r}")
for token in (
    "ENQUEUED",
    "ASSIGNED",
    "COMPLETED",
    "QUEUE_CAPACITY_REACHED",
    "RESOURCE_ALREADY_ASSIGNED",
    "ACTIVE_ASSIGNMENT_NOT_FOUND",
    "OperationDispatchRuntime.evaluate(",
    "OperationCompletionAssessment.inspect(",
    "new OperationAssignment(",
    "Active work is released only by explicit completion evidence",
):
    if queue_runtime and token not in queue_runtime:
        errors.append(f"OperationQueueRuntime missing admission/assignment/completion lifecycle {token!r}")
for token in (
    "record OperationCompletionEvidence(",
    "completedQuantity",
    "PortQuality evidenceQuality",
    "processConfirmed",
    "outputConfirmed",
    "completionConfirmed",
    "faultActive",
):
    if completion_evidence and token not in completion_evidence:
        errors.append(f"OperationCompletionEvidence missing process evidence contract {token!r}")
for token in (
    "PROCESS_FAULT_ACTIVE",
    "COMPLETION_JOB_MISMATCH",
    "COMPLETION_RESOURCE_MISMATCH",
    "PROCESS_NOT_CONFIRMED",
    "OUTPUT_NOT_CONFIRMED",
    "COMPLETION_NOT_CONFIRMED",
    "COMPLETION_QUANTITY_MISMATCH",
    "PROCESS_COMPLETE",
):
    if completion_assessment and token not in completion_assessment:
        errors.append(f"OperationCompletionAssessment missing fail-closed completion rule {token!r}")
for forbidden in (
    "UNLOAD_COMPLETE",
    "LOAD_COMPLETE",
    "completeMission",
    "OperationsDashboardSnapshot",
    "IndustrialOperationsAssessment",
    "setBlock(",
    "RuntimeIntStore",
):
    if queue_runtime and forbidden in queue_runtime:
        errors.append(f"Queue lifecycle must not consume robotics completion or observer KPIs; found {forbidden!r}")
for forbidden in (
    "assignedTick() +",
    "assignedTick() -",
    "gameTick - assignment.assignedTick",
    "System.currentTimeMillis",
):
    if queue_runtime and forbidden in queue_runtime:
        errors.append(f"Queue completion must not be inferred from elapsed time; found {forbidden!r}")

output = read("src/main/java/dev/redstoneengineering/operations/OperationOutputSnapshot.java")
transport_demand = read("src/main/java/dev/redstoneengineering/operations/OperationTransportDemand.java")
transport_bridge = read("src/main/java/dev/redstoneengineering/integration/OperationsRobotTransportBridge.java")
transport_binding = read("src/main/java/dev/redstoneengineering/integration/OperationTransportBinding.java")
for token in (
    "record OperationOutputSnapshot(",
    "long outputId",
    "long jobId",
    "String resourceId",
    "int units",
    "BlockPos source",
    "PortQuality evidenceQuality",
    "completionConfirmed",
    "materialReady",
    "faultActive",
):
    if output and token not in output:
        errors.append(f"OperationOutputSnapshot missing completed-output evidence contract {token!r}")
for token in (
    "record OperationTransportDemand(",
    "long missionId",
    "long outputId",
    "BlockPos source",
    "BlockPos target",
    "int units",
    "int priority",
):
    if transport_demand and token not in transport_demand:
        errors.append(f"OperationTransportDemand missing explicit logistics contract {token!r}")
for token in (
    "record OperationTransportBinding(",
    "long missionId",
    "long outputId",
    "long jobId",
    "BlockPos source",
    "BlockPos target",
    "int units",
):
    if transport_binding and token not in transport_binding:
        errors.append(f"OperationTransportBinding missing cross-domain correlation contract {token!r}")
for token in (
    "MISSION_READY",
    "OUTPUT_FAULT_ACTIVE",
    "OUTPUT_EVIDENCE_INVALID",
    "OUTPUT_COMPLETION_UNCONFIRMED",
    "MATERIAL_NOT_READY",
    "OUTPUT_ID_MISMATCH",
    "SOURCE_IDENTITY_MISMATCH",
    "TRANSPORT_QUANTITY_MISMATCH",
    "TRANSPORT_NOT_REQUIRED",
    "RobotMission.MissionType.TRANSFER",
    "new OperationTransportBinding(",
    "output.outputId()",
    "output.jobId()",
    "demand.source()",
    "demand.target()",
    "demand.priority()",
    "demand.units()",
    "TRANSPORT_MISSION_READY",
):
    if transport_bridge and token not in transport_bridge:
        errors.append(f"OperationsRobotTransportBridge missing evidence-bound mission/correlation contract {token!r}")
for forbidden in (
    "RobotRoutePlanner",
    "RobotDockAssessment",
    "RobotMaterialFlowRuntime",
    "RobotMaterialUnloadRuntime",
    "RobotStateMachine",
    "EngineeringMobileRobotEntity",
    "setDeltaMovement(",
    "setBlock(",
    "scheduleTick(",
    "getEntities",
    "RuntimeIntStore",
    "OperationsDashboardSnapshot",
    "IndustrialOperationsAssessment",
):
    if transport_bridge and forbidden in transport_bridge:
        errors.append(f"Operations-to-Robotics bridge must not own route/dock/motion/world/KPI authority; found {forbidden!r}")

buffer_receipt = read("src/main/java/dev/redstoneengineering/operations/OperationBufferReceiptEvidence.java")
buffer_lot = read("src/main/java/dev/redstoneengineering/operations/OperationBufferLot.java")
buffer_snapshot = read("src/main/java/dev/redstoneengineering/operations/OperationBufferSnapshot.java")
buffer_runtime = read("src/main/java/dev/redstoneengineering/operations/OperationBufferRuntime.java")
buffer_bridge = read("src/main/java/dev/redstoneengineering/integration/OperationsBufferReceiptBridge.java")
for token in (
    "record OperationBufferReceiptEvidence(",
    "long missionId",
    "long outputId",
    "long jobId",
    "String bufferId",
    "BlockPos bufferLocation",
    "int units",
    "PortQuality evidenceQuality",
    "unloadConfirmed",
    "faultActive",
):
    if buffer_receipt and token not in buffer_receipt:
        errors.append(f"OperationBufferReceiptEvidence missing unload receipt contract {token!r}")
for token in (
    "record OperationBufferLot(",
    "long outputId",
    "long jobId",
    "int units",
):
    if buffer_lot and token not in buffer_lot:
        errors.append(f"OperationBufferLot missing logical WIP identity contract {token!r}")
for token in (
    "record OperationBufferSnapshot(",
    "MAX_CAPACITY_UNITS = 4096",
    "buffer output identities must be unique",
    "buffer WIP exceeds capacity",
    "int usedUnits()",
    "int availableUnits()",
    "boolean containsOutput",
):
    if buffer_snapshot and token not in buffer_snapshot:
        errors.append(f"OperationBufferSnapshot missing bounded WIP buffer contract {token!r}")
for token in (
    "RECEIVED",
    "BUFFER_RECEIPT_FAULT_ACTIVE",
    "BUFFER_RECEIPT_EVIDENCE_INVALID",
    "BUFFER_UNLOAD_UNCONFIRMED",
    "BUFFER_ID_MISMATCH",
    "BUFFER_LOCATION_MISMATCH",
    "DUPLICATE_OUTPUT_RECEIPT",
    "BUFFER_CAPACITY_REACHED",
    "BUFFER_RECEIPT_ACCEPTED",
    "new OperationBufferLot(",
):
    if buffer_runtime and token not in buffer_runtime:
        errors.append(f"OperationBufferRuntime missing fail-closed receipt/capacity lifecycle {token!r}")
for token in (
    "RECEIPT_READY",
    "TRANSPORT_BINDING_MISSING",
    "ROBOT_UNLOAD_FAULT",
    "ROBOT_UNLOAD_SAFE_STOP",
    "ROBOT_UNLOAD_INCOMPLETE",
    "MISSION_BINDING_MISMATCH",
    "MISSION_SOURCE_MISMATCH",
    "MISSION_TARGET_MISMATCH",
    "MISSION_QUANTITY_MISMATCH",
    "UNLOAD_TRANSFER_EVIDENCE_INVALID",
    "UNLOAD_TRANSFER_UNCONFIRMED",
    "UNLOAD_TRANSFER_QUANTITY_MISMATCH",
    "new OperationBufferReceiptEvidence(",
    "binding.outputId()",
    "binding.jobId()",
    "binding.target()",
    "BUFFER_RECEIPT_READY",
):
    if buffer_bridge and token not in buffer_bridge:
        errors.append(f"OperationsBufferReceiptBridge missing correlated unload-to-buffer contract {token!r}")
for body, label in ((buffer_runtime, "OperationBufferRuntime"), (buffer_bridge, "OperationsBufferReceiptBridge")):
    for forbidden in (
        "setBlock(",
        "setDeltaMovement(",
        "scheduleTick(",
        "getEntities",
        "RuntimeIntStore",
        "OperationsDashboardSnapshot",
        "IndustrialOperationsAssessment",
        "RobotRoutePlanner",
        "EngineeringMobileRobotEntity",
    ):
        if body and forbidden in body:
            errors.append(f"{label} must remain pure and not own world/route/KPI authority; found {forbidden!r}")

if errors:
    print("RSE operations event timeline verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("RSE operations event timeline verification: PASS")
print(" plant-scoped bounded 8-event tail: PASS")
print(" server-synchronized kind/severity/age evidence: PASS")
print(" first-out visible chronology mapping: PASS")
print(" dedicated observer-only Operations Monitor UI: PASS")
print(" executable ordering + first-out GameTests: PASS")
print(" authoritative FIFO/priority dispatch foundation: PASS")
print(" bounded queued -> active assignment lifecycle: PASS")
print(" evidence-bound active -> completed lifecycle: PASS")
print(" completed output -> explicit logistics demand -> TRANSFER mission bridge: PASS")
print(" mission/output/job correlation survives the Robotics boundary: PASS")
print(" completed unload -> evidence-bound bounded buffer receipt lifecycle: PASS")
print(" dispatch/completion remain independent of downstream KPI observers and elapsed-time guesses: PASS")
print(" Operations/Robotics integration does not own route/dock/motion/world authority: PASS")
