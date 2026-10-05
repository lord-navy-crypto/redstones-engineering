package dev.redstoneengineering.operations.world;

import dev.redstoneengineering.integration.OperationTransportBinding;
import dev.redstoneengineering.operations.OperationTransportDemand;
import net.minecraft.core.BlockPos;

/**
 * Durable Operations-side correlation for one AMR transport mission.
 *
 * <p>This record does not own robot routing, docking, loading, or unloading. It only persists the
 * Operations correlation that must survive world save/reload while Robotics remains the motion and
 * transport authority.</p>
 */
public record OperationTransportRuntimeRecord(
        long missionId,
        long outputId,
        long jobId,
        BlockPos source,
        BlockPos target,
        int units,
        int priority,
        long preparedTick,
        long stateTick,
        Status status,
        String robotId
) {
    public enum Status {
        READY,
        STARTED,
        DELIVERED,
        FAILED,
        CANCELLED;

        public boolean terminal() {
            return this == DELIVERED || this == FAILED || this == CANCELLED;
        }
    }

    public OperationTransportRuntimeRecord {
        if (missionId < 0) throw new IllegalArgumentException("missionId must be non-negative");
        if (outputId < 0) throw new IllegalArgumentException("outputId must be non-negative");
        if (jobId < 0) throw new IllegalArgumentException("jobId must be non-negative");
        if (source == null) throw new IllegalArgumentException("source is required");
        if (target == null) throw new IllegalArgumentException("target is required");
        if (source.equals(target)) throw new IllegalArgumentException("source and target must differ");
        if (units <= 0) throw new IllegalArgumentException("units must be positive");
        if (priority < 0 || priority > 100) throw new IllegalArgumentException("priority must be in 0..100");
        if (preparedTick < 0) throw new IllegalArgumentException("preparedTick must be non-negative");
        if (stateTick < preparedTick) throw new IllegalArgumentException("stateTick must not precede preparedTick");
        if (status == null) throw new IllegalArgumentException("status is required");
        if (robotId != null) {
            robotId = robotId.trim();
            if (robotId.isBlank()) robotId = null;
        }
        if ((status == Status.STARTED || status == Status.DELIVERED) && robotId == null) {
            throw new IllegalArgumentException("started/delivered transport requires robot identity");
        }
    }

    public static OperationTransportRuntimeRecord ready(
            OperationTransportBinding binding,
            OperationTransportDemand demand,
            long gameTick
    ) {
        if (binding == null) throw new IllegalArgumentException("binding is required");
        if (demand == null) throw new IllegalArgumentException("demand is required");
        if (gameTick < 0) throw new IllegalArgumentException("gameTick must be non-negative");
        if (binding.missionId() != demand.missionId()
                || binding.outputId() != demand.outputId()
                || !binding.source().equals(demand.source())
                || !binding.target().equals(demand.target())
                || binding.units() != demand.units()) {
            throw new IllegalArgumentException("binding and demand correlation mismatch");
        }
        return new OperationTransportRuntimeRecord(
                binding.missionId(),
                binding.outputId(),
                binding.jobId(),
                binding.source(),
                binding.target(),
                binding.units(),
                demand.priority(),
                gameTick,
                gameTick,
                Status.READY,
                null
        );
    }

    public OperationTransportBinding binding() {
        return new OperationTransportBinding(missionId, outputId, jobId, source, target, units);
    }

    public OperationTransportDemand demand() {
        return new OperationTransportDemand(missionId, outputId, source, target, units, priority);
    }

    public OperationTransportRuntimeRecord transition(Status nextStatus, long gameTick, String nextRobotId) {
        if (nextStatus == null) throw new IllegalArgumentException("nextStatus is required");
        if (gameTick < stateTick) throw new IllegalArgumentException("gameTick must not move backward");
        if (status.terminal()) {
            if (status == nextStatus && gameTick == stateTick) return this;
            throw new IllegalArgumentException("terminal transport cannot transition");
        }

        boolean legal = switch (status) {
            case READY -> nextStatus == Status.STARTED || nextStatus == Status.FAILED || nextStatus == Status.CANCELLED;
            case STARTED -> nextStatus == Status.DELIVERED || nextStatus == Status.FAILED || nextStatus == Status.CANCELLED;
            case DELIVERED, FAILED, CANCELLED -> false;
        };
        if (!legal) throw new IllegalArgumentException("illegal transport transition " + status + " -> " + nextStatus);

        String resolvedRobotId = nextRobotId;
        if (resolvedRobotId == null || resolvedRobotId.isBlank()) resolvedRobotId = robotId;
        return new OperationTransportRuntimeRecord(
                missionId,
                outputId,
                jobId,
                source,
                target,
                units,
                priority,
                preparedTick,
                gameTick,
                nextStatus,
                resolvedRobotId
        );
    }

    public boolean terminal() {
        return status.terminal();
    }
}
