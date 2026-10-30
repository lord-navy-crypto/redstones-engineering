package dev.redstoneengineering.block;

import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.OpticalObservationSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Shared series wiring; device-specific optical transfer laws remain independent. */
final class OpticalSeriesSupport {
    private OpticalSeriesSupport() {}

    static EngineeringPortSnapshot snapshot(Level level, BlockPos pos, Direction side, EngineeringPort port) {
        var observation = OpticalObservationSupport.observe(level, pos.relative(side));
        return new EngineeringPortSnapshot(port, observation.intensity(), 0.0, 15.0, observation.quality());
    }

    static boolean configurationAffected(BlockState state, BlockState previous, IntegerProperty parameter) {
        if (!state.is(previous.getBlock())) return false;
        boolean routeChanged = previous.hasProperty(DirectionalDomainBlock.FACING)
                && previous.hasProperty(DirectionalDomainBlock.INPUT_FACING)
                && (state.getValue(DirectionalDomainBlock.FACING) != previous.getValue(DirectionalDomainBlock.FACING)
                || state.getValue(DirectionalDomainBlock.INPUT_FACING) != previous.getValue(DirectionalDomainBlock.INPUT_FACING));
        return routeChanged || (previous.hasProperty(parameter)
                && !state.getValue(parameter).equals(previous.getValue(parameter)));
    }

    static void invalidate(ServerLevel level, BlockPos pos, BlockState state) {
        Direction output = DirectionalDomainBlock.seriesOutputSide(state);
        DomainNetwork.driveOptical(level, pos.relative(output), pos, 0, 0, false);
    }

    static void configurationChanged(ServerLevel level, BlockPos pos, BlockState formerState,
                                     Class<? extends Block> expected) {
        invalidate(level, pos, formerState);
        Block block = level.getBlockState(pos).getBlock();
        if (expected.isInstance(block)) level.scheduleTick(pos, block, 1);
    }

    static void removed(BlockState state, Level level, BlockPos pos, BlockState replacement) {
        if (!state.is(replacement.getBlock()) && level instanceof ServerLevel serverLevel) {
            invalidate(serverLevel, pos, state);
            DomainNetwork.recomputeOpticalAround(serverLevel, pos);
        }
    }
}
