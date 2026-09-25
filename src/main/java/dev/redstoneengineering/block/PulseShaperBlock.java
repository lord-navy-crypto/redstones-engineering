package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.blockentity.PulseShaperBlockEntity;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.EngineeringDeviceParameters;
import dev.redstoneengineering.physics.RedstoneObservationSupport;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.signal.PulseShaperLogic;
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
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Optional;

/**
 * Configurable monostable pulse conditioner.
 *
 * BlockState retains only low-cardinality configuration. Precise trigger threshold, Schmitt-style
 * hysteresis and retained trigger evidence live in PulseShaperBlockEntity; pulse timing remains transient.
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
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();

        var input = RedstoneObservationSupport.observe(level, pos, inputSide(state));
        if (side == inputSide(state)) {
            return Optional.of(EngineeringPortSnapshot.redstone(
                    port.get(), input.value(), input.quality()));
        }

        int output = state.getValue(OUTPUT);
        PortQuality outputQuality;
        if (output > 0) {
            // Once a monostable pulse has been accepted, its present HIGH state is owned by the
            // internal timer and remains trustworthy even if upstream evidence disappears.
            outputQuality = PortQuality.VALID;
        } else if (input.valid()) {
            outputQuality = PortQuality.VALID;
        } else {
            // With no active retained pulse, uncertain trigger evidence means the LOW output
            // cannot be advertised as trustworthy VALID evidence.
            outputQuality = input.quality();
        }
        return Optional.of(EngineeringPortSnapshot.redstone(port.get(), output, outputQuality));
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        var inputObservation = RedstoneObservationSupport.observe(level, pos, inputSide(state));
        int[] rt = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        PulseShaperLogic.State previous = new PulseShaperLogic.State(
                rt[INITIALIZED_SLOT] == 1,
                rt[LAST_ABOVE_SLOT] == 1,
                Math.max(0, rt[REMAINING_SLOT])
        );

        if (!inputObservation.valid()) {
            // An accepted monostable pulse is self-timed and may finish even if the input disappears.
            // Reacquisition, however, must establish a fresh Schmitt baseline instead of fabricating
            // a threshold crossing.
            int remaining = Math.max(0, rt[REMAINING_SLOT]);
            boolean outputHigh = remaining > 0;
            rt[REMAINING_SLOT] = Math.max(0, remaining - 1);
            rt[INITIALIZED_SLOT] = 0;
            updateOutput(level, pos, state, outputHigh ? 15 : 0);
            if (outputHigh || rt[REMAINING_SLOT] > 0) level.scheduleTick(pos, this, 1);
            return;
        }

        PulseShaperLogic.Result result = PulseShaperLogic.step(
                inputObservation.value(),
                threshold(level, pos),
                hysteresis(level, pos),
                configuredWidth(level, pos, state),
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
        if (!state.is(newState.getBlock())) {
            RuntimeIntStore.remove(level, KEY, pos);
            if (level instanceof ServerLevel serverLevel) EngineeringDeviceParameters.get(serverLevel).removeExtendedParameters(serverLevel, pos);
        }
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

    public static int configuredWidth(Level level, BlockPos pos, BlockState state) {
        int fallback = state.getValue(WIDTH);
        if (level instanceof ServerLevel serverLevel) {
            return PulseShaperLogic.boundedWidth(EngineeringDeviceParameters.get(serverLevel)
                    .extendedParameters(serverLevel, pos,
                            new EngineeringDeviceParameters.ExtendedParameters(fallback, 0, 0, 0)).a());
        }
        return fallback;
    }

    public static boolean setConfiguredWidth(ServerLevel level, BlockPos pos, int width) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PulseShaperBlock shaper)) return false;
        int bounded = PulseShaperLogic.boundedWidth(width);
        boolean changed = EngineeringDeviceParameters.get(level).setExtendedParameters(
                level, pos, new EngineeringDeviceParameters.ExtendedParameters(bounded, 0, 0, 0));
        if (changed) level.scheduleTick(pos, shaper, 1);
        return changed;
    }

    public static int threshold(Level level, BlockPos pos) {
        PulseShaperBlockEntity entity = persistentState(level, pos);
        return entity == null ? 1 : entity.threshold();
    }

    public static int hysteresis(Level level, BlockPos pos) {
        PulseShaperBlockEntity entity = persistentState(level, pos);
        return entity == null ? 1 : entity.hysteresis();
    }

    public static int rearmThreshold(Level level, BlockPos pos) {
        return PulseShaperLogic.rearmThreshold(threshold(level, pos), hysteresis(level, pos));
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

    /** Shared authoritative operator action used by both HMI and Shift-right-click. */
    public static boolean stepWidth(Level level, BlockPos pos, boolean forward) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PulseShaperBlock shaper)) return false;
        int width = state.getValue(WIDTH);
        int nextWidth = forward
                ? (width >= PulseShaperLogic.MAX_WIDTH ? PulseShaperLogic.MIN_WIDTH : width + 1)
                : (width <= PulseShaperLogic.MIN_WIDTH ? PulseShaperLogic.MAX_WIDTH : width - 1);
        BlockState nextState = state.setValue(WIDTH, nextWidth);
        level.setBlock(pos, nextState, Block.UPDATE_CLIENTS);
        if (level instanceof ServerLevel serverLevel) {
            EngineeringDeviceParameters.get(serverLevel).setExtendedParameters(
                    serverLevel, pos, new EngineeringDeviceParameters.ExtendedParameters(nextWidth, 0, 0, 0));
        }
        level.scheduleTick(pos, shaper, 1);
        return true;
    }

    public static boolean stepThreshold(Level level, BlockPos pos, boolean forward) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PulseShaperBlock shaper)) return false;
        PulseShaperBlockEntity entity = persistentState(level, pos);
        if (entity == null) return false;

        entity.stepThreshold(forward);
        rebaselineInput(level, pos, state, shaper);
        level.scheduleTick(pos, shaper, 1);
        return true;
    }

    public static boolean stepHysteresis(Level level, BlockPos pos, boolean forward) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PulseShaperBlock shaper)) return false;
        PulseShaperBlockEntity entity = persistentState(level, pos);
        if (entity == null) return false;

        entity.stepHysteresis(forward);
        rebaselineInput(level, pos, state, shaper);
        level.scheduleTick(pos, shaper, 1);
        return true;
    }

    private static void rebaselineInput(
            Level level, BlockPos pos, BlockState state, PulseShaperBlock shaper
    ) {
        var observation = RedstoneObservationSupport.observe(level, pos, shaper.inputSide(state));
        int[] rt = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        if (!observation.valid()) {
            rt[INITIALIZED_SLOT] = 0;
            return;
        }
        rt[LAST_ABOVE_SLOT] = observation.value() >= threshold(level, pos) ? 1 : 0;
        rt[INITIALIZED_SLOT] = 1;
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
                                + " | trigger=" + threshold(level, pos) + "/15"
                                + " rearm≤" + rearmThreshold(level, pos) + "/15"
                                + " hysteresis=" + hysteresis(level, pos)
                                + " | retrigger=" + (configured.getValue(RETRIGGERABLE) ? "YES" : "NO")
                                + " | accepted=" + triggerCount(level, pos)
                                + " | suppressed=" + suppressedTriggerCount(level, pos)), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
