package dev.redstoneengineering.signal;

/**
 * Lumped mechanical run-up/coast-down dynamics for a vibration exciter.
 */
public final class MechanicalExciterLogic {
    public static final int MIN_AMPLITUDE = 0;
    public static final int MAX_AMPLITUDE = 15;
    public static final int MIN_CONFIGURED_FREQUENCY = 1;
    public static final int MAX_CONFIGURED_FREQUENCY = 15;
    public static final int DEFAULT_CONFIGURED_FREQUENCY = 8;
    public static final int MIN_RATE = 1;
    public static final int MAX_RATE = 15;
    public static final int DEFAULT_AMPLITUDE_RISE = 2;
    public static final int DEFAULT_AMPLITUDE_FALL = 1;
    public static final int DEFAULT_FREQUENCY_SLEW = 1;
    public static final int CONTROL_TICK_TICKS = 1;

    private MechanicalExciterLogic() {}

    public static int boundedAmplitude(int amplitude) {
        return Math.max(MIN_AMPLITUDE, Math.min(MAX_AMPLITUDE, amplitude));
    }

    public static int boundedRuntimeFrequency(int frequency) {
        return Math.max(0, Math.min(MAX_CONFIGURED_FREQUENCY, frequency));
    }

    public static int boundedConfiguredFrequency(int frequency) {
        return Math.max(MIN_CONFIGURED_FREQUENCY, Math.min(MAX_CONFIGURED_FREQUENCY, frequency));
    }

    public static int boundedRate(int rate) {
        return Math.max(MIN_RATE, Math.min(MAX_RATE, rate));
    }

    public static int fullScaleRampTicks(int rate) {
        int bounded = boundedRate(rate);
        return (MAX_AMPLITUDE + bounded - 1) / bounded;
    }

    public record State(int amplitude, int frequency) {
        public State {
            amplitude = boundedAmplitude(amplitude);
            frequency = boundedRuntimeFrequency(frequency);
        }
    }

    public static State step(int commandedAmplitude, int targetFrequency, State previous) {
        return stepWithRates(
                commandedAmplitude, targetFrequency, previous,
                DEFAULT_AMPLITUDE_RISE, DEFAULT_AMPLITUDE_FALL, DEFAULT_FREQUENCY_SLEW);
    }

    public static State stepWithRates(
            int commandedAmplitude, int targetFrequency, State previous,
            int amplitudeRise, int amplitudeFall, int frequencySlew
    ) {
        State prior = previous == null ? new State(0, 0) : previous;
        int targetAmp = boundedAmplitude(commandedAmplitude);
        int targetFreq = targetAmp > 0 ? boundedConfiguredFrequency(targetFrequency) : 0;

        int amplitude = approachAsymmetric(
                prior.amplitude(), targetAmp, boundedRate(amplitudeRise), boundedRate(amplitudeFall));
        int frequency = approach(
                prior.frequency(), targetFreq, boundedRate(frequencySlew));

        // While coasting with non-zero mechanical energy, preserve a non-zero carrier frequency.
        if (amplitude > 0 && frequency == 0) frequency = 1;
        if (amplitude == 0 && targetAmp == 0) frequency = 0;
        return new State(amplitude, frequency);
    }

    public static boolean settled(int commandedAmplitude, int targetFrequency, State state) {
        int targetAmp = boundedAmplitude(commandedAmplitude);
        int targetFreq = targetAmp > 0 ? boundedConfiguredFrequency(targetFrequency) : 0;
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
