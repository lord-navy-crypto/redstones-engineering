package dev.redstoneengineering.physics;

import dev.redstoneengineering.block.ConnectedCableBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Read-only one-hop comparison support for optical commissioning HMIs. */
public final class OpticalCommissioningSupport {
    private OpticalCommissioningSupport() {}

    public record Evidence(
            int connectedNeighbors,
            int sameChannelNeighbors,
            int channelMismatchNeighbors,
            int strongestSameChannel,
            int weakestSameChannel
    ) {
        public int spread() {
            return sameChannelNeighbors <= 0 ? 0 : Math.max(0, strongestSameChannel - weakestSameChannel);
        }
    }

    public static Evidence compareOneHop(Level level, BlockPos target, int localChannel) {
        int connected = 0;
        int same = 0;
        int mismatch = 0;
        int strongest = 0;
        int weakest = Integer.MAX_VALUE;

        for (Direction direction : Direction.values()) {
            BlockPos neighbor = target.relative(direction);
            if (!level.hasChunkAt(neighbor) || !edgeAllowed(level, target, neighbor, direction)) continue;
            connected++;
            DomainNetwork.OpticalSample sample = DomainNetwork.sampleOptical(level, neighbor);
            if (!sample.valid() || sample.intensity() <= 0) continue;
            if (sample.channel() != localChannel) {
                mismatch++;
                continue;
            }
            same++;
            strongest = Math.max(strongest, sample.intensity());
            weakest = Math.min(weakest, sample.intensity());
        }

        if (weakest == Integer.MAX_VALUE) weakest = 0;
        return new Evidence(connected, same, mismatch, strongest, weakest);
    }

    private static boolean edgeAllowed(Level level, BlockPos a, BlockPos b, Direction direction) {
        BlockState sa = level.getBlockState(a);
        BlockState sb = level.getBlockState(b);
        if (!(sa.getBlock() instanceof ConnectedCableBlock) && !(sb.getBlock() instanceof ConnectedCableBlock)) return false;
        if (sa.getBlock() instanceof ConnectedCableBlock cableA
                && (!cableA.topologyValid(sa) || !ConnectedCableBlock.connected(sa, direction))) return false;
        if (sb.getBlock() instanceof ConnectedCableBlock cableB
                && (!cableB.topologyValid(sb) || !ConnectedCableBlock.connected(sb, direction.getOpposite()))) return false;
        return true;
    }
}
