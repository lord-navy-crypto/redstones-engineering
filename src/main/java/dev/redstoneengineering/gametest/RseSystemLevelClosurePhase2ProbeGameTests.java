package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.PidControllerBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.ServoActuatorBlock;
import dev.redstoneengineering.block.ServoPositionSensorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * System-Level Closure Phase 2 geometry control.
 *
 * <p>This is byte-for-byte the same physical layout direction as the hanging conditioner fixture,
 * except the conditioner position is replaced by one more vanilla dust node. If this remains
 * bounded, geometry and plant orientation are exonerated and the conditioner insertion is the
 * material difference.</p>
 */
public final class RseSystemLevelClosurePhase2ProbeGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseSystemLevelClosurePhase2ProbeGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void identicalGeometryWithoutConditionerAdvancesTime(GameTestHelper helper) {
        BlockPos setpoint = new BlockPos(2, 1, 4);
        BlockPos pid = new BlockPos(2, 1, 3);
        BlockPos servo = new BlockPos(2, 1, 2);
        BlockPos sensor = new BlockPos(2, 1, 1);
        BlockPos[] feedback = {
                new BlockPos(2, 1, 0),
                new BlockPos(1, 1, 0),
                new BlockPos(0, 1, 0),
                new BlockPos(0, 1, 1),
                new BlockPos(0, 1, 2),
                new BlockPos(0, 1, 3),
                new BlockPos(1, 1, 3)
        };

        for (BlockPos wire : feedback) {
            helper.setBlock(wire.below(), Blocks.STONE.defaultBlockState());
            helper.setBlock(wire, Blocks.REDSTONE_WIRE.defaultBlockState());
        }

        helper.setBlock(setpoint, reference(Direction.NORTH, 8));
        helper.setBlock(pid, RedstoneEngineering.PID_CONTROLLER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.NORTH)
                .setValue(PidControllerBlock.TUNING, 0));
        helper.setBlock(servo, RedstoneEngineering.SERVO_ACTUATOR.get().defaultBlockState()
                .setValue(ServoActuatorBlock.FACING, Direction.NORTH)
                .setValue(ServoActuatorBlock.SLEW, 1));
        helper.setBlock(sensor, RedstoneEngineering.SERVO_POSITION_SENSOR.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.NORTH));

        helper.runAfterDelay(50, () -> {
            int out = helper.getBlockState(pid).getValue(DirectionalSignalBlock.OUTPUT);
            int position = ServoActuatorBlock.position(helper.getLevel(), helper.absolutePos(servo));
            int sensorOut = helper.getBlockState(sensor).getValue(DirectionalSignalBlock.OUTPUT);
            int finalPv = helper.getBlockState(feedback[feedback.length - 1]).getValue(RedStoneWireBlock.POWER);

            if (out < 0 || out > 15
                    || position < 0 || position > 15
                    || sensorOut < 0 || sensorOut > 15
                    || finalPv < 0 || finalPv > 15) {
                helper.fail("Identical-geometry control escaped engineering bounds"
                        + " | OUT=" + out
                        + " servo=" + position
                        + " sensor=" + sensorOut
                        + " pvDust=" + finalPv,
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
