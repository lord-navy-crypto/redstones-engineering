package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.EngineeringSystemsModule;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class EngineeringCompassBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 8, 16);

    public EngineeringCompassBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<EngineeringCompassBlock> codec() {
        return EngineeringSystemsModule.ENGINEERING_COMPASS_CODEC.value();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
