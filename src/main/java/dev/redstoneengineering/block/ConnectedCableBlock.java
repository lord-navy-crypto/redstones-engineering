package dev.redstoneengineering.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Six-direction 3-D cable topology. Live voltage/light/signal values belong in
 * runtime storage, leaving only 2^6 = 64 cheap topology states.
 *
 * <p>Connection state is deliberately kept symmetric. When one RSE cable-like
 * block is placed beside another, both endpoints are refreshed immediately;
 * correctness therefore does not depend on which endpoint happened to receive
 * a vanilla neighbor notification first.</p>
 */
public abstract class ConnectedCableBlock extends DomainBlock {
    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty WEST = BooleanProperty.create("west");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");

    private static final VoxelShape CENTER = Block.box(6, 6, 6, 10, 10, 10);
    private static final VoxelShape NORTH_ARM = Block.box(6, 6, 0, 10, 10, 6);
    private static final VoxelShape EAST_ARM = Block.box(10, 6, 6, 16, 10, 10);
    private static final VoxelShape SOUTH_ARM = Block.box(6, 6, 10, 10, 10, 16);
    private static final VoxelShape WEST_ARM = Block.box(0, 6, 6, 6, 10, 10);
    private static final VoxelShape UP_ARM = Block.box(6, 10, 6, 10, 16, 10);
    private static final VoxelShape DOWN_ARM = Block.box(6, 0, 6, 10, 6, 10);

    protected ConnectedCableBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(NORTH, false)
                .setValue(EAST, false)
                .setValue(SOUTH, false)
                .setValue(WEST, false)
                .setValue(UP, false)
                .setValue(DOWN, false));
    }

    protected abstract boolean canConnectTo(
            BlockGetter level,
            BlockPos self,
            Direction direction,
            BlockState neighbor
    );

    /** Plain cable has two ends. Branches should use a Junction/Splitter. */
    protected int maxConnections() {
        return 2;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withConnections(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    protected final BlockState withConnections(BlockState state, BlockGetter level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            state = state.setValue(
                    property(direction),
                    canConnectTo(level, pos, direction, level.getBlockState(pos.relative(direction)))
            );
        }
        return state;
    }

    protected final void refreshConnections(Level level, BlockPos pos, BlockState state) {
        BlockState refreshed = withConnections(state, level, pos);
        if (refreshed != state) {
            level.setBlock(pos, refreshed, Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Refresh both endpoints after placement. This is intentionally explicit:
     * structure placement, GameTest placement, commands, and player placement
     * do not all arrive through exactly the same notification path.
     */
    private void refreshAdjacentCableConnections(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            BlockState neighborState = level.getBlockState(neighborPos);
            if (neighborState.getBlock() instanceof ConnectedCableBlock neighborCable) {
                neighborCable.refreshConnections(level, neighborPos, neighborState);
            }
        }
    }

    public final boolean topologyValid(BlockState state) {
        return connectionCount(state) <= maxConnections();
    }

    public static int connectionCount(BlockState state) {
        int count = 0;
        for (Direction direction : Direction.values()) {
            if (connected(state, direction)) count++;
        }
        return count;
    }

    public static boolean connected(BlockState state, Direction direction) {
        BooleanProperty property = property(direction);
        return state.hasProperty(property) && state.getValue(property);
    }

    private static BooleanProperty property(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case UP -> UP;
            case DOWN -> DOWN;
        };
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        refreshConnections(level, pos, state);
        refreshAdjacentCableConnections(level, pos);
    }

    @Override
    protected void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block neighbor,
            BlockPos neighborPos,
            boolean moved
    ) {
        refreshConnections(level, pos, state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = CENTER;
        if (state.getValue(NORTH)) shape = Shapes.joinUnoptimized(shape, NORTH_ARM, BooleanOp.OR);
        if (state.getValue(EAST)) shape = Shapes.joinUnoptimized(shape, EAST_ARM, BooleanOp.OR);
        if (state.getValue(SOUTH)) shape = Shapes.joinUnoptimized(shape, SOUTH_ARM, BooleanOp.OR);
        if (state.getValue(WEST)) shape = Shapes.joinUnoptimized(shape, WEST_ARM, BooleanOp.OR);
        if (state.getValue(UP)) shape = Shapes.joinUnoptimized(shape, UP_ARM, BooleanOp.OR);
        if (state.getValue(DOWN)) shape = Shapes.joinUnoptimized(shape, DOWN_ARM, BooleanOp.OR);
        return shape;
    }
}
