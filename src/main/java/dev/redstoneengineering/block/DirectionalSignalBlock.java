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
 * <p>Alpha 1.0.10 makes BACK/FRONT a real EngineeringPort contract so every
 * subclass automatically exposes the same topology to diagnostics and UI.</p>
 *
 * <p>The shared interaction contract is explicitly series-oriented: BACK is the
 * only input side and FRONT is the only output side. The whole axis may be rotated
 * in 90-degree steps without swapping the processing function or allowing ambiguous
 * side inputs. Subclasses with richer configuration may override the interaction
 * method while still using {@link #rotateSeriesAxis}.</p>
 */
public abstract class DirectionalSignalBlock extends Block implements EngineeringPortProvider {
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
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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

    public static Direction seriesOutputSide(BlockState state) {
        return state.getValue(FACING);
    }

    public static Direction seriesInputSide(BlockState state) {
        return seriesOutputSide(state).getOpposite();
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
        Direction back = inputSide(state);
        return EngineeringSignal.clamp(level.getSignal(pos.relative(back), back));
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
     * Rotates the complete INPUT -> PROCESS -> OUTPUT axis and refreshes both old and new
     * endpoints. The logical server remains authoritative and the current 0..15 output value
     * is preserved until the scheduled processor tick evaluates the new input side.
     */
    public static boolean rotateSeriesAxis(Level level, BlockPos pos, boolean clockwise) {
        if (level.isClientSide) return false;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof DirectionalSignalBlock block)) return false;

        Direction oldOutput = state.getValue(FACING);
        Direction oldInput = oldOutput.getOpposite();
        Direction newOutput = clockwise ? oldOutput.getClockWise() : oldOutput.getCounterClockWise();
        BlockState next = state.setValue(FACING, newOutput);
        level.setBlock(pos, next, Block.UPDATE_CLIENTS);

        notifyNeighbors(level, pos, block, oldInput, oldOutput, newOutput.getOpposite(), newOutput);
        if (level instanceof ServerLevel serverLevel) serverLevel.scheduleTick(pos, block, 1);
        return true;
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
            notifyNeighbors(level, pos, this, outputSide(state));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected abstract void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random);
}
