package dev.redstoneengineering.block;

import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.RedstoneObservationSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** Observer-only safety input evidence. Invalid and driven-zero channels both block a
 * permit, but remain distinguishable when troubleshooting a real installation.
 */
final class SafetyObservationSupport {
    private SafetyObservationSupport() {}

    static RedstoneObservationSupport.Observation input(Level level, BlockPos pos, Direction side) {
        return RedstoneObservationSupport.observe(level, pos, side);
    }

    static EngineeringPortSnapshot inputPort(Level level, BlockPos pos, Direction side, EngineeringPort port) {
        var evidence = input(level, pos, side);
        return EngineeringPortSnapshot.redstone(port, evidence.value(), evidence.quality());
    }

    static boolean permissive(RedstoneObservationSupport.Observation observation) {
        return observation.valid() && observation.value() > 0;
    }

    /** Bits A, B, C respectively mean not proven to be an asserted permissive. */
    static int failedMask(RedstoneObservationSupport.Observation a,
                          RedstoneObservationSupport.Observation b,
                          RedstoneObservationSupport.Observation c) {
        return (permissive(a) ? 0 : 1)
                | (permissive(b) ? 0 : 2)
                | (permissive(c) ? 0 : 4);
    }

    static String evidence(RedstoneObservationSupport.Observation observation) {
        return observation.value() + "/15:" + observation.quality();
    }

    static String faultReason(RedstoneObservationSupport.Observation observation) {
        if (!observation.valid()) {
            return observation.quality() == PortQuality.STALE ? "UNKNOWN_COVERAGE"
                    : "MISSING_OR_INVALID_SOURCE";
        }
        return observation.value() > 0 ? "ASSERTED" : "VALID_LOW";
    }
}
