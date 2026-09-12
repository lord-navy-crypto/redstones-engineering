package dev.redstoneengineering.block;

import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.signal.EngineeringSignal;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
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
import java.util.List;
import java.util.Optional;

/**
 * Shared directional 0..15 processor base.
 *
 * <p>The processor always has one explicit INPUT and one explicit OUTPUT. Placement defaults to a
 * straight path. Operators may rotate the complete route or either endpoint independently without
 * turning the processor into a splitter or accepting hidden side inputs.</p>
 */
public abstract class DirectionalSignalBlock extends Block implements EngineeringPortProvider {
    /** OUTPUT face; retained as FACING for model/backward compatibility. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Dedicated INPUT face. It is never allowed to equal {@link #FACING}. */
    public static final DirectionProperty INPUT_FACING = DirectionProperty.create("input_facing", Direction.Plane.HORIZONTAL);
    public static final IntegerProperty OUTPUT = IntegerProperty.create("output", 0, 15);

    protected DirectionalSignalBlock(Properties properties) {
        super(properties);
        registerDefaultState(
                stateDefinition.any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(INPUT_FACING, Direction.SOUTH)
                        .setValue(OUTPUT, 0)
        );
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction output = context.getHorizontalDirection().getOpposite();
        return defaultBlockState()
                .setValue(FACING, output)
                .setValue(INPUT_FACING, output.getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, INPUT_FACING, OUTPUT);
    }

    protected Direction outputSide(BlockState state) {
        return state.getValue(FACING);
    }

    protected Direction inputSide(BlockState state) {
        return state.getValue(INPUT_FACING);
    }

    public static Direction seriesOutputSide(BlockState state) {
        return state.getValue(FACING);
    }

    public static Direction seriesInputSide(BlockState state) {
        return state.getValue(INPUT_FACING);
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
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort(
                        "INPUT",
                        inputSide(state),
                        EngineeringDomain.REDSTONE,
                        PortKind.REDSTONE_ANALOG,
                        PortDirection.INPUT,
                        true,
                        "signal"
                ),
                new EngineeringPort(
                        "OUTPUT",
                        outputSide(state),
                        EngineeringDomain.REDSTONE,
                        PortKind.REDSTONE_ANALOG,
                        PortDirection.OUTPUT,
                        true,
                        "signal"
                )
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level,
            BlockPos pos,
            BlockState state,
            Direction side
    ) {
        Optional<EngineeringPort> descriptor = engineeringPort(state, side);
        if (descriptor.isEmpty()) return Optional.empty();
        int value = side == outputSide(state)
                ? state.getValue(OUTPUT)
                : readInputFrom(level, pos, side);
        return Optional.of(EngineeringPortSnapshot.redstone(descriptor.get(), value, PortQuality.VALID));
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            @Nullable Direction direction
    ) {
        return direction != null && isEngineeringPort(state, direction.getOpposite());
    }

    protected int readBackInput(Level level, BlockPos pos, BlockState state) {
        Direction input = inputSide(state);
        return EngineeringSignal.clamp(level.getSignal(pos.relative(input), input));
    }

    protected int readInputFrom(Level level, BlockPos pos, Direction direction) {
        return EngineeringSignal.clamp(level.getSignal(pos.relative(direction), direction));
    }

    protected void updateOutput(Level level, BlockPos pos, BlockState state, int requestedOutput) {
        int output = EngineeringSignal.clamp(requestedOutput);
        int oldOutput = state.getValue(OUTPUT);
        if (oldOutput == output) return;

        BlockState next = state.setValue(OUTPUT, output);
        level.setBlock(pos, next, Block.UPDATE_CLIENTS);
        notifyNeighbors(level, pos, this, outputSide(next));
    }

    /**
     * Compatibility entry point used by existing HMI rotate buttons. A route rotation now rotates
     * the complete configured RX->TX path instead of silently moving only OUTPUT.
     */
    public static boolean rotateSeriesAxis(Level level, BlockPos pos, boolean clockwise) {
        return rotateWholeRoute(level, pos, clockwise);
    }

    /** Rotates INPUT and OUTPUT together as the normal route-rotation operation. */
    public static boolean rotateWholeRoute(Level level, BlockPos pos, boolean clockwise) {
        if (level.isClientSide) return false;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof DirectionalSignalBlock block)) return false;

        Direction oldOutput = seriesOutputSide(state);
        Direction oldInput = seriesInputSide(state);
        Direction newOutput = rotateHorizontal(oldOutput, clockwise);
        Direction newInput = rotateHorizontal(oldInput, clockwise);
        BlockState next = state
                .setValue(FACING, newOutput)
                .setValue(INPUT_FACING, newInput);
        level.setBlock(pos, next, Block.UPDATE_CLIENTS);

        notifyNeighbors(level, pos, block, oldInput, oldOutput, newInput, newOutput);
        if (level instanceof ServerLevel serverLevel) serverLevel.scheduleTick(pos, block, 1);
        return true;
    }

    /** Routes only INPUT while keeping OUTPUT fixed; invalid INPUT=OUTPUT states are skipped. */
    public static boolean rotateSeriesInput(Level level, BlockPos pos, boolean clockwise) {
        if (level.isClientSide) return false;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof DirectionalSignalBlock block)) return false;

        Direction output = seriesOutputSide(state);
        Direction oldInput = seriesInputSide(state);
        Direction newInput = rotateHorizontal(oldInput, clockwise);
        for (int i = 0; i < 3 && newInput == output; i++) {
            newInput = rotateHorizontal(newInput, clockwise);
        }
        if (newInput == output || newInput == oldInput) return false;

        BlockState next = state.setValue(INPUT_FACING, newInput);
        level.setBlock(pos, next, Block.UPDATE_CLIENTS);
        notifyNeighbors(level, pos, block, oldInput, newInput, output);
        if (level instanceof ServerLevel serverLevel) serverLevel.scheduleTick(pos, block, 1);
        return true;
    }

    /**
     * Routes only the OUTPUT face. The INPUT face remains fixed, ordinary processors stay 1-in/1-out,
     * and an invalid INPUT=OUTPUT state is skipped automatically.
     */
    public static boolean rotateSeriesOutput(Level level, BlockPos pos, boolean clockwise) {
        if (level.isClientSide) return false;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof DirectionalSignalBlock block)) return false;

        Direction input = seriesInputSide(state);
        Direction oldOutput = seriesOutputSide(state);
        Direction newOutput = rotateHorizontal(oldOutput, clockwise);
        for (int i = 0; i < 3 && newOutput == input; i++) {
            newOutput = rotateHorizontal(newOutput, clockwise);
        }
        if (newOutput == input || newOutput == oldOutput) return false;

        BlockState next = state.setValue(FACING, newOutput);
        level.setBlock(pos, next, Block.UPDATE_CLIENTS);
        notifyNeighbors(level, pos, block, oldOutput, newOutput, input);
        if (level instanceof ServerLevel serverLevel) serverLevel.scheduleTick(pos, block, 1);
        return true;
    }

    private static Direction rotateHorizontal(Direction direction, boolean clockwise) {
        return clockwise ? direction.getClockWise() : direction.getCounterClockWise();
    }

    private static void notifyNeighbors(Level level, BlockPos pos, Block block, Direction... sides) {
        level.updateNeighborsAt(pos, block);
        for (Direction side : sides) {
            level.updateNeighborsAt(pos.relative(side), block);
        }
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
                Direction clicked = hitResult.getDirection();
                Direction input = seriesInputSide(state);
                Direction output = seriesOutputSide(state);
                String action;
                boolean changed;
                if (clicked == input) {
                    changed = rotateSeriesInput(level, pos, true);
                    action = "RX";
                } else if (clicked == output) {
                    changed = rotateSeriesOutput(level, pos, true);
                    action = "TX";
                } else {
                    changed = rotateWholeRoute(level, pos, true);
                    action = "ROUTE";
                }
                BlockState next = level.getBlockState(pos);
                player.displayClientMessage(Component.literal(
                        (changed ? action + " rotated" : action + " unchanged")
                                + " | RX=" + seriesInputSide(next).getName().toUpperCase()
                                + " -> TX=" + seriesOutputSide(next).getName().toUpperCase()
                                + " | Shift-click RX/TX face to rotate that endpoint; other face rotates whole route"), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return direction == outputSide(state).getOpposite() ? state.getValue(OUTPUT) : 0;
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
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void onPlace(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState oldState,
            boolean movedByPiston
    ) {
        if (!level.isClientSide && !state.is(oldState.getBlock())) level.scheduleTick(pos, this, 1);
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
            notifyNeighbors(level, pos, this, inputSide(state), outputSide(state));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected abstract void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random);
}
