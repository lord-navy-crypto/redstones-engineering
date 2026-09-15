package dev.redstoneengineering.operations.world;

/**
 * Durable plant-wide event used for queue, quality, maintenance, delivery and logistics history.
 * A job id of {@code -1} means the event is not tied to one specific job.
 */
public record OperationPlantEvent(
        long sequence,
        long gameTick,
        Type type,
        String subjectId,
        long jobId,
        String detail
) {
    public enum Type {
        JOB,
        QUEUE,
        QUALITY,
        MAINTENANCE,
        DELIVERY,
        LOGISTICS
    }

    public OperationPlantEvent {
        if (sequence < 0) throw new IllegalArgumentException("sequence must be non-negative");
        if (gameTick < 0) throw new IllegalArgumentException("gameTick must be non-negative");
        if (type == null) throw new IllegalArgumentException("type is required");
        subjectId = subjectId == null ? "" : subjectId.trim();
        if (jobId < -1) throw new IllegalArgumentException("jobId must be -1 or non-negative");
        detail = detail == null ? "" : detail.trim();
    }
}
