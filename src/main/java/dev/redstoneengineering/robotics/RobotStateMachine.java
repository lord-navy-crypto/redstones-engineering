package dev.redstoneengineering.robotics;

/** Pure deterministic AMR lifecycle transition contract. */
public final class RobotStateMachine {
    private RobotStateMachine() {}

    public enum Event {
        ASSIGN_MISSION,
        BEGIN_PLANNING,
        ROUTE_READY,
        OBSTACLE_DETECTED,
        OBSTACLE_CLEARED,
        ROUTE_UNAVAILABLE,
        REPLAN_READY,
        ARRIVE_DOCK,
        DOCKED,
        LOAD_COMPLETE,
        ARRIVE_TARGET,
        UNLOAD_COMPLETE,
        RETURN_REQUESTED,
        ARRIVE_HOME,
        SENSOR_DEGRADED,
        EVIDENCE_RECOVERED,
        LOCALIZATION_LOST,
        SAFETY_STOP_REQUESTED,
        SAFE_CONDITION_RESTORED,
        CRITICAL_FAULT,
        RESET_FAULT
    }

    public static RobotOperatingState next(RobotOperatingState current, Event event) {
        if (current == null || event == null) return RobotOperatingState.FAULT;
        if (event == Event.CRITICAL_FAULT) return RobotOperatingState.FAULT;
        if (event == Event.LOCALIZATION_LOST || event == Event.SAFETY_STOP_REQUESTED) {
            return RobotOperatingState.SAFE_STOP;
        }
        if (event == Event.SENSOR_DEGRADED) return RobotOperatingState.DEGRADED;

        return switch (current) {
            case IDLE -> event == Event.ASSIGN_MISSION ? RobotOperatingState.MISSION_ASSIGNED : current;
            case MISSION_ASSIGNED -> event == Event.BEGIN_PLANNING ? RobotOperatingState.PLANNING : current;
            case PLANNING -> switch (event) {
                case ROUTE_READY -> RobotOperatingState.NAVIGATING;
                case ROUTE_UNAVAILABLE -> RobotOperatingState.REPLANNING;
                default -> current;
            };
            case NAVIGATING -> switch (event) {
                case OBSTACLE_DETECTED -> RobotOperatingState.WAITING;
                case ROUTE_UNAVAILABLE -> RobotOperatingState.REPLANNING;
                case ARRIVE_DOCK -> RobotOperatingState.DOCKING;
                default -> current;
            };
            case WAITING -> switch (event) {
                case OBSTACLE_CLEARED -> RobotOperatingState.NAVIGATING;
                case ROUTE_UNAVAILABLE -> RobotOperatingState.REPLANNING;
                default -> current;
            };
            case REPLANNING -> event == Event.REPLAN_READY ? RobotOperatingState.NAVIGATING : current;
            case DOCKING -> event == Event.DOCKED ? RobotOperatingState.LOADING : current;
            case LOADING -> event == Event.LOAD_COMPLETE ? RobotOperatingState.TRANSPORTING : current;
            case TRANSPORTING -> switch (event) {
                case OBSTACLE_DETECTED -> RobotOperatingState.TRANSPORT_WAITING;
                case ROUTE_UNAVAILABLE -> RobotOperatingState.TRANSPORT_REPLANNING;
                case ARRIVE_TARGET -> RobotOperatingState.UNLOADING;
                default -> current;
            };
            case TRANSPORT_WAITING -> switch (event) {
                case OBSTACLE_CLEARED -> RobotOperatingState.TRANSPORTING;
                case ROUTE_UNAVAILABLE -> RobotOperatingState.TRANSPORT_REPLANNING;
                default -> current;
            };
            case TRANSPORT_REPLANNING -> event == Event.REPLAN_READY ? RobotOperatingState.TRANSPORTING : current;
            case UNLOADING -> event == Event.UNLOAD_COMPLETE ? RobotOperatingState.COMPLETE : current;
            case COMPLETE -> event == Event.RETURN_REQUESTED ? RobotOperatingState.RETURNING : current;
            case RETURNING -> switch (event) {
                case ARRIVE_HOME -> RobotOperatingState.IDLE;
                case OBSTACLE_DETECTED -> RobotOperatingState.WAITING;
                default -> current;
            };
            case DEGRADED -> event == Event.EVIDENCE_RECOVERED ? RobotOperatingState.REPLANNING : current;
            case SAFE_STOP -> event == Event.SAFE_CONDITION_RESTORED ? RobotOperatingState.REPLANNING : current;
            case FAULT -> event == Event.RESET_FAULT ? RobotOperatingState.IDLE : current;
        };
    }
}
