package dev.redstoneengineering.metrology;

import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.SensorModel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Locale;

/** Shared Alpha 1.0.15 helpers for measurement devices across engineering domains. */
public final class MetrologySupport {
    private MetrologySupport() {}

    public static MeasurementSnapshot sample(
            Level level,
            String channel,
            BlockPos pos,
            double reading,
            double reference,
            boolean saturated,
            double resolution,
            long staleAfterTicks
    ) {
        return MetrologyStore.tracker(level, channel, pos, resolution, staleAfterTicks)
                .sample(reading, reference, saturated, level.getGameTime());
    }

    public static MeasurementSnapshot snapshot(
            Level level,
            String channel,
            BlockPos pos,
            double resolution,
            long staleAfterTicks
    ) {
        return MetrologyStore.tracker(level, channel, pos, resolution, staleAfterTicks)
                .snapshot(level.getGameTime());
    }

    /** Deterministic sensor conditioning over an arbitrary bounded engineering range. */
    public static double conditionBounded(
            ServerLevel level,
            BlockPos pos,
            double reference,
            double minimum,
            double maximum,
            int profile
    ) {
        if (!(maximum > minimum)) throw new IllegalArgumentException("maximum must exceed minimum");
        double bounded = Math.max(minimum, Math.min(maximum, reference));
        int normalized = (int) Math.round((bounded - minimum) / (maximum - minimum) * 100.0);
        int conditioned = SensorModel.condition(level, pos, normalized, profile);
        return minimum + conditioned / 100.0 * (maximum - minimum);
    }

    /** Deterministic sensor conditioning while retaining a vanilla-facing 0..15 engineering scale. */
    public static double conditionRedstone(ServerLevel level, BlockPos pos, double reference, int profile) {
        return conditionBounded(level, pos, reference, 0.0, 15.0, profile);
    }

    /** PortQuality has fewer states than MeasurementQuality; preserve explicit hard states without inventing a fault. */
    public static PortQuality portQuality(MeasurementSnapshot measurement) {
        return switch (measurement.quality()) {
            case SATURATED -> PortQuality.SATURATED;
            case STALE -> PortQuality.STALE;
            case INVALID -> PortQuality.FAULT;
            case GOOD, DEGRADED -> PortQuality.VALID;
        };
    }

    public static String compactDiagnostics(MeasurementSnapshot m) {
        if (m.sampleCount() == 0) return m.quality().name() + " | no samples";
        return String.format(
                Locale.ROOT,
                "%s | reading=%.2f repeatability=±%.2f bias=%+.2f drift=%+.2f noise=%.2f resolution=%.2f age=%dt samples=%d uncertainty≈±%.2f",
                m.quality().name(),
                m.reading(),
                m.repeatability(),
                m.bias(),
                m.drift(),
                m.noise(),
                m.resolution(),
                m.sampleAgeTicks(),
                m.sampleCount(),
                m.uncertaintyProxy()
        );
    }
}
