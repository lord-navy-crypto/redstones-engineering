package dev.redstoneengineering.block;

import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Passive junction port evidence; no implicit medium conversion or new edges. */
public final class JunctionEvidenceSupport {
    private JunctionEvidenceSupport() {}
    public static PortQuality loadedPortQuality(Level level, BlockPos pos, BlockState state,
                                                 Direction side, PortQuality quality) {
        if (ConnectedCableBlock.connected(state, side) && !level.hasChunkAt(pos.relative(side)))
            return PortQuality.STALE;
        return quality;
    }
    public static EngineeringPortSnapshot snapshot(EngineeringPort port, int value,
                                                    int maximum, PortQuality quality) {
        return MeterEvidenceSupport.snapshot(port, value, maximum, quality);
    }
}
