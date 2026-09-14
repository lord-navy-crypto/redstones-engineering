package dev.redstoneengineering.robotics;

/**
 * Explicit AMR operating lifecycle. Robotics state is intentionally separate
 * from generic entity AI so missions, safety and diagnostics stay inspectable.
 */
public enum RobotOperatingState {
    IDLE,
    MISSION_ASSIGNED,
    PLANNING,
    NAVIGATING,
    WAITING,
    REPLANNING,
    DOCKING,
    LOADING,
    TRANSPORTING,
    UNLOADING,
    RETURNING,
    COMPLETE,
    DEGRADED,
    SAFE_STOP,
    FAULT;

    public boolean motionCapable() {
        return switch (this) {
            case NAVIGATING, REPLANNING, DOCKING, TRANSPORTING, RETURNING, DEGRADED -> true;
            default -> false;
        };
    }

    public boolean terminal() {
        return this == COMPLETE || this == FAULT;
    }
}
