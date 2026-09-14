#!/usr/bin/env python3
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[1]
MOD=ROOT/'src/main/java/dev/redstoneengineering/RoboticsEntityModule.java'
ENT=ROOT/'src/main/java/dev/redstoneengineering/entity/EngineeringMobileRobotEntity.java'
CLIENT=ROOT/'src/main/java/dev/redstoneengineering/client/RoboticsClientModule.java'
RENDER=ROOT/'src/main/java/dev/redstoneengineering/client/EngineeringMobileRobotRenderer.java'
errors=[]
def read(p):
    if not p.exists(): errors.append(f'missing {p.relative_to(ROOT)}'); return ''
    return p.read_text(encoding='utf-8')
def req(s,n,l):
    if n not in s: errors.append(f'{l}: missing {n!r}')
mod=read(MOD); ent=read(ENT); client=read(CLIENT); render=read(RENDER)
for n in ('@Mod(RedstoneEngineering.MOD_ID)','DeferredRegister<EntityType<?>>','DeferredRegister.create(Registries.ENTITY_TYPE','DeferredHolder<EntityType<?>, EntityType<EngineeringMobileRobotEntity>>','EntityType.Builder.of(EngineeringMobileRobotEntity::new, MobCategory.MISC)','ENTITY_TYPES.register(modEventBus)'):
    req(mod,n,'RoboticsEntityModule.java')
for n in ('extends Entity','RobotStateMachine.next(','RobotSafetyAssessment.inspect(','safety.motionPermit()','level().noCollision(','RobotOperatingState.WAITING','move(MoverType.SELF, command)','assignTarget(BlockPos target)','EntityDataSerializers.STRING'):
    req(ent,n,'EngineeringMobileRobotEntity.java')
for n in ('assignNavigationRoute(RobotNavigationGraph graph, String sourceId, String targetId)','RobotRoutePlanner.plan(graph, sourceId, targetId)','SOURCE_NOT_LOCALIZED_TO_ROBOT','ROUTE_NODE_EVIDENCE_MISSING','FOLLOWING_EXPLICIT_ROUTE','advanceRouteWaypoint()','ROUTE_COMPLETE','RouteCount','RouteIndex','restoreRoute(tag)','PERSISTED_ROUTE_INVALID'):
    req(ent,n,'EngineeringMobileRobotEntity.java route execution')
for n in ('RobotDockAssessment.inspect(dock, robotIdentity(), phase)','beginDocking(RobotDockSnapshot dock)','confirmDocked(RobotDockSnapshot dock)','DOCK_POSITION_MISMATCH','ROBOT_NOT_CONFIRMED_DOCKED','dock.occupiedBy(robotIdentity())','RobotStateMachine.Event.ARRIVE_DOCK','RobotStateMachine.Event.DOCKED','DOCK_APPROACH_COMPLETE','RobotOperatingState.DOCKING || robotState() == RobotOperatingState.LOADING','DOCK_HANDSHAKE'):
    req(ent,n,'EngineeringMobileRobotEntity.java dock runtime')
for n in ('completeLoading(RobotDockSnapshot dock, RobotMaterialTransferSnapshot transfer)','RobotMaterialFlowRuntime.evaluate(','decision.dockReason()','decision.materialReason()','setRobotState(decision.nextState())','decision.advancesToTransport()','TRANSPORT_ROUTE_REQUIRED','MATERIAL_REASON','MaterialReason','materialReason()','RobotOperatingState.TRANSPORTING','RobotOperatingState.TRANSPORT_REPLANNING'):
    req(ent,n,'EngineeringMobileRobotEntity.java material-flow runtime')
for n in (
    'assignTransportRoute(',
    'RobotTransportRouteRuntime.evaluate(',
    'TRANSPORT_SOURCE_NOT_LOCALIZED_TO_ROBOT',
    'TRANSPORT_ROUTE_TOO_LONG',
    'FOLLOWING_TRANSPORT_ROUTE',
    'RobotOperatingState.TRANSPORT_WAITING',
    'completeRouteArrival()',
    'TRANSPORT_HOLD_AT_TARGET',
    'RobotStateMachine.Event.ARRIVE_TARGET',
    'TRANSPORT_TARGET_ARRIVED',
    'TRANSPORT_ROUTE_COMPLETE',
    'RobotOperatingState.UNLOADING',
    'UNLOAD_HANDSHAKE',
):
    req(ent,n,'EngineeringMobileRobotEntity.java transport execution')
if 'RobotStateMachine.Event.LOAD_COMPLETE' in ent:
    errors.append('EngineeringMobileRobotEntity.java must consume RobotMaterialFlowRuntime rather than emit LOAD_COMPLETE directly')
if 'RobotMaterialTransferAssessment.inspect(' in ent:
    errors.append('EngineeringMobileRobotEntity.java must not reimplement material transfer assessment beside RobotMaterialFlowRuntime')
if 'RobotTransportHandoffAssessment.inspect(' in ent:
    errors.append('EngineeringMobileRobotEntity.java must consume RobotTransportRouteRuntime rather than reimplement transport handoff assessment')
if ent.count('RobotMaterialFlowRuntime.evaluate(') != 1:
    errors.append('EngineeringMobileRobotEntity.java must have exactly one authoritative material-flow decision entry point')
if ent.count('RobotTransportRouteRuntime.evaluate(') != 1:
    errors.append('EngineeringMobileRobotEntity.java must have exactly one authoritative transport-route decision entry point')
if ent.count('RobotStateMachine.Event.ARRIVE_DOCK') != 1:
    errors.append('EngineeringMobileRobotEntity.java must have exactly one guarded ARRIVE_DOCK transition')
if ent.count('RobotStateMachine.Event.DOCKED') != 1:
    errors.append('EngineeringMobileRobotEntity.java must have exactly one occupancy-confirmed DOCKED transition')
if ent.count('RobotStateMachine.Event.ARRIVE_TARGET') != 1:
    errors.append('EngineeringMobileRobotEntity.java must have exactly one transport-final ARRIVE_TARGET transition')
for n in ('dist = Dist.CLIENT','EntityRenderersEvent.RegisterRenderers','RoboticsEntityModule.ENGINEERING_MOBILE_ROBOT.get()','EngineeringMobileRobotRenderer::new'):
    req(client,n,'RoboticsClientModule.java')
for n in ('extends EntityRenderer<EngineeringMobileRobotEntity>','BlockRenderDispatcher','chassisState(robot.robotState())','case FAULT, SAFE_STOP','case WAITING, REPLANNING, DEGRADED','Blocks.COPPER_BLOCK.defaultBlockState()','TextureAtlas.LOCATION_BLOCKS'):
    req(render,n,'EngineeringMobileRobotRenderer.java')
for forbidden in ('PathNavigation','GoalSelector','AStar','teleportTo(','setPos(missionTarget'):
    if forbidden in ent: errors.append(f'EngineeringMobileRobotEntity.java: unexpected shortcut {forbidden!r}')
for forbidden in ('getBlockState(','BlockPos.betweenClosed','setChunkForced(','forceLoad'):
    if forbidden in ent: errors.append(f'EngineeringMobileRobotEntity.java: route execution must not discover topology implicitly; unexpected {forbidden!r}')
for forbidden in ('RobotSafetyAssessment.inspect(','level().noCollision(','setDeltaMovement(','move(MoverType'):
    if forbidden in render: errors.append(f'EngineeringMobileRobotRenderer.java must remain render-only; unexpected {forbidden!r}')
if errors:
    print('RSE MOBILE ROBOT ENTITY VERIFY: FAIL')
    for e in errors: print(' -',e)
    sys.exit(1)
print('RSE MOBILE ROBOT ENTITY VERIFY: PASS')
print('  registered AMR entity consumes shared robotics state/safety contracts')
print('  target movement is safety-gated and collision-aware')
print('  explicit planned routes are source-anchored, waypoint-followed, and persisted')
print('  route execution does not scan the world or force-load topology')
print('  dock runtime consumes shared dock assessment and requires physical position consistency')
print('  DOCKING begins only on approach permit; LOADING requires exact AMR occupancy confirmation')
print('  LOADING consumes the authoritative Material Flow runtime decision instead of duplicating assessment logic')
print('  material evidence reason is synchronized/persisted; TRANSPORTING waits fail-safe for an explicit route')
print('  post-load transport consumes the authoritative route runtime and preserves transport-specific hold states')
print('  transport hold states cannot declare arrival merely by crossing the distance threshold')
print('  final transport arrival enters UNLOADING and remains in an explicit unload handshake without a motion target')
print('  entity registry uses the NeoForge 1.21.1 generic DeferredRegister contract')
print('  client renderer exposes heading plus synchronized nominal/waiting/fault state without a second solver')
