package dev.redstoneengineering.signal;

/** Pure finite-response pressure regulator model. */
public final class PressureRegulatorLogic {
    public static final int MIN_PRESSURE = 0;
    public static final int MAX_PRESSURE = 100;
    public static final int MIN_CONFIGURED_SETPOINT = 1;
    public static final int MAX_CONFIGURED_SETPOINT = 100;
    public static final int MIN_RESPONSE_RATE = 1;
    public static final int MAX_RESPONSE_RATE = 100;

    private PressureRegulatorLogic() {}

    public static int boundedPressure(int pressure) {
        return Math.max(MIN_PRESSURE, Math.min(MAX_PRESSURE, pressure));
    }

    public static int boundedConfiguredSetpoint(int setpoint) {
        return Math.max(MIN_CONFIGURED_SETPOINT, Math.min(MAX_CONFIGURED_SETPOINT, setpoint));
    }

    public static int boundedResponseRate(int rate) {
        return Math.max(MIN_RESPONSE_RATE, Math.min(MAX_RESPONSE_RATE, rate));
    }

    public static int responseRate(int mode) {
        return switch (Math.max(0, Math.min(2, mode))) {
            case 0 -> 4;
            case 1 -> 8;
            default -> 16;
        };
    }

    public static int targetPressure(int inletPressure, int setpointPressure) {
        int inlet = boundedPressure(inletPressure);
        int setpoint = boundedPressure(setpointPressure);
        return Math.min(inlet, setpoint);
    }

    public static int stepPressure(int actualPressure, int inletPressure, int setpointPressure, int mode) {
        return stepPressureRate(actualPressure, inletPressure, setpointPressure, responseRate(mode));
    }

    public static int stepPressureRate(int actualPressure, int inletPressure, int setpointPressure, int responseRate) {
        int actual = boundedPressure(actualPressure);
        int target = targetPressure(inletPressure, setpointPressure);
        int rate = boundedResponseRate(responseRate);
        if (target > actual) return Math.min(target, actual + rate);
        if (target < actual) return Math.max(target, actual - rate);
        return actual;
    }

    public static int trackingError(int actualPressure, int inletPressure, int setpointPressure) {
        return targetPressure(inletPressure, setpointPressure)
                - boundedPressure(actualPressure);
    }

    public static String modeName(int mode) {
        return switch (Math.max(0, Math.min(2, mode))) {
            case 0 -> "SOFT";
            case 1 -> "NORMAL";
            default -> "FAST";
        };
    }
}
