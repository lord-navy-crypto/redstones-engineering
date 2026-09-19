package dev.redstoneengineering.signal;

/** Pure finite-rate supply-pressure dynamics for the pneumatic air compressor. */
public final class AirCompressorLogic {
    private AirCompressorLogic() {}

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
        int actual = Math.max(0, Math.min(100, actualPressure));
        int target = Math.max(0, Math.min(100, targetPressure));
        if (target > actual) return Math.min(target, actual + rampUpRate(responseMode));
        if (target < actual) return Math.max(target, actual - rampDownRate(responseMode));
        return actual;
    }

    /** Signed target-minus-actual pressure error. */
    public static int trackingError(int actualPressure, int targetPressure) {
        return Math.max(0, Math.min(100, targetPressure))
                - Math.max(0, Math.min(100, actualPressure));
    }

    public static String modeName(int responseMode) {
        return switch (Math.max(0, Math.min(2, responseMode))) {
            case 0 -> "SOFT";
            case 1 -> "NORMAL";
            default -> "FAST";
        };
    }
}
