package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.LogicAnalyzerBlock;
import dev.redstoneengineering.block.LapisLowPassFilterBlock;
import dev.redstoneengineering.block.OscilloscopeBlock;
import dev.redstoneengineering.block.PidControllerBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.SignalAnalyzerBlock;
import dev.redstoneengineering.block.SignalConditionerBlock;
import dev.redstoneengineering.blockentity.LogicAnalyzerBlockEntity;
import dev.redstoneengineering.blockentity.OscilloscopeBlockEntity;
import dev.redstoneengineering.diagnostics.PidTelemetryStore;
import dev.redstoneengineering.diagnostics.acceptance.EngineeringAcceptanceStatus;
import dev.redstoneengineering.physics.EngineeringParameterProfile;
import dev.redstoneengineering.ui.menu.LogicAnalyzerMenu;
import dev.redstoneengineering.ui.menu.OscilloscopeMenu;
import dev.redstoneengineering.ui.menu.PidControllerMenu;
import dev.redstoneengineering.ui.menu.SignalAnalyzerMenu;
import dev.redstoneengineering.ui.menu.SignalConditionerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Runtime guards for server-authoritative actions exposed through Engineering UI.
 * Client rendering is intentionally not part of GameTest; these tests prove the actions behind
 * buttons preserve the same physical semantics used outside the screens.
 */
public final class RseEngineeringUiGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseEngineeringUiGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void conditionerUiActionsDriveAuthoritativeWorldState(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(1, 1, 2);
        BlockPos conditionerPos = new BlockPos(2, 1, 2);
        BlockPos conditionerWorldPos = helper.absolutePos(conditionerPos);

        helper.setBlock(sourcePos, RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get()
                .defaultBlockState()
                .setValue(RedstoneReferenceSourceBlock.FACING, Direction.EAST)
                .setValue(RedstoneReferenceSourceBlock.POWER, 6));
        helper.setBlock(conditionerPos, RedstoneEngineering.SIGNAL_CONDITIONER.get()
                .defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(SignalConditionerBlock.MODE, 0)
                .setValue(SignalConditionerBlock.PARAM, 2));

        boolean modeChanged = SignalConditionerBlock.applyConfigurationAction(
                helper.getLevel(), conditionerWorldPos, SignalConditionerMenu.BUTTON_MODE_NEXT);
        boolean paramChanged = SignalConditionerBlock.applyConfigurationAction(
                helper.getLevel(), conditionerWorldPos, SignalConditionerMenu.BUTTON_PARAM_INCREASE);

        if (!modeChanged || !paramChanged) {
            helper.fail("Conditioner rejected a valid server-side Engineering UI action", conditionerPos);
            return;
        }

        helper.runAfterDelay(8, () -> {
            BlockState state = helper.getBlockState(conditionerPos);
            if (state.getValue(SignalConditionerBlock.MODE) != 1) {
                helper.fail("UI mode action did not persist authoritative OFFSET mode", conditionerPos);
                return;
            }
            if (state.getValue(SignalConditionerBlock.PARAM) != 6) {
                helper.fail("UI parameter action did not persist OFFSET parameter 6", conditionerPos);
                return;
            }
            if (state.getValue(DirectionalSignalBlock.OUTPUT) != 7) {
                helper.fail("Conditioner UI configuration did not drive real world output 6 + 1 = 7", conditionerPos);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void pidUiActionChangesOnlyBoundedTuningPreset(GameTestHelper helper) {
        BlockPos pidPos = new BlockPos(2, 1, 2);
        BlockPos pidWorldPos = helper.absolutePos(pidPos);
        helper.setBlock(pidPos, RedstoneEngineering.PID_CONTROLLER.get()
                .defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.NORTH)
                .setValue(PidControllerBlock.TUNING, 2));

        BlockState before = helper.getBlockState(pidPos);
        int outputBefore = before.getValue(DirectionalSignalBlock.OUTPUT);

        if (!PidControllerBlock.applyTuningAction(
                helper.getLevel(), pidWorldPos, PidControllerMenu.BUTTON_TUNING_NEXT)) {
            helper.fail("PID rejected a valid Engineering UI tuning action", pidPos);
            return;
        }

        BlockState afterNext = helper.getBlockState(pidPos);
        if (afterNext.getValue(PidControllerBlock.TUNING) != 3) {
            helper.fail("PID UI next action did not select PID-AGGRESSIVE", pidPos);
            return;
        }
        if (afterNext.getValue(DirectionalSignalBlock.OUTPUT) != outputBefore) {
            helper.fail("Tuning UI action directly rewrote control output instead of leaving physics authoritative", pidPos);
            return;
        }

        PidControllerBlock.applyTuningAction(helper.getLevel(), pidWorldPos, PidControllerMenu.BUTTON_TUNING_NEXT);
        if (helper.getBlockState(pidPos).getValue(PidControllerBlock.TUNING) != 0) {
            helper.fail("PID tuning preset did not wrap within the bounded 0..3 range", pidPos);
            return;
        }

        PidControllerBlock.applyTuningAction(helper.getLevel(), pidWorldPos, PidControllerMenu.BUTTON_TUNING_PREVIOUS);
        if (helper.getBlockState(pidPos).getValue(PidControllerBlock.TUNING) != 3) {
            helper.fail("PID tuning previous action did not wrap within 0..3", pidPos);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void pidTrendTelemetryIsServerOwnedAndBounded(GameTestHelper helper) {
        BlockPos setpointPos = new BlockPos(1, 1, 2);
        BlockPos pidPos = new BlockPos(2, 1, 2);
        BlockPos processPos = new BlockPos(2, 1, 1);
        BlockPos worldPos = helper.absolutePos(pidPos);
        helper.setBlock(setpointPos, RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get()
                .defaultBlockState()
                .setValue(RedstoneReferenceSourceBlock.FACING, Direction.EAST)
                .setValue(RedstoneReferenceSourceBlock.POWER, 0));
        helper.setBlock(processPos, RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get()
                .defaultBlockState()
                .setValue(RedstoneReferenceSourceBlock.FACING, Direction.SOUTH)
                .setValue(RedstoneReferenceSourceBlock.POWER, 0));
        helper.setBlock(pidPos, RedstoneEngineering.PID_CONTROLLER.get()
                .defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));

        helper.runAfterDelay(6, () -> {
            List<Integer> natural = PidTelemetryStore.snapshot(helper.getLevel(), worldPos);
            if (natural.isEmpty()) {
                helper.fail("Scheduled PID control ticks did not emit authoritative trend telemetry", pidPos);
                return;
            }

            for (int i = 0; i < 40; i++) {
                PidTelemetryStore.record(helper.getLevel(), worldPos, i & 15, (i + 1) & 15, (i + 2) & 15);
            }
            List<Integer> bounded = PidTelemetryStore.snapshot(helper.getLevel(), worldPos);
            if (bounded.size() != PidTelemetryStore.MAX_SAMPLES_PER_CONTROLLER) {
                helper.fail("PID trend telemetry did not retain exactly the bounded 32-sample tail", pidPos);
                return;
            }
            int latest = bounded.get(bounded.size() - 1);
            if (PidTelemetryStore.setpoint(latest) != 7
                    || PidTelemetryStore.processValue(latest) != 8
                    || PidTelemetryStore.controlOutput(latest) != 9) {
                helper.fail("PID packed trend sample did not preserve bounded SP/PV/OUT channels", pidPos);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void pidTrendTelemetryClearsWithControllerLifecycle(GameTestHelper helper) {
        BlockPos setpointPos = new BlockPos(1, 1, 2);
        BlockPos pidPos = new BlockPos(2, 1, 2);
        BlockPos processPos = new BlockPos(2, 1, 1);
        BlockPos worldPos = helper.absolutePos(pidPos);
        helper.setBlock(setpointPos, RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get()
                .defaultBlockState()
                .setValue(RedstoneReferenceSourceBlock.FACING, Direction.EAST)
                .setValue(RedstoneReferenceSourceBlock.POWER, 0));
        helper.setBlock(processPos, RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get()
                .defaultBlockState()
                .setValue(RedstoneReferenceSourceBlock.FACING, Direction.SOUTH)
                .setValue(RedstoneReferenceSourceBlock.POWER, 0));
        helper.setBlock(pidPos, RedstoneEngineering.PID_CONTROLLER.get()
                .defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));

        helper.runAfterDelay(6, () -> {
            if (PidTelemetryStore.snapshot(helper.getLevel(), worldPos).isEmpty()) {
                helper.fail("PID trend telemetry was not populated before lifecycle cleanup test", pidPos);
                return;
            }
            helper.setBlock(pidPos, Blocks.AIR);
            if (!PidTelemetryStore.snapshot(helper.getLevel(), worldPos).isEmpty()) {
                helper.fail("Removing PID controller left ghost trend telemetry behind", pidPos);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void oscilloscopeUiActionsModifyCaptureConfigurationOnly(GameTestHelper helper) {
        BlockPos scopePos = new BlockPos(2, 1, 2);
        BlockPos worldPos = helper.absolutePos(scopePos);
        helper.setBlock(scopePos, RedstoneEngineering.OSCILLOSCOPE.get().defaultBlockState());

        helper.runAfterDelay(2, () -> {
            if (!(helper.getLevel().getBlockEntity(worldPos) instanceof OscilloscopeBlockEntity scope)) {
                helper.fail("Oscilloscope block entity was not present for UI action test", scopePos);
                return;
            }
            int modeBefore = scope.triggerMode();
            int levelBefore = scope.triggerLevel();
            int cursorBefore = scope.cursorA();
            int periodBefore = scope.samplePeriodTicks();

            boolean mode = OscilloscopeBlock.applyUiAction(helper.getLevel(), worldPos, OscilloscopeMenu.BUTTON_TRIGGER_MODE);
            boolean level = OscilloscopeBlock.applyUiAction(helper.getLevel(), worldPos, OscilloscopeMenu.BUTTON_TRIGGER_LEVEL);
            boolean cursor = OscilloscopeBlock.applyUiAction(helper.getLevel(), worldPos, OscilloscopeMenu.BUTTON_CURSOR_A);
            boolean timebase = OscilloscopeBlock.applyUiAction(helper.getLevel(), worldPos, OscilloscopeMenu.BUTTON_SAMPLE_PERIOD);

            if (!mode || !level || !cursor || !timebase
                    || scope.triggerMode() == modeBefore
                    || scope.triggerLevel() == levelBefore
                    || scope.cursorA() == cursorBefore
                    || scope.samplePeriodTicks() == periodBefore) {
                helper.fail("Oscilloscope UI actions did not update bounded capture/timebase configuration", scopePos);
                return;
            }
            if (scope.samplePeriodTicks() != 4
                    || scope.sampleRateMilliHz() != 5000
                    || scope.nyquistMilliHz() != 2500) {
                helper.fail("Oscilloscope 4-tick timebase did not expose fs=5Hz / Nyquist=2.5Hz", scopePos);
                return;
            }
            if (scope.sampleCount() != 0) {
                helper.fail("Changing oscilloscope timebase mixed samples from incompatible dt values", scopePos);
                return;
            }
            if (!scope.armed()) {
                helper.fail("Trigger/timebase configuration should re-arm the authoritative capture engine", scopePos);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void oscilloscopeSamplingExperimentFreezesComparableEvidence(GameTestHelper helper) {
        BlockPos scopePos = new BlockPos(2, 1, 2);
        BlockPos worldPos = helper.absolutePos(scopePos);
        helper.setBlock(scopePos, RedstoneEngineering.OSCILLOSCOPE.get().defaultBlockState());

        helper.runAfterDelay(2, () -> {
            if (!(helper.getLevel().getBlockEntity(worldPos) instanceof OscilloscopeBlockEntity scope)) {
                helper.fail("Oscilloscope block entity was not present for experiment test", scopePos);
                return;
            }

            // Baseline: a two-sample observed cycle -> explicit RSE sampling-density FAIL.
            for (int i = 0; i < 14; i++) {
                scope.addSample((i & 1) == 0 ? 0 : 15, -1);
            }
            if (scope.estimatedPeriodSamples(0) != 2 || scope.aliasRiskCode(0) != 1) {
                helper.fail("Synthetic baseline did not produce the expected two-sample cycle", scopePos);
                return;
            }
            if (!OscilloscopeBlock.applyUiAction(
                    helper.getLevel(), worldPos, OscilloscopeMenu.BUTTON_EXPERIMENT_BASELINE)) {
                helper.fail("Oscilloscope rejected baseline experiment capture", scopePos);
                return;
            }

            if (!scope.experimentBaseline().isPresent() || scope.experimentCandidate().isPresent()) {
                helper.fail("Baseline capture did not freeze exactly one server experiment record", scopePos);
                return;
            }
            if (scope.experimentBaseline().get().samplePeriodTicks() != 2
                    || scope.experimentBaseline().get().aliasRiskCode() != 1) {
                helper.fail("Baseline experiment record did not preserve timebase/risk evidence", scopePos);
                return;
            }

            // Change only the scope timebase; this must invalidate the live capture, not the baseline.
            if (!OscilloscopeBlock.applyUiAction(
                    helper.getLevel(), worldPos, OscilloscopeMenu.BUTTON_SAMPLE_PERIOD)) {
                helper.fail("Oscilloscope rejected timebase change during experiment", scopePos);
                return;
            }
            if (scope.samplePeriodTicks() != 4 || scope.sampleCount() != 0
                    || scope.experimentBaseline().isEmpty()) {
                helper.fail("Timebase change did not preserve baseline while invalidating live samples", scopePos);
                return;
            }

            // Candidate: six samples/cycle -> RSE observed-margin PASS.
            for (int i = 0; i < 24; i++) {
                scope.addSample((i % 6) < 3 ? 0 : 15, -1);
            }
            if (scope.estimatedPeriodSamples(0) != 6 || scope.aliasRiskCode(0) != 3) {
                helper.fail("Synthetic candidate did not produce the expected six-sample cycle", scopePos);
                return;
            }
            if (!OscilloscopeBlock.applyUiAction(
                    helper.getLevel(), worldPos, OscilloscopeMenu.BUTTON_EXPERIMENT_CANDIDATE)) {
                helper.fail("Oscilloscope rejected candidate experiment capture", scopePos);
                return;
            }

            if (scope.experimentCandidate().isEmpty()
                    || scope.experimentCandidate().get().samplePeriodTicks() != 4
                    || scope.samplingExperimentStatus() != EngineeringAcceptanceStatus.PASS
                    || scope.samplingExperimentSamplesPerCycleDelta() != 4) {
                helper.fail("Sampling experiment comparison did not preserve candidate/verdict evidence", scopePos);
                return;
            }

            if (!OscilloscopeBlock.applyUiAction(
                    helper.getLevel(), worldPos, OscilloscopeMenu.BUTTON_EXPERIMENT_CLEAR)
                    || scope.experimentBaseline().isPresent()
                    || scope.experimentCandidate().isPresent()) {
                helper.fail("Sampling experiment clear did not remove frozen evidence", scopePos);
                return;
            }

            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void logicAnalyzerUiActionsStayWithinDigitalCaptureBounds(GameTestHelper helper) {
        BlockPos logicPos = new BlockPos(2, 1, 2);
        BlockPos worldPos = helper.absolutePos(logicPos);
        helper.setBlock(logicPos, RedstoneEngineering.LOGIC_ANALYZER.get().defaultBlockState()
                .setValue(LogicAnalyzerBlock.THRESHOLD, 8));

        helper.runAfterDelay(2, () -> {
            if (!(helper.getLevel().getBlockEntity(worldPos) instanceof LogicAnalyzerBlockEntity analyzer)) {
                helper.fail("Logic Analyzer block entity was not present for UI action test", logicPos);
                return;
            }
            if (!LogicAnalyzerBlock.applyUiAction(helper.getLevel(), worldPos, LogicAnalyzerMenu.BUTTON_THRESHOLD_INCREASE)
                    || !LogicAnalyzerBlock.applyUiAction(helper.getLevel(), worldPos, LogicAnalyzerMenu.BUTTON_TRIGGER_CHANNEL)
                    || !LogicAnalyzerBlock.applyUiAction(helper.getLevel(), worldPos, LogicAnalyzerMenu.BUTTON_TRIGGER_EDGE)) {
                helper.fail("Logic Analyzer rejected a valid Engineering UI action", logicPos);
                return;
            }
            BlockState state = helper.getBlockState(logicPos);
            if (state.getValue(LogicAnalyzerBlock.THRESHOLD) != 9
                    || analyzer.triggerChannel() != 1
                    || analyzer.triggerEdge() != 2) {
                helper.fail("Logic Analyzer UI state was not applied to authoritative capture configuration", logicPos);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void signalAnalyzerUiKeepsCalibrationDisplayOnly(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(2, 1, 1);
        BlockPos analyzerPos = new BlockPos(2, 1, 2);
        BlockPos worldPos = helper.absolutePos(analyzerPos);

        helper.setBlock(sourcePos, RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get().defaultBlockState()
                .setValue(RedstoneReferenceSourceBlock.FACING, Direction.SOUTH)
                .setValue(RedstoneReferenceSourceBlock.POWER, 6));
        helper.setBlock(analyzerPos, RedstoneEngineering.SIGNAL_ANALYZER.get().defaultBlockState()
                .setValue(SignalAnalyzerBlock.FACING, Direction.NORTH)
                .setValue(SignalAnalyzerBlock.MODE, SignalAnalyzerBlock.TAP)
                .setValue(SignalAnalyzerBlock.CALIBRATION, 2));

        if (!SignalAnalyzerBlock.applyUiAction(helper.getLevel(), worldPos, SignalAnalyzerMenu.BUTTON_MODE_TOGGLE)
                || !SignalAnalyzerBlock.applyUiAction(helper.getLevel(), worldPos, SignalAnalyzerMenu.BUTTON_CALIBRATION_INCREASE)) {
            helper.fail("Signal Analyzer rejected valid mode/calibration UI actions", analyzerPos);
            return;
        }

        helper.runAfterDelay(5, () -> {
            SignalAnalyzerBlock.UiSnapshot snapshot = SignalAnalyzerBlock.uiSnapshot(helper.getLevel(), worldPos);
            if (snapshot.mode() != SignalAnalyzerBlock.INLINE
                    || snapshot.raw() != 6
                    || snapshot.calibrated() != 7
                    || snapshot.output() != 6) {
                helper.fail("Calibration changed the physical INLINE output instead of display-only readback", analyzerPos);
                return;
            }
            if (!SignalAnalyzerBlock.applyUiAction(helper.getLevel(), worldPos, SignalAnalyzerMenu.BUTTON_RESET_HISTORY)) {
                helper.fail("Signal Analyzer rejected history reset", analyzerPos);
                return;
            }
            if (SignalAnalyzerBlock.uiSnapshot(helper.getLevel(), worldPos).totalSamples() != 0) {
                helper.fail("Signal Analyzer history reset did not clear runtime statistics", analyzerPos);
                return;
            }
            helper.succeed();
        });
    }
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void lapisLowPassHmiActionsChangeOnlyAuthoritativeConfiguration(GameTestHelper helper) {
        BlockPos filterPos = new BlockPos(2, 1, 2);
        BlockPos worldPos = helper.absolutePos(filterPos);
        helper.setBlock(filterPos, RedstoneEngineering.LAPIS_LOW_PASS_FILTER.get()
                .defaultBlockState()
                .setValue(LapisLowPassFilterBlock.ALPHA, EngineeringParameterProfile.LAPIS_FILTER_DEFAULT_INDEX));

        int initial = helper.getBlockState(filterPos).getValue(LapisLowPassFilterBlock.ALPHA);
        if (!LapisLowPassFilterBlock.adjustAlpha(helper.getLevel(), worldPos, 1)) {
            helper.fail("Low-pass HMI alpha action was rejected", filterPos);
            return;
        }
        int afterNext = helper.getBlockState(filterPos).getValue(LapisLowPassFilterBlock.ALPHA);
        if (afterNext != Math.floorMod(initial + 1, EngineeringParameterProfile.LAPIS_FILTER_ALPHA_STEPS)) {
            helper.fail("Low-pass HMI alpha action did not update authoritative blockstate", filterPos);
            return;
        }

        if (!LapisLowPassFilterBlock.resetAlpha(helper.getLevel(), worldPos)) {
            helper.fail("Low-pass HMI default action was rejected after a configuration change", filterPos);
            return;
        }
        if (helper.getBlockState(filterPos).getValue(LapisLowPassFilterBlock.ALPHA)
                != EngineeringParameterProfile.LAPIS_FILTER_DEFAULT_INDEX) {
            helper.fail("Low-pass HMI default action did not restore profile default", filterPos);
            return;
        }

        helper.succeed();
    }


}
