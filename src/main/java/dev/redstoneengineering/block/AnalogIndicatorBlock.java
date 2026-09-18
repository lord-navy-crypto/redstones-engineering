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
import dev.redstoneengineering.signal.EngineeringSignal;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.physics.RedstoneObservationSupport;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/** Front-facing process measurement display with a single BACK redstone input port. */
public class AnalogIndicatorBlock extends DirectionalRedstoneEndpointBlock implements EngineeringPortProvider {
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 15);

    private static final String RUNTIME_KEY = "analog_indicator";
    private static final int MIN_SLOT = 0;
    private static final int MAX_SLOT = 1;
    private static final int SAMPLE_COUNT_SLOT = 2;
    private static final int INITIALIZED_SLOT = 3;
    private static final int RUNTIME_SIZE = 4;

    public record InputObservation(int value, PortQuality quality) {}

    public AnalogIndicatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LEVEL, 0));
    }

    @Override
    public MapCodec<AnalogIndicatorBlock> codec() {
        return RedstoneEngineering.ANALOG_INDICATOR_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LEVEL);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(new EngineeringPort(
                "SIGNAL IN",
                backSide(state),
                EngineeringDomain.REDSTONE,
                PortKind.MEASUREMENT,
                PortDirection.INPUT,
                true,
                "signal"
        ));
    }

    /**
     * Read a redstone value and its independent source evidence. A connected source
     * configured to zero is a real measurement; an empty back face is NO_SIGNAL.
     * Missing chunk coverage is STALE and must not overwrite the last displayed value.
     * Engineering-aware upstream devices also propagate their quality state instead of
     * being reduced to the weaker "a block exists here" heuristic.
     */
    public InputObservation inputObservation(Level level, BlockPos pos, BlockState state) {
        var observation = RedstoneObservationSupport.observe(level, pos, backSide(state));
        return new InputObservation(observation.value(), observation.quality());
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level,
            BlockPos pos,
            BlockState state,
            Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        InputObservation observation = inputObservation(level, pos, state);
        return Optional.of(EngineeringPortSnapshot.redstone(
                port.get(), observation.value(), observation.quality()));
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            @Nullable Direction direction
    ) {
        return direction != null && connectionMatches(direction, backSide(state));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide) update(level, pos, state);
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
        if (!level.isClientSide) update(level, pos, state);
    }

    @Override
    protected void onEndpointRouteChanged(Level level, BlockPos pos, BlockState oldState, BlockState newState) {
        update(level, pos, newState);
    }

    private void update(Level level, BlockPos pos, BlockState state) {
        InputObservation observation = inputObservation(level, pos, state);
        if (observation.quality() == PortQuality.STALE) return;
        int value = observation.value();
        if (observation.quality() == PortQuality.VALID) {
            int[] runtime = RuntimeIntStore.get(level, RUNTIME_KEY, pos, RUNTIME_SIZE);
            if (runtime[INITIALIZED_SLOT] == 0) {
                runtime[MIN_SLOT] = value;
                runtime[MAX_SLOT] = value;
                runtime[INITIALIZED_SLOT] = 1;
            } else {
                runtime[MIN_SLOT] = Math.min(runtime[MIN_SLOT], value);
                runtime[MAX_SLOT] = Math.max(runtime[MAX_SLOT], value);
            }
            if (runtime[SAMPLE_COUNT_SLOT] < Integer.MAX_VALUE) runtime[SAMPLE_COUNT_SLOT]++;
        }
        if (value != state.getValue(LEVEL)) {
            level.setBlock(pos, state.setValue(LEVEL, value), Block.UPDATE_CLIENTS);
        }
    }

    public static int retainedMinimum(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, RUNTIME_KEY, pos);
        return runtime == null || runtime.length < RUNTIME_SIZE || runtime[INITIALIZED_SLOT] == 0 ? -1 : runtime[MIN_SLOT];
    }

    public static int retainedMaximum(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, RUNTIME_KEY, pos);
        return runtime == null || runtime.length < RUNTIME_SIZE || runtime[INITIALIZED_SLOT] == 0 ? -1 : runtime[MAX_SLOT];
    }

    public static int sampleCount(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, RUNTIME_KEY, pos);
        return runtime == null || runtime.length < RUNTIME_SIZE ? 0 : Math.max(0, runtime[SAMPLE_COUNT_SLOT]);
    }

    public static boolean resetExtrema(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof AnalogIndicatorBlock indicator)) return false;
        InputObservation observation = indicator.inputObservation(level, pos, state);
        int[] runtime = RuntimeIntStore.get(level, RUNTIME_KEY, pos, RUNTIME_SIZE);
        int value = observation.quality() == PortQuality.VALID ? observation.value() : state.getValue(LEVEL);
        runtime[MIN_SLOT] = value;
        runtime[MAX_SLOT] = value;
        runtime[SAMPLE_COUNT_SLOT] = 0;
        runtime[INITIALIZED_SLOT] = 1;
        return true;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, RUNTIME_KEY, pos);
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                InputObservation observation = inputObservation(level, pos, state);
                player.displayClientMessage(Component.literal(
                        "Analog Process Indicator = " + state.getValue(LEVEL) + "/15"
                                + " | inputQuality=" + observation.quality()
                                + " | FRONT display=" + frontSide(state).getName()
                                + " BACK IN=" + backSide(state).getName()
                                + " | min=" + retainedMinimum(level, pos)
                                + " max=" + retainedMaximum(level, pos)
                                + " samples=" + sampleCount(level, pos)
                                + " | readout-only"
                ), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
