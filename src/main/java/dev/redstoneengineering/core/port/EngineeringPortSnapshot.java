package dev.redstoneengineering.core.port;

/**
 * Runtime value observed at an {@link EngineeringPort}.
 *
 * <p>The descriptor is static; value/quality may change every tick without
 * adding BlockState variants.</p>
 */
public record EngineeringPortSnapshot(
        EngineeringPort port,
        double value,
        double minimum,
        double maximum,
        PortQuality quality
) {
    public EngineeringPortSnapshot {
        if (port == null) throw new IllegalArgumentException("port must not be null");
        if (quality == null) throw new IllegalArgumentException("quality must not be null");
        if (!Double.isFinite(value) || !Double.isFinite(minimum) || !Double.isFinite(maximum)) {
            throw new IllegalArgumentException("port snapshot values must be finite");
        }
        if (maximum < minimum) throw new IllegalArgumentException("maximum must be >= minimum");
    }

    public double normalized() {
        if (maximum == minimum) return 0.0;
        return Math.max(0.0, Math.min(1.0, (value - minimum) / (maximum - minimum)));
    }

    public static EngineeringPortSnapshot redstone(EngineeringPort port, int signal, PortQuality quality) {
        int bounded = Math.max(0, Math.min(15, signal));
        return new EngineeringPortSnapshot(port, bounded, 0.0, 15.0, quality);
    }
}
