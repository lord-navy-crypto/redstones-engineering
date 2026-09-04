package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.signal.EngineeringSignal;
import dev.redstoneengineering.ui.menu.SignalAnalyzerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
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

/**
 * Engineering signal analyzer with explicit topology, calibration and rolling
 * measurement-quality diagnostics.
 *
 * TAP mode is non-invasive. INLINE mode measures TEST/FACING and reproduces the
 * raw 0..15 sample on the opposite OUT face. Calibration only changes the
 * displayed engineering reading; it never alters the INLINE pass-through.
 */
public class SignalAnalyzerBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final IntegerProperty MODE = IntegerProperty.create("mode", 0, 1);
    public static final IntegerProperty OUTPUT = IntegerProperty.create("output", 0, 15);
    // Encoded 0..4 => calibration offset -2..+2. Small persistent configuration only.
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
        registerDefaultState(
                stateDefinition.any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(MODE, TAP)
                        .setValue(OUTPUT, 0)
                        .setValue(CALIBRATION, 2)
        );
    }

    @Override
    public MapCodec<SignalAnalyzerBlock> codec() {
        return RedstoneEngineering.SIGNAL_ANALYZER_CODEC.value();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(
                FACING,
                context.getClickedFace().getOpposite()
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MODE, OUTPUT, CALIBRATION);
    }

    private static Direction testSide(BlockState state) {
        return state.getValue(FACING);
    }

    private static Direction inlineOutputSide(BlockState state) {
        return testSide(state).getOpposite();
    }

    private static int calibrationOffset(BlockState state) {
        return state.getValue(CALIBRATION) - 2;
    }

    private static int calibratedReading(BlockState state, int raw) {
        return EngineeringSignal.clamp(raw + calibrationOffset(state));
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            @Nullable Direction direction
    ) {
        if (state.getValue(MODE) != INLINE || direction == null) return false;
        Direction physicalSide = direction.getOpposite();
        return physicalSide == testSide(state) || physicalSide == inlineOutputSide(state);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return state.getValue(MODE) == INLINE;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (state.getValue(MODE) != INLINE) return 0;
        // IMPORTANT: output is the raw measurement, never the calibrated display value.
        return direction == inlineOutputSide(state).getOpposite()
                ? state.getValue(OUTPUT)
                : 0;
    }

    @Override
    protected void onPlace(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState oldState,
            boolean movedByPiston
    ) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !state.is(oldState.getBlock())) {
            level.scheduleTick(pos, this, 1);
        }
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
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int measured = sampleTarget(level, pos, state);
        recordSample(level, pos, measured);

        // INLINE remains a transparent 0..15 engineering pass-through.
        int requestedOutput = state.getValue(MODE) == INLINE ? measured : 0;
        if (state.getValue(OUTPUT) != requestedOutput) {
            BlockState next = state.setValue(OUTPUT, requestedOutput);
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            level.updateNeighborsAt(pos, this);
            level.updateNeighborsAt(pos.relative(inlineOutputSide(next)), this);
        }

        level.scheduleTick(pos, this, SAMPLE_PERIOD_TICKS);
    }

    private static int sampleTarget(Level level, BlockPos pos, BlockState state) {
        Direction side = testSide(state);
        BlockPos targetPos = pos.relative(side);
        return measureNode(level, targetPos, level.getBlockState(targetPos), side);
    }

    /** Runtime layout: 0 total, 1 latest, 2 min, 3 max, 4 changes, 5 rising,
     * 6 falling, 7 last-change tick, 8 initialized, 9 last delta, 10 max delta,
     * 11 mode switches, 12 ring write index, 13 ring count, 14 last sample tick,
     * 15 calibration switches, 16 reserved, 17..32 rolling samples. */
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
                if (delta > 0) r[5]++;
                else r[6]++;
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

    /** Bounded, read-only server snapshot consumed by the Engineering UI menu. */
    public static UiSnapshot uiSnapshot(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SignalAnalyzerBlock)) return new UiSnapshot(
                TAP, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, -1, 0, 0, 0, new int[DISPLAY_SAMPLES]
        );

        int raw = sampleTarget(level, pos, state);
        int calibrated = calibratedReading(state, raw);
        int[] r = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        int now = (int) Math.min(Integer.MAX_VALUE, level.getGameTime());
        int count = Math.max(0, Math.min(WINDOW, r[13]));
        int stableAge = r[8] == 0 ? 0 : Math.max(0, now - r[7]);
        int sampleAge = r[8] == 0 ? -1 : Math.max(0, now - r[14]);
        int[] samples = new int[DISPLAY_SAMPLES];
        for (int i = 0; i < DISPLAY_SAMPLES; i++) samples[i] = -1;
        int padding = DISPLAY_SAMPLES - count;
        for (int i = 0; i < count; i++) samples[padding + i] = rollingSample(r, count, i);

        return new UiSnapshot(
                state.getValue(MODE),
                calibrationOffset(state),
                raw,
                calibrated,
                state.getValue(OUTPUT),
                r[8] == 0 ? raw : r[2],
                r[8] == 0 ? raw : r[3],
                r[4],
                r[5],
                r[6],
                r[9],
                r[10],
                count,
                rollingAverage100(r, count),
                rollingPeakToPeak(r, count),
                rollingMeanStep100(r, count),
                stableAge,
                sampleAge,
                r[0],
                r[11],
                r[15],
                samples
        );
    }

    /** Applies only bounded UI intent on the logical server; measurement remains tick-authoritative. */
    public static boolean applyUiAction(Level level, BlockPos pos, int action) {
        if (level.isClientSide) return false;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SignalAnalyzerBlock analyzer)) return false;

        switch (action) {
            case SignalAnalyzerMenu.BUTTON_MODE_TOGGLE -> {
                int nextMode = state.getValue(MODE) == TAP ? INLINE : TAP;
                BlockState next = state
                        .setValue(MODE, nextMode)
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
            default -> {
                return false;
            }
        }
        return true;
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
                showSixSideSurvey(level, pos, player);
            } else {
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

    private static void showMeasurement(Level level, BlockPos pos, BlockState state, Player player) {
        Direction side = testSide(state);
        BlockPos targetPos = pos.relative(side);
        BlockState targetState = level.getBlockState(targetPos);
        int raw = measureNode(level, targetPos, targetState, side);
        int calibrated = calibratedReading(state, raw);
        int[] r = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        int now = (int) Math.min(Integer.MAX_VALUE, level.getGameTime());
        int stableAge = r[8] == 0 ? 0 : Math.max(0, now - r[7]);
        int sampleAge = r[8] == 0 ? -1 : Math.max(0, now - r[14]);
        int count = Math.max(0, Math.min(WINDOW, r[13]));
        int avg100 = rollingAverage100(r, count);
        int p2p = rollingPeakToPeak(r, count);
        int meanStep100 = rollingMeanStep100(r, count);

        String targetName = BuiltInRegistries.BLOCK.getKey(targetState.getBlock()).toString();
        player.displayClientMessage(
                Component.literal(
                        "Analyzer " + modeName(state.getValue(MODE))
                                + " | TEST=" + shortName(side)
                                + " raw=" + raw + "/15"
                                + " cal=" + calibrated + "/15(offset " + signed(calibrationOffset(state)) + ")"
                                + (state.getValue(MODE) == INLINE
                                ? " OUT=" + shortName(inlineOutputSide(state)) + ":" + state.getValue(OUTPUT) + "(RAW)"
                                : " non-invasive")
                                + " | life min/max=" + (r[8] == 0 ? raw : r[2]) + "/" + (r[8] == 0 ? raw : r[3])
                                + " changes=" + r[4]
                                + " ↑" + r[5] + " ↓" + r[6]
                                + " lastΔ=" + r[9] + " maxΔ=" + r[10]
                                + " | window=" + count + "/" + WINDOW
                                + " avg=" + decimal100(avg100)
                                + " p2p=" + p2p
                                + " meanStep=" + decimal100(meanStep100)
                                + " state=" + stabilityClass(count, p2p, meanStep100)
                                + " | stableFor=" + stableAge + "t"
                                + " sampleAge=" + sampleAge + "t"
                                + " samples=" + r[0]
                                + " modeSwitches=" + r[11]
                                + " calSwitches=" + r[15]
                                + " | " + targetName
                ),
                true
        );
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
            int v = rollingSample(r, count, i);
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
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

    private static String stabilityClass(int count, int p2p, int meanStep100) {
        if (count < 4) return "WARMUP";
        if (p2p == 0 && meanStep100 == 0) return "STEADY";
        if (p2p <= 1 && meanStep100 <= 50) return "STABLE";
        if (p2p <= 5 && meanStep100 <= 200) return "DYNAMIC";
        return "HIGH_VARIATION";
    }

    private static String decimal100(int value100) {
        int abs = Math.abs(value100);
        return (value100 < 0 ? "-" : "") + (abs / 100) + "." + String.format("%02d", abs % 100);
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
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
        text.append(" | strongest=")
                .append(shortName(strongestSide))
                .append(":")
                .append(Math.max(0, strongest));
        player.displayClientMessage(Component.literal(text.toString()), true);
    }

    public static int measureNode(Level level, BlockPos targetPos, BlockState targetState) {
        return measureNode(level, targetPos, targetState, null);
    }

    public static int measureNode(
            Level level,
            BlockPos targetPos,
            BlockState targetState,
            @Nullable Direction instrumentToTarget
    ) {
        if (targetState.getBlock() instanceof RedstoneSignalCableBlock) {
            return EngineeringSignal.clamp(RedstoneSignalCableBlock.power(level, targetPos));
        }
        if (targetState.getBlock() instanceof RedstoneCableJunctionBlock) {
            return EngineeringSignal.clamp(RedstoneCableJunctionBlock.power(level, targetPos));
        }
        if (targetState.getBlock() instanceof RedstoneCableTerminalBlock) {
            return EngineeringSignal.clamp(targetState.getValue(RedstoneCableTerminalBlock.POWER));
        }
        if (targetState.getBlock() instanceof RedStoneWireBlock) {
            return EngineeringSignal.clamp(targetState.getValue(RedStoneWireBlock.POWER));
        }

        if (instrumentToTarget != null) {
            int directional = targetState.getSignal(level, targetPos, instrumentToTarget);
            if (directional > 0 || targetState.isSignalSource()) {
                return EngineeringSignal.clamp(directional);
            }
        } else {
            int emitted = 0;
            for (Direction direction : Direction.values()) {
                emitted = Math.max(emitted, targetState.getSignal(level, targetPos, direction));
            }
            if (emitted > 0) return EngineeringSignal.clamp(emitted);
        }

        return EngineeringSignal.clamp(level.getBestNeighborSignal(targetPos));
    }

    private static String modeName(int mode) {
        return mode == INLINE ? "INLINE" : "TAP";
    }

    private static String shortName(Direction direction) {
        return switch (direction) {
            case NORTH -> "N";
            case SOUTH -> "S";
            case EAST -> "E";
            case WEST -> "W";
            case UP -> "U";
            case DOWN -> "D";
        });
    }
}
