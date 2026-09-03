package dev.redstoneengineering.block;

import dev.redstoneengineering.signal.EngineeringSignal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/**
 * Shared horizontal FRONT/BACK topology for single-ended vanilla-redstone devices.
 *
 * <p>The physical side stored in {@link #FACING} is the FRONT face. Redstone's
 * query direction is reversed relative to the physical side, so subclasses
 * should use {@link #isQueriedFrom(BlockState, Direction, Direction)} when
 * implementing directional signal output.</p>
 */
public abstract class DirectionalRedstoneEndpointBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    protected DirectionalRedstoneEndpointBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    protected Direction frontSide(BlockState state) {
        return state.getValue(FACING);
    }

    protected Direction backSide(BlockState state) {
        return frontSide(state).getOpposite();
    }

    protected BlockPos frontPos(BlockPos pos, BlockState state) {
        return pos.relative(frontSide(state));
    }

    protected BlockPos backPos(BlockPos pos, BlockState state) {
        return pos.relative(backSide(state));
    }

    protected static boolean isQueriedFrom(BlockState state, Direction queryDirection, Direction physicalSide) {
        return queryDirection == physicalSide.getOpposite();
    }

    protected static boolean connectionMatches(Direction connectionDirection, Direction physicalSide) {
        return connectionDirection.getOpposite() == physicalSide;
    }

    protected int readBackInput(Level level, BlockPos pos, BlockState state) {
        Direction back = backSide(state);
        return EngineeringSignal.clamp(level.getSignal(pos.relative(back), back));
    }

    protected void notifyFrontOutput(Level level, BlockPos pos, BlockState state) {
        level.updateNeighborsAt(pos, this);
        level.updateNeighborsAt(frontPos(pos, state), this);
    }
}
