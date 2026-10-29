package dev.redstoneengineering.block;

import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.PrecisionObservationSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shared, read-only Quartz clock metrology.
 *
 * A clock output port transports a 0/1 phase, never its nominal period.
 * Period and jitter remain separate temporal metadata; a neighboring declared
 * source does not prove an actual network connection.
 */
final class QuartzClockSupport {
    private QuartzClockSupport() {}

    static EngineeringPortSnapshot phasePort(EngineeringPort port, boolean active) {
        return new EngineeringPortSnapshot(port, active ? 1.0 : 0.0, 0.0, 1.0, PortQuality.VALID);
    }

    static int halfPeriodTicks(int periodIndex) {
        return Math.max(1, QuartzTimingLineBlock.periodTicks(periodIndex) / 2);
    }

    static String fieldReport(Level level, BlockPos pos, BlockState state) {
        boolean lab = state.getBlock() instanceof QuartzLabOscillatorBlock;
        boolean active = lab ? state.getValue(QuartzLabOscillatorBlock.ACTIVE)
                : state.getValue(QuartzOscillatorBlock.ACTIVE);
        int index = lab ? state.getValue(QuartzLabOscillatorBlock.PERIOD_INDEX)
                : state.getValue(QuartzOscillatorBlock.PERIOD_INDEX);
        int period = QuartzTimingLineBlock.periodTicks(index);
        var next = PrecisionObservationSupport.quartz(
                level, pos.relative(DirectionalDomainSourceBlock.outputSide(state)));
        String report = "QUARTZ CLOCK FIELD TEST | phase=" + (active ? "HIGH" : "LOW")
                + " | nominal-period=" + period + "t"
                + " | nominal-half=" + halfPeriodTicks(index) + "t"
                + " | OUT=" + DirectionalDomainSourceBlock.outputSide(state).getName().toUpperCase()
                + " | neighbor-observation=" + next.quality()
                + (next.valid() ? (next.active() ? "/HIGH" : "/LOW") : "/UNAVAILABLE");
        if (lab) {
            var evidence = QuartzLabOscillatorBlock.timingEvidence(level, pos, state);
            report += " | jitter-bound=±" + state.getValue(QuartzLabOscillatorBlock.JITTER) + "t";
            report += evidence.available()
                    ? " | last-realized-half=" + evidence.lastHalfInterval() + "t"
                        + " | realized-offset=" + evidence.lastJitterOffset() + "t"
                    : " | last-realized-half=NOT_READY";
        }
        return report + " | adjacent observation is NOT proof of a valid cable route";
    }
}
