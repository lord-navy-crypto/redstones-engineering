package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
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

/** Non-contact occupancy/count sensor: one signal level per nearby living entity, capped at 15. */
public class EntityDensitySensorBlock extends DirectionalRedstoneSensorBlock {
    public EntityDensitySensorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<EntityDensitySensorBlock> codec() {
        return RedstoneEngineering.ENTITY_DENSITY_SENSOR_CODEC.value();
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int count = Math.min(15, level.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(pos).inflate(4.0, 2.0, 4.0)
        ).size());
        updateSensorOutput(level, pos, state, count, 10);
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
                    "Entity Density Sensor = " + state.getValue(POWER) + " nearby"
                            + " | FRONT OUT=" + frontSide(state).getName()
            ), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
