package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.EngineeringSystemsModule;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.RedstoneObservationSupport;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/**
 * Controlled reliability-test fault injector.
 * BACK=signal input, RIGHT=ARM, FRONT=faulted output.
 * Modes: 0 STUCK_LOW, 1 STUCK_HIGH, 2 BIAS_PLUS_4, 3 BIAS_MINUS_4.
 */
public class FaultInjectorBlock extends PassiveDirectionalSignalBlock {
    public static final int MIN_MODE = 0;
    public static final int MAX_MODE = 3;
    public static final int DEFAULT_MODE = 0;
    public static final int MODE_STUCK_LOW = 0;
    public static final int MODE_STUCK_HIGH = 1;
    public static final int MODE_BIAS_PLUS = 2;
    public static final int MODE_BIAS_MINUS = 3;
    public static final int BIAS_STEP = 4;
    public static final int MAX_SIGNAL = 15;
    public static final IntegerProperty MODE = IntegerProperty.create("mode", MIN_MODE, MAX_MODE);
    private static final String KEY = "fault_injector";
    private static final String[] MODE_LABELS = {"STUCK LOW", "STUCK HIGH", "BIAS +4", "BIAS -4"};
    private static final int RUNTIME_SIZE = 5;

    public FaultInjectorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(MODE, DEFAULT_MODE));
    }

    @Override
    public MapCodec<FaultInjectorBlock> codec() {
        return EngineeringSystemsModule.FAULT_INJECTOR_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MODE);
    }

    @Override
    protected boolean isEngineeringPort(BlockState state, Direction side) {
        Direction front = outputSide(state);
        return side == inputSide(state) || side == rightOf(front) || side == front;
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        Direction front = outputSide(state);
        return List.of(
                new EngineeringPort("SIGNAL IN", inputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.REDSTONE_ANALOG, PortDirection.INPUT, true, "signal"),
                new EngineeringPort("FAULT ARM", rightOf(front), EngineeringDomain.REDSTONE,
                        PortKind.CONTROL, PortDirection.INPUT, true, "arm"),
                new EngineeringPort("FAULTED OUT", front, EngineeringDomain.REDSTONE,
                        PortKind.REDSTONE_ANALOG, PortDirection.OUTPUT, true, "faulted_signal")
        );
    }

    private record Evidence(
            RedstoneObservationSupport.Observation signal,
            RedstoneObservationSupport.Observation arm
    ) {}

    private Evidence evidence(Level level, BlockPos pos, BlockState state) {
        Direction front = outputSide(state);
        return new Evidence(
                RedstoneObservationSupport.observe(level, pos, inputSide(state)),
                RedstoneObservationSupport.observe(level, pos, rightOf(front))
        );
    }

    public static PortQuality signalQuality(Level level, BlockPos pos, BlockState state) {
        return RedstoneObservationSupport.observe(
                level, pos, DirectionalSignalBlock.seriesInputSide(state)).quality();
    }

    public static PortQuality armQuality(Level level, BlockPos pos, BlockState state) {
        Direction front = DirectionalSignalBlock.seriesOutputSide(state);
        return RedstoneObservationSupport.observe(level, pos, rightOf(front)).quality();
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        Direction front = outputSide(state);
        Evidence evidence = evidence(level, pos, state);
        if (side == front) {
            PortQuality quality = RedstoneObservationSupport.combineQuality(
                    evidence.signal().quality(), evidence.arm().quality());
            if (active(level, pos)) {
                quality = RedstoneObservationSupport.combineQuality(quality, PortQuality.FAULT);
            }
            return Optional.of(EngineeringPortSnapshot.redstone(
                    port.get(), state.getValue(OUTPUT), quality));
        }
        RedstoneObservationSupport.Observation observation =
                side == inputSide(state) ? evidence.signal() : evidence.arm();
        return Optional.of(EngineeringPortSnapshot.redstone(
                port.get(), observation.value(), observation.quality()));
    }

    @Override
    protected int computeOutput(Level level, BlockPos pos, BlockState state) {
        Evidence evidence = evidence(level, pos, state);
        int input = evidence.signal().value();
        boolean armed = evidence.arm().valid() && evidence.arm().value() > 0;
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        if (armed && runtime[0] == 0) runtime[1]++;
        runtime[0] = armed ? 1 : 0;

        int output = input;
        if (armed) {
            output = applyFault(input, state.getValue(MODE));
            if (output != input) runtime[2]++;
        }
        runtime[3] = input;
        runtime[4] = output;
        return output;
    }

    public static int boundedMode(int mode) {
        return Math.max(MIN_MODE, Math.min(MAX_MODE, mode));
    }

    public static int boundedSignal(int value) {
        return Math.max(0, Math.min(MAX_SIGNAL, value));
    }

    public static int applyFault(int input, int mode) {
        int x = boundedSignal(input);
        return switch (boundedMode(mode)) {
            case MODE_STUCK_LOW -> 0;
            case MODE_STUCK_HIGH -> MAX_SIGNAL;
            case MODE_BIAS_PLUS -> Math.min(MAX_SIGNAL, x + BIAS_STEP);
            case MODE_BIAS_MINUS -> Math.max(0, x - BIAS_STEP);
            default -> x;
        };
    }

    public static String transferLawText(int mode) {
        return switch (boundedMode(mode)) {
            case MODE_STUCK_LOW -> "y = 0";
            case MODE_STUCK_HIGH -> "y = " + MAX_SIGNAL;
            case MODE_BIAS_PLUS -> "y = min(" + MAX_SIGNAL + ", x + " + BIAS_STEP + ")";
            case MODE_BIAS_MINUS -> "y = max(0, x - " + BIAS_STEP + ")";
            default -> "y = x";
        };
    }

    public static boolean active(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime != null && runtime.length > 0 && runtime[0] != 0;
    }

    public static int activationCount(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length < 2 ? 0 : runtime[1];
    }

    public static int effectCount(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length < 3 ? 0 : Math.max(0, runtime[2]);
    }

    public static int lastInput(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length < 4 ? 0 : runtime[3];
    }

    public static int lastOutput(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length < 5 ? 0 : runtime[4];
    }

    public static String modeLabel(BlockState state) {
        return modeLabelFor(state.getValue(MODE));
    }

    public static String modeLabelFor(int mode) {
        return MODE_LABELS[boundedMode(mode)];
    }

    public boolean adjustMode(Level level, BlockPos pos, int delta) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(this)) return false;
        int current = boundedMode(state.getValue(MODE));
        int next = MIN_MODE + Math.floorMod(
                current - MIN_MODE + delta, MAX_MODE - MIN_MODE + 1);
        BlockState updated = state.setValue(MODE, next);
        level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
        if (level instanceof ServerLevel server) server.scheduleTick(pos, this, 1);
        return true;
    }

    /** Clears retained fault statistics while preserving live ARM state and the latest I/O evidence. */
    public boolean resetDiagnostics(Level level, BlockPos pos) {
        if (!level.getBlockState(pos).is(this)) return false;
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        if (runtime == null || runtime.length < RUNTIME_SIZE) return true;
        runtime[1] = 0;
        runtime[2] = 0;
        return true;
    }

    public void cycleMode(Level level, BlockPos pos) {
        adjustMode(level, pos, 1);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server) server.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        updateOutput(level, pos, state, outputValue(level, pos, state));
        level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, KEY, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                resetDiagnostics(level, pos);
                player.displayClientMessage(Component.literal("Fault injector statistics reset"), true);
            } else {
                FieldDeviceUi.openUniversal(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
