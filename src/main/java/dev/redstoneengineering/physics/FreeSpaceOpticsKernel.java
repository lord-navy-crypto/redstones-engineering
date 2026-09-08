package dev.redstoneengineering.physics;

import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.FreeSpaceOpticalReceiverBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/** Straight-line optical link with line-of-sight, channel filtering, alignment and finite power budget. */
public final class FreeSpaceOpticsKernel {
    private FreeSpaceOpticsKernel() {}

    public static void emit(ServerLevel level, BlockPos source, Direction direction, int power, int channel) {
        int remaining = Math.max(0, Math.min(15, power));
        for (int i = 1; i <= 48 && remaining > 0; i++) {
            BlockPos target = source.relative(direction, i);
            if (!level.hasChunkAt(target)) break;
            var state = level.getBlockState(target);
            if (state.getBlock() instanceof FreeSpaceOpticalReceiverBlock) {
                boolean aligned = state.getValue(DirectionalSignalBlock.FACING) == direction;
                if (aligned) {
                    int quality = Math.max(5, 100 - i * 2);
                    boolean channelOk = state.getValue(FreeSpaceOpticalReceiverBlock.CHANNEL) == channel;
                    InformationRuntime.write(
                            level,
                            "free_optical",
                            target,
                            remaining,
                            channel,
                            channelOk,
                            quality
                    );
                    level.scheduleTick(target, state.getBlock(), 1);
                }
                break;
            }
            if (!state.isAir()) break;
            if (i % 8 == 0) remaining--;
        }
    }
}
