package dev.redstoneengineering.signal;

/**
 * Lumped one-way check-valve pressure behavior.
 *
 * <p>Directionality is enforced by the pneumatic topology solver. This helper models only the
 * additional pressure required to unseat the valve and the resulting pressure drop.</p>
 */
public final class PneumaticCheckValveLogic {
    private PneumaticCheckValveLogic() {}

    public static int transmittedPressure(int upstreamPressure, int crackingPressure) {
        int upstream = clamp(upstreamPressure, 0, 100);
        int crack = clamp(crackingPressure, 0, 100);
        return Math.max(0, upstream - crack);
    }

    public static boolean crackedOpen(int upstreamPressure, int crackingPressure) {
        return transmittedPressure(upstreamPressure, crackingPressure) > 0;
    }

    private static int clamp(int value, int lo, int hi) {
        return Math.max(lo, Math.min(hi, value));
    }
}
