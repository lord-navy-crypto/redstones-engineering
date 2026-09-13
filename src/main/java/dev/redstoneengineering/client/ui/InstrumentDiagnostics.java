package dev.redstoneengineering.client.ui;

import java.util.function.IntUnaryOperator;

/**
 * Pure client-side analysis of samples that have already been synchronized by an instrument menu.
 *
 * This helper never reads the world and never invents samples. Invalid/out-of-range entries are
 * excluded explicitly so screens can distinguish evidence quality from signal behavior.
 */
public final class InstrumentDiagnostics {
    private InstrumentDiagnostics() {
    }

    public record Summary(
            int validSamples,
            int invalidSamples,
            int minimum,
            int maximum,
            int average100,
            int lastValid,
            int transitions,
            int longestRun
    ) {
        public int span() {
            return validSamples == 0 ? 0 : maximum - minimum;
        }

        public int coveragePercent() {
            int total = validSamples + invalidSamples;
            return total == 0 ? 0 : Math.round(validSamples * 100.0f / total);
        }
    }

    public static Summary summarize(
            int sampleCount,
            IntUnaryOperator sampleAt,
            int minimumAllowed,
            int maximumAllowed
    ) {
        int valid = 0;
        int invalid = 0;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        long sum = 0L;
        int last = minimumAllowed;
        int previous = Integer.MIN_VALUE;
        int transitions = 0;
        int run = 0;
        int longestRun = 0;

        for (int slot = 0; slot < Math.max(0, sampleCount); slot++) {
            int value = sampleAt.applyAsInt(slot);
            if (value < minimumAllowed || value > maximumAllowed) {
                invalid++;
                previous = Integer.MIN_VALUE;
                run = 0;
                continue;
            }

            valid++;
            min = Math.min(min, value);
            max = Math.max(max, value);
            sum += value;
            last = value;

            if (previous == Integer.MIN_VALUE) {
                run = 1;
            } else if (previous == value) {
                run++;
            } else {
                transitions++;
                run = 1;
            }
            longestRun = Math.max(longestRun, run);
            previous = value;
        }

        if (valid == 0) {
            return new Summary(0, invalid, 0, 0, 0, 0, 0, 0);
        }
        return new Summary(valid, invalid, min, max, (int) Math.round(sum * 100.0 / valid), last, transitions, longestRun);
    }

    public static String freshnessLabel(int ageTicks) {
        if (ageTicks < 0) return "NO SAMPLE";
        if (ageTicks <= 4) return "LIVE";
        if (ageTicks <= 20) return "RECENT";
        return "STALE";
    }

    public static int freshnessSeverity(int ageTicks) {
        if (ageTicks < 0 || ageTicks > 20) return 2;
        if (ageTicks > 4) return 1;
        return 0;
    }

    public static String analogDiagnosis(Summary summary) {
        if (summary.validSamples() == 0) return "NO TRUSTWORTHY SAMPLES";
        if (summary.coveragePercent() < 75) return "SAMPLE COVERAGE LOW";
        if (summary.span() == 0) return "STEADY / DC-LIKE";
        if (summary.span() <= 2) return "LOW VARIATION";
        if (summary.span() <= 7) return "DYNAMIC";
        return "WIDE EXCURSION";
    }

    public static String digitalDiagnosis(Summary summary) {
        if (summary.validSamples() == 0) return "NO TRUSTWORTHY SAMPLES";
        if (summary.coveragePercent() < 75) return "CHANNEL COVERAGE LOW";
        if (summary.transitions() == 0) return summary.lastValid() > 0 ? "STATIC HIGH" : "STATIC LOW";
        if (summary.longestRun() <= 2) return "HIGH TOGGLE RATE";
        return "ACTIVE DIGITAL";
    }
}
