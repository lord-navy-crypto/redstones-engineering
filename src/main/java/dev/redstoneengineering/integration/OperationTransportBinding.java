package dev.redstoneengineering.integration;

import net.minecraft.core.BlockPos;

/** Immutable correlation evidence carried beside a Robotics mission across the Operations boundary. */
public record OperationTransportBinding(
        long missionId,
        long outputId,
        long jobId,
        BlockPos source,
        BlockPos target,
        int units
) {
    public OperationTransportBinding {
        if (missionId < 0) throw new IllegalArgumentException("missionId must be non-negative");
        if (outputId < 0) throw new IllegalArgumentException("outputId must be non-negative");
        if (jobId < 0) throw new IllegalArgumentException("jobId must be non-negative");
        if (source == null) throw new IllegalArgumentException("source is required");
        if (target == null) throw new IllegalArgumentException("target is required");
        if (units <= 0) throw new IllegalArgumentException("units must be positive");
    }
}
