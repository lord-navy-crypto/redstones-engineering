package dev.redstoneengineering.signal;

/**
 * Hysteretic safety-relief decision with a finite blowdown band.
 */
public final class PneumaticReliefValveLogic {
    public static final int MIN_PRESSURE = 0;
    public static final int MAX_PRESSURE = 100;
    public static final int MIN_CONFIGURED_SETPOINT = 1;
    public static final int MAX_CONFIGURED_SETPOINT = 100;
    public static final int MIN_BLOWDOWN = 1;
    public static final int MAX_BLOWDOWN = 25;
    public static final int DEFAULT_BLOWDOWN = 5;

    private PneumaticReliefValveLogic() {}

    public static int boundedPressure(int pressure) {
        return Math.max(MIN_PRESSURE, Math.min(MAX_PRESSURE, pressure));
    }

    public static int boundedConfiguredSetpoint(int setpoint) {
        return Math.max(MIN_CONFIGURED_SETPOINT, Math.min(MAX_CONFIGURED_SETPOINT, setpoint));
    }

    public static int boundedConfiguredBlowdown(int setpoint, int blowdown) {
        int boundedSetpoint = boundedConfiguredSetpoint(setpoint);
        int max = Math.min(MAX_BLOWDOWN, boundedSetpoint);
        return Math.max(MIN_BLOWDOWN, Math.min(max, blowdown));
    }

    public static int reseatPressure(int setpoint, int blowdown) {
        return Math.max(MIN_PRESSURE, boundedPressure(setpoint) - Math.max(0, blowdown));
    }

    public static boolean shouldVent(
            int pressure,
            int setpoint,
            int blowdown,
            boolean currentlyVenting
    ) {
        int boundedPressure = boundedPressure(pressure);
        int boundedSetpoint = boundedPressure(setpoint);
        if (currentlyVenting) {
            return boundedPressure > reseatPressure(boundedSetpoint, blowdown);
        }
        return boundedPressure > boundedSetpoint;
    }
}
