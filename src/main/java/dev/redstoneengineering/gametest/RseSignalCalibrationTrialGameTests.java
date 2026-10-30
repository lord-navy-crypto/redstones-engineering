package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.SignalAnalyzerBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.diagnostics.SignalCalibrationTrialComparison;
import dev.redstoneengineering.diagnostics.SignalCalibrationTrialRecord;
import dev.redstoneengineering.diagnostics.SignalCalibrationTrialStore;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Executable contracts for the Signal Analyzer internal-reference calibration trial. */
public final class RseSignalCalibrationTrialGameTests {
    private static final String TEMPLATE = "empty5x4x5";
    private static final BlockPos KEY = new BlockPos(2, 1, 2);

    private RseSignalCalibrationTrialGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void lowerReferenceErrorIsImproved(GameTestHelper helper) {
        SignalCalibrationTrialComparison comparison = SignalCalibrationTrialComparison.between(
                record(1, 8, 0, 0, 760, 40, 1, 10),
                record(2, 8, 0, 1, 800, 0, 1, 10)
        );
        if (comparison.trend() != SignalCalibrationTrialComparison.Trend.IMPROVED
                || comparison.errorDelta100() != -40) {
            helper.fail("Lower absolute reference error must rank as IMPROVED", KEY);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void changedReferenceIsIncomparable(GameTestHelper helper) {
        SignalCalibrationTrialComparison comparison = SignalCalibrationTrialComparison.between(
                record(1, 8, 0, 0, 800, 0, 0, 0),
                record(2, 9, 0, 1, 900, 0, 0, 0)
        );
        if (comparison.comparable()
                || comparison.trend() != SignalCalibrationTrialComparison.Trend.INCOMPARABLE) {
            helper.fail("Different calibration references must not be ranked", KEY);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void lessClippingBreaksEqualErrorTie(GameTestHelper helper) {
        SignalCalibrationTrialComparison comparison = SignalCalibrationTrialComparison.between(
                record(1, 15, 0, 2, 1500, 0, 4, 0),
                record(2, 15, 0, 1, 1500, 0, 1, 0)
        );
        if (comparison.trend() != SignalCalibrationTrialComparison.Trend.IMPROVED
                || comparison.clippingDelta() != -3) {
            helper.fail("With equal reference error, lower clipping evidence must rank as improved", KEY);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void storeRequiresBaselineAndNewBaselineClearsCandidate(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos world = helper.absolutePos(KEY);
        SignalCalibrationTrialStore.clear(level, world);

        SignalCalibrationTrialRecord ready = record(1, 8, 0, 0, 800, 0, 0, 0);
        if (SignalCalibrationTrialStore.captureCandidate(level, world, ready).isPresent()) {
            helper.fail("Candidate capture must be rejected before a baseline exists", KEY);
            return;
        }

        SignalCalibrationTrialRecord baseline = SignalCalibrationTrialStore.captureBaseline(level, world, ready);
        SignalCalibrationTrialRecord candidate = SignalCalibrationTrialStore.captureCandidate(level, world, ready).orElse(null);
        if (baseline == null || candidate == null || SignalCalibrationTrialStore.comparison(level, world).isEmpty()) {
            helper.fail("Ready baseline/candidate evidence should produce a fixed comparison", KEY);
            return;
        }

        SignalCalibrationTrialStore.captureBaseline(level, world, ready);
        if (SignalCalibrationTrialStore.candidate(level, world).isPresent()) {
            helper.fail("Starting a new baseline must discard the previous candidate", KEY);
            return;
        }
        helper.succeed();
    }

    /**
     * Direct-entry TextField bindings may submit the already visible value.
     * Only a real server-side configuration transition counts as an experiment edit.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void directEntryIsBoundedAndCountsActualChangesOnly(GameTestHelper helper) {
        helper.setBlock(KEY, RedstoneEngineering.SIGNAL_ANALYZER.get().defaultBlockState());
        var level = helper.getLevel();
        BlockPos world = helper.absolutePos(KEY);
        var initial = SignalAnalyzerBlock.uiSnapshot(level, world);

        // Default offset=0 and x_ref=8: retransmission must not count as a switch.
        boolean unchangedAccepted = SignalAnalyzerBlock.setCalibrationOffset(level, world, 0)
                && SignalAnalyzerBlock.setReference(level, world, 8);
        var unchanged = SignalAnalyzerBlock.uiSnapshot(level, world);
        if (!unchangedAccepted
                || unchanged.calibrationSwitches() != initial.calibrationSwitches()
                || unchanged.referenceSwitches() != initial.referenceSwitches()) {
            helper.fail("Re-entering current values must not fabricate calibration/reference changes", KEY);
            return;
        }

        boolean newValuesAccepted = SignalAnalyzerBlock.setCalibrationOffset(level, world, 1)
                && SignalAnalyzerBlock.setReference(level, world, 9);
        var changed = SignalAnalyzerBlock.uiSnapshot(level, world);
        if (!newValuesAccepted
                || changed.calibrationOffset() != 1 || changed.reference() != 9
                || changed.calibrationSwitches() != initial.calibrationSwitches() + 1
                || changed.referenceSwitches() != initial.referenceSwitches() + 1) {
            helper.fail("One real edit per field must update the displayed value and its switch evidence once", KEY);
            return;
        }

        boolean repeatedAccepted = SignalAnalyzerBlock.setCalibrationOffset(level, world, 1)
                && SignalAnalyzerBlock.setReference(level, world, 9);
        boolean invalidRejected = !SignalAnalyzerBlock.setCalibrationOffset(level, world, -3)
                && !SignalAnalyzerBlock.setCalibrationOffset(level, world, 3)
                && !SignalAnalyzerBlock.setReference(level, world, -1)
                && !SignalAnalyzerBlock.setReference(level, world, 16);
        var finalSnapshot = SignalAnalyzerBlock.uiSnapshot(level, world);
        if (!repeatedAccepted || !invalidRejected
                || finalSnapshot.calibrationSwitches() != initial.calibrationSwitches() + 1
                || finalSnapshot.referenceSwitches() != initial.referenceSwitches() + 1
                || finalSnapshot.calibrationOffset() != 1 || finalSnapshot.reference() != 9) {
            helper.fail("Repeated or out-of-range entry must not alter actual values or switch history", KEY);
            return;
        }
        helper.succeed();
    }

    private static SignalCalibrationTrialRecord record(
            long sequence,
            int reference,
            int mode,
            int offset,
            int calibratedAverage100,
            int error100,
            int clipping,
            int meanStep100
    ) {
        return new SignalCalibrationTrialRecord(
                sequence,
                sequence * 10,
                reference,
                mode,
                net.minecraft.core.Direction.NORTH.ordinal(),
                offset,
                calibratedAverage100,
                calibratedAverage100,
                error100,
                0,
                meanStep100,
                clipping,
                16,
                16,
                0,
                PortQuality.VALID
        );
    }
}
