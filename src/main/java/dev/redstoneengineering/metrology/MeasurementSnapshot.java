package dev.redstoneengineering.metrology;

/**
 * Immutable engineering readout for one measurement channel.
 *
 * uncertaintyProxy is intentionally a diagnostic proxy, not a formal GUM
 * expanded-uncertainty statement. Bias is reported separately so error and
 * uncertainty are not silently treated as the same quantity.
 */
public record MeasurementSnapshot(
        double reading,
        double repeatability,
        double bias,
        double drift,
        double noise,
        double resolution,
        boolean saturated,
        long sampleAgeTicks,
        int sampleCount,
        double uncertaintyProxy,
        MeasurementQuality quality
) {
    public static MeasurementSnapshot invalid(double resolution) {
        return new MeasurementSnapshot(
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                resolution,
                false,
                Long.MAX_VALUE,
                0,
                Double.NaN,
                MeasurementQuality.INVALID
        );
    }

    public String compact() {
        if (quality == MeasurementQuality.INVALID) return "NO DATA";
        return String.format(
                java.util.Locale.ROOT,
                "reading=%.2f repeatability=±%.2f bias=%+.2f drift=%+.2f noise=%.2f resolution=%.2f age=%dt samples=%d uncertainty≈±%.2f quality=%s",
                reading,
                repeatability,
                bias,
                drift,
                noise,
                resolution,
                sampleAgeTicks,
                sampleCount,
                uncertaintyProxy,
                quality
        );
    }
}
