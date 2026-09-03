package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

public class SignalProbeBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final IntegerProperty CHANNEL = IntegerProperty.create("channel", 0, 3);

    public SignalProbeBlock(Properties properties) {
        super(properties);
        registerDefaultState(
                stateDefinition.any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(CHANNEL, 0)
        );
    }

    @Override
    public MapCodec<SignalProbeBlock> codec() {
        return RedstoneEngineering.SIGNAL_PROBE_CODEC.value();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getClickedFace().getOpposite())
                .setValue(CHANNEL, 0);
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder
    ) {
        builder.add(FACING, CHANNEL);
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            @Nullable Direction direction
    ) {
        return false;
    }

    public int sample(Level level, BlockPos pos, BlockState state) {
        Direction targetSide = state.getValue(FACING);
        BlockPos targetPos = pos.relative(targetSide);

        return SignalAnalyzerBlock.measureNode(
                level,
                targetPos,
                level.getBlockState(targetPos)
        );
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                int value = sample(level, pos, state);
                player.displayClientMessage(
                        Component.literal(
                                "Probe " + channelName(state.getValue(CHANNEL))
                                        + " | test=" + state.getValue(FACING).getName()
                                        + " | value=" + value + "/15"
                        ),
                        true
                );
            } else {
                int nextChannel = (state.getValue(CHANNEL) + 1) % 4;
                BlockState next = state.setValue(CHANNEL, nextChannel);
                level.setBlock(pos, next, Block.UPDATE_CLIENTS);

                player.displayClientMessage(
                        Component.literal(
                                "Probe channel → " + channelName(nextChannel)
                        ),
                        true
                );
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static String channelName(int channel) {
        return switch (channel) {
            case 0 -> "A";
            case 1 -> "B";
            case 2 -> "C";
            case 3 -> "D";
            default -> "?";
        };
    }
}
