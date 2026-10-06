package dev.redstoneengineering.diagnostics;

import java.util.Objects;

/**
 * Deterministic baseline-versus-candidate internal-reference calibration comparison.
 *
 * Error-to-reference is the primary result. Clipping, span, then mean step are secondary
 * repeatability/stability evidence. No absolute compliance tolerance is invented here.
 */
public record SignalCalibrationTrialComparison(
        long baselineSequence,
        long candidateSequence,
        Trend trend,
        boolean comparable,
        int errorDelta100,
        int clippingDelta,
        int spanDelta,
        int meanStepDelta100,
        int calibrationOffsetDelta
) {
    public enum Trend {
        IMPROVED,
        SAME,
        REGRESSED,
        INCOMPARABLE
    }

    public SignalCalibrationTrialComparison {
        if (baselineSequence < 1 || candidateSequence < 1) {
            throw new IllegalArgumentException("record sequences must be >= 1");
        }
        Objects.requireNonNull(trend, "trend");
        if (!comparable && trend != Trend.INCOMPARABLE) {
            throw new IllegalArgumentException("non-comparable calibration trials must be INCOMPARABLE");
        }
    }

    public static SignalCalibrationTrialComparison between(
            SignalCalibrationTrialRecord baseline,
            SignalCalibrationTrialRecord candidate
    ) {
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(candidate, "candidate");

        boolean comparable = baseline.reference() == candidate.reference()
                && baseline.mode() == candidate.mode()
                && baseline.facingOrdinal() == candidate.facingOrdinal();

        int errorDelta = candidate.absoluteError100() - baseline.absoluteError100();
        int clippingDelta = candidate.clippingSamples() - baseline.clippingSamples();
        int spanDelta = candidate.calibratedSpan() - baseline.calibratedSpan();
        int stepDelta = candidate.calibratedMeanStep100() - baseline.calibratedMeanStep100();
        int offsetDelta = candidate.calibrationOffset() - baseline.calibrationOffset();

        Trend trend = comparable
                ? classify(errorDelta, clippingDelta, spanDelta, stepDelta)
                : Trend.INCOMPARABLE;

        return new SignalCalibrationTrialComparison(
                baseline.sequence(),
                candidate.sequence(),
                trend,
                comparable,
                errorDelta,
                clippingDelta,
                spanDelta,
                stepDelta,
                offsetDelta
        );
    }

    private static Trend classify(int errorDelta, int clippingDelta, int spanDelta, int stepDelta) {
        if (errorDelta != 0) return errorDelta < 0 ? Trend.IMPROVED : Trend.REGRESSED;
        if (clippingDelta != 0) return clippingDelta < 0 ? Trend.IMPROVED : Trend.REGRESSED;
        if (spanDelta != 0) return spanDelta < 0 ? Trend.IMPROVED : Trend.REGRESSED;
        if (stepDelta != 0) return stepDelta < 0 ? Trend.IMPROVED : Trend.REGRESSED;
        return Trend.SAME;
    }

    public String compact() {
        return "#" + baselineSequence + "→#" + candidateSequence
                + " " + trend
                + " Δ|error|=" + decimal100(errorDelta100)
                + " Δclip=" + signed(clippingDelta)
                + " Δspan=" + signed(spanDelta)
                + " ΔmeanStep=" + decimal100(meanStepDelta100)
                + " Δcal=" + signed(calibrationOffsetDelta);
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    private static String decimal100(int value) {
        int abs = Math.abs(value);
        return (value < 0 ? "-" : "") + (abs / 100) + "." + String.format("%02d", abs % 100);
    }
}
