package dev.redstoneengineering.diagnostics;

import dev.redstoneengineering.core.port.PortQuality;

import java.util.Objects;

/**
 * Frozen internal-reference calibration evidence from one complete Signal Analyzer window.
 *
 * This is an RSE in-world comparison record. It is not a metrological traceability claim.
 */
public record SignalCalibrationTrialRecord(
        long sequence,
        long gameTick,
        int reference,
        int mode,
        int facingOrdinal,
        int calibrationOffset,
        int rawAverage100,
        int calibratedAverage100,
        int absoluteError100,
        int calibratedSpan,
        int calibratedMeanStep100,
        int clippingSamples,
        int windowCount,
        int validWindowCount,
        int sampleAgeTicks,
        PortQuality measurementQuality
) {
    public SignalCalibrationTrialRecord {
        if (sequence < 1) throw new IllegalArgumentException("sequence must be >= 1");
        if (gameTick < 0) throw new IllegalArgumentException("gameTick must be >= 0");
        if (reference < 0 || reference > 15) throw new IllegalArgumentException("reference must be 0..15");
        if (mode < 0 || mode > 1) throw new IllegalArgumentException("mode must be TAP/INLINE");
        if (facingOrdinal < 0 || facingOrdinal > 5) throw new IllegalArgumentException("facing ordinal must be 0..5");
        if (calibrationOffset < -2 || calibrationOffset > 2) throw new IllegalArgumentException("calibration offset must be -2..2");
        rawAverage100 = Math.max(0, Math.min(1500, rawAverage100));
        calibratedAverage100 = Math.max(0, Math.min(1500, calibratedAverage100));
        absoluteError100 = Math.max(0, absoluteError100);
        calibratedSpan = Math.max(0, Math.min(15, calibratedSpan));
        calibratedMeanStep100 = Math.max(0, calibratedMeanStep100);
        clippingSamples = Math.max(0, Math.min(16, clippingSamples));
        windowCount = Math.max(0, Math.min(16, windowCount));
        validWindowCount = Math.max(0, Math.min(windowCount, validWindowCount));
        Objects.requireNonNull(measurementQuality, "measurementQuality");
    }

    public boolean captureReady() {
        return windowCount == 16
                && validWindowCount == 16
                && sampleAgeTicks >= 0
                && sampleAgeTicks <= 4
                && measurementQuality == PortQuality.VALID;
    }

    public String compact() {
        return "#" + sequence
                + " ref=" + reference
                + " cal=" + signed(calibrationOffset)
                + " avg=" + decimal100(calibratedAverage100)
                + " |error|=" + decimal100(absoluteError100)
                + " span=" + calibratedSpan
                + " meanStep=" + decimal100(calibratedMeanStep100)
                + " clip=" + clippingSamples
                + " coverage=" + validWindowCount + "/" + windowCount;
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    private static String decimal100(int value) {
        int abs = Math.abs(value);
        return (value < 0 ? "-" : "") + (abs / 100) + "." + String.format("%02d", abs % 100);
    }
}
