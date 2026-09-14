package dev.redstoneengineering.robotics;

/**
 * Explicit AMR operating lifecycle. Robotics state is intentionally separate
 * from generic entity AI so missions, safety and diagnostics stay inspectable.
 *
 * Keep the original persisted states in their historical declaration order:
 * EngineeringMobileRobotEntity stores RobotState by ordinal in NBT. New states
 * therefore append after FAULT so existing worlds retain their meaning.
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
    FAULT,
    TRANSPORT_WAITING,
    TRANSPORT_REPLANNING;

    public boolean motionCapable() {
        return switch (this) {
            case NAVIGATING, REPLANNING, DOCKING, TRANSPORTING, TRANSPORT_REPLANNING, RETURNING, DEGRADED -> true;
            default -> false;
        };
    }

    public boolean terminal() {
        return this == COMPLETE || this == FAULT;
    }
}
