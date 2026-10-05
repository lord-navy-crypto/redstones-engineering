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

    /** True only after this channel has received at least one authoritative measurement sample. */
    public boolean hasSample() {
        return sampleCount > 0;
    }

    public String compact() {
        if (!hasSample()) return "NOT_READY | awaiting first sample";
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
