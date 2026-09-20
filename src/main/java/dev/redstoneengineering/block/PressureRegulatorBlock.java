package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.physics.EngineeringDeviceParameters;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.PneumaticNetwork;
import dev.redstoneengineering.physics.PneumaticObservationSupport;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.signal.PressureRegulatorLogic;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/**
 * Inline pneumatic regulator with calibrated setpoint and finite diaphragm response.
 * BACK=input, FRONT=regulated output.
 */
public class PressureRegulatorBlock extends DirectionalDomainBlock implements EngineeringPortProvider {
    public static final IntegerProperty SETPOINT = IntegerProperty.create("setpoint", 1, 10);
    public static final IntegerProperty RESPONSE_MODE = IntegerProperty.create("response_mode", 0, 2);

    private static final String RUNTIME_KEY = "pressure_regulator";
    private static final int ACTUAL_PRESSURE = 0;
    private static final int LAST_INLET = 1;
    private static final int INITIALIZED = 2;
    private static final int RUNTIME_SIZE = 3;

    public PressureRegulatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(SETPOINT, 5).setValue(RESPONSE_MODE, 1));
    }

    @Override
    public MapCodec<PressureRegulatorBlock> codec() {
        return RedstoneEngineering.PRESSURE_REGULATOR_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SETPOINT, RESPONSE_MODE);
    }

    public static int setpointPressure(BlockState state) {
        return state.getValue(SETPOINT) * 10;
    }

    public static int setpointPressure(Level level, BlockPos pos, BlockState state) {
        int fallback = setpointPressure(state);
        if (level instanceof ServerLevel serverLevel) {
            return Math.max(1, Math.min(100, EngineeringDeviceParameters.get(serverLevel)
                    .extendedParameters(serverLevel, pos,
                            new EngineeringDeviceParameters.ExtendedParameters(fallback, PressureRegulatorLogic.responseRate(state.getValue(RESPONSE_MODE)), 0, 0)).a()));
        }
        return fallback;
    }

    public static int responseRate(Level level, BlockPos pos, BlockState state) {
        int fallback = PressureRegulatorLogic.responseRate(state.getValue(RESPONSE_MODE));
        if (level instanceof ServerLevel serverLevel) {
            return Math.max(1, Math.min(100, EngineeringDeviceParameters.get(serverLevel)
                    .extendedParameters(serverLevel, pos,
                            new EngineeringDeviceParameters.ExtendedParameters(setpointPressure(state), fallback, 0, 0)).b()));
        }
        return fallback;
    }

    public static boolean setEngineeringParameters(ServerLevel level, BlockPos pos, int setpoint, int rate) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PressureRegulatorBlock)) return false;
        int boundedSetpoint = Math.max(1, Math.min(100, setpoint));
        int boundedRate = Math.max(1, Math.min(100, rate));
        boolean changed = EngineeringDeviceParameters.get(level).setExtendedParameters(
                level, pos, new EngineeringDeviceParameters.ExtendedParameters(boundedSetpoint, boundedRate, 0, 0));
        if (changed) level.scheduleTick(pos, state.getBlock(), 1);
        return changed;
    }

    private static int[] snapshot(Level level, BlockPos pos) {
        int[] rt = RuntimeIntStore.peek(level, RUNTIME_KEY, pos);
        return rt != null && rt.length == RUNTIME_SIZE ? rt : null;
    }

    public static int actualRegulatedPressure(Level level, BlockPos pos) {
        int[] rt = snapshot(level, pos);
        return rt == null ? 0 : Math.max(0, Math.min(100, rt[ACTUAL_PRESSURE]));
    }

    public static int inletPressure(Level level, BlockPos pos, BlockState state) {
        return PneumaticObservationSupport.observe(level, pos.relative(DirectionalDomainBlock.seriesInputSide(state))).pressure();
    }

    public static int trackingError(Level level, BlockPos pos, BlockState state) {
        return PressureRegulatorLogic.trackingError(
                actualRegulatedPressure(level, pos),
                inletPressure(level, pos, state),
                setpointPressure(level, pos, state)
        );
    }

    public static boolean stepResponseMode(Level level, BlockPos pos, boolean forward) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PressureRegulatorBlock regulator)) return false;
        int mode = state.getValue(RESPONSE_MODE);
        int next = Math.floorMod(mode + (forward ? 1 : -1), 3);
        BlockState nextState = state.setValue(RESPONSE_MODE, next);
        level.setBlock(pos, nextState, Block.UPDATE_CLIENTS);
        if (level instanceof ServerLevel server) {
            EngineeringDeviceParameters.get(server).setExtendedParameters(
                    server, pos,
                    new EngineeringDeviceParameters.ExtendedParameters(setpointPressure(nextState),
                            PressureRegulatorLogic.responseRate(next), 0, 0));
            server.scheduleTick(pos, regulator, 1);
        }
        return true;
    }

    public static boolean stepSetpoint(Level level, BlockPos pos, boolean forward) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PressureRegulatorBlock regulator)) return false;
        int value = state.getValue(SETPOINT);
        int next = forward ? (value >= 10 ? 1 : value + 1) : (value <= 1 ? 10 : value - 1);
        BlockState nextState = state.setValue(SETPOINT, next);
        level.setBlock(pos, nextState, Block.UPDATE_CLIENTS);
        if (level instanceof ServerLevel server) {
            EngineeringDeviceParameters.get(server).setExtendedParameters(
                    server, pos,
                    new EngineeringDeviceParameters.ExtendedParameters(setpointPressure(nextState),
                            PressureRegulatorLogic.responseRate(nextState.getValue(RESPONSE_MODE)), 0, 0));
            server.scheduleTick(pos, regulator, 1);
        }
        return true;
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort(
                        "PNEUMATIC IN", inputSide(state), EngineeringDomain.PNEUMATIC,
                        PortKind.CONTROL, PortDirection.INPUT, false, "pressure"
                ),
                new EngineeringPort(
                        "REGULATED OUT", outputSide(state), EngineeringDomain.PNEUMATIC,
                        PortKind.CONTROL, PortDirection.OUTPUT, false, "pressure"
                )
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, net.minecraft.core.Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        PneumaticObservationSupport.Observation observation =
                PneumaticObservationSupport.observe(level, pos.relative(side));
        return Optional.of(new EngineeringPortSnapshot(
                port.get(), observation.pressure(), 0.0, 100.0, observation.quality()));
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
        if (level instanceof ServerLevel server) server.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int inlet = inletPressure(level, pos, state);
        int[] rt = RuntimeIntStore.get(level, RUNTIME_KEY, pos, RUNTIME_SIZE);
        int oldActual = rt[ACTUAL_PRESSURE];
        int next = PressureRegulatorLogic.stepPressureRate(
                oldActual, inlet, setpointPressure(level, pos, state), responseRate(level, pos, state));

        rt[ACTUAL_PRESSURE] = next;
        rt[LAST_INLET] = inlet;
        rt[INITIALIZED] = 1;

        if (next != oldActual) PneumaticNetwork.recompute(level, pos);
        if (next != PressureRegulatorLogic.targetPressure(inlet, setpointPressure(level, pos, state))) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            RuntimeIntStore.remove(level, RUNTIME_KEY, pos);
            if (level instanceof ServerLevel serverLevel) EngineeringDeviceParameters.get(serverLevel).removeExtendedParameters(serverLevel, pos);
            if (level instanceof ServerLevel server) {
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
                stepSetpoint(level, pos, true);
                BlockState updated = level.getBlockState(pos);
                player.displayClientMessage(Component.literal(
                        "Pressure regulator | setpoint=" + setpointPressure(updated) + "/100"
                                + " actual=" + actualRegulatedPressure(level, pos) + "/100"
                                + " error=" + trackingError(level, pos, updated)
                                + " response=" + PressureRegulatorLogic.modeName(updated.getValue(RESPONSE_MODE))
                                + " rate=" + PressureRegulatorLogic.responseRate(updated.getValue(RESPONSE_MODE)) + "/t"
                                + " | IN=" + inputSide(updated).getName().toUpperCase()
                                + " → OUT=" + outputSide(updated).getName().toUpperCase()
                ), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
