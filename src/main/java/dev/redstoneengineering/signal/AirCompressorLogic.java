package dev.redstoneengineering.signal;

/** Pure finite-rate supply-pressure dynamics for the pneumatic air compressor. */
public final class AirCompressorLogic {
    public static final int MIN_PRESSURE = 0;
    public static final int MAX_PRESSURE = 100;
    public static final int MIN_RAMP_RATE = 1;
    public static final int MAX_RAMP_RATE = 100;

    private AirCompressorLogic() {}

    public static int boundedPressure(int pressure) {
        return Math.max(MIN_PRESSURE, Math.min(MAX_PRESSURE, pressure));
    }

    public static int boundedRampRate(int rate) {
        return Math.max(MIN_RAMP_RATE, Math.min(MAX_RAMP_RATE, rate));
    }

    public static int rampUpRate(int responseMode) {
        return switch (Math.max(0, Math.min(2, responseMode))) {
            case 0 -> 5;
            case 1 -> 10;
            default -> 20;
        };
    }

    public static int rampDownRate(int responseMode) {
        return switch (Math.max(0, Math.min(2, responseMode))) {
            case 0 -> 8;
            case 1 -> 15;
            default -> 25;
        };
    }

    public static int stepPressure(int actualPressure, int targetPressure, int responseMode) {
        return stepPressureRates(actualPressure, targetPressure,
                rampUpRate(responseMode), rampDownRate(responseMode));
    }

    public static int stepPressureRates(int actualPressure, int targetPressure, int upRate, int downRate) {
        int actual = boundedPressure(actualPressure);
        int target = boundedPressure(targetPressure);
        int up = boundedRampRate(upRate);
        int down = boundedRampRate(downRate);
        if (target > actual) return Math.min(target, actual + up);
        if (target < actual) return Math.max(target, actual - down);
        return actual;
    }

    /** Signed target-minus-actual pressure error. */
    public static int trackingError(int actualPressure, int targetPressure) {
        return boundedPressure(targetPressure) - boundedPressure(actualPressure);
    }

    public static String modeName(int responseMode) {
        return switch (Math.max(0, Math.min(2, responseMode))) {
            case 0 -> "SOFT";
            case 1 -> "NORMAL";
            default -> "FAST";
        };
    }
}
