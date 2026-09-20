package dev.redstoneengineering.signal;

/**
 * Lumped mechanical run-up/coast-down dynamics for a vibration exciter.
 */
public final class MechanicalExciterLogic {
    private MechanicalExciterLogic() {}

    public record State(int amplitude, int frequency) {
        public State {
            amplitude = clamp(amplitude, 0, 15);
            frequency = clamp(frequency, 0, 15);
        }
    }

    public static State step(int commandedAmplitude, int targetFrequency, State previous) {
        State prior = previous == null ? new State(0, 0) : previous;
        int targetAmp = clamp(commandedAmplitude, 0, 15);
        int targetFreq = targetAmp > 0 ? clamp(targetFrequency, 1, 15) : 0;

        int amplitude = approachAsymmetric(prior.amplitude(), targetAmp, 2, 1);
        int frequency = approach(prior.frequency(), targetFreq, 1);

        // While coasting with non-zero mechanical energy, preserve a non-zero carrier frequency.
        if (amplitude > 0 && frequency == 0) frequency = 1;
        if (amplitude == 0 && targetAmp == 0) frequency = 0;
        return new State(amplitude, frequency);
    }

    public static boolean settled(int commandedAmplitude, int targetFrequency, State state) {
        int targetAmp = clamp(commandedAmplitude, 0, 15);
        int targetFreq = targetAmp > 0 ? clamp(targetFrequency, 1, 15) : 0;
        return state != null && state.amplitude() == targetAmp && state.frequency() == targetFreq;
    }

    private static int approachAsymmetric(int value, int target, int rise, int fall) {
        if (value < target) return Math.min(target, value + Math.max(1, rise));
        if (value > target) return Math.max(target, value - Math.max(1, fall));
        return value;
    }

    private static int approach(int value, int target, int step) {
        int s = Math.max(1, step);
        if (value < target) return Math.min(target, value + s);
        if (value > target) return Math.max(target, value - s);
        return value;
    }

    private static int clamp(int value, int lo, int hi) {
        return Math.max(lo, Math.min(hi, value));
    }
}
