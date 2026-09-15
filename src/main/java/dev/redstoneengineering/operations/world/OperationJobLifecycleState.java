package dev.redstoneengineering.operations.world;

/** Durable plant-level lifecycle states for one Operations job. */
public enum OperationJobLifecycleState {
    RELEASED,
    QUEUED,
    ASSIGNED,
    RUNNING,
    QUALITY_HOLD,
    TRANSPORT,
    COMPLETED
}
