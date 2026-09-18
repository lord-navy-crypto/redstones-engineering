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
 * Redstone engineering relay.
 *
 * <p>The coil/control path is independent from the switched 0..15 signal path. This is deliberately
 * a Minecraft-scale relay rather than a contact-physics simulation: NO/NC contact semantics,
 * isolated control authority, and switching evidence are the engineering gameplay.</p>
 */
public final class SingleRelayBlock extends DirectionalSignalBlock {
    public static final BooleanProperty NORMALLY_CLOSED = BooleanProperty.create("normally_closed");

    private static final String RUNTIME_KEY = "single_relay";
    private static final int LAST_CLOSED = 0;
    private static final int SWITCH_COUNT = 1;
    private static final int ENERGIZED_TICKS = 2;
    private static final int INITIALIZED = 3;
    private static final int RUNTIME_SIZE = 4;

    public SingleRelayBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(NORMALLY_CLOSED, false));
    }

    @Override
    public MapCodec<SingleRelayBlock> codec() {
        return RedstoneEngineering.SINGLE_RELAY_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(NORMALLY_CLOSED);
    }

    public static Direction controlSide(BlockState state) {
        return leftOf(seriesOutputSide(state));
    }

    public static boolean coilEnergized(Level level, BlockPos pos, BlockState state) {
        Direction control = controlSide(state);
        return level.getSignal(pos.relative(control), control) > 0;
    }

    public static boolean contactClosed(Level level, BlockPos pos, BlockState state) {
        boolean energized = coilEnergized(level, pos, state);
        return state.getValue(NORMALLY_CLOSED) ? !energized : energized;
    }

    public static int switchCount(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, RUNTIME_KEY, pos);
        return runtime == null || runtime.length < RUNTIME_SIZE ? 0 : Math.max(0, runtime[SWITCH_COUNT]);
    }

    public static int energizedTicks(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, RUNTIME_KEY, pos);
        return runtime == null || runtime.length < RUNTIME_SIZE ? 0 : Math.max(0, runtime[ENERGIZED_TICKS]);
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
        int value;
        if (side == outputSide(state)) value = state.getValue(OUTPUT);
        else if (side == inputSide(state)) value = readInputFrom(level, pos, inputSide(state));
        else value = coilEnergized(level, pos, state) ? 15 : 0;
        return Optional.of(EngineeringPortSnapshot.redstone(port.get(), value, PortQuality.VALID));
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean energized = coilEnergized(level, pos, state);
        boolean closed = state.getValue(NORMALLY_CLOSED) ? !energized : energized;
        int[] runtime = RuntimeIntStore.get(level, RUNTIME_KEY, pos, RUNTIME_SIZE);
        if (runtime[INITIALIZED] == 0) {
            runtime[LAST_CLOSED] = closed ? 1 : 0;
            runtime[INITIALIZED] = 1;
        } else if (runtime[LAST_CLOSED] != (closed ? 1 : 0)) {
            runtime[LAST_CLOSED] = closed ? 1 : 0;
            if (runtime[SWITCH_COUNT] < Integer.MAX_VALUE) runtime[SWITCH_COUNT]++;
        }
        if (energized && runtime[ENERGIZED_TICKS] < Integer.MAX_VALUE) runtime[ENERGIZED_TICKS]++;

        int input = readBackInput(level, pos, state);
        updateOutput(level, pos, state, closed ? input : 0);
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
                                + " | contact=" + (contactClosed(level, pos, next) ? "CLOSED" : "OPEN")
                                + " | switches=" + switchCount(level, pos)), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
