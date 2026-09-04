package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.metrology.MeasurementQuality;
import dev.redstoneengineering.metrology.MeasurementSnapshot;
import dev.redstoneengineering.metrology.MetrologySupport;
import dev.redstoneengineering.metrology.MetrologyTracker;
import dev.redstoneengineering.visualization.MechatronicsVisualState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Executable contracts for metrology rollout and the read-only visualization boundary. */
public final class RseMetrologyGameTests {
    private static final String TEMPLATE = "empty5x4x5";
    private static final BlockPos MARKER = new BlockPos(2, 1, 2);

    private RseMetrologyGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void stableMeasurementReportsGoodQuality(GameTestHelper helper) {
        MetrologyTracker tracker = new MetrologyTracker(1.0, 30);
        MeasurementSnapshot snapshot = null;
        for (int i = 0; i < MetrologyTracker.WINDOW; i++) snapshot = tracker.sample(8.0, 8.0, false, i);
        if (snapshot == null
                || snapshot.quality() != MeasurementQuality.GOOD
                || snapshot.sampleCount() != MetrologyTracker.WINDOW
                || Math.abs(snapshot.bias()) > 1.0e-9
                || Math.abs(snapshot.repeatability()) > 1.0e-9) {
            helper.fail("Stable reference-aligned measurement must remain GOOD with zero bias/repeatability", MARKER);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void driftAndBiasDegradeMeasurementQuality(GameTestHelper helper) {
        MetrologyTracker tracker = new MetrologyTracker(1.0, 30);
        MeasurementSnapshot snapshot = null;
        for (int i = 0; i < MetrologyTracker.WINDOW; i++) {
            double residual = i < MetrologyTracker.WINDOW / 2 ? 0.0 : 2.0;
            snapshot = tracker.sample(8.0 + residual, 8.0, false, i);
        }
        if (snapshot == null
                || snapshot.quality() != MeasurementQuality.DEGRADED
                || snapshot.bias() < 0.9
                || snapshot.drift() < 1.9
                || snapshot.uncertaintyProxy() <= 1.0) {
            helper.fail("Bias/drift window must produce a degraded, nontrivial uncertainty estimate", MARKER);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void saturationAndAgeAreExplicitQualityStates(GameTestHelper helper) {
        MetrologyTracker tracker = new MetrologyTracker(1.0, 30);
        MeasurementSnapshot saturated = tracker.sample(15.0, 15.0, true, 100);
        MeasurementSnapshot stale = tracker.snapshot(131);
        if (saturated.quality() != MeasurementQuality.SATURATED) {
            helper.fail("Saturated sample must report SATURATED", MARKER);
            return;
        }
        if (stale.quality() != MeasurementQuality.STALE || stale.sampleAgeTicks() != 31) {
            helper.fail("Measurement older than stale threshold must report STALE with sample age", MARKER);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void sharedPortQualityPreservesHardMeasurementStates(GameTestHelper helper) {
        MetrologyTracker tracker = new MetrologyTracker(1.0, 30);
        MeasurementSnapshot saturated = tracker.sample(15.0, 15.0, true, 10);
        if (MetrologySupport.portQuality(saturated) != PortQuality.SATURATED) {
            helper.fail("Shared metrology support must preserve SATURATED at the Engineering Port boundary", MARKER);
            return;
        }
        MeasurementSnapshot stale = tracker.snapshot(41);
        if (MetrologySupport.portQuality(stale) != PortQuality.STALE) {
            helper.fail("Shared metrology support must preserve STALE at the Engineering Port boundary", MARKER);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void calibrationComparisonSeparatesResidualFromUncertainty(GameTestHelper helper) {
        MetrologyTracker tracker = new MetrologyTracker(1.0, 30);
        MeasurementSnapshot snapshot = null;
        for (int i = 0; i < MetrologyTracker.WINDOW; i++) snapshot = tracker.sample(6.5, 8.0, false, i);
        if (snapshot == null
                || Math.abs(snapshot.bias() + 1.5) > 1.0e-9
                || snapshot.uncertaintyProxy() < 1.5
                || snapshot.quality() != MeasurementQuality.DEGRADED) {
            helper.fail("Calibration comparison must expose signed residual bias separately from uncertainty proxy", MARKER);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void visualizationProjectionIsNormalizedAndImmutable(GameTestHelper helper) {
        MechatronicsVisualState servo = MechatronicsVisualState.servo(15, 3, true, 3);
        MechatronicsVisualState cylinder = MechatronicsVisualState.cylinder(8, 1, 50);
        MechatronicsVisualState valve = MechatronicsVisualState.valve(12, 75);
        if (Math.abs(servo.position01() - 1.0) > 1.0e-9
                || Math.abs(servo.velocitySigned() - 1.0) > 1.0e-9
                || !servo.braked()
                || Math.abs(cylinder.position01() - (8.0 / 15.0)) > 1.0e-9
                || Math.abs(cylinder.pressure01() - 0.5) > 1.0e-9
                || Math.abs(valve.opening01() - 0.8) > 1.0e-9) {
            helper.fail("Mechatronics visual projection must preserve normalized simulation state", MARKER);
            return;
        }
        helper.succeed();
    }
}
