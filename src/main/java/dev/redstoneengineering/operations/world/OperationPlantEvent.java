package dev.redstoneengineering.operations.world;

/** Append-only plant evidence event. Optional numeric identities use -1. */
public record OperationPlantEvent(
        long sequence,
        long tick,
        Type type,
        long jobId,
        long outputId,
        long missionId,
        String resourceId,
        String detail
) {
    public enum Type {
        JOB_RELEASED,
        JOB_QUEUED,
        JOB_ASSIGNED,
        JOB_STARTED,
        QUALITY_HOLD,
        QUALITY_INSPECTED,
        QUALITY_GOOD,
        QUALITY_REJECTED,
        REWORK_REQUESTED,
        REWORK_RELEASED,
        TRANSPORT_REQUESTED,
        AMR_ASSIGNED,
        AMR_PICKED_UP,
        AMR_ARRIVED,
        AMR_UNLOADED,
        DOWNSTREAM_RECEIVED,
        JOB_COMPLETED,
        DELIVERY_ON_TIME,
        DELIVERY_LATE,
        MAINTENANCE_DUE,
        MAINTENANCE_STARTED,
        MAINTENANCE_FAULT,
        MAINTENANCE_COMPLETED
    }

    public OperationPlantEvent {
        if (sequence < 0) throw new IllegalArgumentException("sequence must be non-negative");
        if (tick < 0) throw new IllegalArgumentException("tick must be non-negative");
        if (type == null) throw new IllegalArgumentException("type is required");
        if (jobId < -1 || outputId < -1 || missionId < -1) {
            throw new IllegalArgumentException("optional ids must be -1 or non-negative");
        }
        resourceId = resourceId == null ? "" : resourceId.trim();
        detail = detail == null ? "" : detail.trim();
    }
}
