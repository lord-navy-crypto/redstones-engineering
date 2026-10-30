package dev.redstoneengineering.block;

import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.instrument.InstrumentNetwork;
import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Shared read-only instrument coverage and shielding evidence; no extra scan. */
public final class InstrumentCableEvidenceSupport {
    private InstrumentCableEvidenceSupport() {}
    public static EngineeringPortSnapshot snapshot(Level level, BlockPos pos, BlockState state,
                                                     EngineeringPort port,
                                                     InstrumentNetwork.ProbeSnapshot scan) {
        PortQuality quality = scan.qualityForMask(0xF);
        // A visibly connected arm crossing an unloaded boundary means we cannot
        // certify that the observed subset contains every probe or conflict.
        for (Direction side : Direction.values()) {
            if (ConnectedCableBlock.connected(state, side)
                    && !level.hasChunkAt(pos.relative(side))) {
                quality = PortQuality.STALE;
                break;
            }
        }
        return MeterEvidenceSupport.snapshot(port, scan.validChannelsInMask(0xF), 4, quality);
    }
    public static String interference(InstrumentNetwork.ProbeSnapshot scan) {
        return "shielding=" + scan.shieldingIntegrity()
                + " " + scan.shieldingCoveragePercent() + "%"
                + " | interference=" + scan.interferenceIntegrity()
                + " exposure=" + scan.interferenceExposurePercent() + "%"
                + " confidence=" + scan.interferenceConfidencePercent() + "%";
    }
}
