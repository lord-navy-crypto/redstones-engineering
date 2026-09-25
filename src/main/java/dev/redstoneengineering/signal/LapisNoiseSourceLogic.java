package dev.redstoneengineering.signal;

/** Pure configuration contract for the deterministic Lapis noise source. */
public final class LapisNoiseSourceLogic {
    public static final int MIN_BASELINE = 0;
    public static final int MAX_BASELINE = 100;
    public static final int DEFAULT_BASELINE = 50;

    public static final int MIN_NOISE_AMPLITUDE = 0;
    public static final int MAX_NOISE_AMPLITUDE = 50;
    public static final int DEFAULT_NOISE_AMPLITUDE = 6;

    public static final int MIN_SAMPLE_PERIOD_TICKS = 1;
    public static final int MAX_SAMPLE_PERIOD_TICKS = 64;
    public static final int DEFAULT_SAMPLE_PERIOD_TICKS = 4;

    public static final int MIN_LEGACY_BASELINE_INDEX = 0;
    public static final int MAX_LEGACY_BASELINE_INDEX = 20;
    public static final int DEFAULT_LEGACY_BASELINE_INDEX = 10;
    public static final int LEGACY_BASELINE_STEP = 5;

    public static final int MIN_LEGACY_NOISE_INDEX = 0;
    public static final int MAX_LEGACY_NOISE_INDEX = 10;
    public static final int DEFAULT_LEGACY_NOISE_INDEX = 3;
    public static final int LEGACY_NOISE_STEP = 2;

    public static final int MIN_LEGACY_RATE_INDEX = 0;
    public static final int MAX_LEGACY_RATE_INDEX = 3;
    public static final int DEFAULT_LEGACY_RATE_INDEX = 1;
    public static final int LEGACY_PERIOD_FAST = 2;
    public static final int LEGACY_PERIOD_MEDIUM = 4;
    public static final int LEGACY_PERIOD_SLOW = 8;
    public static final int LEGACY_PERIOD_DRIFT = 16;

    private LapisNoiseSourceLogic() {}

    public static int boundedBaseline(int value) {
        return Math.max(MIN_BASELINE, Math.min(MAX_BASELINE, value));
    }

    public static int boundedNoiseAmplitude(int value) {
        return Math.max(MIN_NOISE_AMPLITUDE, Math.min(MAX_NOISE_AMPLITUDE, value));
    }

    public static int boundedSamplePeriod(int value) {
        return Math.max(MIN_SAMPLE_PERIOD_TICKS, Math.min(MAX_SAMPLE_PERIOD_TICKS, value));
    }

    public static int boundedLegacyBaselineIndex(int value) {
        return Math.max(MIN_LEGACY_BASELINE_INDEX, Math.min(MAX_LEGACY_BASELINE_INDEX, value));
    }

    public static int boundedLegacyNoiseIndex(int value) {
        return Math.max(MIN_LEGACY_NOISE_INDEX, Math.min(MAX_LEGACY_NOISE_INDEX, value));
    }

    public static int boundedLegacyRateIndex(int value) {
        return Math.max(MIN_LEGACY_RATE_INDEX, Math.min(MAX_LEGACY_RATE_INDEX, value));
    }

    public static int baselineForLegacyIndex(int index) {
        return boundedLegacyBaselineIndex(index) * LEGACY_BASELINE_STEP;
    }

    public static int noiseForLegacyIndex(int index) {
        return boundedLegacyNoiseIndex(index) * LEGACY_NOISE_STEP;
    }

    public static int samplePeriodForLegacyRate(int index) {
        return switch (boundedLegacyRateIndex(index)) {
            case 0 -> LEGACY_PERIOD_FAST;
            case 1 -> LEGACY_PERIOD_MEDIUM;
            case 2 -> LEGACY_PERIOD_SLOW;
            default -> LEGACY_PERIOD_DRIFT;
        };
    }
}
