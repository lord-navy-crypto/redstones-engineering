package dev.redstoneengineering.signal;

/**
 * Pure time-current model for the copper fuse.
 *
 * Thermal exposure is a bounded I²t-style proxy. Overcurrent adds heat proportional to the
 * square of current/rating above unity; safe current cools the element. Large faults therefore
 * trip much faster than modest overloads without requiring a full thermal simulation.
 */
public final class CopperFuseLogic {
    private static final int TRIP_THRESHOLD = 1000;

    private CopperFuseLogic() {}

    public static int tripThreshold() {
        return TRIP_THRESHOLD;
    }

    public static double currentRatio(double current, int rating) {
        return Math.max(0.0, current) / Math.max(1, rating);
    }

    public static int nextThermal(int thermalExposure, double current, int rating) {
        int thermal = Math.max(0, Math.min(TRIP_THRESHOLD, thermalExposure));
        double ratio = currentRatio(current, rating);
        if (ratio > 1.0) {
            int heating = (int) Math.ceil((ratio * ratio - 1.0) * 50.0);
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
