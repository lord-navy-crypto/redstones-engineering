package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.diagnostics.ClosedLoopCommissioning;
import dev.redstoneengineering.diagnostics.CommissioningComparison;
import dev.redstoneengineering.diagnostics.CommissioningSnapshot;
import dev.redstoneengineering.diagnostics.CommissioningStatus;
import dev.redstoneengineering.diagnostics.FaultInjectionModel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Executable contracts for Alpha 1.0.16 closed-loop commissioning and fault injection. */
public final class RseCommissioningGameTests {
    private static final String TEMPLATE = "empty5x4x5";
    private static final BlockPos MARKER = new BlockPos(2, 1, 2);

    private RseCommissioningGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void pidRuntimeProducesCommissioningSnapshot(GameTestHelper helper) {
        int[] rt = pidRuntime(12, 12, 9, 30, 60, 1, 1, 100);
        CommissioningSnapshot snapshot = ClosedLoopCommissioning.fromPidRuntime(rt, 200);
        if (!snapshot.available()
                || snapshot.status() != CommissioningStatus.PASS
                || snapshot.rise90Ticks() != 30
                || snapshot.settlingTicks() != 60
                || snapshot.overshoot() != 1
                || snapshot.error() != 0
                || snapshot.score() < 75) {
            helper.fail("Stable PID step response must project into a passing commissioning snapshot", MARKER);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void disturbedRunLosesRobustness(GameTestHelper helper) {
        CommissioningSnapshot baseline = ClosedLoopCommissioning.fromPidRuntime(
                pidRuntime(12, 12, 9, 30, 60, 1, 1, 100), 200);
        CommissioningSnapshot disturbed = ClosedLoopCommissioning.fromPidRuntime(
                pidRuntime(12, 10, 15, 65, 140, 3, 8, 100), 260);
        CommissioningComparison comparison = ClosedLoopCommissioning.compare(baseline, disturbed);
        if (comparison.robust()
                || comparison.scoreLoss() <= 20
                || comparison.overshootIncrease() != 2
                || comparison.saturationIncrease() != 7) {
            helper.fail("Materially degraded disturbed run must fail the robustness comparison", MARKER);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void faultInjectionPrimitivesAreBoundedAndRepeatable(GameTestHelper helper) {
        int a = FaultInjectionModel.addDeterministicNoise(8, 3, 41, 99, 0, 15);
        int b = FaultInjectionModel.addDeterministicNoise(8, 3, 41, 99, 0, 15);
        int biased = FaultInjectionModel.addBias(14, 4, 0, 15);
        int saturated = FaultInjectionModel.applySaturation(15, 9, 0, 15);
        int dropout = FaultInjectionModel.applyDropout(12, 5, 10, 0, 15);
        int latency = FaultInjectionModel.latencyTicks(99, 8);
        if (a != b || a < 5 || a > 11 || biased != 15 || saturated != 9 || dropout != 0 || latency != 8) {
            helper.fail("Fault injection must be deterministic where promised and preserve configured engineering bounds", MARKER);
            return;
        }
        helper.succeed();
    }

    private static int[] pidRuntime(int setpoint, int process, int output, int rise90, int settling,
                                    int overshoot, int saturationEvents, int stepStartTick) {
        int[] rt = new int[22];
        rt[3] = output;
        rt[5] = saturationEvents;
        rt[6] = process;
        rt[7] = setpoint;
        rt[8] = stepStartTick;
        rt[10] = rise90;
        rt[11] = settling;
        rt[12] = 0;
        rt[14] = overshoot;
        rt[17] = 0;
        rt[19] = 0;
        rt[21] = 1;
        return rt;
    }
}
