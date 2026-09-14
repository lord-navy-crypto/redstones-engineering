package dev.redstoneengineering.diagnostics;

import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Read-only commissioning facade for the existing PID runtime.
 *
 * The PID remains authoritative. This class consumes RuntimeIntStore.peek(), so
 * opening diagnostics cannot create, resize, or mutate controller state.
 */
public final class ClosedLoopCommissioning {
    private static final String PID_KEY = "pid";
    private static final int PID_RUNTIME_SIZE = 22;

    private ClosedLoopCommissioning() {}

    public static CommissioningSnapshot inspectPid(Level level, BlockPos pidPos) {
        CommissioningSnapshot controller = fromPidRuntime(
                RuntimeIntStore.peek(level, PID_KEY, pidPos), level.getGameTime());
        if (!controller.available()) return controller;

        PneumaticClosedLoopWitness.Snapshot plant = PneumaticClosedLoopWitness.inspect(level, pidPos);
        if (!plant.detected()) return controller;
        return combineWithPneumaticPlant(controller, plant);
    }

    /** Read-only plant evidence for HMI/diagnostic surfaces that need to explain the system verdict. */
    public static PneumaticClosedLoopWitness.Snapshot inspectPneumaticPlant(Level level, BlockPos pidPos) {
        return PneumaticClosedLoopWitness.inspect(level, pidPos);
    }

    /**
     * Adds a conservative pneumatic-plant penalty only when an explicit cylinder feedback witness
     * exists. Generic PID loops retain their original commissioning semantics unchanged.
     */
    public static CommissioningSnapshot combineWithPneumaticPlant(
            CommissioningSnapshot controller,
            PneumaticClosedLoopWitness.Snapshot plant
    ) {
        if (controller == null || plant == null || !controller.available() || !plant.detected()) {
            return controller;
        }

        if (!plant.ready()) {
            return copyWith(controller, controller.score(), CommissioningStatus.RUNNING);
        }

        int combinedScore = Math.max(0, controller.score() - plant.penalty());
        CommissioningStatus combinedStatus = worse(controller.status(), plant.plantStatus());
        if (combinedStatus == CommissioningStatus.PASS) {
            if (combinedScore < 50) combinedStatus = CommissioningStatus.FAIL;
            else if (combinedScore < 75) combinedStatus = CommissioningStatus.MARGINAL;
        }
        return copyWith(controller, combinedScore, combinedStatus);
    }

    /** Converts the established Alpha PID runtime layout into a stable diagnostic contract. */
    public static CommissioningSnapshot fromPidRuntime(int[] rt, long gameTime) {
        if (rt == null || rt.length < PID_RUNTIME_SIZE || rt[21] == 0) {
            return CommissioningSnapshot.unavailable();
        }

        int setpoint = rt[7];
        int process = rt[6];
        int error = setpoint - process;
        int rise90 = Math.max(0, rt[10]);
        int settling = Math.max(0, rt[11]);
        int overshoot = Math.max(0, rt[14]);
        int saturationEvents = Math.max(0, rt[5]);
        boolean stepActive = rt[12] != 0;
        int stepAge = rt[8] <= 0 ? 0 : clampToInt(Math.max(0L, gameTime - rt[8]));

        int score = score(error, settling, overshoot, saturationEvents, stepAge, stepActive);
        CommissioningStatus status;
        boolean hasStepEvidence = rt[8] > 0 || rise90 > 0 || settling > 0 || overshoot > 0;
        if (!hasStepEvidence) {
            status = CommissioningStatus.IDLE;
        } else if (stepActive) {
            status = CommissioningStatus.RUNNING;
        } else if (settling == 0 && stepAge > 600) {
            status = CommissioningStatus.FAIL;
        } else if (score >= 75) {
            status = CommissioningStatus.PASS;
        } else if (score >= 50) {
            status = CommissioningStatus.MARGINAL;
        } else {
            status = CommissioningStatus.FAIL;
        }

        return new CommissioningSnapshot(
                true,
                setpoint,
                process,
                rt[3],
                error,
                rise90,
                settling,
                overshoot,
                saturationEvents,
                stepAge,
                stepActive,
                rt[17] == 1,
                rt[4] != 0,
                Math.max(0, rt[19]),
                score,
                status
        );
    }

    public static CommissioningComparison compare(CommissioningSnapshot baseline, CommissioningSnapshot disturbed) {
        if (baseline == null || disturbed == null || !baseline.available() || !disturbed.available()) {
            throw new IllegalArgumentException("baseline and disturbed snapshots must be available");
        }
        int scoreLoss = Math.max(0, baseline.score() - disturbed.score());
        int settlingPenalty = Math.max(0, disturbed.settlingTicks() - baseline.settlingTicks());
        int overshootIncrease = Math.max(0, disturbed.overshoot() - baseline.overshoot());
        int saturationIncrease = Math.max(0, disturbed.saturationEvents() - baseline.saturationEvents());
        boolean robust = disturbed.status() != CommissioningStatus.FAIL
                && scoreLoss <= 20
                && settlingPenalty <= 80
                && overshootIncrease <= 1
                && saturationIncrease <= 4;
        return new CommissioningComparison(
                baseline,
                disturbed,
                scoreLoss,
                settlingPenalty,
                overshootIncrease,
                saturationIncrease,
                robust
        );
    }

    private static CommissioningSnapshot copyWith(
            CommissioningSnapshot source,
            int score,
            CommissioningStatus status
    ) {
        return new CommissioningSnapshot(
                source.available(),
                source.setpoint(),
                source.processValue(),
                source.controlOutput(),
                source.error(),
                source.rise90Ticks(),
                source.settlingTicks(),
                source.overshoot(),
                source.saturationEvents(),
                source.stepAgeTicks(),
                source.stepActive(),
                source.manualMode(),
                source.inhibited(),
                source.modeTransfers(),
                score,
                status
        );
    }

    private static CommissioningStatus worse(CommissioningStatus controller, CommissioningStatus plant) {
        if (controller == CommissioningStatus.FAIL || plant == CommissioningStatus.FAIL) return CommissioningStatus.FAIL;
        if (controller == CommissioningStatus.MARGINAL || plant == CommissioningStatus.MARGINAL) return CommissioningStatus.MARGINAL;
        if (controller == CommissioningStatus.UNAVAILABLE || plant == CommissioningStatus.UNAVAILABLE) return CommissioningStatus.UNAVAILABLE;
        if (controller == CommissioningStatus.RUNNING || plant == CommissioningStatus.RUNNING) return CommissioningStatus.RUNNING;
        if (controller == CommissioningStatus.IDLE || plant == CommissioningStatus.IDLE) return CommissioningStatus.IDLE;
        return CommissioningStatus.PASS;
    }

    private static int score(int error, int settling, int overshoot, int saturationEvents, int stepAge, boolean active) {
        int score = 100;
        score -= Math.min(32, Math.abs(error) * 8);
        score -= Math.min(24, overshoot * 8);
        score -= Math.min(18, saturationEvents);
        if (settling > 0) score -= Math.min(20, settling / 12);
        else if (!active && stepAge > 600) score -= 40;
        return Math.max(0, Math.min(100, score));
    }

    private static int clampToInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }
}
