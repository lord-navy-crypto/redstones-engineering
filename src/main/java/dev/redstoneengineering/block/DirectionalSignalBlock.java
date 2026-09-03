package dev.redstoneengineering.block;

import dev.redstoneengineering.signal.EngineeringSignal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import javax.annotation.Nullable;

public abstract class DirectionalSignalBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty OUTPUT = IntegerProperty.create("output", 0, 15);

    protected DirectionalSignalBlock(Properties properties) {
        super(properties);
        registerDefaultState(
                stateDefinition.any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(OUTPUT, 0)
        );
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OUTPUT);
    }

    protected Direction outputSide(BlockState state) {
        return state.getValue(FACING);
    }

    protected Direction inputSide(BlockState state) {
        return outputSide(state).getOpposite();
    }

    protected static Direction leftOf(Direction facing) {
        return switch (facing) {
            case NORTH -> Direction.WEST;
            case WEST -> Direction.SOUTH;
            case SOUTH -> Direction.EAST;
            case EAST -> Direction.NORTH;
            default -> Direction.WEST;
        };
    }

    protected static Direction rightOf(Direction facing) {
        return switch (facing) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.NORTH;
            default -> Direction.EAST;
        };
    }

    protected boolean isEngineeringPort(BlockState state, Direction side) {
        return side == inputSide(state) || side == outputSide(state);
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            @Nullable Direction direction
    ) {
        return direction != null
                && isEngineeringPort(state, direction.getOpposite());
    }

    protected int readBackInput(Level level, BlockPos pos, BlockState state) {
        Direction back = inputSide(state);
        return EngineeringSignal.clamp(level.getSignal(pos.relative(back), back));
    }

    protected int readInputFrom(Level level, BlockPos pos, Direction direction) {
        return EngineeringSignal.clamp(level.getSignal(pos.relative(direction), direction));
    }

    protected void updateOutput(Level level, BlockPos pos, BlockState state, int requestedOutput) {
        int output = EngineeringSignal.clamp(requestedOutput);
        int oldOutput = state.getValue(OUTPUT);

        if (oldOutput == output) {
            return;
        }

        BlockState next = state.setValue(OUTPUT, output);
        level.setBlock(pos, next, Block.UPDATE_CLIENTS);
        level.updateNeighborsAt(pos, this);
        level.updateNeighborsAt(pos.relative(outputSide(next)), this);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return direction == outputSide(state).getOpposite()
                ? state.getValue(OUTPUT)
                : 0;
    }

    @Override
    protected void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block neighborBlock,
            BlockPos neighborPos,
            boolean movedByPiston
    ) {
        if (!level.isClientSide) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void onPlace(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState oldState,
            boolean movedByPiston
    ) {
        if (!level.isClientSide && !state.is(oldState.getBlock())) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void onRemove(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState newState,
            boolean movedByPiston
    ) {
        if (!state.is(newState.getBlock())) {
            level.updateNeighborsAt(pos, this);
            level.updateNeighborsAt(pos.relative(outputSide(state)), this);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected abstract void tick(
            BlockState state,
            ServerLevel level,
            BlockPos pos,
            RandomSource random
    );
}
