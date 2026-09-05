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
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.signal.EngineeringSignal;
import dev.redstoneengineering.ui.menu.SignalAnalyzerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RedStoneWireBlock;
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
 * Engineering signal analyzer with explicit topology, calibration and rolling
 * measurement-quality diagnostics.
 *
 * <p>TAP mode is a non-invasive measurement aperture. INLINE mode makes the
 * TEST and OUT faces a real 0..15 redstone path. Calibration affects display
 * only; it never mutates the physical pass-through value.</p>
 */
public class SignalAnalyzerBlock extends Block implements EngineeringPortProvider {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final IntegerProperty MODE = IntegerProperty.create("mode", 0, 1);
    public static final IntegerProperty OUTPUT = IntegerProperty.create("output", 0, 15);
    public static final IntegerProperty CALIBRATION = IntegerProperty.create("calibration", 0, 4);

    public static final int TAP = 0;
    public static final int INLINE = 1;
    public static final int DISPLAY_SAMPLES = 16;

    private static final String KEY = "signal_analyzer";
    private static final int SAMPLE_PERIOD_TICKS = 2;
    private static final int WINDOW = DISPLAY_SAMPLES;
    private static final int WINDOW_BASE = 17;
    private static final int RUNTIME_SIZE = WINDOW_BASE + WINDOW;

    public SignalAnalyzerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(MODE, TAP)
                .setValue(OUTPUT, 0)
                .setValue(CALIBRATION, 2));
    }

    @Override public MapCodec<SignalAnalyzerBlock> codec() { return RedstoneEngineering.SIGNAL_ANALYZER_CODEC.value(); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite()); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, MODE, OUTPUT, CALIBRATION); }

    private static Direction testSide(BlockState state) { return state.getValue(FACING); }
    private static Direction inlineOutputSide(BlockState state) { return testSide(state).getOpposite(); }
    private static int calibrationOffset(BlockState state) { return state.getValue(CALIBRATION) - 2; }
    private static int calibratedReading(BlockState state, int raw) { return EngineeringSignal.clamp(raw + calibrationOffset(state)); }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        EngineeringPort test = new EngineeringPort(
                state.getValue(MODE) == INLINE ? "TEST IN" : "TAP APERTURE",
                testSide(state), EngineeringDomain.REDSTONE,
                state.getValue(MODE) == INLINE ? PortKind.REDSTONE_ANALOG : PortKind.MEASUREMENT,
                PortDirection.INPUT, state.getValue(MODE) == INLINE, "signal");
        if (state.getValue(MODE) == TAP) return List.of(test);
        return List.of(
                test,
                new EngineeringPort(
                        "INLINE OUT", inlineOutputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.REDSTONE_ANALOG, PortDirection.OUTPUT, true, "signal"));
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        int value = side == inlineOutputSide(state) && state.getValue(MODE) == INLINE
                ? state.getValue(OUTPUT)
                : sampleTarget(level, pos, state);
        return Optional.of(EngineeringPortSnapshot.redstone(port.get(), value, PortQuality.VALID));
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        if (state.getValue(MODE) != INLINE || direction == null) return false;
        Direction physicalSide = direction.getOpposite();
        return physicalSide == testSide(state) || physicalSide == inlineOutputSide(state);
    }

    @Override protected boolean isSignalSource(BlockState state) { return state.getValue(MODE) == INLINE; }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (state.getValue(MODE) != INLINE) return 0;
        return direction == inlineOutputSide(state).getOpposite() ? state.getValue(OUTPUT) : 0;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !state.is(oldState.getBlock())) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int measured = sampleTarget(level, pos, state);
        recordSample(level, pos, measured);
        int requestedOutput = state.getValue(MODE) == INLINE ? measured : 0;
        if (state.getValue(OUTPUT) != requestedOutput) {
            BlockState next = state.setValue(OUTPUT, requestedOutput);
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            level.updateNeighborsAt(pos, this);
            level.updateNeighborsAt(pos.relative(inlineOutputSide(next)), this);
        }
        level.scheduleTick(pos, this, SAMPLE_PERIOD_TICKS);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            RuntimeIntStore.remove(level, KEY, pos);
            if (state.getValue(MODE) == INLINE) {
                level.updateNeighborsAt(pos, this);
                level.updateNeighborsAt(pos.relative(inlineOutputSide(state)), this);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private static int sampleTarget(Level level, BlockPos pos, BlockState state) {
        Direction side = testSide(state);
        BlockPos targetPos = pos.relative(side);
        return measureNode(level, targetPos, level.getBlockState(targetPos), side);
    }

    /** Runtime: totals/latest/min/max/edges/timestamps + 16-sample ring at 17..32. */
    private static void recordSample(Level level, BlockPos pos, int measured) {
        int[] r = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        int now = (int) Math.min(Integer.MAX_VALUE, level.getGameTime());
        r[0]++;
        r[14] = now;
        if (r[8] == 0) {
            r[1] = measured;
            r[2] = measured;
            r[3] = measured;
            r[7] = now;
            r[8] = 1;
        } else {
            r[2] = Math.min(r[2], measured);
            r[3] = Math.max(r[3], measured);
            int delta = measured - r[1];
            r[9] = delta;
            r[10] = Math.max(r[10], Math.abs(delta));
            if (delta != 0) {
                r[4]++;
                if (delta > 0) r[5]++; else r[6]++;
                r[7] = now;
            }
            r[1] = measured;
        }
        int write = Math.floorMod(r[12], WINDOW);
        r[WINDOW_BASE + write] = measured;
        r[12] = (write + 1) % WINDOW;
        r[13] = Math.min(WINDOW, r[13] + 1);
    }

    public record UiSnapshot(
            int mode,
            int calibrationOffset,
            int raw,
            int calibrated,
            int output,
            int lifeMin,
            int lifeMax,
            int changes,
            int rising,
            int falling,
            int lastDelta,
            int maxDelta,
            int windowCount,
            int average100,
            int peakToPeak,
            int meanStep100,
            int stableAgeTicks,
            int sampleAgeTicks,
            int totalSamples,
            int modeSwitches,
            int calibrationSwitches,
            int[] samples
    ) {}

    public static UiSnapshot uiSnapshot(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SignalAnalyzerBlock)) {
            int[] empty = new int[DISPLAY_SAMPLES];
            java.util.Arrays.fill(empty, -1);
            return new UiSnapshot(TAP, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, -1, 0, 0, 0, empty);
        }

        int raw = sampleTarget(level, pos, state);
        int[] r = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        int now = (int) Math.min(Integer.MAX_VALUE, level.getGameTime());
        int count = Math.max(0, Math.min(WINDOW, r[13]));
        int[] samples = new int[DISPLAY_SAMPLES];
        java.util.Arrays.fill(samples, -1);
        int padding = DISPLAY_SAMPLES - count;
        for (int i = 0; i < count; i++) samples[padding + i] = rollingSample(r, count, i);

        return new UiSnapshot(
                state.getValue(MODE), calibrationOffset(state), raw, calibratedReading(state, raw), state.getValue(OUTPUT),
                r[8] == 0 ? raw : r[2], r[8] == 0 ? raw : r[3], r[4], r[5], r[6], r[9], r[10], count,
                rollingAverage100(r, count), rollingPeakToPeak(r, count), rollingMeanStep100(r, count),
                r[8] == 0 ? 0 : Math.max(0, now - r[7]),
                r[8] == 0 ? -1 : Math.max(0, now - r[14]),
                r[0], r[11], r[15], samples
        );
    }

    /** Applies bounded UI intent only; sampling and pass-through remain tick-authoritative. */
    public static boolean applyUiAction(Level level, BlockPos pos, int action) {
        if (level.isClientSide) return false;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SignalAnalyzerBlock analyzer)) return false;

        switch (action) {
            case SignalAnalyzerMenu.BUTTON_MODE_TOGGLE -> {
                int nextMode = state.getValue(MODE) == TAP ? INLINE : TAP;
                BlockState next = state.setValue(MODE, nextMode)
                        .setValue(OUTPUT, nextMode == INLINE ? state.getValue(OUTPUT) : 0);
                level.setBlock(pos, next, Block.UPDATE_CLIENTS);
                RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE)[11]++;
                level.updateNeighborsAt(pos, analyzer);
                level.updateNeighborsAt(pos.relative(inlineOutputSide(next)), analyzer);
                level.scheduleTick(pos, analyzer, 1);
            }
            case SignalAnalyzerMenu.BUTTON_CALIBRATION_DECREASE -> {
                int encoded = Math.floorMod(state.getValue(CALIBRATION) - 1, 5);
                level.setBlock(pos, state.setValue(CALIBRATION, encoded), Block.UPDATE_CLIENTS);
                RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE)[15]++;
            }
            case SignalAnalyzerMenu.BUTTON_CALIBRATION_INCREASE -> {
                int encoded = (state.getValue(CALIBRATION) + 1) % 5;
                level.setBlock(pos, state.setValue(CALIBRATION, encoded), Block.UPDATE_CLIENTS);
                RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE)[15]++;
            }
            case SignalAnalyzerMenu.BUTTON_RESET_HISTORY -> RuntimeIntStore.remove(level, KEY, pos);
            default -> { return false; }
        }
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) showSixSideSurvey(level, pos, player);
            else {
                serverPlayer.openMenu(
                        new SimpleMenuProvider(
                                (containerId, inventory, ignored) -> new SignalAnalyzerMenu(containerId, inventory, pos),
                                Component.translatable("block.redstoneengineering.signal_analyzer")
                        ),
                        data -> data.writeBlockPos(pos)
                );
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static int rollingAverage100(int[] r, int count) {
        if (count <= 0) return 0;
        int sum = 0;
        for (int i = 0; i < count; i++) sum += rollingSample(r, count, i);
        return (sum * 100 + count / 2) / count;
    }

    private static int rollingPeakToPeak(int[] r, int count) {
        if (count <= 0) return 0;
        int lo = 15, hi = 0;
        for (int i = 0; i < count; i++) {
            int value = rollingSample(r, count, i);
            lo = Math.min(lo, value);
            hi = Math.max(hi, value);
        }
        return hi - lo;
    }

    private static int rollingMeanStep100(int[] r, int count) {
        if (count < 2) return 0;
        int total = 0;
        int before = rollingSample(r, count, 0);
        for (int i = 1; i < count; i++) {
            int now = rollingSample(r, count, i);
            total += Math.abs(now - before);
            before = now;
        }
        return (total * 100 + (count - 1) / 2) / (count - 1);
    }

    private static int rollingSample(int[] r, int count, int chronologicalIndex) {
        int oldest = Math.floorMod(r[12] - count, WINDOW);
        int slot = (oldest + chronologicalIndex) % WINDOW;
        return r[WINDOW_BASE + slot];
    }

    private static void showSixSideSurvey(Level level, BlockPos analyzerPos, Player player) {
        StringBuilder text = new StringBuilder("Analyzer SURVEY");
        int strongest = -1;
        Direction strongestSide = Direction.NORTH;
        for (Direction side : Direction.values()) {
            BlockPos targetPos = analyzerPos.relative(side);
            int value = measureNode(level, targetPos, level.getBlockState(targetPos), side);
            text.append(" | ").append(shortName(side)).append("=").append(value);
            if (value > strongest) {
                strongest = value;
                strongestSide = side;
            }
        }
        text.append(" | strongest=").append(shortName(strongestSide)).append(":").append(Math.max(0, strongest));
        player.displayClientMessage(Component.literal(text.toString()), true);
    }

    public static int measureNode(Level level, BlockPos targetPos, BlockState targetState) {
        return measureNode(level, targetPos, targetState, null);
    }

    public static int measureNode(Level level, BlockPos targetPos, BlockState targetState, @Nullable Direction instrumentToTarget) {
        if (targetState.getBlock() instanceof RedstoneSignalCableBlock) return EngineeringSignal.clamp(RedstoneSignalCableBlock.power(level, targetPos));
        if (targetState.getBlock() instanceof RedstoneCableJunctionBlock) return EngineeringSignal.clamp(RedstoneCableJunctionBlock.power(level, targetPos));
        if (targetState.getBlock() instanceof RedstoneCableTerminalBlock) return EngineeringSignal.clamp(targetState.getValue(RedstoneCableTerminalBlock.POWER));
        if (targetState.getBlock() instanceof RedStoneWireBlock) return EngineeringSignal.clamp(targetState.getValue(RedStoneWireBlock.POWER));

        if (instrumentToTarget != null) {
            int directional = targetState.getSignal(level, targetPos, instrumentToTarget);
            if (directional > 0 || targetState.isSignalSource()) return EngineeringSignal.clamp(directional);
        } else {
            int emitted = 0;
            for (Direction direction : Direction.values()) emitted = Math.max(emitted, targetState.getSignal(level, targetPos, direction));
            if (emitted > 0) return EngineeringSignal.clamp(emitted);
        }
        return EngineeringSignal.clamp(level.getBestNeighborSignal(targetPos));
    }

    private static String shortName(Direction direction) {
        return switch (direction) {
            case NORTH -> "N";
            case SOUTH -> "S";
            case EAST -> "E";
            case WEST -> "W";
            case UP -> "U";
            case DOWN -> "D";
        };
    }
}
