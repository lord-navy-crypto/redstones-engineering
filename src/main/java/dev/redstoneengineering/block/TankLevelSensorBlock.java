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

/** Base-mounted tank probe: counts contiguous fluid blocks above, up to 15. */
public class TankLevelSensorBlock extends DirectionalRedstoneSensorBlock {
    public TankLevelSensorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<TankLevelSensorBlock> codec() {
        return RedstoneEngineering.TANK_LEVEL_SENSOR_CODEC.value();
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int count = 0;
        for (int i = 1; i <= 15; i++) {
            BlockPos sample = pos.above(i);
            if (!level.hasChunkAt(sample) || level.getFluidState(sample).isEmpty()) break;
            count++;
        }
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
                    "Tank Level Sensor = " + state.getValue(POWER) + "/15 blocks"
                            + " | FRONT OUT=" + frontSide(state).getName()
            ), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
