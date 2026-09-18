package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.blockentity.PulseShaperBlockEntity;
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
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Configurable monostable pulse conditioner.
 *
 * BlockState retains only low-cardinality configuration. The precise trigger threshold and
 * retained trigger evidence live in PulseShaperBlockEntity; pulse timing remains transient.
 */
public class PulseShaperBlock extends DirectionalSignalBlock implements EntityBlock {
    public static final IntegerProperty WIDTH = IntegerProperty.create("width", 1, 8);
    public static final BooleanProperty RETRIGGERABLE = BooleanProperty.create("retriggerable");

    private static final String KEY = "redstone_pulse_shaper";
    private static final int RUNTIME_SIZE = 3;
    private static final int LAST_ABOVE_SLOT = 0;
    private static final int REMAINING_SLOT = 1;
    private static final int INITIALIZED_SLOT = 2;

    public PulseShaperBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(WIDTH, 4)
                .setValue(RETRIGGERABLE, false));
    }

    @Override public MapCodec<PulseShaperBlock> codec() { return RedstoneEngineering.PULSE_SHAPER_CODEC.value(); }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WIDTH, RETRIGGERABLE);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PulseShaperBlockEntity(pos, state);
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
                threshold(level, pos),
                state.getValue(WIDTH),
                state.getValue(RETRIGGERABLE),
                previous
        );

        rt[INITIALIZED_SLOT] = result.state().initialized() ? 1 : 0;
        rt[LAST_ABOVE_SLOT] = result.state().lastAboveThreshold() ? 1 : 0;
        rt[REMAINING_SLOT] = result.state().remainingTicks();

        PulseShaperBlockEntity shaperState = persistentState(level, pos);
        if (shaperState != null) {
            if (result.acceptedTrigger()) shaperState.recordAcceptedTrigger(level.getGameTime());
            if (result.suppressedTrigger()) shaperState.recordSuppressedTrigger();
        }

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

    private static PulseShaperBlockEntity persistentState(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof PulseShaperBlockEntity entity) return entity;
        if (level.isClientSide) return null;

        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PulseShaperBlock)) return null;

        // Lazy migration for worlds containing Pulse Shapers placed before this block gained a BlockEntity.
        PulseShaperBlockEntity created = new PulseShaperBlockEntity(pos, state);
        level.setBlockEntity(created);
        created.setChanged();
        return created;
    }

    public static int threshold(Level level, BlockPos pos) {
        PulseShaperBlockEntity entity = persistentState(level, pos);
        return entity == null ? 1 : entity.threshold();
    }

    public static int triggerCount(Level level, BlockPos pos) {
        PulseShaperBlockEntity entity = persistentState(level, pos);
        return entity == null ? 0 : entity.acceptedTriggerCount();
    }

    public static int suppressedTriggerCount(Level level, BlockPos pos) {
        PulseShaperBlockEntity entity = persistentState(level, pos);
        return entity == null ? 0 : entity.suppressedTriggerCount();
    }

    public static int lastTriggerAgeTicks(Level level, BlockPos pos) {
        PulseShaperBlockEntity entity = persistentState(level, pos);
        return entity == null ? -1 : entity.lastTriggerAgeTicks(level.getGameTime());
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
        PulseShaperBlockEntity entity = persistentState(level, pos);
        if (entity == null) return false;

        int nextThreshold = entity.stepThreshold(forward);

        // Re-baseline at the new threshold so configuration changes never fabricate a trigger.
        int[] rt = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        rt[LAST_ABOVE_SLOT] = shaper.readBackInput(level, pos, state) >= nextThreshold ? 1 : 0;
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
                                + " | threshold=" + threshold(level, pos) + "/15"
                                + " | retrigger=" + (configured.getValue(RETRIGGERABLE) ? "YES" : "NO")
                                + " | accepted=" + triggerCount(level, pos)
                                + " | suppressed=" + suppressedTriggerCount(level, pos)), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
