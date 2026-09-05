package dev.redstoneengineering.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/** Horizontal input/output topology for non-redstone RSE domain processors. */
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
}
