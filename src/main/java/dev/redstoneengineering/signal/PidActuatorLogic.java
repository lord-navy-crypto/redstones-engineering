package dev.redstoneengineering.signal;

/**
 * Pure controller-output dynamics shared by the Minecraft-facing PID block.
 *
 * <p>This intentionally models only engineering constraints that matter to the control loop:
 * finite actuator command slew and a filtered derivative of the measured process variable.
 * It is not a motor/valve physics simulator.</p>
 */
public final class PidActuatorLogic {
    private PidActuatorLogic() {}

    public record SlewResult(int output, boolean limited) {}

    public static SlewResult slew(int current, int target, int riseLimit, int fallLimit) {
        int now = clamp(current, 0, 15);
        int requested = clamp(target, 0, 15);
        int rise = Math.max(1, riseLimit);
        int fall = Math.max(1, fallLimit);

        int next;
        if (requested > now) next = Math.min(requested, now + rise);
        else if (requested < now) next = Math.max(requested, now - fall);
        else next = now;
        return new SlewResult(next, next != requested);
    }

    /**
     * First-order filtering of d(PV)/dt. PID control uses the negative of this term,
     * which avoids the classic derivative kick caused only by a setpoint step.
     */
    public static int filteredMeasurementDerivative(
            int previousProcess,
            int currentProcess,
            int previousFilteredDerivative,
            int smoothing
    ) {
        int raw = currentProcess - previousProcess;
        int divisor = Math.max(1, smoothing);
        return previousFilteredDerivative + (raw - previousFilteredDerivative) / divisor;
    }

    private static int clamp(int value, int lo, int hi) {
        return Math.max(lo, Math.min(hi, value));
    }
}
