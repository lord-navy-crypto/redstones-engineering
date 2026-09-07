package dev.redstoneengineering.physics;

import dev.redstoneengineering.block.LapisNoiseSourceBlock;
import dev.redstoneengineering.block.LapisPrecisionSourceBlock;
import dev.redstoneengineering.block.LapisSignalLineBlock;
import dev.redstoneengineering.block.QuartzLabOscillatorBlock;
import dev.redstoneengineering.block.QuartzOscillatorBlock;
import dev.redstoneengineering.block.QuartzTimingLineBlock;
import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Observer-only quality-preserving reads for the precision Lapis and Quartz domains. */
public final class PrecisionObservationSupport {
    private PrecisionObservationSupport() {}

    public record LapisObservation(int value, PortQuality quality) {
        public boolean valid() {
            return quality == PortQuality.VALID || quality == PortQuality.SATURATED;
        }
    }

    public record QuartzObservation(boolean active, int periodTicks, PortQuality quality) {
        public boolean valid() {
            return quality == PortQuality.VALID;
        }
    }

    public static LapisObservation lapis(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return new LapisObservation(0, PortQuality.STALE);
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof LapisSignalLineBlock) {
            return new LapisObservation(
                    LapisSignalLineBlock.value(level, pos),
                    LapisSignalLineBlock.quality(level, pos)
            );
        }
        if (state.getBlock() instanceof LapisPrecisionSourceBlock) {
            return new LapisObservation(state.getValue(LapisPrecisionSourceBlock.VALUE), PortQuality.VALID);
        }
        if (state.getBlock() instanceof LapisNoiseSourceBlock) {
            return new LapisObservation(LapisNoiseSourceBlock.currentValue(level, pos, state), PortQuality.VALID);
        }
        return new LapisObservation(0, PortQuality.NO_SIGNAL);
    }

    public static QuartzObservation quartz(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return new QuartzObservation(false, 0, PortQuality.STALE);
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof QuartzTimingLineBlock) {
            return new QuartzObservation(
                    QuartzTimingLineBlock.active(level, pos),
                    QuartzTimingLineBlock.period(level, pos),
                    QuartzTimingLineBlock.quality(level, pos)
            );
        }
        if (state.getBlock() instanceof QuartzOscillatorBlock) {
            return new QuartzObservation(
                    state.getValue(QuartzOscillatorBlock.ACTIVE),
                    QuartzTimingLineBlock.periodTicks(state.getValue(QuartzOscillatorBlock.PERIOD_INDEX)),
                    PortQuality.VALID
            );
        }
        if (state.getBlock() instanceof QuartzLabOscillatorBlock) {
            return new QuartzObservation(
                    state.getValue(QuartzLabOscillatorBlock.ACTIVE),
                    QuartzTimingLineBlock.periodTicks(state.getValue(QuartzLabOscillatorBlock.PERIOD_INDEX)),
                    PortQuality.VALID
            );
        }
        return new QuartzObservation(false, 0, PortQuality.NO_SIGNAL);
    }
}
