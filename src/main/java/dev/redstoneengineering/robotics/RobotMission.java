package dev.redstoneengineering.robotics;

import net.minecraft.core.BlockPos;

/** Immutable mission assignment consumed by a mobile robot runtime. */
public record RobotMission(
        long missionId,
        MissionType type,
        BlockPos source,
        BlockPos target,
        int priority,
        int payloadUnits
) {
    public enum MissionType {
        DELIVERY,
        TRANSFER,
        INSPECTION,
        RETURN_HOME
    }

    public RobotMission {
        if (missionId < 0) throw new IllegalArgumentException("missionId must be non-negative");
        if (type == null) throw new IllegalArgumentException("type is required");
        if (target == null) throw new IllegalArgumentException("target is required");
        priority = Math.max(0, Math.min(100, priority));
        payloadUnits = Math.max(0, payloadUnits);
    }

    public String compact() {
        return "MISSION #" + missionId + " " + type
                + " priority=" + priority
                + " payload=" + payloadUnits
                + " target=" + target.toShortString();
    }
}
