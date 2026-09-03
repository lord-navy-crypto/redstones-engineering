package dev.redstoneengineering.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Base class for RSE domains that must not accidentally behave as vanilla redstone. */
public abstract class DomainBlock extends Block {
    protected DomainBlock(Properties properties) { super(properties); }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return false;
    }
}
