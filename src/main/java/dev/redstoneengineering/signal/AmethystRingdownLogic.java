package dev.redstoneengineering.signal;

/**
 * Reduced free-decay model for a manually excited resonator.
 *
 * <p>The configured amplitude is the initial excitation magnitude. Once excited, the resonator
 * rings at its configured frequency while the retained amplitude decays toward zero. This is a
 * lumped gameplay abstraction of damped free vibration, not a continuous harmonic-oscillator
 * solver.</p>
 */
public final class AmethystRingdownLogic {
    private AmethystRingdownLogic() {}

    public static int excite(int configuredAmplitude) {
        return clamp(configuredAmplitude, 0, 15);
    }

    public static int decay(int currentAmplitude) {
        return Math.max(0, clamp(currentAmplitude, 0, 15) - 1);
    }

    public static boolean active(int currentAmplitude) {
        return clamp(currentAmplitude, 0, 15) > 0;
    }

    private static int clamp(int value, int lo, int hi) {
        return Math.max(lo, Math.min(hi, value));
    }
}
