package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.PidControllerBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.ServoActuatorBlock;
import dev.redstoneengineering.block.ServoPositionSensorBlock;
import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * System-Level Closure Phase 2 diagnostic bisection.
 *
 * <p>This contains PID, Servo, and Position Sensor simultaneously, but keeps PID PV on an independent
 * fixed reference. If this remains bounded, the three-device adjacency is healthy and the defect
 * requires the sensor feedback to be physically returned to PID.</p>
 */
public final class RseSystemLevelClosurePhase2ProbeGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseSystemLevelClosurePhase2ProbeGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void pidServoSensorTriadWithoutReturnIsBounded(GameTestHelper helper) {
        BlockPos setpoint = new BlockPos(0, 1, 2);
        BlockPos pid = new BlockPos(1, 1, 2);
        BlockPos servo = new BlockPos(2, 1, 2);
        BlockPos sensor = new BlockPos(3, 1, 2);
        BlockPos processValue = new BlockPos(1, 1, 1);

        helper.setBlock(setpoint, reference(Direction.EAST, 4));
        helper.setBlock(processValue, reference(Direction.SOUTH, 4));
        helper.setBlock(pid, RedstoneEngineering.PID_CONTROLLER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(PidControllerBlock.TUNING, 0));
        helper.setBlock(servo, RedstoneEngineering.SERVO_ACTUATOR.get().defaultBlockState()
                .setValue(ServoActuatorBlock.FACING, Direction.EAST)
                .setValue(ServoActuatorBlock.SLEW, 1));
        helper.setBlock(sensor, RedstoneEngineering.SERVO_POSITION_SENSOR.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));

        helper.runAfterDelay(40, () -> {
            int pidOutput = helper.getBlockState(pid).getValue(DirectionalSignalBlock.OUTPUT);
            int servoPosition = ServoActuatorBlock.position(helper.getLevel(), helper.absolutePos(servo));
            int sensorOutput = helper.getBlockState(sensor).getValue(DirectionalSignalBlock.OUTPUT);
            PortQuality quality = ServoPositionSensorBlock.sourceQuality(
                    helper.getLevel(), helper.absolutePos(sensor), helper.getBlockState(sensor));

            if (pidOutput != 8
                    || servoPosition != 8
                    || quality != PortQuality.VALID
                    || Math.abs(sensorOutput - servoPosition) > 1) {
                helper.fail("PID->Servo->Sensor triad without return mismatch"
                        + " | OUT=" + pidOutput
                        + " servo=" + servoPosition
                        + " sensor=" + sensorOutput + "/" + quality,
                        servo);
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
