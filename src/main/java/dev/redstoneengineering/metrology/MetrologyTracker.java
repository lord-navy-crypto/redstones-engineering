package dev.redstoneengineering.metrology;

import java.util.Arrays;

/**
 * Fixed-memory rolling metrology estimator.
 *
 * Each sample carries both the instrument reading and a reference/expected
 * value when one is available. The residual stream (reading-reference) is used
 * to separate repeatability, bias and drift from ordinary process motion.
 */
public final class MetrologyTracker {
    public static final int WINDOW = 32;

    private final double[] readings = new double[WINDOW];
    private final double[] residuals = new double[WINDOW];
    private final long[] sampleTicks = new long[WINDOW];
    private final double resolution;
    private final long staleAfterTicks;

    private int head;
    private int count;
    private boolean saturated;
    private long lastSampleTick = Long.MIN_VALUE;

    public MetrologyTracker(double resolution, long staleAfterTicks) {
        if (!Double.isFinite(resolution) || resolution <= 0.0) {
            throw new IllegalArgumentException("resolution must be finite and > 0");
        }
        if (staleAfterTicks < 1) throw new IllegalArgumentException("staleAfterTicks must be >= 1");
        this.resolution = resolution;
        this.staleAfterTicks = staleAfterTicks;
        Arrays.fill(readings, Double.NaN);
        Arrays.fill(residuals, Double.NaN);
    }

    public synchronized MeasurementSnapshot sample(
            double reading,
            double reference,
            boolean sampleSaturated,
            long gameTick
    ) {
        if (!Double.isFinite(reading) || !Double.isFinite(reference)) {
            return snapshot(gameTick);
        }

        readings[head] = reading;
        residuals[head] = reading - reference;
        sampleTicks[head] = gameTick;
        head = (head + 1) % WINDOW;
        count = Math.min(WINDOW, count + 1);
        saturated = sampleSaturated;
        lastSampleTick = gameTick;
        return snapshot(gameTick);
    }

    public synchronized MeasurementSnapshot snapshot(long gameTick) {
        if (count == 0) return MeasurementSnapshot.invalid(resolution);

        double[] r = chronological(readings);
        double[] e = chronological(residuals);
        double reading = r[r.length - 1];
        double bias = mean(e);
        double repeatability = standardDeviation(e, bias);
        double noise = firstDifferenceNoise(e);
        double drift = halfWindowDrift(e);
        long age = Math.max(0L, gameTick - lastSampleTick);

        // Conservative diagnostic RSS. Bias is included as the observed
        // calibration residual contribution, but remains separately reported.
        double quantization = resolution / Math.sqrt(12.0);
        double uncertaintyProxy = Math.sqrt(
                repeatability * repeatability
                        + noise * noise
                        + drift * drift
                        + bias * bias
                        + quantization * quantization
        );

        MeasurementQuality quality;
        if (!Double.isFinite(reading) || !Double.isFinite(uncertaintyProxy)) {
            quality = MeasurementQuality.INVALID;
        } else if (age > staleAfterTicks) {
            quality = MeasurementQuality.STALE;
        } else if (saturated) {
            quality = MeasurementQuality.SATURATED;
        } else if (uncertaintyProxy > Math.max(1.0, resolution * 1.5)
                || Math.abs(drift) > Math.max(0.5, resolution)
                || Math.abs(bias) > Math.max(0.75, resolution)) {
            quality = MeasurementQuality.DEGRADED;
        } else {
            quality = MeasurementQuality.GOOD;
        }

        return new MeasurementSnapshot(
                reading,
                repeatability,
                bias,
                drift,
                noise,
                resolution,
                saturated,
                age,
                count,
                uncertaintyProxy,
                quality
        );
    }

    public synchronized void clear() {
        Arrays.fill(readings, Double.NaN);
        Arrays.fill(residuals, Double.NaN);
        Arrays.fill(sampleTicks, 0L);
        head = 0;
        count = 0;
        saturated = false;
        lastSampleTick = Long.MIN_VALUE;
    }

    private double[] chronological(double[] source) {
        double[] out = new double[count];
        int start = (head - count + WINDOW) % WINDOW;
        for (int i = 0; i < count; i++) out[i] = source[(start + i) % WINDOW];
        return out;
    }

    private static double mean(double[] values) {
        double sum = 0.0;
        for (double value : values) sum += value;
        return sum / values.length;
    }

    private static double standardDeviation(double[] values, double mean) {
        if (values.length < 2) return 0.0;
        double sumSquares = 0.0;
        for (double value : values) {
            double d = value - mean;
            sumSquares += d * d;
        }
        return Math.sqrt(sumSquares / (values.length - 1));
    }

    /** High-frequency residual variation proxy, approximately sigma for white noise. */
    private static double firstDifferenceNoise(double[] values) {
        if (values.length < 2) return 0.0;
        double sumSquares = 0.0;
        int pairs = 0;
        for (int i = 1; i < values.length; i++) {
            double d = values[i] - values[i - 1];
            sumSquares += d * d;
            pairs++;
        }
        return pairs == 0 ? 0.0 : Math.sqrt(sumSquares / pairs) / Math.sqrt(2.0);
    }

    /** Difference between late-window and early-window residual means. */
    private static double halfWindowDrift(double[] values) {
        if (values.length < 4) return 0.0;
        int split = values.length / 2;
        double early = 0.0;
        double late = 0.0;
        for (int i = 0; i < split; i++) early += values[i];
        for (int i = split; i < values.length; i++) late += values[i];
        early /= split;
        late /= (values.length - split);
        return late - early;
    }
}
