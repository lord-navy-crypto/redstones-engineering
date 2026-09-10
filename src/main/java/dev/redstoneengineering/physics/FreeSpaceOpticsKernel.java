package dev.redstoneengineering.physics;

import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.FreeSpaceOpticalReceiverBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.Set;

/** Straight-line optical link with line-of-sight, channel filtering, alignment and finite power budget. */
public final class FreeSpaceOpticsKernel {
    private static final String MEDIUM = "free_optical";
    private static final int RANGE = 48;
    private static final int STALE_MARKER_QUALITY = 1;

    private FreeSpaceOpticsKernel() {}

    public static void emit(ServerLevel level, BlockPos source, Direction direction, int power, int channel) {
        int remaining = Math.max(0, Math.min(15, power));
        for (int i = 1; i <= 48 && remaining > 0; i++) {
            BlockPos target = source.relative(direction, i);
            if (!level.hasChunkAt(target)) {
                invalidateRetainedBeyondUnknownCoverage(level, source, direction, i, channel);
                break;
            }
            var state = level.getBlockState(target);
            if (state.getBlock() instanceof FreeSpaceOpticalReceiverBlock) {
                boolean aligned = state.getValue(DirectionalSignalBlock.FACING) == direction;
                if (aligned) {
                    int quality = Math.max(5, 100 - i * 2);
                    boolean channelOk = state.getValue(FreeSpaceOpticalReceiverBlock.CHANNEL) == channel;
                    InformationRuntime.write(
                            level,
                            MEDIUM,
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

    /**
     * An unavailable LOS sample means the current emission cannot revalidate any retained endpoint
     * farther along this same finite ray. Only already-existing runtime endpoints are touched; this
     * method never loads unknown chunks or searches arbitrary world blocks.
     */
    private static void invalidateRetainedBeyondUnknownCoverage(
            ServerLevel level,
            BlockPos source,
            Direction direction,
            int firstUnknownDistance,
            int channel
    ) {
        Set<BlockPos> retained = InformationRuntime.positions(level, MEDIUM);
        if (retained.isEmpty()) return;

        for (int distance = firstUnknownDistance; distance <= RANGE; distance++) {
            BlockPos endpoint = source.relative(direction, distance);
            if (!retained.contains(endpoint)) continue;

            InformationRuntime.Snapshot packet = InformationRuntime.snapshot(level, MEDIUM, endpoint);
            if (packet.ageTicks() < 0 || packet.selector() != channel) continue;

            if (level.hasChunkAt(endpoint)) {
                var state = level.getBlockState(endpoint);
                if (state.getBlock() instanceof FreeSpaceOpticalReceiverBlock receiver
                        && state.getValue(DirectionalSignalBlock.FACING) == direction
                        && state.getValue(FreeSpaceOpticalReceiverBlock.CHANNEL) == channel) {
                    receiver.invalidateCoverage(level, endpoint, state, channel);
                    continue;
                }
            }

            // Preserve explicit stale evidence even when the retained endpoint itself is unloaded.
            InformationRuntime.write(
                    level,
                    MEDIUM,
                    endpoint,
                    0,
                    channel,
                    false,
                    STALE_MARKER_QUALITY
            );
        }
    }
}
