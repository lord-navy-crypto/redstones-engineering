package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.metrology.MeasurementSnapshot;
import dev.redstoneengineering.metrology.MetrologyStore;
import dev.redstoneengineering.physics.SensorModel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Locale;

/**
 * Base-mounted tank probe with Alpha 1.0.14 metrology diagnostics.
 *
 * The world-facing signal remains vanilla-compatible 0..15. Internally the
 * instrument keeps a conditioned measurement stream so repeatability, bias,
 * drift, noise, saturation, sample age and an uncertainty proxy can be
 * inspected without encoding diagnostic state into BlockState.
 */
public class TankLevelSensorBlock extends DirectionalRedstoneSensorBlock {
    private static final String METROLOGY_CHANNEL = "tank_level";
    private static final int SENSOR_PROFILE = 2; // PRECISION
    private static final int SAMPLE_PERIOD_TICKS = 10;
    private static final long STALE_AFTER_TICKS = 30L;

    public TankLevelSensorBlock(Properties properties) {
        super(properties);
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
        int normalizedReference = (int) Math.round(reference / 15.0 * 100.0);
        int conditioned = SensorModel.condition(level, pos, normalizedReference, SENSOR_PROFILE);
        double conditionedReading = conditioned / 100.0 * 15.0;
        int output = Math.max(0, Math.min(15, (int) Math.round(conditionedReading)));

        MetrologyStore.tracker(level, METROLOGY_CHANNEL, pos, 1.0, STALE_AFTER_TICKS)
                .sample(conditionedReading, reference, saturated, level.getGameTime());

        updateSensorOutput(level, pos, state, output, SAMPLE_PERIOD_TICKS);
    }

    public static MeasurementSnapshot measurement(Level level, BlockPos pos) {
        return MetrologyStore.tracker(level, METROLOGY_CHANNEL, pos, 1.0, STALE_AFTER_TICKS)
                .snapshot(level.getGameTime());
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
            MeasurementSnapshot m = measurement(level, pos);
            String diagnostics = m.quality().name();
            if (m.sampleCount() > 0) {
                diagnostics = String.format(
                        Locale.ROOT,
                        "%s | repeatability=±%.2f bias=%+.2f drift=%+.2f noise=%.2f resolution=%.2f age=%dt samples=%d uncertainty≈±%.2f",
                        m.quality().name(),
                        m.repeatability(),
                        m.bias(),
                        m.drift(),
                        m.noise(),
                        m.resolution(),
                        m.sampleAgeTicks(),
                        m.sampleCount(),
                        m.uncertaintyProxy()
                );
            }
            player.displayClientMessage(Component.literal(
                    "Tank Level Sensor | Reading=" + state.getValue(POWER) + "/15"
                            + " | " + diagnostics
                            + " | FRONT OUT=" + frontSide(state).getName()
            ), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
