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
import dev.redstoneengineering.physics.RedstoneObservationSupport;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.signal.MechanicalExciterLogic;
import dev.redstoneengineering.physics.VibrationNetwork;
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
 * Redstone-driven vibration source. DOWN is the control-power input; UP and the
 * four horizontal faces are mechanical-vibration outputs.
 */
public class MechanicalExciterBlock extends Block implements EngineeringPortProvider {
    public static final IntegerProperty FREQUENCY = IntegerProperty.create("frequency", 1, 15);

    private static final String RUNTIME_KEY = "mechanical_exciter";
    private static final int ACTUAL_AMPLITUDE = 0;
    private static final int ACTUAL_FREQUENCY = 1;
    private static final int TARGET_AMPLITUDE = 2;
    private static final int START_COUNT = 3;
    private static final int RUN_TICKS = 4;
    private static final int INITIALIZED = 5;
    private static final int RUNTIME_SIZE = 6;

    private static final Direction[] OUTPUTS = {
            Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    public MechanicalExciterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FREQUENCY, 8));
    }

    @Override
    public MapCodec<MechanicalExciterBlock> codec() {
        return RedstoneEngineering.MECHANICAL_EXCITER_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FREQUENCY);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("DRIVE IN", Direction.DOWN, EngineeringDomain.REDSTONE,
                        PortKind.CONTROL, PortDirection.INPUT, true, "signal"),
                vibrationOutput(Direction.UP),
                vibrationOutput(Direction.NORTH),
                vibrationOutput(Direction.SOUTH),
                vibrationOutput(Direction.WEST),
                vibrationOutput(Direction.EAST)
        );
    }

    private static EngineeringPort vibrationOutput(Direction side) {
        return new EngineeringPort("VIBRATION OUT", side, EngineeringDomain.MECHANICAL_VIBRATION,
                PortKind.ACTUATOR, PortDirection.OUTPUT, false, "amplitude");
    }

    public static RedstoneObservationSupport.Observation driveObservation(Level level, BlockPos pos) {
        return RedstoneObservationSupport.observe(level, pos, Direction.DOWN);
    }

    public static PortQuality outputQuality(Level level, BlockPos pos) {
        var drive = driveObservation(level, pos);
        return actualAmplitude(level, pos) > 0
                ? PortQuality.VALID
                : drive.valid() ? PortQuality.NO_SIGNAL : drive.quality();
    }

    private static int[] snapshot(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, RUNTIME_KEY, pos);
        return runtime != null && runtime.length >= RUNTIME_SIZE ? runtime : null;
    }

    public static int actualAmplitude(Level level, BlockPos pos) {
        int[] runtime = snapshot(level, pos);
        return runtime == null ? 0 : Math.max(0, Math.min(15, runtime[ACTUAL_AMPLITUDE]));
    }

    public static int actualFrequency(Level level, BlockPos pos) {
        int[] runtime = snapshot(level, pos);
        return runtime == null ? 0 : Math.max(0, Math.min(15, runtime[ACTUAL_FREQUENCY]));
    }

    public static int targetAmplitude(Level level, BlockPos pos) {
        int[] runtime = snapshot(level, pos);
        return runtime == null ? 0 : Math.max(0, Math.min(15, runtime[TARGET_AMPLITUDE]));
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

    public static EngineeringDeviceParameters.ExtendedParameters configuredDynamics(Level level, BlockPos pos) {
        var fallback = new EngineeringDeviceParameters.ExtendedParameters(2, 1, 1, 0);
        if (level instanceof ServerLevel serverLevel) {
            return EngineeringDeviceParameters.get(serverLevel).extendedParameters(serverLevel, pos, fallback);
        }
        return fallback;
    }

    public static boolean setConfiguredDynamics(ServerLevel level, BlockPos pos, int rise, int fall, int frequencySlew) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof MechanicalExciterBlock exciter)) return false;
        var next = new EngineeringDeviceParameters.ExtendedParameters(
                Math.max(1, Math.min(15, rise)),
                Math.max(1, Math.min(15, fall)),
                Math.max(1, Math.min(15, frequencySlew)),
                0);
        boolean changed = EngineeringDeviceParameters.get(level).setExtendedParameters(level, pos, next);
        if (changed) level.scheduleTick(pos, exciter, 1);
        return changed;
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        RedstoneObservationSupport.Observation drive = driveObservation(level, pos);
        if (side == Direction.DOWN) {
            return Optional.of(EngineeringPortSnapshot.redstone(port.get(), drive.value(), drive.quality()));
        }
        int amplitude = actualAmplitude(level, pos);
        return Optional.of(new EngineeringPortSnapshot(
                port.get(), amplitude, 0.0, 15.0, outputQuality(level, pos)));
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction
    ) {
        return direction != null && direction.getOpposite() == Direction.DOWN;
    }

    private static int inputAmplitude(Level level, BlockPos pos) {
        RedstoneObservationSupport.Observation drive = driveObservation(level, pos);
        return drive.valid() ? drive.value() : 0;
    }

    private static void scheduleUpdate(Level level, BlockPos pos, MechanicalExciterBlock block) {
        if (level instanceof ServerLevel serverLevel) serverLevel.scheduleTick(pos, block, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        RedstoneObservationSupport.Observation drive = driveObservation(level, pos);
        int command = drive.valid() ? drive.value() : 0;
        int[] runtime = RuntimeIntStore.get(level, RUNTIME_KEY, pos, RUNTIME_SIZE);

        if (runtime[INITIALIZED] == 0) {
            runtime[ACTUAL_AMPLITUDE] = 0;
            runtime[ACTUAL_FREQUENCY] = 0;
            runtime[TARGET_AMPLITUDE] = 0;
            runtime[INITIALIZED] = 1;
        }

        int previousTarget = runtime[TARGET_AMPLITUDE];
        var dynamics = configuredDynamics(level, pos);
        MechanicalExciterLogic.State next = MechanicalExciterLogic.stepWithRates(
                command,
                state.getValue(FREQUENCY),
                new MechanicalExciterLogic.State(
                        runtime[ACTUAL_AMPLITUDE],
                        runtime[ACTUAL_FREQUENCY]),
                dynamics.a(), dynamics.b(), dynamics.c()
        );

        if (command > 0 && previousTarget <= 0 && runtime[START_COUNT] < Integer.MAX_VALUE) {
            runtime[START_COUNT]++;
        }
        runtime[TARGET_AMPLITUDE] = command;
        runtime[ACTUAL_AMPLITUDE] = next.amplitude();
        runtime[ACTUAL_FREQUENCY] = next.frequency();
        if (next.amplitude() > 0 && runtime[RUN_TICKS] < Integer.MAX_VALUE) runtime[RUN_TICKS]++;

        if (next.amplitude() > 0) {
            InformationRuntime.write(level, "mech_exciter", pos,
                    next.amplitude(), Math.max(1, next.frequency()), true, 100);
            // A powered exciter is a continuous mechanical source, not a one-shot packet.
            VibrationNetwork.propagate(level, pos,
                    next.amplitude(), Math.max(1, next.frequency()), OUTPUTS);
        } else {
            InformationRuntime.clear(level, "mech_exciter", pos);
        }

        if (command > 0 || next.amplitude() > 0
                || !MechanicalExciterLogic.settled(command, state.getValue(FREQUENCY), next)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void neighborChanged(
            BlockState state, Level level, BlockPos pos, Block neighborBlock,
            BlockPos neighborPos, boolean movedByPiston
    ) {
        if (neighborPos.equals(pos.below())) scheduleUpdate(level, pos, this);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!state.is(oldState.getBlock())) scheduleUpdate(level, pos, this);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            RuntimeIntStore.remove(level, RUNTIME_KEY, pos);
            InformationRuntime.clear(level, "mech_exciter", pos);
            if (level instanceof ServerLevel serverLevel) {
                EngineeringDeviceParameters.get(serverLevel).removeExtendedParameters(serverLevel, pos);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                int frequency = state.getValue(FREQUENCY) % 15 + 1;
                BlockState next = state.setValue(FREQUENCY, frequency);
                level.setBlock(pos, next, Block.UPDATE_CLIENTS);
                scheduleUpdate(level, pos, this);
                player.displayClientMessage(Component.literal(
                        "Mechanical exciter | targetA=" + inputAmplitude(level, pos)
                                + " actualA=" + actualAmplitude(level, pos)
                                + " | targetF=" + frequency
                                + " actualF=" + actualFrequency(level, pos)
                                + " | starts=" + startCount(level, pos)
                                + " runTicks=" + runTicks(level, pos)), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
