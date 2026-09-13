package dev.redstoneengineering.physics;

import dev.redstoneengineering.block.ConnectedCableBlock;
import dev.redstoneengineering.block.OpticalFiberBlock;
import dev.redstoneengineering.block.OpticalFiberJunctionBlock;
import dev.redstoneengineering.block.OpticalReceiverBlock;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.PortDirection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Read-only optical commissioning support. It never resolves or drives the optical graph. */
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

    /**
     * End-to-end evidence for one passive guided segment. A segment starts at an RSE optical OUTPUT
     * endpoint and ends at an Optical Receiver, with only passive fiber/junction nodes in between.
     * Splitters, filters and attenuators therefore become the source of their downstream segment;
     * their local transfer loss is not counted twice here.
     */
    public record SegmentBudget(
            boolean bounded,
            int passiveNodes,
            int passiveHops,
            int sourceCount,
            int sourceIntensity,
            int sourceChannel,
            int receiverIntensity,
            int receiverChannel,
            int observedSegmentLoss,
            int receiverHeadroom
    ) {
        public boolean channelCoherent() {
            return sourceCount == 1 && sourceChannel == receiverChannel;
        }

        public String integrity() {
            if (!bounded) return "TRUNCATED";
            if (sourceCount == 0) return "NO_SOURCE";
            if (sourceCount > 1) return "MULTIPLE_SOURCES";
            if (!channelCoherent()) return "CHANNEL_MISMATCH";
            if (receiverIntensity <= 0) return "DARK";
            if (receiverHeadroom <= 1) return "MARGINAL";
            return "HEALTHY";
        }
    }

    private record Visit(BlockPos pos, int hops) {}

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

    /**
     * Audits the passive guided segment feeding a receiver. The observer walks only already-loaded
     * physical fiber arms, is bounded by the shared network budget, and reads existing snapshots.
     */
    public static SegmentBudget segmentBudget(Level level, BlockPos receiverPos) {
        int receiverIntensity = OpticalReceiverBlock.intensity(level, receiverPos);
        int receiverChannel = OpticalReceiverBlock.channel(level, receiverPos);
        ArrayDeque<Visit> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        Map<BlockPos, Integer> hopsByNode = new HashMap<>();
        Map<BlockPos, BlockPos> sourceNodeByDevice = new HashMap<>();
        boolean bounded = true;

        for (Direction side : Direction.values()) {
            BlockPos neighbor = receiverPos.relative(side);
            if (!level.hasChunkAt(neighbor)) continue;
            BlockState state = level.getBlockState(neighbor);
            if (isPassiveOptical(state)
                    && ConnectedCableBlock.connected(state, side.getOpposite())) {
                queue.addLast(new Visit(neighbor, 0));
            }
        }

        while (!queue.isEmpty()) {
            if (visited.size() >= NetworkKernel.MAX_NODES) {
                bounded = false;
                break;
            }
            Visit visit = queue.removeFirst();
            if (!visited.add(visit.pos())) continue;
            hopsByNode.put(visit.pos(), visit.hops());
            BlockState state = level.getBlockState(visit.pos());

            for (Direction side : Direction.values()) {
                if (!ConnectedCableBlock.connected(state, side)) continue;
                BlockPos neighbor = visit.pos().relative(side);
                if (!level.hasChunkAt(neighbor)) {
                    bounded = false;
                    continue;
                }
                BlockState neighborState = level.getBlockState(neighbor);
                if (isPassiveOptical(neighborState)) {
                    if (edgeAllowed(level, visit.pos(), neighbor, side) && !visited.contains(neighbor)) {
                        queue.addLast(new Visit(neighbor, visit.hops() + 1));
                    }
                    continue;
                }
                if (neighbor.equals(receiverPos)) continue;
                if (isOpticalOutputToward(level, neighbor, neighborState, side.getOpposite())) {
                    sourceNodeByDevice.putIfAbsent(neighbor.immutable(), visit.pos().immutable());
                }
            }
        }

        int sourceCount = sourceNodeByDevice.size();
        int sourceIntensity = 0;
        int sourceChannel = 0;
        int passiveHops = 0;
        if (sourceCount == 1) {
            BlockPos sourceNode = sourceNodeByDevice.values().iterator().next();
            DomainNetwork.OpticalSample sample = DomainNetwork.sampleOptical(level, sourceNode);
            sourceIntensity = sample.valid() ? sample.intensity() : 0;
            sourceChannel = sample.valid() ? sample.channel() : 0;
            passiveHops = hopsByNode.getOrDefault(sourceNode, 0);
        }

        int observedLoss = sourceCount == 1 ? Math.max(0, sourceIntensity - receiverIntensity) : 0;
        // Receiver validity threshold in the current RSE optical model is intensity > 0.
        int headroom = Math.max(0, receiverIntensity - 1);
        return new SegmentBudget(
                bounded, visited.size(), passiveHops, sourceCount,
                sourceIntensity, sourceChannel, receiverIntensity, receiverChannel,
                observedLoss, headroom);
    }

    private static boolean isPassiveOptical(BlockState state) {
        return state.getBlock() instanceof OpticalFiberBlock
                || state.getBlock() instanceof OpticalFiberJunctionBlock;
    }

    private static boolean isOpticalOutputToward(Level level, BlockPos pos, BlockState state, Direction faceTowardSegment) {
        if (!(state.getBlock() instanceof EngineeringPortProvider provider)) return false;
        var port = provider.engineeringPort(state, faceTowardSegment);
        if (port.isEmpty()) return false;
        if (port.get().domain() != EngineeringDomain.OPTICAL || port.get().direction() != PortDirection.OUTPUT) return false;
        return provider.engineeringSnapshot(level, pos, state, faceTowardSegment)
                .map(snapshot -> snapshot.quality() == dev.redstoneengineering.core.port.PortQuality.VALID
                        && snapshot.value() > 0.0)
                .orElse(false);
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
