package dev.redstoneengineering.robotics;

import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;

import java.util.Optional;

/**
 * Immutable operator/diagnostic projection for an AMR runtime.
 * Motion, planning and sensing implementations remain outside this record.
 */
public record MobileRobotSnapshot(
        long robotId,
        RobotOperatingState state,
        Optional<RobotMission> mission,
        BlockPos position,
        Optional<BlockPos> target,
        RobotLocalizationQuality localizationQuality,
        PortQuality frontRangeQuality,
        int frontRangeDeciblocks,
        int speedMilliBlocksPerTick,
        int payloadUnits,
        int payloadCapacity,
        RobotSafetyAssessment.Snapshot safety
) {
    public MobileRobotSnapshot {
        if (robotId < 0) throw new IllegalArgumentException("robotId must be non-negative");
        if (state == null) state = RobotOperatingState.FAULT;
        mission = mission == null ? Optional.empty() : mission;
        target = target == null ? Optional.empty() : target;
        if (localizationQuality == null) localizationQuality = RobotLocalizationQuality.LOST;
        if (frontRangeQuality == null) frontRangeQuality = PortQuality.NO_SIGNAL;
        frontRangeDeciblocks = Math.max(0, frontRangeDeciblocks);
        speedMilliBlocksPerTick = Math.max(0, speedMilliBlocksPerTick);
        payloadCapacity = Math.max(0, payloadCapacity);
        payloadUnits = Math.max(0, Math.min(payloadUnits, payloadCapacity));
        if (safety == null) safety = RobotSafetyAssessment.inspect(null);
    }

    public boolean missionActive() {
        return mission.isPresent() && !state.terminal();
    }

    public boolean motionPermitted() {
        return safety.motionPermit() && state.motionCapable();
    }

    public String compact() {
        return "AMR-" + robotId
                + " state=" + state
                + " mission=" + mission.map(m -> "#" + m.missionId()).orElse("NONE")
                + " target=" + target.map(BlockPos::toShortString).orElse("NONE")
                + " localization=" + localizationQuality
                + " frontRange=" + String.format(java.util.Locale.ROOT, "%.1f", frontRangeDeciblocks / 10.0) + "b/" + frontRangeQuality
                + " speed=" + String.format(java.util.Locale.ROOT, "%.3f", speedMilliBlocksPerTick / 1000.0) + "b/t"
                + " payload=" + payloadUnits + "/" + payloadCapacity
                + " safety=" + safety.verdict() + ":" + safety.primaryReason();
    }
}
