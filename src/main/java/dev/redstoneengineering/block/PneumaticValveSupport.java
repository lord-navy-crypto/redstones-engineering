package dev.redstoneengineering.block;

import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.PneumaticNetwork;
import dev.redstoneengineering.physics.PneumaticObservationSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Shared pressure observation and safe lifecycle, not a shared flow law. */
final class PneumaticValveSupport {
    private PneumaticValveSupport() {}

    static EngineeringPortSnapshot snapshot(Level level, BlockPos pos, Direction side, EngineeringPort port) {
        var observation = PneumaticObservationSupport.observe(level, pos.relative(side));
        return new EngineeringPortSnapshot(port, observation.pressure(), 0.0, 100.0, observation.quality());
    }

    static void placed(Level level, BlockPos pos) {
        if (level instanceof ServerLevel server) PneumaticNetwork.recomputeAround(server, pos);
    }

    static void removed(BlockState state, Level level, BlockPos pos, BlockState replacement) {
        if (!state.is(replacement.getBlock()) && level instanceof ServerLevel server) {
            InformationRuntime.clear(level, "pneumatic", pos);
            PneumaticNetwork.recomputeAround(server, pos);
        }
    }
}
