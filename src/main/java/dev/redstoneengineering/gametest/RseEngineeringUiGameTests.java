package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.PidControllerBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.SignalConditionerBlock;
import dev.redstoneengineering.ui.menu.PidControllerMenu;
import dev.redstoneengineering.ui.menu.SignalConditionerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Runtime guards for the server-authoritative actions exposed through the Engineering UI.
 * Client rendering is intentionally not part of GameTest; these tests prove the actions behind
 * the buttons preserve the same physical block semantics used outside the screen.
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

        PidControllerBlock.applyTuningAction(
                helper.getLevel(), pidWorldPos, PidControllerMenu.BUTTON_TUNING_NEXT);
        if (helper.getBlockState(pidPos).getValue(PidControllerBlock.TUNING) != 0) {
            helper.fail("PID tuning preset did not wrap within the bounded 0..3 range", pidPos);
            return;
        }

        PidControllerBlock.applyTuningAction(
                helper.getLevel(), pidWorldPos, PidControllerMenu.BUTTON_TUNING_PREVIOUS);
        if (helper.getBlockState(pidPos).getValue(PidControllerBlock.TUNING) != 3) {
            helper.fail("PID tuning previous action did not wrap within 0..3", pidPos);
            return;
        }

        helper.succeed();
    }
}
