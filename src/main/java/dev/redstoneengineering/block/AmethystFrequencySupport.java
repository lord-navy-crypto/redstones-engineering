package dev.redstoneengineering.block;

import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.DomainNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Read-only quality and observable amplitude contract for the AMETHYST medium.
 *
 * Source, exact filter and finite-bandwidth resonance retain separate physics.
 * A missing chunk is unknown, not a measured zero or valid resonance.
 */
public final class AmethystFrequencySupport {
    private AmethystFrequencySupport() {}

    public static PortQuality qualityAt(Level level, BlockPos samplePos,
                                        DomainNetwork.AmethystSample sample) {
        if (!level.hasChunkAt(samplePos)) return PortQuality.STALE;
        if (level.getBlockState(samplePos).getBlock() instanceof AmethystResonanceDustBlock) {
            return switch (AmethystResonanceDustBlock.status(level, samplePos)) {
                case ACTIVE -> sample.active() ? PortQuality.VALID : PortQuality.NO_SIGNAL;
                case FREQUENCY_CONFLICT -> PortQuality.TOPOLOGY_ERROR;
                case STALE -> PortQuality.STALE;
                case IDLE -> PortQuality.NO_SIGNAL;
            };
        }
        return sample.active() ? PortQuality.VALID : PortQuality.NO_SIGNAL;
    }

    public static int readableAmplitude(PortQuality quality, int amplitude) {
        return quality == PortQuality.VALID || quality == PortQuality.SATURATED
                ? Math.max(0, Math.min(15, amplitude)) : 0;
    }

    public static EngineeringPortSnapshot sourcePort(EngineeringPort port, int configuredAmplitude,
                                                     boolean pulsing) {
        int value = pulsing ? Math.max(0, Math.min(15, configuredAmplitude)) : 0;
        return new EngineeringPortSnapshot(port, value, 0.0, 15.0,
                value > 0 ? PortQuality.VALID : PortQuality.NO_SIGNAL);
    }

    /** A denied input must never be presented as an active output measurement. */
    public static PortQuality outputQuality(PortQuality upstream, PortQuality observedOutput) {
        if (upstream == PortQuality.TOPOLOGY_ERROR || upstream == PortQuality.STALE)
            return upstream;
        if (upstream != PortQuality.VALID) return PortQuality.NO_SIGNAL;
        return observedOutput;
    }
}
