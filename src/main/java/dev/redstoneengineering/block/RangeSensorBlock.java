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
import dev.redstoneengineering.signal.EngineeringSignal;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

public class RangeSensorBlock extends Block implements EngineeringPortProvider {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty OUTPUT = IntegerProperty.create("output", 0, 15);
    public static final IntegerProperty MODE = IntegerProperty.create("mode", 0, 2);
    public static final IntegerProperty RANGE_MODE = IntegerProperty.create("range_mode", 0, 2);
    public static final IntegerProperty RESPONSE = IntegerProperty.create("response", 0, 3);
    private static final String RUNTIME_KEY = "range_sensor_scan";

    public enum ScanStatus {
        UNINITIALIZED,
        TARGET,
        CLEAR,
        INCOMPLETE_UNLOADED
    }

    public record ScanResult(int distance, ScanStatus status, int scannedCells, int configuredRange) {
        public boolean complete() { return status == ScanStatus.TARGET || status == ScanStatus.CLEAR; }
        public boolean targetDetected() { return status == ScanStatus.TARGET && distance > 0; }
    }

    public RangeSensorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OUTPUT, 0)
                .setValue(MODE, 2)
                .setValue(RANGE_MODE, 2)
                .setValue(RESPONSE, 0));
    }

    @Override public MapCodec<RangeSensorBlock> codec() { return RedstoneEngineering.RANGE_SENSOR_CODEC.value(); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OUTPUT, MODE, RANGE_MODE, RESPONSE);
    }

    public static Direction sensingSide(BlockState state) { return state.getValue(FACING); }
    public static Direction outputSide(BlockState state) { return sensingSide(state).getOpposite(); }
    public static int configuredRange(BlockState state) { return rangeForMode(state.getValue(RANGE_MODE)); }

    /** Immediate physical scan for solver/tests; it does not mutate sensor runtime. */
    public static int detectedDistance(Level level, BlockPos pos, BlockState state) {
        return scan(level, pos, state).distance();
    }

    /** Observer-neutral server-owned result from the most recent scheduled sensor scan. */
    public static ScanResult lastScan(Level level, BlockPos pos, BlockState state) {
        int range = configuredRange(state);
        int[] runtime = RuntimeIntStore.peek(level, RUNTIME_KEY, pos);
        if (runtime == null || runtime.length != 3) return new ScanResult(0, ScanStatus.UNINITIALIZED, 0, range);
        int statusIndex = Math.max(0, Math.min(ScanStatus.values().length - 1, runtime[1]));
        return new ScanResult(runtime[0], ScanStatus.values()[statusIndex], runtime[2], range);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(new EngineeringPort(
                "RANGE SIGNAL OUT", outputSide(state), EngineeringDomain.REDSTONE,
                PortKind.SENSOR, PortDirection.OUTPUT, true, "signal"));
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        ScanResult scan = lastScan(level, pos, state);
        PortQuality quality = scan.complete() ? PortQuality.VALID : PortQuality.NO_SIGNAL;
        return Optional.of(EngineeringPortSnapshot.redstone(port.get(), state.getValue(OUTPUT), quality));
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        if (direction == null) return false;
        return direction == state.getValue(FACING);
    }

    @Override protected boolean isSignalSource(BlockState state) { return true; }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        Direction outputSide = state.getValue(FACING).getOpposite();
        return direction == outputSide.getOpposite() ? state.getValue(OUTPUT) : 0;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide && !state.is(oldState.getBlock())) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            RuntimeIntStore.remove(level, RUNTIME_KEY, pos);
            level.updateNeighborsAt(pos, this);
            level.updateNeighborsAt(pos.relative(state.getValue(FACING).getOpposite()), this);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int range = configuredRange(state);
        ScanResult scan = scan(level, pos, state);
        int[] runtime = RuntimeIntStore.get(level, RUNTIME_KEY, pos, 3);
        runtime[0] = scan.distance();
        runtime[1] = scan.status().ordinal();
        runtime[2] = scan.scannedCells();

        int output = scan.complete() ? responseSignal(scan.distance(), range, state.getValue(RESPONSE)) : 0;
        int oldOutput = state.getValue(OUTPUT);
        if (oldOutput != output) {
            BlockState next = state.setValue(OUTPUT, output);
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            level.updateNeighborsAt(pos, this);
            level.updateNeighborsAt(pos.relative(next.getValue(FACING).getOpposite()), this);
        }
        level.scheduleTick(pos, this, 4);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!player.isShiftKeyDown()) {
                FieldDeviceUi.open(serverPlayer, pos);
                return InteractionResult.CONSUME;
            }
            BlockState next = state;
            Direction sensingFace = state.getValue(FACING);
            if (hitResult.getDirection() == sensingFace) {
                next = next.setValue(RESPONSE, (state.getValue(RESPONSE) + 1) % 4);
            } else if (hitResult.getDirection() == sensingFace.getOpposite()) {
                next = next.setValue(RANGE_MODE, (state.getValue(RANGE_MODE) + 1) % 3);
            } else {
                next = next.setValue(MODE, (state.getValue(MODE) + 1) % 3);
            }
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            level.scheduleTick(pos, this, 1);

            ScanResult scan = lastScan(level, pos, next);
            player.displayClientMessage(Component.literal(
                    "Range Sensor | detect=" + modeName(next.getValue(MODE))
                            + " | range=" + configuredRange(next)
                            + " | response=" + responseName(next.getValue(RESPONSE))
                            + " | scan=" + scan.status()
                            + " " + scan.scannedCells() + "/" + scan.configuredRange()
                            + " | distance=" + scan.distance()
                            + " | OUT=" + next.getValue(OUTPUT)), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static ScanResult scan(Level level, BlockPos pos, BlockState state) {
        Direction facing = sensingSide(state);
        int range = configuredRange(state);
        int mode = state.getValue(MODE);
        for (int distance = 1; distance <= range; distance++) {
            BlockPos target = pos.relative(facing, distance);
            if (!level.hasChunkAt(target)) {
                return new ScanResult(0, ScanStatus.INCOMPLETE_UNLOADED, distance - 1, range);
            }

            boolean blockDetected = !level.getBlockState(target).isAir();
            AABB box = new AABB(target.getX(), target.getY(), target.getZ(),
                    target.getX() + 1, target.getY() + 1, target.getZ() + 1);
            boolean entityDetected = !level.getEntitiesOfClass(LivingEntity.class, box).isEmpty();
            boolean detected = switch (mode) {
                case 0 -> blockDetected;
                case 1 -> entityDetected;
                case 2 -> blockDetected || entityDetected;
                default -> false;
            };
            if (detected) return new ScanResult(distance, ScanStatus.TARGET, distance, range);
        }
        // A complete scan that finds nothing is valid measurement evidence: the measured range is clear.
        return new ScanResult(0, ScanStatus.CLEAR, range, range);
    }

    private static int responseSignal(int distance, int range, int response) {
        if (distance <= 0) return 0;
        return switch (response) {
            case 0 -> EngineeringSignal.clamp((int) Math.round(((range - distance + 1.0) / range) * 15.0));
            case 1 -> EngineeringSignal.clamp((int) Math.round((distance / (double) range) * 15.0));
            case 2 -> distance <= Math.max(1, range / 2) ? 15 : 0;
            case 3 -> {
                int low = Math.max(1, range / 3);
                int high = Math.max(low, (range * 2) / 3);
                yield distance >= low && distance <= high ? 15 : 0;
            }
            default -> 0;
        };
    }

    private static int rangeForMode(int rangeMode) {
        return switch (rangeMode) { case 0 -> 4; case 1 -> 8; case 2 -> 15; default -> 15; };
    }

    private static String modeName(int mode) {
        return switch (mode) { case 0 -> "BLOCK"; case 1 -> "ENTITY"; case 2 -> "ANY"; default -> "ANY"; };
    }

    private static String responseName(int response) {
        return switch (response) { case 0 -> "PROXIMITY"; case 1 -> "DISTANCE"; case 2 -> "THRESHOLD"; case 3 -> "WINDOW"; default -> "PROXIMITY"; };
    }
}
