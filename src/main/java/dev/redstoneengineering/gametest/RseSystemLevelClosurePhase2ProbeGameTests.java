package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.PidControllerBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.SignalConditionerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * System-Level Closure Phase 2 minimal scheduled-processor feedback diagnostic.
 *
 * <p>The only loop is PID CONTROL OUT -> four vanilla dust nodes -> identity Conditioner -> PID PV.
 * The conditioner is GAIN x1, so it changes no signal arithmetic. If this stalls while the pure
 * PID dust loop passes, the defect is in closed-loop scheduling between reactive processors rather
 * than in transfer gain, Servo mechanics, sensing, or sampling.</p>
 */
public final class RseSystemLevelClosurePhase2ProbeGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseSystemLevelClosurePhase2ProbeGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void minimalPidIdentityConditionerFeedbackAdvancesTime(GameTestHelper helper) {
        BlockPos setpoint = new BlockPos(2, 1, 4);
        BlockPos pid = new BlockPos(2, 1, 3);
        BlockPos dustA = new BlockPos(2, 1, 2);
        BlockPos dustB = new BlockPos(1, 1, 2);
        BlockPos dustC = new BlockPos(0, 1, 2);
        BlockPos dustD = new BlockPos(0, 1, 3);
        BlockPos conditioner = new BlockPos(1, 1, 3);

        for (BlockPos wire : new BlockPos[] {dustA, dustB, dustC, dustD}) {
            helper.setBlock(wire.below(), Blocks.STONE.defaultBlockState());
            helper.setBlock(wire, Blocks.REDSTONE_WIRE.defaultBlockState());
        }

        helper.setBlock(setpoint, reference(Direction.NORTH, 8));
        helper.setBlock(pid, RedstoneEngineering.PID_CONTROLLER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.NORTH)
                .setValue(PidControllerBlock.TUNING, 0));
        helper.setBlock(conditioner, RedstoneEngineering.SIGNAL_CONDITIONER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(SignalConditionerBlock.MODE, 0)
                .setValue(SignalConditionerBlock.PARAM, 1)); // identity GAIN x1

        helper.runAfterDelay(40, () -> {
            int out = helper.getBlockState(pid).getValue(DirectionalSignalBlock.OUTPUT);
            int conditionerIn = SignalConditionerBlock.inspectInput(
                    helper.getLevel(), helper.absolutePos(conditioner), helper.getBlockState(conditioner));
            int conditionerOut = helper.getBlockState(conditioner).getValue(DirectionalSignalBlock.OUTPUT);
            int dust = helper.getBlockState(dustD).getValue(RedStoneWireBlock.POWER);

            if (out < 0 || out > 15
                    || conditionerIn < 0 || conditionerIn > 15
                    || conditionerOut != conditionerIn
                    || dust != conditionerIn) {
                helper.fail("Minimal PID<->Conditioner loop mismatch"
                        + " | OUT=" + out
                        + " dustIn=" + dust
                        + " condIn=" + conditionerIn
                        + " condOut=" + conditionerOut,
                        conditioner);
                return;
            }
            helper.succeed();
        });
    }

    private static net.minecraft.world.level.block.state.BlockState reference(Direction facing, int power) {
        return RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, facing)
                .setValue(RedstoneReferenceSourceBlock.POWER, power);
    }
}
