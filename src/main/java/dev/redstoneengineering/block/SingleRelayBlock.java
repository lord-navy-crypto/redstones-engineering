package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.physics.RedstoneObservationSupport;
import dev.redstoneengineering.signal.RelayDynamicsLogic;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/**
 * Redstone engineering relay.
 *
 * <p>The coil/control path is independent from the switched 0..15 signal path. This is deliberately
 * a Minecraft-scale relay rather than a contact-physics simulation: NO/NC contact semantics,
 * isolated control authority, and switching evidence are the engineering gameplay.</p>
 */
public final class SingleRelayBlock extends DirectionalSignalBlock {
    public static final BooleanProperty NORMALLY_CLOSED = BooleanProperty.create("normally_closed");
    public static final IntegerProperty PICKUP_MODE = IntegerProperty.create("pickup_mode", 0, 3);
    public static final IntegerProperty TIMING_MODE = IntegerProperty.create("timing_mode", 0, 3);
    private static final int[] PICKUP_LEVELS = {1, 4, 8, 12};
    private static final int[] OPERATE_DELAYS = {0, 1, 2, 4};
    private static final int[] RELEASE_DELAYS = {0, 1, 3, 6};

    private static final String RUNTIME_KEY = "single_relay";
    private static final int LAST_CLOSED = 0;
    private static final int SWITCH_COUNT = 1;
    private static final int ENERGIZED_TICKS = 2;
    private static final int INITIALIZED = 3;
    private static final int COIL_ACTIVE = 4;
    private static final int CONTROL_HOLD_ACTIVE = 5;
    private static final int CONTROL_BAD_EPISODES = 6;
    private static final int PAYLOAD_HOLD_ACTIVE = 7;
    private static final int PAYLOAD_BAD_EPISODES = 8;
    private static final int PENDING_TARGET = 9;
    private static final int TRANSITION_REMAINING = 10;
    private static final int RUNTIME_SIZE = 11;

    public SingleRelayBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(NORMALLY_CLOSED, false)
                .setValue(PICKUP_MODE, 0)
                .setValue(TIMING_MODE, 0));
    }

    @Override
    public MapCodec<SingleRelayBlock> codec() {
        return RedstoneEngineering.SINGLE_RELAY_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(NORMALLY_CLOSED, PICKUP_MODE, TIMING_MODE);
    }

    public static Direction controlSide(BlockState state) {
        return leftOf(seriesOutputSide(state));
    }

    public static int pickupLevel(BlockState state) {
        return PICKUP_LEVELS[Math.max(0, Math.min(PICKUP_LEVELS.length - 1, state.getValue(PICKUP_MODE)))];
    }

    public static int dropoutLevel(BlockState state) {
        return Math.max(0, pickupLevel(state) - 2);
    }

    public static int operateDelayTicks(BlockState state) {
        return operateDelayForMode(state.getValue(TIMING_MODE));
    }

    public static int releaseDelayTicks(BlockState state) {
        return releaseDelayForMode(state.getValue(TIMING_MODE));
    }

    public static int operateDelayForMode(int mode) {
        return OPERATE_DELAYS[Math.max(0, Math.min(OPERATE_DELAYS.length - 1, mode))];
    }

    public static int releaseDelayForMode(int mode) {
        return RELEASE_DELAYS[Math.max(0, Math.min(RELEASE_DELAYS.length - 1, mode))];
    }

    public static String timingName(BlockState state) {
        return timingNameForMode(state.getValue(TIMING_MODE));
    }

    public static String timingNameForMode(int mode) {
        return switch (mode) {
            case 0 -> "INSTANT";
            case 1 -> "FAST";
            case 2 -> "STANDARD";
            case 3 -> "HEAVY";
            default -> "INSTANT";
        };
    }

    public static RedstoneObservationSupport.Observation coilObservation(Level level, BlockPos pos, BlockState state) {
        return RedstoneObservationSupport.observe(level, pos, controlSide(state));
    }

    public static int coilInput(Level level, BlockPos pos, BlockState state) {
        return coilObservation(level, pos, state).value();
    }

    public static PortQuality controlQuality(Level level, BlockPos pos, BlockState state) {
        return coilObservation(level, pos, state).quality();
    }

    public static PortQuality payloadQuality(Level level, BlockPos pos, BlockState state) {
        return RedstoneObservationSupport.observe(
                level, pos, DirectionalSignalBlock.seriesInputSide(state)).quality();
    }

    private static boolean controlEvidenceUnusable(PortQuality quality) {
        return quality == PortQuality.STALE
                || quality == PortQuality.FAULT
                || quality == PortQuality.DOMAIN_MISMATCH
                || quality == PortQuality.TOPOLOGY_ERROR;
    }

    public static boolean coilEnergized(Level level, BlockPos pos, BlockState state) {
        int[] runtime = RuntimeIntStore.peek(level, RUNTIME_KEY, pos);
        if (runtime != null && runtime.length >= RUNTIME_SIZE && runtime[INITIALIZED] != 0) {
            return runtime[COIL_ACTIVE] != 0;
        }
        // Timed electromechanical profiles start de-energized until the operate delay is completed.
        return operateDelayTicks(state) == 0 && coilInput(level, pos, state) >= pickupLevel(state);
    }

    public static int transitionRemaining(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, RUNTIME_KEY, pos);
        return runtime == null || runtime.length < RUNTIME_SIZE
                ? 0 : Math.max(0, runtime[TRANSITION_REMAINING]);
    }

    public static boolean transitionPending(Level level, BlockPos pos) {
        return transitionRemaining(level, pos) > 0;
    }

    public static boolean pendingCoilTarget(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, RUNTIME_KEY, pos);
        return runtime != null && runtime.length >= RUNTIME_SIZE
                && runtime[TRANSITION_REMAINING] > 0
                && runtime[PENDING_TARGET] != 0;
    }

    public static boolean contactClosed(Level level, BlockPos pos, BlockState state) {
        boolean energized = coilEnergized(level, pos, state);
        return state.getValue(NORMALLY_CLOSED) ? !energized : energized;
    }

    public static boolean stepPickup(Level level, BlockPos pos, boolean forward) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SingleRelayBlock relay)) return false;
        int current = state.getValue(PICKUP_MODE);
        int next = Math.floorMod(current + (forward ? 1 : -1), PICKUP_LEVELS.length);
        level.setBlock(pos, state.setValue(PICKUP_MODE, next), Block.UPDATE_CLIENTS);
        if (level instanceof ServerLevel server) server.scheduleTick(pos, relay, 1);
        return true;
    }

    public static boolean stepTiming(Level level, BlockPos pos, boolean forward) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SingleRelayBlock relay)) return false;
        int current = state.getValue(TIMING_MODE);
        int nextMode = Math.floorMod(current + (forward ? 1 : -1), OPERATE_DELAYS.length);
        BlockState next = state.setValue(TIMING_MODE, nextMode);
        level.setBlock(pos, next, Block.UPDATE_CLIENTS);

        int[] runtime = RuntimeIntStore.get(level, RUNTIME_KEY, pos, RUNTIME_SIZE);
        runtime[PENDING_TARGET] = runtime[COIL_ACTIVE];
        runtime[TRANSITION_REMAINING] = 0;
        if (level instanceof ServerLevel server) server.scheduleTick(pos, relay, 1);
        return true;
    }

    public static int switchCount(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, RUNTIME_KEY, pos);
        return runtime == null || runtime.length < RUNTIME_SIZE ? 0 : Math.max(0, runtime[SWITCH_COUNT]);
    }

    public static int energizedTicks(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, RUNTIME_KEY, pos);
        return runtime == null || runtime.length < RUNTIME_SIZE ? 0 : Math.max(0, runtime[ENERGIZED_TICKS]);
    }

    public static boolean controlHoldActive(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, RUNTIME_KEY, pos);
        return runtime != null && runtime.length >= RUNTIME_SIZE && runtime[CONTROL_HOLD_ACTIVE] != 0;
    }

    public static int controlBadEpisodes(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, RUNTIME_KEY, pos);
        return runtime == null || runtime.length < RUNTIME_SIZE ? 0 : Math.max(0, runtime[CONTROL_BAD_EPISODES]);
    }

    public static boolean payloadHoldActive(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, RUNTIME_KEY, pos);
        return runtime != null && runtime.length >= RUNTIME_SIZE && runtime[PAYLOAD_HOLD_ACTIVE] != 0;
    }

    public static int payloadBadEpisodes(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, RUNTIME_KEY, pos);
        return runtime == null || runtime.length < RUNTIME_SIZE ? 0 : Math.max(0, runtime[PAYLOAD_BAD_EPISODES]);
    }

    public static boolean toggleContactMode(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SingleRelayBlock relay)) return false;
        BlockState updated = state.setValue(NORMALLY_CLOSED, !state.getValue(NORMALLY_CLOSED));
        level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
        if (level instanceof ServerLevel server) server.scheduleTick(pos, relay, 1);
        return true;
    }

    @Override
    protected boolean isEngineeringPort(BlockState state, Direction side) {
        return side == inputSide(state) || side == outputSide(state) || side == controlSide(state);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("SWITCHED SIGNAL IN", inputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.REDSTONE_ANALOG, PortDirection.INPUT, true, "signal"),
                new EngineeringPort("SWITCHED SIGNAL OUT", outputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.REDSTONE_ANALOG, PortDirection.OUTPUT, true, "signal"),
                new EngineeringPort("RELAY COIL CONTROL", controlSide(state), EngineeringDomain.REDSTONE,
                        PortKind.CONTROL, PortDirection.INPUT, true, "coil")
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        if (side == outputSide(state)) {
            var coil = coilObservation(level, pos, state);
            if (!contactClosed(level, pos, state)) {
                return Optional.of(EngineeringPortSnapshot.redstone(
                        port.get(),
                        0,
                        RedstoneObservationSupport.combineQuality(PortQuality.VALID, coil.quality())));
            }

            var input = RedstoneObservationSupport.observe(level, pos, inputSide(state));
            return Optional.of(EngineeringPortSnapshot.redstone(
                    port.get(),
                    state.getValue(OUTPUT),
                    RedstoneObservationSupport.combineQuality(input.quality(), coil.quality())));
        }
        if (side == inputSide(state)) {
            var input = RedstoneObservationSupport.observe(level, pos, inputSide(state));
            return Optional.of(EngineeringPortSnapshot.redstone(port.get(), input.value(), input.quality()));
        }
        var coil = RedstoneObservationSupport.observe(level, pos, controlSide(state));
        return Optional.of(EngineeringPortSnapshot.redstone(port.get(), coil.value(), coil.quality()));
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        var coil = coilObservation(level, pos, state);
        int coilInput = coil.value();
        int[] runtime = RuntimeIntStore.get(level, RUNTIME_KEY, pos, RUNTIME_SIZE);
        boolean coilWasActive = runtime[INITIALIZED] != 0 && runtime[COIL_ACTIVE] != 0;
        boolean badControl = controlEvidenceUnusable(coil.quality());

        if (badControl) {
            if (runtime[CONTROL_HOLD_ACTIVE] == 0 && runtime[CONTROL_BAD_EPISODES] < Integer.MAX_VALUE) {
                runtime[CONTROL_BAD_EPISODES]++;
            }
            runtime[CONTROL_HOLD_ACTIVE] = 1;
            // Unknown control evidence freezes the actual armature and cancels an in-flight move.
            runtime[PENDING_TARGET] = coilWasActive ? 1 : 0;
            runtime[TRANSITION_REMAINING] = 0;
        } else {
            runtime[CONTROL_HOLD_ACTIVE] = 0;
        }

        boolean desiredEnergized = badControl
                ? coilWasActive
                : coilWasActive
                ? coilInput > dropoutLevel(state)
                : coilInput >= pickupLevel(state);

        RelayDynamicsLogic.Result mechanical = RelayDynamicsLogic.step(
                desiredEnergized,
                operateDelayTicks(state),
                releaseDelayTicks(state),
                new RelayDynamicsLogic.State(
                        coilWasActive,
                        runtime[PENDING_TARGET] != 0,
                        runtime[TRANSITION_REMAINING]
                )
        );
        boolean energized = mechanical.state().energized();
        runtime[COIL_ACTIVE] = energized ? 1 : 0;
        runtime[PENDING_TARGET] = mechanical.state().pendingTarget() ? 1 : 0;
        runtime[TRANSITION_REMAINING] = mechanical.state().remainingTicks();
        if (mechanical.transitionPending()) level.scheduleTick(pos, this, 1);

        boolean closed = state.getValue(NORMALLY_CLOSED) ? !energized : energized;
        if (runtime[INITIALIZED] == 0) {
            runtime[LAST_CLOSED] = closed ? 1 : 0;
            runtime[INITIALIZED] = 1;
        } else if (runtime[LAST_CLOSED] != (closed ? 1 : 0)) {
            runtime[LAST_CLOSED] = closed ? 1 : 0;
            if (runtime[SWITCH_COUNT] < Integer.MAX_VALUE) runtime[SWITCH_COUNT]++;
        }
        if (energized && runtime[ENERGIZED_TICKS] < Integer.MAX_VALUE) runtime[ENERGIZED_TICKS]++;

        if (!closed) {
            runtime[PAYLOAD_HOLD_ACTIVE] = 0;
            updateOutput(level, pos, state, 0);
            return;
        }

        var input = RedstoneObservationSupport.observe(level, pos, inputSide(state));
        if (input.valid()) {
            runtime[PAYLOAD_HOLD_ACTIVE] = 0;
            updateOutput(level, pos, state, input.value());
            return;
        }

        if (input.quality() == PortQuality.NO_SIGNAL) {
            runtime[PAYLOAD_HOLD_ACTIVE] = 0;
            updateOutput(level, pos, state, 0);
            return;
        }

        if (runtime[PAYLOAD_HOLD_ACTIVE] == 0 && runtime[PAYLOAD_BAD_EPISODES] < Integer.MAX_VALUE) {
            runtime[PAYLOAD_BAD_EPISODES]++;
        }
        runtime[PAYLOAD_HOLD_ACTIVE] = 1;
        // Preserve the last trustworthy switched value until payload evidence recovers.
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, RUNTIME_KEY, pos);
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                toggleContactMode(level, pos);
                BlockState next = level.getBlockState(pos);
                player.displayClientMessage(Component.literal(
                        "Single Relay | mode=" + (next.getValue(NORMALLY_CLOSED) ? "NC" : "NO")
                                + " | coil=" + (coilEnergized(level, pos, next) ? "ENERGIZED" : "OFF")
                                + " input=" + coilInput(level, pos, next) + "/15"
                                + " quality=" + coilObservation(level, pos, next).quality()
                                + " pickup=" + pickupLevel(next)
                                + " dropout=" + dropoutLevel(next)
                                + " | timing=" + timingName(next)
                                + " op/release=" + operateDelayTicks(next) + "/" + releaseDelayTicks(next) + "t"
                                + (transitionPending(level, pos)
                                ? " pending=" + (pendingCoilTarget(level, pos) ? "PICKUP" : "RELEASE")
                                + ":" + transitionRemaining(level, pos) + "t"
                                : " steady")
                                + " | contact=" + (contactClosed(level, pos, next) ? "CLOSED" : "OPEN")
                                + " | evidence=" + (controlHoldActive(level, pos) ? "CONTROL_HOLD" : "CONTROL_LIVE")
                                + "/" + (payloadHoldActive(level, pos) ? "PAYLOAD_HOLD" : "PAYLOAD_LIVE")
                                + " | badEpisodes=" + controlBadEpisodes(level, pos)
                                + "/" + payloadBadEpisodes(level, pos)
                                + " | switches=" + switchCount(level, pos)), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
