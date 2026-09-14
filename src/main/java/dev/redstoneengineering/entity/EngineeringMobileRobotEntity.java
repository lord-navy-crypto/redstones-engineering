package dev.redstoneengineering.entity;

import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.robotics.RobotLocalizationQuality;
import dev.redstoneengineering.robotics.RobotOperatingState;
import dev.redstoneengineering.robotics.RobotSafetyAssessment;
import dev.redstoneengineering.robotics.RobotStateMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** First world-running RSE autonomous mobile robot entity. */
public final class EngineeringMobileRobotEntity extends Entity {
    private static final double CRUISE_SPEED = 0.12D;
    private static final double ARRIVAL_DISTANCE = 0.45D;
    private static final double OBSTACLE_LOOKAHEAD = 0.80D;

    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(EngineeringMobileRobotEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> HAS_TARGET = SynchedEntityData.defineId(EngineeringMobileRobotEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> TARGET_X = SynchedEntityData.defineId(EngineeringMobileRobotEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TARGET_Y = SynchedEntityData.defineId(EngineeringMobileRobotEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TARGET_Z = SynchedEntityData.defineId(EngineeringMobileRobotEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SAFETY = SynchedEntityData.defineId(EngineeringMobileRobotEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> SAFETY_REASON = SynchedEntityData.defineId(EngineeringMobileRobotEntity.class, EntityDataSerializers.STRING);

    private RobotLocalizationQuality localization = RobotLocalizationQuality.VALID;
    private boolean driveReady = true;
    private boolean emergencyStopClear = true;

    public EngineeringMobileRobotEntity(EntityType<? extends EngineeringMobileRobotEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STATE, RobotOperatingState.IDLE.ordinal());
        builder.define(HAS_TARGET, false);
        builder.define(TARGET_X, 0);
        builder.define(TARGET_Y, 0);
        builder.define(TARGET_Z, 0);
        builder.define(SAFETY, RobotSafetyAssessment.Verdict.SAFE_STOP.ordinal());
        builder.define(SAFETY_REASON, "NO_MISSION");
    }

    public RobotOperatingState robotState() {
        RobotOperatingState[] values = RobotOperatingState.values();
        return values[Math.max(0, Math.min(values.length - 1, entityData.get(STATE)))];
    }

    private void setRobotState(RobotOperatingState state) { entityData.set(STATE, state.ordinal()); }
    private void transition(RobotStateMachine.Event event) { setRobotState(RobotStateMachine.next(robotState(), event)); }
    public boolean hasMissionTarget() { return entityData.get(HAS_TARGET); }
    public BlockPos missionTarget() { return new BlockPos(entityData.get(TARGET_X), entityData.get(TARGET_Y), entityData.get(TARGET_Z)); }

    public RobotSafetyAssessment.Verdict safetyVerdict() {
        RobotSafetyAssessment.Verdict[] values = RobotSafetyAssessment.Verdict.values();
        return values[Math.max(0, Math.min(values.length - 1, entityData.get(SAFETY)))];
    }

    public String safetyReason() { return entityData.get(SAFETY_REASON); }

    public void assignTarget(BlockPos target) {
        if (level().isClientSide || target == null) return;
        entityData.set(TARGET_X, target.getX());
        entityData.set(TARGET_Y, target.getY());
        entityData.set(TARGET_Z, target.getZ());
        entityData.set(HAS_TARGET, true);
        setRobotState(RobotOperatingState.IDLE);
        transition(RobotStateMachine.Event.ASSIGN_MISSION);
        transition(RobotStateMachine.Event.BEGIN_PLANNING);
        transition(RobotStateMachine.Event.ROUTE_READY);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        if (!hasMissionTarget()) {
            stopMotion(RobotSafetyAssessment.Verdict.SAFE_STOP, "NO_MISSION");
            if (robotState() != RobotOperatingState.IDLE && robotState() != RobotOperatingState.COMPLETE) setRobotState(RobotOperatingState.IDLE);
            return;
        }

        Vec3 target = Vec3.atCenterOf(missionTarget());
        Vec3 horizontal = new Vec3(target.x - getX(), 0.0D, target.z - getZ());
        if (horizontal.length() <= ARRIVAL_DISTANCE) {
            setDeltaMovement(Vec3.ZERO);
            entityData.set(HAS_TARGET, false);
            setRobotState(RobotOperatingState.COMPLETE);
            entityData.set(SAFETY, RobotSafetyAssessment.Verdict.PERMIT.ordinal());
            entityData.set(SAFETY_REASON, "MISSION_COMPLETE");
            return;
        }

        Vec3 direction = horizontal.normalize();
        boolean obstacleClear = level().noCollision(this, getBoundingBox().move(direction.scale(OBSTACLE_LOOKAHEAD)));
        RobotSafetyAssessment.Snapshot safety = RobotSafetyAssessment.inspect(new RobotSafetyAssessment.Input(
                localization, PortQuality.VALID, obstacleClear, driveReady, emergencyStopClear));
        entityData.set(SAFETY, safety.verdict().ordinal());
        entityData.set(SAFETY_REASON, safety.primaryReason());

        if (!safety.motionPermit()) {
            setDeltaMovement(Vec3.ZERO);
            if (!obstacleClear && robotState() == RobotOperatingState.NAVIGATING) transition(RobotStateMachine.Event.OBSTACLE_DETECTED);
            else if (safety.verdict() == RobotSafetyAssessment.Verdict.FAULT) transition(RobotStateMachine.Event.CRITICAL_FAULT);
            else if (robotState() != RobotOperatingState.WAITING) transition(RobotStateMachine.Event.LOCALIZATION_LOST);
            return;
        }

        if (robotState() == RobotOperatingState.WAITING) transition(RobotStateMachine.Event.OBSTACLE_CLEARED);
        if (robotState() == RobotOperatingState.SAFE_STOP) {
            transition(RobotStateMachine.Event.SAFE_CONDITION_RESTORED);
            transition(RobotStateMachine.Event.REPLAN_READY);
        }
        if (robotState() == RobotOperatingState.REPLANNING) transition(RobotStateMachine.Event.REPLAN_READY);
        if (robotState() != RobotOperatingState.NAVIGATING) setRobotState(RobotOperatingState.NAVIGATING);

        setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
        Vec3 command = direction.scale(CRUISE_SPEED);
        setDeltaMovement(command);
        move(MoverType.SELF, command);
    }

    private void stopMotion(RobotSafetyAssessment.Verdict verdict, String reason) {
        setDeltaMovement(Vec3.ZERO);
        entityData.set(SAFETY, verdict.ordinal());
        entityData.set(SAFETY_REASON, reason);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!level().isClientSide && hand == InteractionHand.MAIN_HAND) {
            BlockPos target = player.blockPosition().relative(player.getDirection(), 8);
            assignTarget(target);
            player.displayClientMessage(Component.literal("AMR mission target: " + target.toShortString()), true);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("RobotState", robotState().ordinal());
        tag.putBoolean("HasTarget", hasMissionTarget());
        if (hasMissionTarget()) {
            BlockPos target = missionTarget();
            tag.putInt("TargetX", target.getX()); tag.putInt("TargetY", target.getY()); tag.putInt("TargetZ", target.getZ());
        }
        tag.putString("Localization", localization.name());
        tag.putBoolean("DriveReady", driveReady);
        tag.putBoolean("EmergencyStopClear", emergencyStopClear);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        int ordinal = Math.max(0, Math.min(RobotOperatingState.values().length - 1, tag.getInt("RobotState")));
        entityData.set(STATE, ordinal);
        entityData.set(HAS_TARGET, tag.getBoolean("HasTarget"));
        entityData.set(TARGET_X, tag.getInt("TargetX")); entityData.set(TARGET_Y, tag.getInt("TargetY")); entityData.set(TARGET_Z, tag.getInt("TargetZ"));
        try { localization = RobotLocalizationQuality.valueOf(tag.getString("Localization")); }
        catch (IllegalArgumentException ignored) { localization = RobotLocalizationQuality.LOST; }
        driveReady = tag.getBoolean("DriveReady");
        emergencyStopClear = tag.getBoolean("EmergencyStopClear");
    }

    @Override public boolean isPickable() { return true; }
    @Override public boolean isPushable() { return false; }
}
