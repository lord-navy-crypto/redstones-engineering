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
import dev.redstoneengineering.metrology.MetrologySupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/** Base-mounted tank probe with an explicit UP fluid-column aperture and FRONT redstone readout. */
public class TankLevelSensorBlock extends DirectionalRedstoneSensorBlock {
    private static final String METROLOGY_CHANNEL = "tank_level";
    private static final int SENSOR_PROFILE = 2; // PRECISION
    private static final int SAMPLE_PERIOD_TICKS = 10;
    private static final int MAX_SCAN_HEIGHT = 16;

    /** Physical column observation. complete=false means an unloaded cell hid the true top of the column. */
    public record ColumnSample(int fluidBlocks, int scannedCells, int expectedCells, boolean complete) {
        public boolean saturated() { return complete && fluidBlocks > 15; }
    }

    public TankLevelSensorBlock(Properties properties) {
        super(properties);
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
            ColumnSample sample = columnSample(level, pos);
            PortQuality quality = !sample.complete()
                    ? PortQuality.STALE
                    : sample.saturated() ? PortQuality.SATURATED : PortQuality.VALID;
            return Optional.of(new EngineeringPortSnapshot(
                    port.get(), Math.min(15, sample.fluidBlocks()), 0.0, 15.0, quality));
        }
        return Optional.of(EngineeringPortSnapshot.redstone(
                port.get(), state.getValue(POWER), MetrologySupport.portQuality(sensorMeasurement(level, pos))));
    }

    /**
     * Scan upward until the first loaded empty cell or the 16-block measurement
     * ceiling. An unloaded cell is unknown coverage, never a confirmed fluid-air
     * boundary.
     */
    public static ColumnSample columnSample(Level level, BlockPos pos) {
        int count = 0;
        int scanned = 0;
        for (int i = 1; i <= MAX_SCAN_HEIGHT; i++) {
            BlockPos sample = pos.above(i);
            if (!level.hasChunkAt(sample)) {
                return new ColumnSample(count, scanned, MAX_SCAN_HEIGHT, false);
            }
            scanned++;
            if (level.getFluidState(sample).isEmpty()) {
                return new ColumnSample(count, scanned, scanned, true);
            }
            count++;
        }
        return new ColumnSample(count, scanned, MAX_SCAN_HEIGHT, true);
    }

    /** Compatibility numeric accessor; use columnSample when certainty matters. */
    public static int physicalCount(Level level, BlockPos pos) {
        return columnSample(level, pos).fluidBlocks();
    }

    @Override
    protected void neighborChanged(
            BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean movedByPiston
    ) {
        super.neighborChanged(state, level, pos, blockOrSelf(neighbor), neighborPos, movedByPiston);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    private static Block blockOrSelf(Block block) { return block; }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        ColumnSample column = columnSample(level, pos);
        if (!column.complete()) {
            // Retain the last trustworthy output/sample. Its age will naturally
            // transition to STALE if coverage remains incomplete.
            level.scheduleTick(pos, this, SAMPLE_PERIOD_TICKS);
            return;
        }
        boolean saturated = column.saturated();
        double reference = Math.min(15, column.fluidBlocks());
        double reading = MetrologySupport.conditionRedstone(level, pos, reference, SENSOR_PROFILE);
        sampleMeasurement(level, pos, reading, reference, saturated);
        updateSensorOutput(level, pos, state, (int) Math.round(reading), SAMPLE_PERIOD_TICKS);
    }

    public static MeasurementSnapshot measurement(Level level, BlockPos pos) {
        return MetrologySupport.snapshot(level, METROLOGY_CHANNEL, pos, 1.0, 30L);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {
        if (!level.isClientSide) {
            ColumnSample column = columnSample(level, pos);
            player.displayClientMessage(Component.literal(
                    "Tank Level Sensor | UP column=" + column.fluidBlocks() + " blocks"
                            + " | coverage=" + column.scannedCells() + "/" + column.expectedCells()
                            + " " + (column.complete() ? "COMPLETE" : "INCOMPLETE")
                            + " | Reading=" + state.getValue(POWER) + "/15"
                            + " | " + MetrologySupport.compactDiagnostics(measurement(level, pos))
                            + " | FRONT OUT=" + frontSide(state).getName()
            ), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
