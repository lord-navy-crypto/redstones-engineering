package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.PidControllerBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.ServoActuatorBlock;
import dev.redstoneengineering.block.ServoPositionSensorBlock;
import dev.redstoneengineering.block.SignalConditionerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * System-Level Closure Phase 2 differential probe.
 *
 * <p>This restores the dynamic physical loop while keeping the conditioner mathematically identity:
 * SP -> PID -> Servo -> Position Sensor -> lossy vanilla dust -> GAIN x1 Conditioner -> PID PV.
 * Sample & Hold and offset compensation are absent. This isolates whether merely inserting the
 * reactive conditioner into an otherwise healthy physical feedback return changes scheduling.</p>
 */
public final class RseSystemLevelClosurePhase2ProbeGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseSystemLevelClosurePhase2ProbeGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void physicalLoopWithIdentityConditionerAdvancesTime(GameTestHelper helper) {
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
                new BlockPos(0, 1, 3)
        };
        BlockPos conditioner = new BlockPos(1, 1, 3);

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
        helper.setBlock(conditioner, RedstoneEngineering.SIGNAL_CONDITIONER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(SignalConditionerBlock.MODE, 0)
                .setValue(SignalConditionerBlock.PARAM, 1)); // identity GAIN x1

        helper.runAfterDelay(50, () -> {
            int out = helper.getBlockState(pid).getValue(DirectionalSignalBlock.OUTPUT);
            int position = ServoActuatorBlock.position(helper.getLevel(), helper.absolutePos(servo));
            int sensorOut = helper.getBlockState(sensor).getValue(DirectionalSignalBlock.OUTPUT);
            int finalDust = helper.getBlockState(feedback[feedback.length - 1]).getValue(RedStoneWireBlock.POWER);
            int conditionerIn = SignalConditionerBlock.inspectInput(
                    helper.getLevel(), helper.absolutePos(conditioner), helper.getBlockState(conditioner));
            int conditionerOut = helper.getBlockState(conditioner).getValue(DirectionalSignalBlock.OUTPUT);

            if (out < 0 || out > 15
                    || position < 0 || position > 15
                    || sensorOut < 0 || sensorOut > 15
                    || finalDust < 0 || finalDust > 15
                    || conditionerIn != finalDust
                    || conditionerOut != conditionerIn) {
                helper.fail("Physical PID loop with identity conditioner escaped expected bounds"
                        + " | OUT=" + out
                        + " servo=" + position
                        + " sensor=" + sensorOut
                        + " dust=" + finalDust
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
