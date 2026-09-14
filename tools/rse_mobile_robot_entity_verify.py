#!/usr/bin/env python3
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[1]
MOD=ROOT/'src/main/java/dev/redstoneengineering/RoboticsEntityModule.java'
ENT=ROOT/'src/main/java/dev/redstoneengineering/entity/EngineeringMobileRobotEntity.java'
errors=[]
def read(p):
    if not p.exists(): errors.append(f'missing {p.relative_to(ROOT)}'); return ''
    return p.read_text(encoding='utf-8')
def req(s,n,l):
    if n not in s: errors.append(f'{l}: missing {n!r}')
mod=read(MOD); ent=read(ENT)
for n in ('@Mod(RedstoneEngineering.MOD_ID)','DeferredRegister<EntityType<?>>','DeferredRegister.create(Registries.ENTITY_TYPE','DeferredHolder<EntityType<?>, EntityType<EngineeringMobileRobotEntity>>','EntityType.Builder.of(EngineeringMobileRobotEntity::new, MobCategory.MISC)','ENTITY_TYPES.register(modEventBus)'):
    req(mod,n,'RoboticsEntityModule.java')
for n in ('extends Entity','RobotStateMachine.next(','RobotSafetyAssessment.inspect(','safety.motionPermit()','level().noCollision(','RobotOperatingState.WAITING','move(MoverType.SELF, command)','assignTarget(BlockPos target)','EntityDataSerializers.STRING'):
    req(ent,n,'EngineeringMobileRobotEntity.java')
for forbidden in ('PathNavigation','GoalSelector','AStar','teleportTo(','setPos(missionTarget'):
    if forbidden in ent: errors.append(f'EngineeringMobileRobotEntity.java: unexpected shortcut {forbidden!r}')
if errors:
    print('RSE MOBILE ROBOT ENTITY VERIFY: FAIL')
    for e in errors: print(' -',e)
    sys.exit(1)
print('RSE MOBILE ROBOT ENTITY VERIFY: PASS')
print('  registered AMR entity consumes shared robotics state/safety contracts')
print('  target movement is safety-gated and collision-aware')
print('  entity registry uses the NeoForge 1.21.1 generic DeferredRegister contract')
print('  v1 remains lumped deterministic motion; graph navigation intentionally deferred')
