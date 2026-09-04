package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.metrology.MeasurementSnapshot;
import dev.redstoneengineering.metrology.MetrologySupport;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Base-mounted tank probe with shared Alpha 1.0.15 metrology support. */
public class TankLevelSensorBlock extends DirectionalRedstoneSensorBlock {
    private static final String METROLOGY_CHANNEL = "tank_level";
    private static final int SENSOR_PROFILE = 2; // PRECISION
    private static final int SAMPLE_PERIOD_TICKS = 10;

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
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int physicalCount = 0;
        for (int i = 1; i <= 16; i++) {
            BlockPos sample = pos.above(i);
            if (!level.hasChunkAt(sample) || level.getFluidState(sample).isEmpty()) break;
            physicalCount++;
        }

        boolean saturated = physicalCount > 15;
        double reference = Math.min(15, physicalCount);
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
            player.displayClientMessage(Component.literal(
                    "Tank Level Sensor | Reading=" + state.getValue(POWER) + "/15"
                            + " | " + MetrologySupport.compactDiagnostics(measurement(level, pos))
                            + " | FRONT OUT=" + frontSide(state).getName()
            ), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
