package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.metrology.MeasurementSnapshot;
import dev.redstoneengineering.metrology.MetrologyStore;
import dev.redstoneengineering.metrology.MetrologySupport;
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

import java.util.List;
import java.util.Optional;

/**
 * Base-mounted tank level transmitter with selectable calibrated full-scale height.
 *
 * The UP aperture measures a continuous fluid column. The FRONT output is normalized to
 * redstone 0..15 against the configured 8/16/32-block full scale instead of exposing a raw count.
 */
public class TankLevelSensorBlock extends DirectionalRedstoneSensorBlock {
    public static final IntegerProperty RANGE_MODE = IntegerProperty.create("range_mode", 0, 2);

    private static final String METROLOGY_CHANNEL = "tank_level";
    private static final int SENSOR_PROFILE = 2; // PRECISION
    private static final int SAMPLE_PERIOD_TICKS = 10;

    /** complete=false means an unloaded cell hid the true top of the observed column. */
    public record ColumnSample(int fluidBlocks, int scannedCells, int expectedCells, int fullScale, boolean complete) {
        public boolean saturated() { return complete && fluidBlocks >= fullScale; }
        public PortQuality quality() {
            if (!complete) return PortQuality.STALE;
            return saturated() ? PortQuality.SATURATED : PortQuality.VALID;
        }
    }

    public TankLevelSensorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(RANGE_MODE, 1)); // legacy 16-block scale
    }

    @Override
    protected String metrologyChannel() {
        return METROLOGY_CHANNEL;
    }

    @Override
    public MapCodec<TankLevelSensorBlock> codec() {
        return RedstoneEngineering.TANK_LEVEL_SENSOR_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RANGE_MODE);
    }

    public static int heightForMode(int mode) {
        return switch (Math.max(0, Math.min(2, mode))) {
            case 0 -> 8;
            case 1 -> 16;
            default -> 32;
        };
    }

    public static int configuredHeight(BlockState state) {
        return heightForMode(state.getValue(RANGE_MODE));
    }

    public static int scaledLevelSignal(int fluidBlocks, int fullScale) {
        int boundedScale = Math.max(1, fullScale);
        int boundedLevel = Math.max(0, Math.min(boundedScale, fluidBlocks));
        return Math.max(0, Math.min(15,
                (int) Math.round((boundedLevel / (double) boundedScale) * 15.0)));
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort(
                        "TANK COLUMN",
                        Direction.UP,
                        EngineeringDomain.GENERIC,
                        PortKind.SENSOR,
                        PortDirection.INPUT,
                        false,
                        "blocks"
                ),
                new EngineeringPort(
                        "SENSOR OUT",
                        frontSide(state),
                        EngineeringDomain.REDSTONE,
                        PortKind.SENSOR,
                        PortDirection.OUTPUT,
                        true,
                        "signal"
                )
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        if (side == Direction.UP) {
            ColumnSample sample = columnSample(level, pos, state);
            return Optional.of(new EngineeringPortSnapshot(
                    port.get(),
                    Math.min(sample.fullScale(), sample.fluidBlocks()),
                    0.0,
                    sample.fullScale(),
                    sample.quality()));
        }
        return Optional.of(EngineeringPortSnapshot.redstone(
                port.get(), state.getValue(POWER), MetrologySupport.portQuality(sensorMeasurement(level, pos))));
    }

    /**
     * Scan upward until the first loaded empty cell or the configured full-scale ceiling.
     * An unloaded cell is unknown coverage, never a confirmed fluid-air boundary.
     */
    public static ColumnSample columnSample(Level level, BlockPos pos, BlockState state) {
        int fullScale = configuredHeight(state);
        int count = 0;
        int scanned = 0;
        for (int i = 1; i <= fullScale; i++) {
            BlockPos sample = pos.above(i);
            if (!level.hasChunkAt(sample)) {
                return new ColumnSample(count, scanned, fullScale, fullScale, false);
            }
            scanned++;
            if (level.getFluidState(sample).isEmpty()) {
                return new ColumnSample(count, scanned, scanned, fullScale, true);
            }
            count++;
        }
        return new ColumnSample(count, scanned, fullScale, fullScale, true);
    }

    /** Compatibility accessor preserving the old default 16-block physical count contract. */
    public static int physicalCount(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof TankLevelSensorBlock) {
            return columnSample(level, pos, state).fluidBlocks();
        }
        return 0;
    }

    @Override
    protected void neighborChanged(
            BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean movedByPiston
    ) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, movedByPiston);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        ColumnSample column = columnSample(level, pos, state);
        if (!column.complete()) {
            // Preserve the last trustworthy output/sample; age naturally turns it STALE.
            level.scheduleTick(pos, this, SAMPLE_PERIOD_TICKS);
            return;
        }

        boolean saturated = column.saturated();
        double reference = scaledLevelSignal(column.fluidBlocks(), column.fullScale());
        double reading = MetrologySupport.conditionRedstone(level, pos, reference, SENSOR_PROFILE);
        sampleMeasurement(level, pos, reading, reference, saturated);
        updateSensorOutput(level, pos, state, (int) Math.round(reading), SAMPLE_PERIOD_TICKS);
    }

    public static MeasurementSnapshot measurement(Level level, BlockPos pos) {
        return MetrologySupport.snapshot(level, METROLOGY_CHANNEL, pos, 1.0, 30L);
    }

    public static boolean adjustRange(Level level, BlockPos pos, int delta) {
        if (!(level instanceof ServerLevel server)) return false;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof TankLevelSensorBlock sensor)) return false;
        int nextMode = Math.floorMod(state.getValue(RANGE_MODE) + delta, 3);
        BlockState updated = state.setValue(RANGE_MODE, nextMode);
        level.setBlock(pos, updated, Block.UPDATE_CLIENTS);

        // A new full-scale range makes the old normalized reading incomparable.
        MetrologyStore.remove(level, METROLOGY_CHANNEL, pos);
        server.scheduleTick(pos, sensor, 1);
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                ColumnSample column = columnSample(level, pos, state);
                player.displayClientMessage(Component.literal(
                        "Tank Level Sensor | UP column=" + column.fluidBlocks() + " blocks"
                                + " | fullScale=" + column.fullScale() + " blocks"
                                + " | normalized=" + scaledLevelSignal(column.fluidBlocks(), column.fullScale()) + "/15"
                                + " | coverage=" + column.scannedCells() + "/" + column.expectedCells()
                                + " " + (column.complete() ? "COMPLETE" : "INCOMPLETE")
                                + " | Reading=" + state.getValue(POWER) + "/15"
                                + " | " + MetrologySupport.compactDiagnostics(measurement(level, pos))
                                + " | FRONT OUT=" + frontSide(state).getName()
                ), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
