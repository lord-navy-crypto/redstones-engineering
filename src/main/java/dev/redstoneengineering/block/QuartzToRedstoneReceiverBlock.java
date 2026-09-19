package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.PrecisionObservationSupport;
import dev.redstoneengineering.physics.RuntimeIntStore;
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
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Quartz timing -> one-tick Redstone event receiver.
 *
 * <p>Only a trustworthy LOW->HIGH transition creates a pulse. The first valid sample after
 * placement or an evidence gap establishes phase baseline only, preventing fabricated edges
 * after chunk loss, source conflicts, or other uncertain timing evidence.</p>
 */
public class QuartzToRedstoneReceiverBlock extends Block implements EngineeringPortProvider {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final DirectionProperty INPUT_FACING =
            DirectionProperty.create("input_facing", Direction.Plane.HORIZONTAL);
    public static final IntegerProperty POWER = IntegerProperty.create("power", 0, 15);

    private static final String KEY = "quartz_to_redstone_receiver";
    private static final int PREVIOUS = 0;
    private static final int INITIALIZED = 1;
    private static final int QUALITY = 2;
    private static final int PERIOD = 3;
    private static final int EDGE_COUNT = 4;
    private static final int RUNTIME_SIZE = 5;

    public QuartzToRedstoneReceiverBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(INPUT_FACING, Direction.SOUTH)
                .setValue(POWER, 0));
    }

    @Override public MapCodec<QuartzToRedstoneReceiverBlock> codec() {
        return RedstoneEngineering.QUARTZ_TO_REDSTONE_RECEIVER_CODEC.value();
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, INPUT_FACING, POWER);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction output = context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, output).setValue(INPUT_FACING, output.getOpposite());
    }

    public static Direction outputSide(BlockState state) { return state.getValue(FACING); }
    public static Direction inputSide(BlockState state) { return state.getValue(INPUT_FACING); }

    public static int edgeCount(Level level, BlockPos pos) {
        int[] rt = RuntimeIntStore.peek(level, KEY, pos);
        return rt == null || rt.length != RUNTIME_SIZE ? 0 : Math.max(0, rt[EDGE_COUNT]);
    }

    public static int observedPeriod(Level level, BlockPos pos) {
        int[] rt = RuntimeIntStore.peek(level, KEY, pos);
        return rt == null || rt.length != RUNTIME_SIZE ? 0 : Math.max(0, rt[PERIOD]);
    }

    public static PortQuality inputQuality(Level level, BlockPos pos) {
        int[] rt = RuntimeIntStore.peek(level, KEY, pos);
        if (rt == null || rt.length != RUNTIME_SIZE || rt[QUALITY] <= 0) return PortQuality.STALE;
        int ordinal = rt[QUALITY] - 1;
        return ordinal >= 0 && ordinal < PortQuality.values().length
                ? PortQuality.values()[ordinal] : PortQuality.STALE;
    }

    private static int encodeQuality(PortQuality quality) { return quality.ordinal() + 1; }

    @Override public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("QUARTZ CLOCK IN", inputSide(state), EngineeringDomain.QUARTZ,
                        PortKind.CONVERTER, PortDirection.INPUT, false, "clock"),
                new EngineeringPort("REDSTONE EDGE OUT", outputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.CONVERTER, PortDirection.OUTPUT, true, "pulse")
        );
    }

    @Override public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        if (side == inputSide(state)) {
            var q = PrecisionObservationSupport.quartz(level, pos.relative(inputSide(state)));
            return Optional.of(new EngineeringPortSnapshot(
                    port.get(), q.active() ? 1.0 : 0.0, 0.0, 1.0, q.quality()));
        }
        return Optional.of(EngineeringPortSnapshot.redstone(
                port.get(), state.getValue(POWER), inputQuality(level, pos)));
    }

    @Override public boolean canConnectRedstone(
            BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction
    ) {
        return direction != null && direction == outputSide(state).getOpposite();
    }

    @Override protected boolean isSignalSource(BlockState state) { return true; }

    @Override protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return direction == outputSide(state).getOpposite() ? state.getValue(POWER) : 0;
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override protected void neighborChanged(
            BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean moved
    ) {
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        var q = PrecisionObservationSupport.quartz(level, pos.relative(inputSide(state)));
        int[] rt = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        rt[QUALITY] = encodeQuality(q.quality());

        int nextPower = 0;
        if (q.valid()) {
            rt[PERIOD] = q.periodTicks();
            if (rt[INITIALIZED] == 0) {
                // First trustworthy observation establishes input phase only.
                rt[PREVIOUS] = q.active() ? 1 : 0;
                rt[INITIALIZED] = 1;
            } else {
                boolean rising = rt[PREVIOUS] == 0 && q.active();
                if (rising) {
                    nextPower = 15;
                    if (rt[EDGE_COUNT] < Integer.MAX_VALUE) rt[EDGE_COUNT]++;
                }
                rt[PREVIOUS] = q.active() ? 1 : 0;
            }
        } else {
            // Any evidence gap invalidates edge continuity. Reacquisition must establish a new baseline.
            rt[INITIALIZED] = 0;
            if (q.quality() == PortQuality.NO_SIGNAL) {
                rt[PREVIOUS] = 0;
                rt[PERIOD] = 0;
            }
        }

        if (state.getValue(POWER) != nextPower) {
            BlockState next = state.setValue(POWER, nextPower);
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            level.updateNeighborsAt(pos, this);
            level.updateNeighborsAt(pos.relative(outputSide(next)), this);
        }
        level.scheduleTick(pos, this, 1);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, KEY, pos);
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!player.isShiftKeyDown()) {
                FieldDeviceUi.open(serverPlayer, pos);
            } else {
                player.displayClientMessage(Component.literal(
                        "Quartz->Redstone Receiver | period=" + observedPeriod(level, pos) + "t"
                                + " | risingEdges=" + edgeCount(level, pos)
                                + " | output=" + state.getValue(POWER) + "/15"
                                + " | quality=" + inputQuality(level, pos)
                                + " | baseline-after-gap prevents fabricated pulses"), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
