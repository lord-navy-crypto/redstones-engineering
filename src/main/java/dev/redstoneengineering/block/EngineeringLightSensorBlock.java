package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Measures local combined brightness, including artificial light, as 0..15 Redstone. */
public class EngineeringLightSensorBlock extends DirectionalRedstoneSensorBlock {
    public EngineeringLightSensorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<EngineeringLightSensorBlock> codec() {
        return RedstoneEngineering.ENGINEERING_LIGHT_SENSOR_CODEC.value();
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int value = level.getMaxLocalRawBrightness(pos.above());
        updateSensorOutput(level, pos, state, value, 10);
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
                    "Local Light Sensor = " + state.getValue(POWER) + "/15"
                            + " | FRONT OUT=" + frontSide(state).getName()
            ), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
