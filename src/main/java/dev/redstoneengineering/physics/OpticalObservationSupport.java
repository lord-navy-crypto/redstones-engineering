package dev.redstoneengineering.physics;

import dev.redstoneengineering.block.OpticalEmitterBlock;
import dev.redstoneengineering.block.OpticalFiberBlock;
import dev.redstoneengineering.block.OpticalFiberJunctionBlock;
import dev.redstoneengineering.block.OpticalReceiverBlock;
import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Read-only optical carrier evidence shared by meters and passive processors. */
public final class OpticalObservationSupport {
    private OpticalObservationSupport() {}

    public record Observation(int intensity, int channel, PortQuality quality) {
        public boolean carrierPresent() {
            return quality == PortQuality.VALID && intensity > 0;
        }
    }

    public static Observation observe(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return new Observation(0, 0, PortQuality.STALE);
        BlockState state = level.getBlockState(pos);
        DomainNetwork.OpticalSample sample = DomainNetwork.sampleOptical(level, pos);
        PortQuality quality;

        if (state.getBlock() instanceof OpticalFiberBlock) {
            quality = OpticalFiberBlock.quality(level, pos, state);
        } else if (state.getBlock() instanceof OpticalFiberJunctionBlock junction) {
            if (state.getValue(OpticalFiberJunctionBlock.SERVICE_OPEN)) {
                quality = PortQuality.NO_SIGNAL;
            } else if (!junction.topologyValid(state) || OpticalFiberJunctionBlock.driverCount(level, pos) > 1) {
                quality = PortQuality.TOPOLOGY_ERROR;
            } else {
                quality = OpticalFiberJunctionBlock.valid(level, pos) ? PortQuality.VALID : PortQuality.NO_SIGNAL;
            }
        } else if (state.getBlock() instanceof OpticalReceiverBlock) {
            quality = OpticalReceiverBlock.quality(level, pos);
        } else if (state.getBlock() instanceof OpticalEmitterBlock) {
            // A configured zero-intensity source is still a valid source setting at its own terminal.
            quality = PortQuality.VALID;
        } else {
            quality = PortQuality.NO_SIGNAL;
        }

        return new Observation(
                Math.max(0, Math.min(15, sample.intensity())),
                Math.max(0, Math.min(15, sample.channel())),
                quality
        );
    }
}
