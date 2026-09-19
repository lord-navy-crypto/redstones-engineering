package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.PneumaticNetwork;
import dev.redstoneengineering.physics.PneumaticObservationSupport;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.signal.PneumaticIsolationValveLogic;
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
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/**
 * Manual axial isolation valve with finite mechanical travel.
 *
 * <p>OPEN is the operator command. The pneumatic solver uses the retained actual valve position;
 * opening/closing therefore changes network topology only after the valve finishes travelling.</p>
 */
public class PneumaticValveBlock extends DirectionalDomainBlock implements EngineeringPortProvider {
    public static final BooleanProperty OPEN = BooleanProperty.create("open");

    private static final String RUNTIME_KEY = "pneumatic_isolation_valve";
    private static final int ACTUAL_OPEN = 0;
    private static final int PENDING_TARGET_OPEN = 1;
    private static final int TRANSITION_REMAINING = 2;
    private static final int INITIALIZED = 3;
    private static final int RUNTIME_SIZE = 4;
    private static final int OPENING_TRAVEL_TICKS = 3;
    private static final int CLOSING_TRAVEL_TICKS = 2;

    public PneumaticValveBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(OPEN, true));
    }

    @Override public MapCodec<PneumaticValveBlock> codec() {
        return RedstoneEngineering.PNEUMATIC_VALVE_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(OPEN);
    }

    public static boolean commandedOpen(BlockState state) {
        return state.getValue(OPEN);
    }

    private static int[] snapshot(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, RUNTIME_KEY, pos);
        return runtime != null && runtime.length >= RUNTIME_SIZE ? runtime : null;
    }

    private static int[] ensureInitialized(Level level, BlockPos pos, BlockState state) {
        int[] runtime = RuntimeIntStore.get(level, RUNTIME_KEY, pos, RUNTIME_SIZE);
        if (runtime[INITIALIZED] == 0) {
            boolean open = commandedOpen(state);
            runtime[ACTUAL_OPEN] = open ? 1 : 0;
            runtime[PENDING_TARGET_OPEN] = open ? 1 : 0;
            runtime[TRANSITION_REMAINING] = 0;
            runtime[INITIALIZED] = 1;
        }
        return runtime;
    }

    public static boolean actualOpen(Level level, BlockPos pos, BlockState state) {
        int[] runtime = snapshot(level, pos);
        return runtime == null || runtime[INITIALIZED] == 0
                ? commandedOpen(state)
                : runtime[ACTUAL_OPEN] != 0;
    }

    public static boolean moving(Level level, BlockPos pos) {
        int[] runtime = snapshot(level, pos);
        return runtime != null && runtime[TRANSITION_REMAINING] > 0;
    }

    public static boolean pendingTargetOpen(Level level, BlockPos pos) {
        int[] runtime = snapshot(level, pos);
        return runtime != null && runtime[TRANSITION_REMAINING] > 0
                && runtime[PENDING_TARGET_OPEN] != 0;
    }

    public static int transitionRemaining(Level level, BlockPos pos) {
        int[] runtime = snapshot(level, pos);
        return runtime == null ? 0 : Math.max(0, runtime[TRANSITION_REMAINING]);
    }

    public static int openingTravelTicks() { return OPENING_TRAVEL_TICKS; }
    public static int closingTravelTicks() { return CLOSING_TRAVEL_TICKS; }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort(
                        "PNEUMATIC BACK", inputSide(state), EngineeringDomain.PNEUMATIC,
                        PortKind.CONTROL, PortDirection.BIDIRECTIONAL, false, "pressure"
                ),
                new EngineeringPort(
                        "PNEUMATIC FRONT", outputSide(state), EngineeringDomain.PNEUMATIC,
                        PortKind.CONTROL, PortDirection.BIDIRECTIONAL, false, "pressure"
                )
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> descriptor = engineeringPort(state, side);
        if (descriptor.isEmpty()) return Optional.empty();
        PneumaticObservationSupport.Observation observation =
                PneumaticObservationSupport.observe(level, pos.relative(side));
        return Optional.of(new EngineeringPortSnapshot(
                descriptor.get(), observation.pressure(), 0.0, 100.0, observation.quality()
        ));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (level instanceof ServerLevel server) {
            if (!state.is(oldState.getBlock())) ensureInitialized(level, pos, state);
            PneumaticNetwork.recomputeAround(server, pos);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int[] runtime = ensureInitialized(level, pos, state);
        PneumaticIsolationValveLogic.Result result = PneumaticIsolationValveLogic.step(
                commandedOpen(state),
                OPENING_TRAVEL_TICKS,
                CLOSING_TRAVEL_TICKS,
                new PneumaticIsolationValveLogic.State(
                        runtime[ACTUAL_OPEN] != 0,
                        runtime[PENDING_TARGET_OPEN] != 0,
                        runtime[TRANSITION_REMAINING]
                )
        );

        runtime[ACTUAL_OPEN] = result.state().actualOpen() ? 1 : 0;
        runtime[PENDING_TARGET_OPEN] = result.state().pendingTargetOpen() ? 1 : 0;
        runtime[TRANSITION_REMAINING] = result.state().remainingTicks();

        if (result.transitioned()) {
            PneumaticNetwork.recomputeAround(level, pos);
            level.updateNeighborsAt(pos, this);
        }
        if (result.moving()) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            RuntimeIntStore.remove(level, RUNTIME_KEY, pos);
            if (level instanceof ServerLevel server) {
                InformationRuntime.clear(level, "pneumatic", pos);
                PneumaticNetwork.recomputeAround(server, pos);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                int[] runtime = ensureInitialized(level, pos, state);
                boolean nextOpen = !state.getValue(OPEN);
                BlockState nextState = state.setValue(OPEN, nextOpen);
                level.setBlock(pos, nextState, Block.UPDATE_CLIENTS);
                runtime[PENDING_TARGET_OPEN] = nextOpen ? 1 : 0;
                runtime[TRANSITION_REMAINING] = 0;
                if (level instanceof ServerLevel server) server.scheduleTick(pos, this, 1);
                player.displayClientMessage(Component.literal(
                        "Pneumatic isolation valve | command=" + (nextOpen ? "OPEN" : "CLOSED")
                                + " actual=" + (actualOpen(level, pos, nextState) ? "OPEN" : "CLOSED")
                                + " | travel=" + (nextOpen ? OPENING_TRAVEL_TICKS : CLOSING_TRAVEL_TICKS) + "t"
                                + " | BACK↔FRONT only"
                ), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
