package dev.redstoneengineering.block;

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
 * Shared horizontal topology for single-output non-redstone sources.
 *
 * <p>Unlike {@link DirectionalDomainBlock}, this endpoint has no synthetic input port. FACING is
 * the one physical output face. Source solvers must honor this face when discovering or claiming
 * a connected medium so rotating the source changes real propagation rather than HMI text only.</p>
 */
public abstract class DirectionalDomainSourceBlock extends DomainBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    protected DirectionalDomainSourceBlock(Properties properties) {
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

    public static Direction outputSide(BlockState state) {
        return state.getValue(FACING);
    }

    protected BlockPos outputPos(BlockPos pos, BlockState state) {
        return pos.relative(outputSide(state));
    }

    public static boolean outputsToward(BlockState state, Direction direction) {
        return state.hasProperty(FACING) && outputSide(state) == direction;
    }

    /** Rotate the one source output face and notify both the old and new attached segments. */
    public static boolean rotateOutput(Level level, BlockPos pos, boolean clockwise) {
        if (level.isClientSide) return false;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof DirectionalDomainSourceBlock block)) return false;

        Direction oldOutput = outputSide(state);
        Direction newOutput = clockwise ? oldOutput.getClockWise() : oldOutput.getCounterClockWise();
        BlockState next = state.setValue(FACING, newOutput);
        level.setBlock(pos, next, Block.UPDATE_CLIENTS);
        notifyNeighbors(level, pos, block, oldOutput, newOutput);
        return true;
    }

    private static void notifyNeighbors(Level level, BlockPos pos, Block block, Direction... sides) {
        level.updateNeighborsAt(pos, block);
        for (Direction side : sides) {
            level.updateNeighborsAt(pos.relative(side), block);
        }
    }
}
