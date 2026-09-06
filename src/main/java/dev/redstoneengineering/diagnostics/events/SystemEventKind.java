package dev.redstoneengineering.diagnostics.events;

/** Stable event vocabulary for system-wide operational evidence. */
public enum SystemEventKind {
    ALARM_RAISED(true),
    ALARM_ACKNOWLEDGED(false),
    ALARM_CLEARED(false),
    INTERLOCK_TRIPPED(true),
    INTERLOCK_READY(false),
    SEQUENCE_STARTED(false),
    SEQUENCE_STEP(false),
    SEQUENCE_COMPLETED(false),
    SEQUENCE_RESET(false),
    TOPOLOGY_ISSUE(true),
    TOPOLOGY_CLEAR(false),
    OPERATIONS_STATE_CHANGED(false);

    private final boolean abnormal;

    SystemEventKind(boolean abnormal) {
        this.abnormal = abnormal;
    }

    public boolean abnormal() {
        return abnormal;
    }
}
