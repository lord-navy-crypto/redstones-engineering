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
import dev.redstoneengineering.physics.MagneticPhysics;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Observer-only scalar magnetic-field sensor with explicit free-space scan coverage. */
public class MagneticFieldSensorBlock extends DomainBlock implements EngineeringPortProvider {
    public static final IntegerProperty FIELD = IntegerProperty.create("field", 0, 15);
    private static final String KEY = "magnetic_field_sensor";
    private static final int SCANNED = 0;
    private static final int EXPECTED = 1;
    private static final int INITIALIZED = 2;
    private static final int RUNTIME_SIZE = 3;
    private static final int RADIUS = 6;

    public record Observation(int field, int scannedCells, int expectedCells, boolean initialized, boolean complete) {}

    public MagneticFieldSensorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FIELD, 0));
    }

    @Override public MapCodec<MagneticFieldSensorBlock> codec() { return RedstoneEngineering.MAGNETIC_FIELD_SENSOR_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FIELD); }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return Arrays.stream(Direction.values())
                .map(side -> new EngineeringPort(
                        "MAGNETIC APERTURE " + side.getName().toUpperCase(), side,
                        EngineeringDomain.IRON_MAGNETIC, PortKind.MEASUREMENT,
                        PortDirection.INPUT, false, "field"))
                .toList();
    }

    public static Observation observation(Level level, BlockPos pos, BlockState state) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        if (runtime == null || runtime.length != RUNTIME_SIZE || runtime[INITIALIZED] == 0) {
            return new Observation(state.getValue(FIELD), 0, 0, false, false);
        }
        int scanned = Math.max(0, runtime[SCANNED]);
        int expected = Math.max(0, runtime[EXPECTED]);
        return new Observation(state.getValue(FIELD), scanned, expected, true, scanned == expected);
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        Observation observation = observation(level, pos, state);
        return Optional.of(EngineeringPortSnapshot.redstone(
                port.get(), observation.field(), observation.complete() ? PortQuality.VALID : PortQuality.NO_SIGNAL));
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        MagneticPhysics.FieldSample sample = MagneticPhysics.fieldSample(level, pos, RADIUS);
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        runtime[SCANNED] = sample.scannedCells();
        runtime[EXPECTED] = sample.expectedCells();
        runtime[INITIALIZED] = 1;
        if (sample.field() != state.getValue(FIELD)) {
            level.setBlock(pos, state.setValue(FIELD, sample.field()), Block.UPDATE_CLIENTS);
        }
        level.scheduleTick(pos, this, 5);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock())) RuntimeIntStore.remove(level, KEY, pos);
        super.onRemove(state, level, pos, next, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!player.isShiftKeyDown()) {
                FieldDeviceUi.open(serverPlayer, pos);
                return InteractionResult.CONSUME;
            }
            Observation observation = observation(level, pos, state);
            player.displayClientMessage(Component.literal(
                    "Magnetic field sensor | B-level=" + observation.field() + "/15"
                            + " | radius=" + RADIUS
                            + " | coverage=" + observation.scannedCells() + "/" + observation.expectedCells()
                            + " | " + (observation.complete() ? "VALID" : "INCOMPLETE")), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
