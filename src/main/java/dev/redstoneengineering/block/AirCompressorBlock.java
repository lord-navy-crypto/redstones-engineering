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
import dev.redstoneengineering.physics.EngineeringDeviceParameters;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.PneumaticNetwork;
import dev.redstoneengineering.physics.RedstoneObservationSupport;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.signal.AirCompressorLogic;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
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

/**
 * Ambient-air compressor with finite spool-up/spool-down dynamics.
 *
 * DOWN is the redstone pressure command; UP is the pneumatic outlet. The command is a target,
 * not an instantaneous source: actual supply pressure approaches it according to RESPONSE_MODE.
 */
public class AirCompressorBlock extends Block implements EngineeringPortProvider {
    public static final IntegerProperty RESPONSE_MODE = IntegerProperty.create("response_mode", 0, 2);

    private static final String RUNTIME_KEY = "air_compressor";
    private static final int ACTUAL_PRESSURE = 0;
    private static final int LAST_TARGET = 1;
    private static final int START_COUNT = 2;
    private static final int RUN_TICKS = 3;
    private static final int INITIALIZED = 4;
    private static final int RUNTIME_SIZE = 5;

    public AirCompressorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(RESPONSE_MODE, 1));
    }

    @Override
    public MapCodec<AirCompressorBlock> codec() {
        return RedstoneEngineering.AIR_COMPRESSOR_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RESPONSE_MODE);
    }

    public static RedstoneObservationSupport.Observation commandObservation(Level level, BlockPos pos) {
        return RedstoneObservationSupport.observe(level, pos, Direction.DOWN);
    }

    public static int commandSignal(Level level, BlockPos pos) {
        RedstoneObservationSupport.Observation observation = commandObservation(level, pos);
        return observation.valid() ? observation.value() : 0;
    }

    public static int commandedPressure(Level level, BlockPos pos) {
        return Math.round(commandSignal(level, pos) * 100f / 15f);
    }

    private static int[] snapshot(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, RUNTIME_KEY, pos);
        return runtime != null && runtime.length == RUNTIME_SIZE ? runtime : null;
    }

    public static int actualPressure(Level level, BlockPos pos) {
        int[] runtime = snapshot(level, pos);
        return runtime == null ? 0 : AirCompressorLogic.boundedPressure(runtime[ACTUAL_PRESSURE]);
    }

    public static int trackingError(Level level, BlockPos pos) {
        return AirCompressorLogic.trackingError(actualPressure(level, pos), commandedPressure(level, pos));
    }

    public static int startCount(Level level, BlockPos pos) {
        int[] runtime = snapshot(level, pos);
        return runtime == null ? 0 : Math.max(0, runtime[START_COUNT]);
    }

    public static int runTicks(Level level, BlockPos pos) {
        int[] runtime = snapshot(level, pos);
        return runtime == null ? 0 : Math.max(0, runtime[RUN_TICKS]);
    }

    public static boolean initialized(Level level, BlockPos pos) {
        int[] runtime = snapshot(level, pos);
        return runtime != null && runtime[INITIALIZED] != 0;
    }

    public static EngineeringDeviceParameters.ExtendedParameters configuredResponse(Level level, BlockPos pos, BlockState state) {
        var fallback = new EngineeringDeviceParameters.ExtendedParameters(
                AirCompressorLogic.rampUpRate(state.getValue(RESPONSE_MODE)),
                AirCompressorLogic.rampDownRate(state.getValue(RESPONSE_MODE)), 0, 0);
        if (level instanceof ServerLevel serverLevel) {
            var stored = EngineeringDeviceParameters.get(serverLevel)
                    .extendedParameters(serverLevel, pos, fallback);
            return new EngineeringDeviceParameters.ExtendedParameters(
                    AirCompressorLogic.boundedRampRate(stored.a()),
                    AirCompressorLogic.boundedRampRate(stored.b()), 0, 0);
        }
        return fallback;
    }

    public static boolean setResponseRates(ServerLevel level, BlockPos pos, int up, int down) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof AirCompressorBlock compressor)) return false;
        var next = new EngineeringDeviceParameters.ExtendedParameters(
                AirCompressorLogic.boundedRampRate(up),
                AirCompressorLogic.boundedRampRate(down), 0, 0);
        boolean changed = EngineeringDeviceParameters.get(level).setExtendedParameters(level, pos, next);
        if (changed) level.scheduleTick(pos, compressor, 1);
        return changed;
    }

    public static boolean stepResponseMode(Level level, BlockPos pos, boolean forward) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof AirCompressorBlock compressor)) return false;
        int mode = state.getValue(RESPONSE_MODE);
        int next = Math.floorMod(mode + (forward ? 1 : -1), 3);
        BlockState nextState = state.setValue(RESPONSE_MODE, next);
        level.setBlock(pos, nextState, Block.UPDATE_CLIENTS);
        if (level instanceof ServerLevel server) {
            EngineeringDeviceParameters.get(server).setExtendedParameters(
                    server, pos, new EngineeringDeviceParameters.ExtendedParameters(
                            AirCompressorLogic.rampUpRate(next),
                            AirCompressorLogic.rampDownRate(next), 0, 0));
            server.scheduleTick(pos, compressor, 1);
        }
        return true;
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("PRESSURE COMMAND", Direction.DOWN, EngineeringDomain.REDSTONE,
                        PortKind.CONTROL, PortDirection.INPUT, true, "signal"),
                new EngineeringPort("COMPRESSED AIR OUT", Direction.UP, EngineeringDomain.PNEUMATIC,
                        PortKind.CONVERTER, PortDirection.OUTPUT, false, "pressure")
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        if (side == Direction.DOWN) {
            RedstoneObservationSupport.Observation command = commandObservation(level, pos);
            return Optional.of(EngineeringPortSnapshot.redstone(
                    port.get(), command.value(), command.quality()));
        }
        PortQuality quality = initialized(level, pos) ? PortQuality.VALID : PortQuality.STALE;
        return Optional.of(new EngineeringPortSnapshot(
                port.get(), actualPressure(level, pos), 0.0, 100.0, quality));
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction
    ) {
        return direction != null && direction.getOpposite() == Direction.DOWN;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (level instanceof ServerLevel server && !state.is(oldState.getBlock())) {
            server.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void neighborChanged(
            BlockState state, Level level, BlockPos pos, Block neighbor,
            BlockPos neighborPos, boolean moved
    ) {
        if (level instanceof ServerLevel server && neighborPos.equals(pos.below())) {
            server.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int target = commandedPressure(level, pos);
        int[] runtime = RuntimeIntStore.get(level, RUNTIME_KEY, pos, RUNTIME_SIZE);
        int previousActual = runtime[ACTUAL_PRESSURE];
        int previousTarget = runtime[LAST_TARGET];

        if (runtime[INITIALIZED] == 0) {
            runtime[LAST_TARGET] = 0;
            runtime[INITIALIZED] = 1;
            previousTarget = 0;
        }

        if (target > 0 && previousTarget <= 0 && runtime[START_COUNT] < Integer.MAX_VALUE) {
            runtime[START_COUNT]++;
        }

        var response = configuredResponse(level, pos, state);
        int actual = AirCompressorLogic.stepPressureRates(
                previousActual, target, response.a(), response.b());
        runtime[ACTUAL_PRESSURE] = actual;
        runtime[LAST_TARGET] = target;
        if (actual > 0 && runtime[RUN_TICKS] < Integer.MAX_VALUE) runtime[RUN_TICKS]++;

        if (actual != previousActual) {
            PneumaticNetwork.recompute(level, pos);
            level.updateNeighborsAt(pos, this);
            level.updateNeighborsAt(pos.above(), this);
        }

        if (actual > 0 || target > 0 || actual != target) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            RuntimeIntStore.remove(level, RUNTIME_KEY, pos);
            if (level instanceof ServerLevel server) {
                EngineeringDeviceParameters.get(server).removeExtendedParameters(server, pos);
                InformationRuntime.clear(level, "pneumatic", pos);
                PneumaticNetwork.recomputeAround(server, pos);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                player.displayClientMessage(Component.literal(
                        "Air Compressor | command=" + commandSignal(level, pos) + "/15"
                                + " target=" + commandedPressure(level, pos) + "/100"
                                + " actual=" + actualPressure(level, pos) + "/100"
                                + " error=" + trackingError(level, pos)
                                + " | response=" + AirCompressorLogic.modeName(state.getValue(RESPONSE_MODE))
                                + " up=" + AirCompressorLogic.rampUpRate(state.getValue(RESPONSE_MODE)) + "/t"
                                + " down=" + AirCompressorLogic.rampDownRate(state.getValue(RESPONSE_MODE)) + "/t"
                                + " | starts=" + startCount(level, pos)
                                + " runTicks=" + runTicks(level, pos)
                ), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
