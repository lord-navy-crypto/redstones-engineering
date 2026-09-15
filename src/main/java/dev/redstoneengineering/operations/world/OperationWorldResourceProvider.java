package dev.redstoneengineering.operations.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Evidence-only bridge from an existing world block into Industrial Operations.
 * Implementations must expose native runtime truth and must not own dispatch or scheduling logic.
 */
public interface OperationWorldResourceProvider {
    OperationWorldResourceSnapshot operationResourceSnapshot(Level level, BlockPos pos, BlockState state);
}
