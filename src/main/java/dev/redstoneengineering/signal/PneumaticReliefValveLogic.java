package dev.redstoneengineering.signal;

/**
 * Hysteretic safety-relief decision with a finite blowdown band.
 */
public final class PneumaticReliefValveLogic {
    private PneumaticReliefValveLogic() {}

    public static int reseatPressure(int setpoint, int blowdown) {
        return Math.max(0, Math.min(100, setpoint) - Math.max(0, blowdown));
    }

    public static boolean shouldVent(
            int pressure,
            int setpoint,
            int blowdown,
            boolean currentlyVenting
    ) {
        int boundedPressure = Math.max(0, Math.min(100, pressure));
        int boundedSetpoint = Math.max(0, Math.min(100, setpoint));
        if (currentlyVenting) {
            return boundedPressure > reseatPressure(boundedSetpoint, blowdown);
        }
        return boundedPressure > boundedSetpoint;
    }
}
