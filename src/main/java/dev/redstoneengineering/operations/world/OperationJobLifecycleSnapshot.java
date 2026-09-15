package dev.redstoneengineering.operations.world;

import dev.redstoneengineering.operations.OperationJob;

/** Immutable durable projection of a plant job's current lifecycle state. */
public record OperationJobLifecycleSnapshot(
        OperationJob job,
        OperationJobLifecycleState state,
        String resourceId,
        long outputId,
        long missionId,
        long completionTick,
        DeliveryStatus deliveryStatus
) {
    public enum DeliveryStatus { NONE, ON_TIME, LATE }

    public OperationJobLifecycleSnapshot {
        if (job == null) throw new IllegalArgumentException("job is required");
        if (state == null) throw new IllegalArgumentException("state is required");
        resourceId = resourceId == null ? "" : resourceId.trim();
        if (outputId < -1) throw new IllegalArgumentException("outputId must be -1 or non-negative");
        if (missionId < -1) throw new IllegalArgumentException("missionId must be -1 or non-negative");
        if (completionTick < -1) throw new IllegalArgumentException("completionTick must be -1 or non-negative");
        if (deliveryStatus == null) deliveryStatus = DeliveryStatus.NONE;
    }

    public boolean completed() {
        return state == OperationJobLifecycleState.COMPLETED;
    }
}
