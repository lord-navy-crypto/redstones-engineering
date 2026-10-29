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

    /**
     * Authoritative configuration transaction: revoke old scan evidence and
     * de-energize the previous redstone claim BEFORE any new scan is trusted.
     * All HMI and Shift controls use this entry point; the runtime wire layout
     * and public BlockState schema remain unchanged.
     */
    public static boolean applyConfiguration(Level level, BlockPos pos, BlockState requested) {
        if (!(level instanceof ServerLevel serverLevel)) return false;
        BlockState former = level.getBlockState(pos);
        if (!(former.getBlock() instanceof RangeSensorBlock sensor) || !requested.is(sensor)) return false;
        if (former.getValue(FACING) == requested.getValue(FACING)
                && former.getValue(MODE).equals(requested.getValue(MODE))
                && former.getValue(RANGE_MODE).equals(requested.getValue(RANGE_MODE))
                && former.getValue(RESPONSE).equals(requested.getValue(RESPONSE))) return false;

        Direction oldSense = sensingSide(former);
        Direction oldOutput = outputSide(former);
        BlockState safe = requested.setValue(OUTPUT, 0);
        RuntimeIntStore.remove(serverLevel, RUNTIME_KEY, pos);
        serverLevel.setBlock(pos, safe, Block.UPDATE_CLIENTS);
        // Old and newly selected output neighborhoods must both forget the
        // previous reading, even when only the mode or range was changed.
        serverLevel.updateNeighborsAt(pos, sensor);
        for (Direction direction : new Direction[] {
                oldSense, oldOutput, sensingSide(safe), outputSide(safe)
        }) serverLevel.updateNeighborsAt(pos.relative(direction), sensor);
        serverLevel.scheduleTick(pos, sensor, 1);
        return true;
    }

    /** Rotate the sensing/output pair without carrying old scan results into the new direction. */
    public static boolean rotateSensingAxis(Level level, BlockPos pos, boolean clockwise) {
        if (level.isClientSide) return false;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof RangeSensorBlock)) return false;
        Direction facing = sensingSide(state);
        Direction updated = clockwise ? facing.getClockWise() : facing.getCounterClockWise();
        return applyConfiguration(level, pos, state.setValue(FACING, updated));
    }

    /** Immediate physical scan for solver/tests; it does not mutate sensor runtime. */
    public static int detectedDistance(Level level, BlockPos pos, BlockState state) {
        return scan(level, pos, state).distance();
    }

    /** Observer-neutral server-owned result from the most recent scheduled sensor scan. */
    public static ScanResult lastScan(Level level, BlockPos pos, BlockState state) {
        int range = configuredRange(state);
        int[] runtime = RuntimeIntStore.peek(level, RUNTIME_KEY, pos);
        if (runtime == null || runtime.length != 3)
            return new ScanResult(0, ScanStatus.UNINITIALIZED, 0, range);
        if (runtime[1] < 0 || runtime[1] >= ScanStatus.values().length)
            return new ScanResult(0, ScanStatus.UNINITIALIZED, 0, range);
        ScanStatus status = ScanStatus.values()[runtime[1]];
        int distance = runtime[0];
        int coverage = runtime[2];
        // Reject inconsistent or previously configured scan data. A complete
        // CLEAR requires full range coverage; TARGET requires its hit cell.
        boolean coherent = switch (status) {
            case TARGET -> distance >= 1 && distance <= range && coverage == distance;
            case CLEAR -> distance == 0 && coverage == range;
            case INCOMPLETE_UNLOADED -> distance == 0 && coverage >= 0 && coverage < range;
            case UNINITIALIZED -> distance == 0 && coverage == 0;
        };
        return coherent ? new ScanResult(distance, status, coverage, range)
                : new ScanResult(0, ScanStatus.UNINITIALIZED, 0, range);
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
        // A complete CLEAR scan is a valid zero-distance measurement.
        // Uninitialized and unloaded scans are unknown, never "no signal".
        PortQuality quality = scan.complete() ? PortQuality.VALID : PortQuality.STALE;
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
            if (hitResult.getDirection() == Direction.UP) {
                ScanResult observed = lastScan(level, pos, state);
                PortQuality quality = observed.complete() ? PortQuality.VALID : PortQuality.STALE;
                player.displayClientMessage(Component.literal(
                        "RANGE FIELD TEST | direction=" + sensingSide(state).getName().toUpperCase()
                                + " | mode=" + modeName(state.getValue(MODE))
                                + " | scan=" + observed.status()
                                + " | coverage=" + observed.scannedCells() + "/" + observed.configuredRange()
                                + " | target-distance=" + (observed.targetDetected() ? observed.distance() : "NONE")
                                + " | output=" + state.getValue(OUTPUT) + "/15:" + quality
                                + " | UP=inspect only; other Shift faces=configure"
                                + " | CLEAR is a complete scan; UNLOADED is not a measurement"), false);
                return InteractionResult.sidedSuccess(false);
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
            if (!applyConfiguration(level, pos, next)) return InteractionResult.sidedSuccess(false);
            BlockState configured = level.getBlockState(pos);
            ScanResult scan = lastScan(level, pos, configured);
            player.displayClientMessage(Component.literal(
                    "Range Sensor | detect=" + modeName(configured.getValue(MODE))
                            + " | range=" + configuredRange(configured)
                            + " | response=" + responseName(configured.getValue(RESPONSE))
                            + " | scan=" + scan.status()
                            + " " + scan.scannedCells() + "/" + scan.configuredRange()
                            + " | distance=" + scan.distance()
                            + " | OUT=" + configured.getValue(OUTPUT) + " | awaiting a fresh scan"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static ScanResult scan(Level level, BlockPos pos, BlockState state) {
        int mode = state.getValue(MODE);
        RangeScanSupport.RayResult ray = RangeScanSupport.scan(
                level, pos, sensingSide(state), configuredRange(state),
                (world, cell) -> {
                    // Skip costly entity queries when BLOCK-only mode is selected.
                    if (mode != 1 && !world.getBlockState(cell).isAir()) return true;
                    if (mode == 0) return false;
                    AABB area = new AABB(cell.getX(), cell.getY(), cell.getZ(),
                            cell.getX() + 1, cell.getY() + 1, cell.getZ() + 1);
                    return !world.getEntitiesOfClass(LivingEntity.class, area).isEmpty();
                });
        ScanStatus status = switch (ray.status()) {
            case TARGET -> ScanStatus.TARGET;
            case CLEAR -> ScanStatus.CLEAR;
            case INCOMPLETE_UNLOADED -> ScanStatus.INCOMPLETE_UNLOADED;
        };
        return new ScanResult(ray.distance(), status, ray.scannedCells(), ray.maxRange());
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
