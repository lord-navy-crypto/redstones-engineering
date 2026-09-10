package dev.redstoneengineering.block;

import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Horizontal input/output topology for non-redstone RSE domain processors.
 *
 * <p>The default contract is a strict series path: the face opposite {@link #FACING}
 * is INPUT and {@link #FACING} is OUTPUT. Rotating the block rotates the whole path;
 * it never creates a second implicit input or output.</p>
 */
public abstract class DirectionalDomainBlock extends DomainBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    protected DirectionalDomainBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    protected Direction outputSide(BlockState state) { return state.getValue(FACING); }
    protected Direction inputSide(BlockState state) { return outputSide(state).getOpposite(); }
    protected BlockPos inputPos(BlockPos pos, BlockState state) { return pos.relative(inputSide(state)); }
    protected BlockPos outputPos(BlockPos pos, BlockState state) { return pos.relative(outputSide(state)); }

    public static Direction seriesOutputSide(BlockState state) { return state.getValue(FACING); }
    public static Direction seriesInputSide(BlockState state) { return seriesOutputSide(state).getOpposite(); }

    public static Direction leftOf(Direction facing) {
        return switch (facing) {
            case NORTH -> Direction.WEST;
            case WEST -> Direction.SOUTH;
            case SOUTH -> Direction.EAST;
            case EAST -> Direction.NORTH;
            default -> Direction.WEST;
        };
    }

    protected static Direction rightOf(Direction facing) {
        return leftOf(facing).getOpposite();
    }

    /** Server-authoritative 90-degree rotation of the complete series axis. */
    public static boolean rotateSeriesAxis(Level level, BlockPos pos, boolean clockwise) {
        if (level.isClientSide) return false;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof DirectionalDomainBlock block)) return false;

        Direction oldOutput = state.getValue(FACING);
        Direction oldInput = oldOutput.getOpposite();
        Direction newOutput = clockwise ? oldOutput.getClockWise() : oldOutput.getCounterClockWise();
        BlockState next = state.setValue(FACING, newOutput);
        level.setBlock(pos, next, Block.UPDATE_CLIENTS);

        level.updateNeighborsAt(pos, block);
        level.updateNeighborsAt(pos.relative(oldInput), block);
        level.updateNeighborsAt(pos.relative(oldOutput), block);
        level.updateNeighborsAt(pos.relative(newOutput.getOpposite()), block);
        level.updateNeighborsAt(pos.relative(newOutput), block);
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                rotateSeriesAxis(level, pos, true);
                BlockState next = level.getBlockState(pos);
                player.displayClientMessage(Component.literal(
                        "Series I/O | IN=" + seriesInputSide(next).getName().toUpperCase()
                                + " → OUT=" + seriesOutputSide(next).getName().toUpperCase()
                                + " | normal right-click opens Engineering UI"), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
