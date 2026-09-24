package dev.redstoneengineering.signal;

/**
 * Reduced driven/free response for a tuned resonance element.
 *
 * <p>Higher Q narrows the frequency response, builds stored amplitude more slowly and releases
 * stored energy more slowly. While driven the resonator follows the forcing frequency; after the
 * drive disappears it rings freely at its natural frequency.</p>
 */
public final class AmethystTunedResonatorLogic {
    private AmethystTunedResonatorLogic() {}

    public record State(int amplitude, int frequency, boolean driven) {
        public State {
            amplitude = clamp(amplitude, 0, 15);
            frequency = clamp(frequency, 0, 15);
        }
    }

    /** Compatibility overload retaining the historical one-step free decay. */
    public static State step(
            int targetAmplitude,
            int driveFrequency,
            int naturalFrequency,
            int qIndex,
            boolean driven,
            State previous
    ) {
        return step(targetAmplitude, driveFrequency, naturalFrequency, qIndex, 1, driven, previous);
    }

    public static State step(
            int targetAmplitude,
            int driveFrequency,
            int naturalFrequency,
            int qIndex,
            int decayRate,
            boolean driven,
            State previous
    ) {
        State prior = previous == null ? new State(0, 0, false) : previous;
        int q = clamp(qIndex, 1, 4);
        int target = driven ? clamp(targetAmplitude, 0, 15) : 0;
        int responseStep = driven ? responseStep(q) : freeDecayStep(decayRate);

        int amplitude = approach(prior.amplitude(), target, responseStep);
        int frequency;
        if (amplitude <= 0) {
            frequency = 0;
        } else if (driven && target > 0) {
            frequency = clamp(driveFrequency, 1, 15);
        } else {
            frequency = clamp(naturalFrequency, 1, 15);
        }
        return new State(amplitude, frequency, driven && target > 0);
    }

    public static boolean settled(int targetAmplitude, int qIndex, boolean driven, State state) {
        if (state == null) return false;
        int target = driven ? clamp(targetAmplitude, 0, 15) : 0;
        return state.amplitude() == target;
    }

    public static int responseStep(int qIndex) {
        return Math.max(1, 5 - clamp(qIndex, 1, 4));
    }

    /**
     * Free ring-down is intentionally independent from Q so the player can study bandwidth
     * and stored-energy release as separate model variables.
     */
    public static int freeDecayStep(int decayRate) {
        return clamp(decayRate, 1, 4);
    }

    private static int approach(int value, int target, int step) {
        if (value < target) return Math.min(target, value + step);
        if (value > target) return Math.max(target, value - step);
        return value;
    }

    private static int clamp(int value, int lo, int hi) {
        return Math.max(lo, Math.min(hi, value));
    }
}
