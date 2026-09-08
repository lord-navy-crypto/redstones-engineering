package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.PidControllerBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * System-Level Closure Phase 2 minimal feedback diagnostic.
 *
 * <p>This removes Servo, Sensor, Conditioner, and Sample & Hold. The only cycle is:
 * PID CONTROL OUT -> three vanilla dust nodes -> PID PROCESS VALUE IN.
 * If this stalls the GameTest server while both open-loop half-chains pass, the defect is
 * localized to PID scheduling/update behavior under a changing feedback return.</p>
 */
public final class RseSystemLevelClosurePhase2ProbeGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseSystemLevelClosurePhase2ProbeGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void minimalPidRedstoneFeedbackLoopAdvancesTime(GameTestHelper helper) {
        BlockPos setpoint = new BlockPos(1, 1, 2);
        BlockPos pid = new BlockPos(2, 1, 2);
        BlockPos dustA = new BlockPos(3, 1, 2);
        BlockPos dustB = new BlockPos(3, 1, 1);
        BlockPos dustC = new BlockPos(2, 1, 1);

        for (BlockPos wire : new BlockPos[] {dustA, dustB, dustC}) {
            helper.setBlock(wire.below(), Blocks.STONE.defaultBlockState());
            helper.setBlock(wire, Blocks.REDSTONE_WIRE.defaultBlockState());
        }

        helper.setBlock(setpoint, reference(Direction.EAST, 8));
        helper.setBlock(pid, RedstoneEngineering.PID_CONTROLLER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(PidControllerBlock.TUNING, 0));

        helper.runAfterDelay(40, () -> {
            int out = helper.getBlockState(pid).getValue(DirectionalSignalBlock.OUTPUT);
            int a = helper.getBlockState(dustA).getValue(RedStoneWireBlock.POWER);
            int b = helper.getBlockState(dustB).getValue(RedStoneWireBlock.POWER);
            int c = helper.getBlockState(dustC).getValue(RedStoneWireBlock.POWER);

            if (out < 0 || out > 15 || a < 0 || a > 15 || b < 0 || b > 15 || c < 0 || c > 15) {
                helper.fail("Minimal PID feedback escaped redstone bounds"
                        + " | OUT=" + out + " dust=" + a + "->" + b + "->" + c,
                        pid);
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
