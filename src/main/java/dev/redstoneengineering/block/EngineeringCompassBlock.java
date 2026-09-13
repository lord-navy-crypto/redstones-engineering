package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.EngineeringSystemsModule;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
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

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            player.displayClientMessage(Component.literal(
                    "World datum • N=-Z • E=+X • S=+Z • W=-X • Pos="
                            + pos.getX() + "," + pos.getY() + "," + pos.getZ()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
