package dev.redstoneengineering.core.port;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

/**
 * Common RSE contract consumed by diagnostics and future ecosystem adapters.
 * Core simulation code owns this interface; Jade/Fusion/other integrations read it.
 */
public interface EngineeringPortProvider {
    List<EngineeringPort> engineeringPorts(BlockState state);

    default Optional<EngineeringPort> engineeringPort(BlockState state, Direction side) {
        return engineeringPorts(state).stream().filter(port -> port.side() == side).findFirst();
    }

    default Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level,
            BlockPos pos,
            BlockState state,
            Direction side
    ) {
        return Optional.empty();
    }
}
