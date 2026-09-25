package dev.redstoneengineering.signal;

/**
 * Pure time-current model for the copper fuse.
 *
 * Thermal exposure is a bounded I²t-style proxy. Overcurrent adds heat proportional to the
 * square of current/rating above unity; safe current cools the element. Large faults therefore
 * trip much faster than modest overloads without requiring a full thermal simulation.
 */
public final class CopperFuseLogic {
    public static final int MIN_RATING = 1;
    public static final int MAX_RATING = 15;
    public static final int DEFAULT_RATING = 4;
    public static final int MIN_TIME_CURRENT_CLASS = 0;
    public static final int MAX_TIME_CURRENT_CLASS = 2;
    public static final int FAST_CLASS = 0;
    public static final int NORMAL_CLASS = 1;
    public static final int SLOW_CLASS = 2;
    public static final int DEFAULT_TIME_CURRENT_CLASS = NORMAL_CLASS;
    public static final int TRIP_THRESHOLD = 1000;

    private CopperFuseLogic() {}

    public static int boundedRating(int rating) {
        return Math.max(MIN_RATING, Math.min(MAX_RATING, rating));
    }

    public static int boundedTimeCurrentClass(int timeCurrentClass) {
        return Math.max(MIN_TIME_CURRENT_CLASS, Math.min(MAX_TIME_CURRENT_CLASS, timeCurrentClass));
    }

    public static double timeCurrentClassFactor(int timeCurrentClass) {
        return switch (boundedTimeCurrentClass(timeCurrentClass)) {
            case FAST_CLASS -> 1.50;
            case SLOW_CLASS -> 0.65;
            default -> 1.00;
        };
    }

    public static int tripThreshold() {
        return TRIP_THRESHOLD;
    }

    public static double currentRatio(double current, int rating) {
        return Math.max(0.0, current) / boundedRating(rating);
    }

    public static int nextThermal(int thermalExposure, double current, int rating) {
        return nextThermal(
                thermalExposure, current, rating, DEFAULT_TIME_CURRENT_CLASS);
    }

    /**
     * Time-current class: 0=FAST, 1=NORMAL, 2=SLOW.
     * The class changes overload heating rate while preserving the same rated-current threshold.
     */
    public static int nextThermal(int thermalExposure, double current, int rating, int timeCurrentClass) {
        int thermal = Math.max(0, Math.min(TRIP_THRESHOLD, thermalExposure));
        double ratio = currentRatio(current, rating);
        if (ratio > 1.0) {
            double classFactor = timeCurrentClassFactor(timeCurrentClass);
            int heating = (int) Math.ceil((ratio * ratio - 1.0) * 50.0 * classFactor);
            return Math.min(TRIP_THRESHOLD, thermal + Math.max(1, heating));
        }

        int cooling = 10 + (int) Math.ceil((1.0 - ratio) * 20.0);
        return Math.max(0, thermal - cooling);
    }

    public static int tripProgressPermille(int thermalExposure) {
        int thermal = Math.max(0, Math.min(TRIP_THRESHOLD, thermalExposure));
        return (int) Math.round(thermal * 1000.0 / TRIP_THRESHOLD);
    }
}
