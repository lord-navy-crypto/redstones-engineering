package dev.redstoneengineering.operations.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/** Resolves exactly one explicitly addressed world resource; no proximity discovery is permitted here. */
public final class OperationWorldResourceResolver {
    private OperationWorldResourceResolver() {}

    public static Optional<OperationWorldResourceSnapshot> resolve(Level level, BlockPos pos) {
        if (level == null || pos == null || !level.isLoaded(pos)) return Optional.empty();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof OperationWorldResourceProvider provider)) return Optional.empty();
        return Optional.ofNullable(provider.operationResourceSnapshot(level, pos, state));
    }
}
