package dev.redstoneengineering.entity;

import dev.redstoneengineering.robotics.RobotOperatingState;
import dev.redstoneengineering.robotics.RobotSafetyAssessment;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** First world-running RSE autonomous mobile robot entity. */
public final class EngineeringMobileRobotEntity extends Entity {
    private static final EntityDataAccessor<Integer> STATE =
            SynchedEntityData.defineId(EngineeringMobileRobotEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> HAS_TARGET =
            SynchedEntityData.defineId(EngineeringMobileRobotEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> TARGET_X =
            SynchedEntityData.defineId(EngineeringMobileRobotEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TARGET_Y =
            SynchedEntityData.defineId(EngineeringMobileRobotEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TARGET_Z =
            SynchedEntityData.defineId(EngineeringMobileRobotEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SAFETY =
            SynchedEntityData.defineId(EngineeringMobileRobotEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> SAFETY_REASON =
            SynchedEntityData.defineId(EngineeringMobileRobotEntity.class, EntityDataSerializers.STRING);

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

    public boolean hasMissionTarget() { return entityData.get(HAS_TARGET); }

    public BlockPos missionTarget() {
        return new BlockPos(entityData.get(TARGET_X), entityData.get(TARGET_Y), entityData.get(TARGET_Z));
    }

    public RobotSafetyAssessment.Verdict safetyVerdict() {
        RobotSafetyAssessment.Verdict[] values = RobotSafetyAssessment.Verdict.values();
        return values[Math.max(0, Math.min(values.length - 1, entityData.get(SAFETY)))];
    }

    public String safetyReason() { return entityData.get(SAFETY_REASON); }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("RobotState", robotState().ordinal());
        tag.putBoolean("HasTarget", hasMissionTarget());
        if (hasMissionTarget()) {
            BlockPos target = missionTarget();
            tag.putInt("TargetX", target.getX());
            tag.putInt("TargetY", target.getY());
            tag.putInt("TargetZ", target.getZ());
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        int ordinal = Math.max(0, Math.min(RobotOperatingState.values().length - 1, tag.getInt("RobotState")));
        entityData.set(STATE, ordinal);
        entityData.set(HAS_TARGET, tag.getBoolean("HasTarget"));
        entityData.set(TARGET_X, tag.getInt("TargetX"));
        entityData.set(TARGET_Y, tag.getInt("TargetY"));
        entityData.set(TARGET_Z, tag.getInt("TargetZ"));
    }
}
