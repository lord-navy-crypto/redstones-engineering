package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.LogicAnalyzerBlock;
import dev.redstoneengineering.block.OscilloscopeBlock;
import dev.redstoneengineering.block.PidControllerBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.SignalAnalyzerBlock;
import dev.redstoneengineering.block.SignalConditionerBlock;
import dev.redstoneengineering.blockentity.LogicAnalyzerBlockEntity;
import dev.redstoneengineering.blockentity.OscilloscopeBlockEntity;
import dev.redstoneengineering.ui.menu.LogicAnalyzerMenu;
import dev.redstoneengineering.ui.menu.OscilloscopeMenu;
import dev.redstoneengineering.ui.menu.PidControllerMenu;
import dev.redstoneengineering.ui.menu.SignalAnalyzerMenu;
import dev.redstoneengineering.ui.menu.SignalConditionerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

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

            boolean mode = OscilloscopeBlock.applyUiAction(helper.getLevel(), worldPos, OscilloscopeMenu.BUTTON_TRIGGER_MODE);
            boolean level = OscilloscopeBlock.applyUiAction(helper.getLevel(), worldPos, OscilloscopeMenu.BUTTON_TRIGGER_LEVEL);
            boolean cursor = OscilloscopeBlock.applyUiAction(helper.getLevel(), worldPos, OscilloscopeMenu.BUTTON_CURSOR_A);

            if (!mode || !level || !cursor
                    || scope.triggerMode() == modeBefore
                    || scope.triggerLevel() == levelBefore
                    || scope.cursorA() == cursorBefore) {
                helper.fail("Oscilloscope UI actions did not update bounded capture configuration", scopePos);
                return;
            }
            if (!scope.armed()) {
                helper.fail("Trigger configuration should re-arm the authoritative capture engine", scopePos);
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
}
