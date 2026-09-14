package dev.redstoneengineering.diagnostics;

import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.PidControllerBlock;
import dev.redstoneengineering.block.PneumaticCylinderBlock;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.physics.PneumaticNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * Read-only system witness for the first Golden Engineering System.
 *
 * <p>The witness is deliberately conservative: it recognizes a pneumatic plant only when the
 * PID PROCESS VALUE face is directly connected to a formal cylinder FEEDBACK output. It never
 * radius-scans for a convenient actuator and never mutates controller, network, or plant state.</p>
 */
public final class PneumaticClosedLoopWitness {
    public static final int MIN_SAMPLES = 4;

    /** Evidence-based diagnostic candidate; this is not a claim about the exact failed component. */
    public enum Diagnosis {
        NO_WITNESS,
        COLLECTING_EVIDENCE,
        NOMINAL,
        NO_SUPPLY,
        RESTRICTION,
        LOW_ACTUATOR_PRESSURE,
        STALLED
    }

    private PneumaticClosedLoopWitness() {}

    public record Snapshot(
            boolean detected,
            boolean ready,
            BlockPos cylinderPos,
            int position,
            int target,
            int actuatorPressure,
            int supplyPressure,
            int observedLoss,
            int lineLoss,
            int restrictionLoss,
            int stallTicks,
            int samples,
            int penalty,
            CommissioningStatus plantStatus,
            Diagnosis diagnosis
    ) {
        public static Snapshot absent() {
            return new Snapshot(false, false, BlockPos.ZERO, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    CommissioningStatus.UNAVAILABLE, Diagnosis.NO_WITNESS);
        }
    }

    public static Snapshot inspect(Level level, BlockPos pidPos) {
        BlockState pidState = level.getBlockState(pidPos);
        if (!(pidState.getBlock() instanceof PidControllerBlock)) return Snapshot.absent();

        Direction controlOut = DirectionalSignalBlock.seriesOutputSide(pidState);
        Direction processSide = controlOut.getCounterClockWise();
        BlockPos cylinderPos = pidPos.relative(processSide);
        BlockState cylinderState = level.getBlockState(cylinderPos);
        if (!(cylinderState.getBlock() instanceof PneumaticCylinderBlock cylinder)) return Snapshot.absent();

        Direction cylinderFaceTowardPid = processSide.getOpposite();
        Optional<EngineeringPort> feedback = ((EngineeringPortProvider) cylinder)
                .engineeringPort(cylinderState, cylinderFaceTowardPid);
        if (feedback.isEmpty()
                || feedback.get().kind() != PortKind.FEEDBACK
                || feedback.get().direction() != PortDirection.OUTPUT) {
            return Snapshot.absent();
        }

        int position = PneumaticCylinderBlock.position(level, cylinderPos);
        int target = PneumaticCylinderBlock.target(level, cylinderPos);
        int pressure = PneumaticCylinderBlock.pressure(level, cylinderPos);
        int stallTicks = PneumaticCylinderBlock.stallTicks(level, cylinderPos);
        int samples = PneumaticCylinderBlock.samples(level, cylinderPos);
        PneumaticNetwork.ActuatorPathEvidence path = PneumaticNetwork.actuatorPathEvidence(level, cylinderPos);

        boolean ready = samples >= MIN_SAMPLES;
        int penalty = 0;
        CommissioningStatus status = ready ? CommissioningStatus.PASS : CommissioningStatus.RUNNING;
        int trackingError = Math.abs(target - position);

        if (ready) {
            if (stallTicks >= 10) {
                penalty += 45;
                status = CommissioningStatus.FAIL;
            } else if (stallTicks >= 4) {
                penalty += 20;
                status = worse(status, CommissioningStatus.MARGINAL);
            }

            if (path.restrictionLoss() >= 50 || path.observedLoss() >= 60) {
                penalty += 35;
                status = CommissioningStatus.FAIL;
            } else if (path.restrictionLoss() >= 25 || path.observedLoss() >= 30) {
                penalty += 15;
                status = worse(status, CommissioningStatus.MARGINAL);
            }

            if (trackingError >= 4 && pressure < 20) {
                penalty += 25;
                status = CommissioningStatus.FAIL;
            } else if (trackingError >= 2 && pressure < 35) {
                penalty += 10;
                status = worse(status, CommissioningStatus.MARGINAL);
            }

            if (path.supplyPressure() <= 0 && target > 0) {
                penalty += 30;
                status = CommissioningStatus.FAIL;
            }
        }

        Diagnosis diagnosis = diagnose(
                ready,
                target,
                pressure,
                path.supplyPressure(),
                path.observedLoss(),
                path.restrictionLoss(),
                trackingError,
                stallTicks
        );

        return new Snapshot(
                true,
                ready,
                cylinderPos,
                position,
                target,
                pressure,
                path.supplyPressure(),
                path.observedLoss(),
                path.lineLoss(),
                path.restrictionLoss(),
                stallTicks,
                samples,
                Math.min(100, penalty),
                status,
                diagnosis
        );
    }

    /**
     * Produces a likely-cause candidate from observed plant evidence only.
     * Upstream supply/restriction evidence outranks downstream symptoms such as stall.
     */
    static Diagnosis diagnose(
            boolean ready,
            int target,
            int actuatorPressure,
            int supplyPressure,
            int observedLoss,
            int restrictionLoss,
            int trackingError,
            int stallTicks
    ) {
        if (!ready) return Diagnosis.COLLECTING_EVIDENCE;
        if (supplyPressure <= 0 && target > 0) return Diagnosis.NO_SUPPLY;
        if (restrictionLoss >= 25 || observedLoss >= 30) return Diagnosis.RESTRICTION;
        if (trackingError >= 2 && actuatorPressure < 35) return Diagnosis.LOW_ACTUATOR_PRESSURE;
        if (stallTicks >= 4) return Diagnosis.STALLED;
        return Diagnosis.NOMINAL;
    }

    private static CommissioningStatus worse(CommissioningStatus a, CommissioningStatus b) {
        if (a == CommissioningStatus.FAIL || b == CommissioningStatus.FAIL) return CommissioningStatus.FAIL;
        if (a == CommissioningStatus.MARGINAL || b == CommissioningStatus.MARGINAL) return CommissioningStatus.MARGINAL;
        return a;
    }
}
