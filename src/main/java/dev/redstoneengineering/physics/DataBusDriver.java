package dev.redstoneengineering.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Contract for blocks allowed to publish runtime `bus8_out` state.
 * A resolver validates both block identity and the physical output face that actually touches the bus.
 */
public interface DataBusDriver {
    boolean drivesDataBusAt(BlockPos driverPos, BlockState driverState, BlockPos busPos);
}
