package dev.redstoneengineering.diagnostics.topology;

import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortCompatibility;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds an all-face diagnostic projection from the authoritative Engineering Port contract.
 *
 * This class does not solve networks, schedule ticks, modify BlockState, or write runtime stores.
 * It observes descriptors/snapshots that the simulation already owns and classifies only the
 * immediate physical interface with the existing PortCompatibility rules.
 */
public final class EngineeringTopologyView {
    private EngineeringTopologyView() {}

    public static TopologyVisualizationSnapshot inspect(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof EngineeringPortProvider provider)) {
            return TopologyVisualizationSnapshot.empty();
        }

        List<TopologyFaceSnapshot> faces = new ArrayList<>(Direction.values().length);
        int portCount = 0;
        int connectedCount = 0;
        int issueCount = 0;

        for (Direction side : Direction.values()) {
            EngineeringPort local = provider.engineeringPort(state, side).orElse(null);
            EngineeringPortSnapshot observation = local == null
                    ? null
                    : provider.engineeringSnapshot(level, pos, state, side).orElse(null);

            TopologyFaceSnapshot face;
            if (local == null) {
                face = new TopologyFaceSnapshot(side, null, null, TopologyLinkStatus.ISOLATED, "none", "no engineering port");
            } else {
                portCount++;
                face = inspectPort(level, pos, side, local, observation);
                if (face.linkStatus() == TopologyLinkStatus.CONNECTED) connectedCount++;
                if (face.topologyIssue()) issueCount++;
            }
            faces.add(face);
        }

        return new TopologyVisualizationSnapshot(faces, portCount, connectedCount, issueCount);
    }

    private static TopologyFaceSnapshot inspectPort(
            Level level,
            BlockPos pos,
            Direction side,
            EngineeringPort local,
            EngineeringPortSnapshot observation
    ) {
        BlockPos neighborPos = pos.relative(side);
        if (!level.hasChunkAt(neighborPos)) {
            return new TopologyFaceSnapshot(side, local, observation, TopologyLinkStatus.UNLOADED, "unloaded", "neighbor chunk unavailable");
        }

        BlockState neighborState = level.getBlockState(neighborPos);
        String neighborId = BuiltInRegistries.BLOCK.getKey(neighborState.getBlock()).toString();
        if (!(neighborState.getBlock() instanceof EngineeringPortProvider neighborProvider)) {
            return new TopologyFaceSnapshot(side, local, observation, TopologyLinkStatus.OPEN, neighborId, "neighbor has no engineering port contract");
        }

        EngineeringPort remote = neighborProvider.engineeringPort(neighborState, side.getOpposite()).orElse(null);
        if (remote == null) {
            return new TopologyFaceSnapshot(side, local, observation, TopologyLinkStatus.OPEN, neighborId, "neighbor opposite face is isolated");
        }

        PortCompatibility.Result result = PortCompatibility.evaluate(local, remote);
        TopologyLinkStatus status = switch (result.status()) {
            case COMPATIBLE -> TopologyLinkStatus.CONNECTED;
            case DOMAIN_MISMATCH -> TopologyLinkStatus.DOMAIN_MISMATCH;
            case DIRECTION_MISMATCH -> TopologyLinkStatus.DIRECTION_MISMATCH;
            case ISOLATED -> TopologyLinkStatus.ISOLATED;
        };
        return new TopologyFaceSnapshot(side, local, observation, status, neighborId, result.detail());
    }
}
