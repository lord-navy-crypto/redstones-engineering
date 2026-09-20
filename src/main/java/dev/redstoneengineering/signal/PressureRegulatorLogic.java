package dev.redstoneengineering.signal;

/** Pure finite-response pressure regulator model. */
public final class PressureRegulatorLogic {
    private PressureRegulatorLogic() {}

    public static int responseRate(int mode) {
        return switch (Math.max(0, Math.min(2, mode))) {
            case 0 -> 4;
            case 1 -> 8;
            default -> 16;
        };
    }

    public static int targetPressure(int inletPressure, int setpointPressure) {
        int inlet = Math.max(0, Math.min(100, inletPressure));
        int setpoint = Math.max(0, Math.min(100, setpointPressure));
        return Math.min(inlet, setpoint);
    }

    public static int stepPressure(int actualPressure, int inletPressure, int setpointPressure, int mode) {
        return stepPressureRate(actualPressure, inletPressure, setpointPressure, responseRate(mode));
    }

    public static int stepPressureRate(int actualPressure, int inletPressure, int setpointPressure, int responseRate) {
        int actual = Math.max(0, Math.min(100, actualPressure));
        int target = targetPressure(inletPressure, setpointPressure);
        int rate = Math.max(1, Math.min(100, responseRate));
        if (target > actual) return Math.min(target, actual + rate);
        if (target < actual) return Math.max(target, actual - rate);
        return actual;
    }

    public static int trackingError(int actualPressure, int inletPressure, int setpointPressure) {
        return targetPressure(inletPressure, setpointPressure)
                - Math.max(0, Math.min(100, actualPressure));
    }

    public static String modeName(int mode) {
        return switch (Math.max(0, Math.min(2, mode))) {
            case 0 -> "SOFT";
            case 1 -> "NORMAL";
            default -> "FAST";
        };
    }
}
