package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
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

/** Measures local combined brightness with Alpha 1.0.15 metrology diagnostics. */
public class EngineeringLightSensorBlock extends DirectionalRedstoneSensorBlock {
    private static final int SENSOR_PROFILE = 1; // BALANCED

    public EngineeringLightSensorBlock(Properties properties) { super(properties); }

    @Override protected String metrologyChannel() { return "light_sensor"; }

    @Override
    public MapCodec<EngineeringLightSensorBlock> codec() {
        return RedstoneEngineering.ENGINEERING_LIGHT_SENSOR_CODEC.value();
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        double reference = level.getMaxLocalRawBrightness(pos.above());
        double reading = MetrologySupport.conditionRedstone(level, pos, reference, SENSOR_PROFILE);
        sampleMeasurement(level, pos, reading, reference, false);
        updateSensorOutput(level, pos, state, (int) Math.round(reading), 10);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            player.displayClientMessage(Component.literal(
                    "Local Light Sensor = " + state.getValue(POWER) + "/15"
                            + " | " + MetrologySupport.compactDiagnostics(sensorMeasurement(level, pos))
                            + " | FRONT OUT=" + frontSide(state).getName()
            ), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
