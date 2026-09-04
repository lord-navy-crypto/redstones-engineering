package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.metrology.MetrologySupport;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

/** Non-contact occupancy/count sensor with explicit saturation and measurement quality. */
public class EntityDensitySensorBlock extends DirectionalRedstoneSensorBlock {
    private static final int SENSOR_PROFILE = 1; // BALANCED

    public EntityDensitySensorBlock(Properties properties) { super(properties); }

    @Override protected String metrologyChannel() { return "entity_density"; }

    @Override
    public MapCodec<EntityDensitySensorBlock> codec() {
        return RedstoneEngineering.ENTITY_DENSITY_SENSOR_CODEC.value();
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int physicalCount = level.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(pos).inflate(4.0, 2.0, 4.0)
        ).size();
        boolean saturated = physicalCount > 15;
        double reference = Math.min(15, physicalCount);
        double reading = MetrologySupport.conditionRedstone(level, pos, reference, SENSOR_PROFILE);
        sampleMeasurement(level, pos, reading, reference, saturated);
        updateSensorOutput(level, pos, state, (int) Math.round(reading), 10);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            player.displayClientMessage(Component.literal(
                    "Entity Density Sensor = " + state.getValue(POWER) + " nearby"
                            + " | " + MetrologySupport.compactDiagnostics(sensorMeasurement(level, pos))
                            + " | FRONT OUT=" + frontSide(state).getName()
            ), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
