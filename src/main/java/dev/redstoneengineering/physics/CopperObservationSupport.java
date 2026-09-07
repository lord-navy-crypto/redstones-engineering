package dev.redstoneengineering.physics;

import dev.redstoneengineering.block.CopperCableJunctionBlock;
import dev.redstoneengineering.block.CopperCapacitorBlock;
import dev.redstoneengineering.block.CopperFuseBlock;
import dev.redstoneengineering.block.CopperResistiveLoadBlock;
import dev.redstoneengineering.block.CopperSeriesResistorBlock;
import dev.redstoneengineering.block.CopperVoltageSourceBlock;
import dev.redstoneengineering.block.CopperWireBlock;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.block.InductionCoilBlock;
import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Non-recursive, observer-only Copper evidence.
 *
 * Processor outputs expose only runtime evidence already produced by their server tick;
 * this deliberately avoids walking engineeringSnapshot chains and therefore cannot
 * recurse forever through a cyclic processor layout.
 */
public final class CopperObservationSupport {
    private CopperObservationSupport() {}

    public record Observation(int voltage, PortQuality quality) {
        public boolean initialized() {
            return quality != PortQuality.STALE;
        }
    }

    /** Observe an upstream source/output that is physically facing {@code observerPos}. */
    public static Observation observe(Level level, BlockPos pos, BlockPos observerPos) {
        if (!level.hasChunkAt(pos)) return new Observation(0, PortQuality.NO_SIGNAL);
        BlockState state = level.getBlockState(pos);

        if (state.getBlock() instanceof CopperWireBlock) {
            return new Observation(
                    CopperWireBlock.voltage(level, pos),
                    CopperWireBlock.quality(level, pos, state));
        }
        if (state.getBlock() instanceof CopperCableJunctionBlock junction) {
            PortQuality quality;
            if (!junction.topologyValid(state) || CopperCableJunctionBlock.driverCount(level, pos) > 1) {
                quality = PortQuality.TOPOLOGY_ERROR;
            } else if (CopperCableJunctionBlock.driverCount(level, pos) == 1) {
                quality = PortQuality.VALID;
            } else {
                quality = PortQuality.NO_SIGNAL;
            }
            return new Observation(CopperCableJunctionBlock.voltage(level, pos), quality);
        }
        if (state.getBlock() instanceof CopperVoltageSourceBlock) {
            // Zero volts is still a valid configured source state at the source terminal itself.
            return new Observation(state.getValue(CopperVoltageSourceBlock.VOLTAGE), PortQuality.VALID);
        }
        if (state.getBlock() instanceof CopperResistiveLoadBlock) {
            // A load is INPUT-only and must never masquerade as an upstream source.
            return new Observation(state.getValue(CopperResistiveLoadBlock.VOLTAGE), PortQuality.NO_SIGNAL);
        }

        if (observerPos != null && state.hasProperty(DirectionalDomainBlock.FACING)) {
            Direction facing = state.getValue(DirectionalDomainBlock.FACING);
            if (!observerPos.equals(pos.relative(facing))) return new Observation(0, PortQuality.NO_SIGNAL);

            if (state.getBlock() instanceof CopperSeriesResistorBlock) {
                return new Observation(
                        CopperSeriesResistorBlock.outputVoltage(level, pos),
                        CopperSeriesResistorBlock.outputQuality(level, pos));
            }
            if (state.getBlock() instanceof CopperCapacitorBlock) {
                return new Observation(
                        CopperCapacitorBlock.outputVoltage(level, pos),
                        CopperCapacitorBlock.outputQuality(level, pos));
            }
            if (state.getBlock() instanceof CopperFuseBlock) {
                return new Observation(
                        CopperFuseBlock.outputVoltage(level, pos),
                        CopperFuseBlock.outputQuality(level, pos, state));
            }
            if (state.getBlock() instanceof InductionCoilBlock) {
                int voltage = InductionCoilBlock.outputVoltage(level, pos);
                return new Observation(voltage, voltage > 0 ? PortQuality.VALID : PortQuality.NO_SIGNAL);
            }
        }

        return new Observation(0, PortQuality.NO_SIGNAL);
    }

    /**
     * Measure a Copper node as a diagnostic target. Unlike {@link #observe}, a terminal
     * load may be measured without being reclassified as an upstream source.
     */
    public static Observation measure(Level level, BlockPos pos, BlockPos observerPos) {
        if (!level.hasChunkAt(pos)) return new Observation(0, PortQuality.NO_SIGNAL);
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof CopperResistiveLoadBlock) {
            CopperNetworkSupport.TerminalInput input = CopperResistiveLoadBlock.input(level, pos);
            return new Observation(input.voltage(), input.quality());
        }
        return observe(level, pos, observerPos);
    }
}
