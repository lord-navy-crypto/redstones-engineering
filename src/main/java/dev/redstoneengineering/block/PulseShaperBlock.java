package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.signal.PulseShaperLogic;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Configurable monostable pulse conditioner.
 * A rising crossing of the configured threshold starts a bounded full-scale pulse.
 */
public class PulseShaperBlock extends DirectionalSignalBlock {
    public static final IntegerProperty WIDTH = IntegerProperty.create("width", 1, 8);
    public static final IntegerProperty THRESHOLD = IntegerProperty.create("threshold", 1, 15);
    public static final BooleanProperty RETRIGGERABLE = BooleanProperty.create("retriggerable");

    private static final String KEY = "redstone_pulse_shaper";
    private static final int RUNTIME_SIZE = 6;
    private static final int LAST_ABOVE_SLOT = 0;
    private static final int REMAINING_SLOT = 1;
    private static final int INITIALIZED_SLOT = 2;
    private static final int TRIGGER_COUNT_SLOT = 3;
    private static final int SUPPRESSED_COUNT_SLOT = 4;
    private static final int LAST_TRIGGER_TICK_SLOT = 5;

    public PulseShaperBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(WIDTH, 4)
                .setValue(THRESHOLD, 1)
                .setValue(RETRIGGERABLE, false));
    }

    @Override public MapCodec<PulseShaperBlock> codec() { return RedstoneEngineering.PULSE_SHAPER_CODEC.value(); }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WIDTH, THRESHOLD, RETRIGGERABLE);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int input = readBackInput(level, pos, state);
        int[] rt = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        PulseShaperLogic.State previous = new PulseShaperLogic.State(
                rt[INITIALIZED_SLOT] == 1,
                rt[LAST_ABOVE_SLOT] == 1,
                Math.max(0, rt[REMAINING_SLOT])
        );
        PulseShaperLogic.Result result = PulseShaperLogic.step(
                input,
                state.getValue(THRESHOLD),
                state.getValue(WIDTH),
                state.getValue(RETRIGGERABLE),
                previous
        );

        rt[INITIALIZED_SLOT] = result.state().initialized() ? 1 : 0;
        rt[LAST_ABOVE_SLOT] = result.state().lastAboveThreshold() ? 1 : 0;
        rt[REMAINING_SLOT] = result.state().remainingTicks();
        if (result.acceptedTrigger()) {
            rt[TRIGGER_COUNT_SLOT]++;
            rt[LAST_TRIGGER_TICK_SLOT] = boundedTick(level.getGameTime());
        }
        if (result.suppressedTrigger()) rt[SUPPRESSED_COUNT_SLOT]++;

        updateOutput(level, pos, state, result.outputHigh() ? 15 : 0);
        // A one-tick pulse still needs one cleanup tick after its output-high tick.
        if (result.outputHigh() || result.state().remainingTicks() > 0) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, KEY, pos);
        super.onRemove(state, level, pos, newState, moved);
    }

    private static int[] snapshot(Level level, BlockPos pos) {
        int[] rt = RuntimeIntStore.peek(level, KEY, pos);
        return rt != null && rt.length == RUNTIME_SIZE ? rt : null;
    }

    /** Compatibility readback: 1 means the most recent sample was at/above the trigger threshold. */
    public static int lastInput(Level level, BlockPos pos) {
        int[] rt = snapshot(level, pos);
        return rt == null ? 0 : rt[LAST_ABOVE_SLOT];
    }

    public static int pulseRemaining(Level level, BlockPos pos) {
        int[] rt = snapshot(level, pos);
        return rt == null ? 0 : Math.max(0, rt[REMAINING_SLOT]);
    }

    public static boolean initialized(Level level, BlockPos pos) {
        int[] rt = snapshot(level, pos);
        return rt != null && rt[INITIALIZED_SLOT] == 1;
    }

    public static int triggerCount(Level level, BlockPos pos) {
        int[] rt = snapshot(level, pos);
        return rt == null ? 0 : Math.max(0, rt[TRIGGER_COUNT_SLOT]);
    }

    public static int suppressedTriggerCount(Level level, BlockPos pos) {
        int[] rt = snapshot(level, pos);
        return rt == null ? 0 : Math.max(0, rt[SUPPRESSED_COUNT_SLOT]);
    }

    public static int lastTriggerAgeTicks(Level level, BlockPos pos) {
        int[] rt = snapshot(level, pos);
        if (rt == null || rt[TRIGGER_COUNT_SLOT] <= 0) return -1;
        long age = Math.max(0L, level.getGameTime() - Integer.toUnsignedLong(rt[LAST_TRIGGER_TICK_SLOT]));
        return (int) Math.min(Integer.MAX_VALUE, age);
    }

    /** Shared authoritative operator action used by HMI and Shift-right-click. */
    public static boolean stepWidth(Level level, BlockPos pos, boolean forward) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PulseShaperBlock shaper)) return false;
        int width = state.getValue(WIDTH);
        int nextWidth = forward ? (width >= 8 ? 1 : width + 1) : (width <= 1 ? 8 : width - 1);
        level.setBlock(pos, state.setValue(WIDTH, nextWidth), Block.UPDATE_CLIENTS);
        level.scheduleTick(pos, shaper, 1);
        return true;
    }

    public static boolean stepThreshold(Level level, BlockPos pos, boolean forward) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PulseShaperBlock shaper)) return false;
        int threshold = state.getValue(THRESHOLD);
        int nextThreshold = forward ? (threshold >= 15 ? 1 : threshold + 1) : (threshold <= 1 ? 15 : threshold - 1);
        BlockState next = state.setValue(THRESHOLD, nextThreshold);
        level.setBlock(pos, next, Block.UPDATE_CLIENTS);

        // Re-baseline the detector at the new threshold so configuration changes never fabricate a trigger.
        int[] rt = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        rt[LAST_ABOVE_SLOT] = shaper.readBackInput(level, pos, next) >= nextThreshold ? 1 : 0;
        rt[INITIALIZED_SLOT] = 1;
        level.scheduleTick(pos, shaper, 1);
        return true;
    }

    public static boolean toggleRetriggerable(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PulseShaperBlock shaper)) return false;
        level.setBlock(pos, state.setValue(RETRIGGERABLE, !state.getValue(RETRIGGERABLE)), Block.UPDATE_CLIENTS);
        level.scheduleTick(pos, shaper, 1);
        return true;
    }

    private static int boundedTick(long tick) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, tick));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!player.isShiftKeyDown()) {
                FieldDeviceUi.open(serverPlayer, pos);
                return InteractionResult.CONSUME;
            }
            if (stepWidth(level, pos, true)) {
                BlockState configured = level.getBlockState(pos);
                player.displayClientMessage(Component.literal(
                        "Pulse Shaper | width=" + configured.getValue(WIDTH) + "t"
                                + " | threshold=" + configured.getValue(THRESHOLD) + "/15"
                                + " | retrigger=" + (configured.getValue(RETRIGGERABLE) ? "YES" : "NO")
                                + " | accepted=" + triggerCount(level, pos)
                                + " | suppressed=" + suppressedTriggerCount(level, pos)), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
