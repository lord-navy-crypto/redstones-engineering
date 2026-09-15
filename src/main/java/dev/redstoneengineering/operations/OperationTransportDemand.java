package dev.redstoneengineering.operations;

import net.minecraft.core.BlockPos;

/**
 * Immutable logistics demand emitted by Industrial Operations after completed output is available.
 * It describes what must move and where; it does not choose a robot, route, dock, or safety action.
 */
public record OperationTransportDemand(
        long missionId,
        long outputId,
        BlockPos source,
        BlockPos target,
        int units,
        int priority
) {
    public OperationTransportDemand {
        if (missionId < 0) throw new IllegalArgumentException("missionId must be non-negative");
        if (outputId < 0) throw new IllegalArgumentException("outputId must be non-negative");
        if (source == null) throw new IllegalArgumentException("source is required");
        if (target == null) throw new IllegalArgumentException("target is required");
        if (units <= 0) throw new IllegalArgumentException("units must be positive");
        if (priority < 0 || priority > 100) {
            throw new IllegalArgumentException("priority must be in 0..100");
        }
    }
}
