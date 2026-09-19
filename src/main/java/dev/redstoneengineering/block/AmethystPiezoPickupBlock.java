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
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Explicit Amethyst resonance -> Redstone transducer.
 *
 * <p>The gameplay abstraction is a piezoelectric pickup followed by a rectifier/envelope detector:
 * Amethyst resonance amplitude 0..15 becomes a Redstone process signal 0..15. Frequency is retained
 * as measurement evidence, not silently converted into amplitude. Confirmed NO_SIGNAL de-energizes
 * the output; STALE or conflicting resonance evidence preserves the last trustworthy numerical
 * output while downgrading {@link #outputQuality(Level, BlockPos)}.</p>
 */
public class AmethystPiezoPickupBlock extends Block implements EngineeringPortProvider {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final DirectionProperty INPUT_FACING =
            DirectionProperty.create("input_facing", Direction.Plane.HORIZONTAL);
    public static final IntegerProperty POWER = IntegerProperty.create("power", 0, 15);

    private static final String KEY = "amethyst_piezo_pickup";
    private static final int QUALITY_SLOT = 0;
    private static final int FREQUENCY_SLOT = 1;
    private static final int RUNTIME_SIZE = 2;

    public record ResonanceObservation(int frequency, int amplitude, PortQuality quality) {
        public boolean valid() {
            return quality == PortQuality.VALID || quality == PortQuality.SATURATED;
        }
    }

    public AmethystPiezoPickupBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(INPUT_FACING, Direction.SOUTH)
                .setValue(POWER, 0));
    }

    @Override
    public MapCodec<AmethystPiezoPickupBlock> codec() {
        return RedstoneEngineering.AMETHYST_PIEZO_PICKUP_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, INPUT_FACING, POWER);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction output = context.getHorizontalDirection().getOpposite();
        return defaultBlockState()
                .setValue(FACING, output)
                .setValue(INPUT_FACING, output.getOpposite());
    }

    public static Direction outputSide(BlockState state) {
        return state.getValue(FACING);
    }

    public static Direction inputSide(BlockState state) {
        return state.getValue(INPUT_FACING);
    }

    public static ResonanceObservation inputObservation(Level level, BlockPos pos, BlockState state) {
        BlockPos samplePos = pos.relative(inputSide(state));
        if (!level.hasChunkAt(samplePos)) {
            return new ResonanceObservation(0, 0, PortQuality.STALE);
        }

        BlockState sampleState = level.getBlockState(samplePos);
        if (sampleState.getBlock() instanceof AmethystResonanceDustBlock) {
            return switch (AmethystResonanceDustBlock.status(level, samplePos)) {
                case ACTIVE -> new ResonanceObservation(
                        AmethystResonanceDustBlock.frequency(level, samplePos),
                        AmethystResonanceDustBlock.amplitude(level, samplePos),
                        PortQuality.VALID);
                case IDLE -> new ResonanceObservation(0, 0, PortQuality.NO_SIGNAL);
                case STALE -> new ResonanceObservation(
                        AmethystResonanceDustBlock.frequency(level, samplePos),
                        AmethystResonanceDustBlock.amplitude(level, samplePos),
                        PortQuality.STALE);
                case FREQUENCY_CONFLICT -> new ResonanceObservation(
                        AmethystResonanceDustBlock.frequency(level, samplePos),
                        AmethystResonanceDustBlock.amplitude(level, samplePos),
                        PortQuality.TOPOLOGY_ERROR);
            };
        }

        if (sampleState.getBlock() instanceof AmethystResonatorBlock) {
            int amplitude = AmethystResonatorBlock.currentAmplitude(level, samplePos);
            return new ResonanceObservation(
                    amplitude > 0 ? sampleState.getValue(AmethystResonatorBlock.FREQUENCY) : 0,
                    amplitude,
                    amplitude > 0 ? PortQuality.VALID : PortQuality.NO_SIGNAL);
        }

        return new ResonanceObservation(0, 0, PortQuality.NO_SIGNAL);
    }

    private static int encodeQuality(PortQuality quality) {
        return quality.ordinal() + 1;
    }

    public static PortQuality outputQuality(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        if (runtime == null || runtime.length != RUNTIME_SIZE || runtime[QUALITY_SLOT] <= 0) {
            return PortQuality.STALE;
        }
        int ordinal = runtime[QUALITY_SLOT] - 1;
        PortQuality[] values = PortQuality.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : PortQuality.STALE;
    }

    public static int lastFrequency(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length != RUNTIME_SIZE ? 0 : Math.max(0, Math.min(15, runtime[FREQUENCY_SLOT]));
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort(
                        "AMETHYST RESONANCE IN", inputSide(state), EngineeringDomain.AMETHYST,
                        PortKind.CONVERTER, PortDirection.INPUT, false, "amplitude"),
                new EngineeringPort(
                        "RECTIFIED REDSTONE OUT", outputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.CONVERTER, PortDirection.OUTPUT, true, "signal")
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();

        if (side == inputSide(state)) {
            ResonanceObservation observation = inputObservation(level, pos, state);
            return Optional.of(new EngineeringPortSnapshot(
                    port.get(), observation.amplitude(), 0.0, 15.0, observation.quality()));
        }
        return Optional.of(EngineeringPortSnapshot.redstone(
                port.get(), state.getValue(POWER), outputQuality(level, pos)));
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction
    ) {
        return direction != null && direction == outputSide(state).getOpposite();
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return direction == outputSide(state).getOpposite() ? state.getValue(POWER) : 0;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide && !state.is(oldState.getBlock())) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void neighborChanged(
            BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean moved
    ) {
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        ResonanceObservation observation = inputObservation(level, pos, state);
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        runtime[QUALITY_SLOT] = encodeQuality(observation.quality());

        int requestedPower = state.getValue(POWER);
        if (observation.valid()) {
            requestedPower = Math.max(0, Math.min(15, observation.amplitude()));
            runtime[FREQUENCY_SLOT] = Math.max(0, Math.min(15, observation.frequency()));
        } else if (observation.quality() == PortQuality.NO_SIGNAL) {
            requestedPower = 0;
            runtime[FREQUENCY_SLOT] = 0;
        }
        // STALE / TOPOLOGY_ERROR / FAULT evidence cannot define a new numerical envelope.

        if (requestedPower != state.getValue(POWER)) {
            BlockState next = state.setValue(POWER, requestedPower);
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            level.updateNeighborsAt(pos, this);
            level.updateNeighborsAt(pos.relative(outputSide(next)), this);
        }
        level.scheduleTick(pos, this, 2);
    }

    public static boolean rotateInput(Level level, BlockPos pos, boolean clockwise) {
        if (level.isClientSide) return false;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof AmethystPiezoPickupBlock block)) return false;
        Direction output = outputSide(state);
        Direction current = inputSide(state);
        Direction next = nextFreeHorizontal(current, output, clockwise);
        if (next == current) return false;
        level.setBlock(pos, state.setValue(INPUT_FACING, next), Block.UPDATE_CLIENTS);
        if (level instanceof ServerLevel server) server.scheduleTick(pos, block, 1);
        level.updateNeighborsAt(pos, block);
        return true;
    }

    public static boolean rotateOutput(Level level, BlockPos pos, boolean clockwise) {
        if (level.isClientSide) return false;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof AmethystPiezoPickupBlock block)) return false;
        Direction input = inputSide(state);
        Direction current = outputSide(state);
        Direction nextOutput = nextFreeHorizontal(current, input, clockwise);
        if (nextOutput == current) return false;
        BlockState next = state.setValue(FACING, nextOutput);
        level.setBlock(pos, next, Block.UPDATE_CLIENTS);
        level.updateNeighborsAt(pos, block);
        level.updateNeighborsAt(pos.relative(current), block);
        level.updateNeighborsAt(pos.relative(nextOutput), block);
        if (level instanceof ServerLevel server) server.scheduleTick(pos, block, 1);
        return true;
    }

    private static Direction nextFreeHorizontal(Direction current, Direction forbidden, boolean clockwise) {
        Direction candidate = current;
        for (int i = 0; i < 3; i++) {
            candidate = clockwise ? candidate.getClockWise() : candidate.getCounterClockWise();
            if (candidate != forbidden) return candidate;
        }
        return current;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, KEY, pos);
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!player.isShiftKeyDown()) {
                FieldDeviceUi.open(serverPlayer, pos);
            } else {
                ResonanceObservation observation = inputObservation(level, pos, state);
                player.displayClientMessage(Component.literal(
                        "Amethyst Piezo Pickup | RX=" + inputSide(state).getName().toUpperCase()
                                + " TX=" + outputSide(state).getName().toUpperCase()
                                + " | resonance f=" + observation.frequency()
                                + " A=" + observation.amplitude() + "/15"
                                + " " + observation.quality()
                                + " | rectified=" + state.getValue(POWER) + "/15"
                                + " outputQuality=" + outputQuality(level, pos)
                                + " | piezo pickup + envelope detector"), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
