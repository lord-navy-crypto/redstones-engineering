package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.PidControllerBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.ServoActuatorBlock;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

/**
 * System-Level Closure Phase 2 diagnostic bisection.
 *
 * <p>This control probe is intentionally identical to the previously passing open-loop half-chain
 * except for a 180-tick timeout. It exists only to rule in/out GameTest timeout/batch grouping as
 * the cause of the earlier closed-loop hangs before production behavior is touched.</p>
 */
public final class RseSystemLevelClosurePhase2ProbeGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseSystemLevelClosurePhase2ProbeGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 180)
    public static void pidToServoOpenLoopLongTimeoutControlIsBounded(GameTestHelper helper) {
        BlockPos setpoint = new BlockPos(0, 1, 2);
        BlockPos pid = new BlockPos(1, 1, 2);
        BlockPos servo = new BlockPos(2, 1, 2);
        BlockPos processValue = new BlockPos(1, 1, 1);

        helper.setBlock(setpoint, reference(Direction.EAST, 4));
        helper.setBlock(processValue, reference(Direction.SOUTH, 4));
        helper.setBlock(pid, RedstoneEngineering.PID_CONTROLLER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(PidControllerBlock.TUNING, 0));
        helper.setBlock(servo, RedstoneEngineering.SERVO_ACTUATOR.get().defaultBlockState()
                .setValue(ServoActuatorBlock.FACING, Direction.EAST)
                .setValue(ServoActuatorBlock.SLEW, 1));

        helper.runAfterDelay(36, () -> {
            BlockPos pidWorld = helper.absolutePos(pid);
            BlockPos servoWorld = helper.absolutePos(servo);
            BlockState pidState = helper.getBlockState(pid);
            PidControllerBlock controller = RedstoneEngineering.PID_CONTROLLER.get();

            EngineeringPortSnapshot sp = snapshot(controller, helper, pidWorld, pidState, Direction.WEST);
            EngineeringPortSnapshot pv = snapshot(controller, helper, pidWorld, pidState, Direction.NORTH);
            EngineeringPortSnapshot out = snapshot(controller, helper, pidWorld, pidState, Direction.EAST);
            int pidOutput = pidState.getValue(DirectionalSignalBlock.OUTPUT);
            int servoCommand = ServoActuatorBlock.command(helper.getLevel(), servoWorld);
            int servoPosition = ServoActuatorBlock.position(helper.getLevel(), servoWorld);

            if (sp.quality() != PortQuality.VALID
                    || pv.quality() != PortQuality.VALID
                    || out.quality() != PortQuality.VALID
                    || sp.value() != 4
                    || pv.value() != 4
                    || pidOutput != 8
                    || out.value() != 8
                    || servoCommand != 8
                    || servoPosition != 8
                    || ServoActuatorBlock.braking(helper.getLevel(), servoWorld)) {
                helper.fail("PID->Servo long-timeout control mismatch"
                        + " | SP=" + sp.value() + "/" + sp.quality()
                        + " PV=" + pv.value() + "/" + pv.quality()
                        + " OUT=" + pidOutput + "/" + out.quality()
                        + " servoCommand=" + servoCommand
                        + " servoPosition=" + servoPosition
                        + " braking=" + ServoActuatorBlock.braking(helper.getLevel(), servoWorld),
                        pid);
                return;
            }
            helper.succeed();
        });
    }

    private static EngineeringPortSnapshot snapshot(
            PidControllerBlock controller,
            GameTestHelper helper,
            BlockPos pidWorld,
            BlockState pidState,
            Direction side
    ) {
        Optional<EngineeringPortSnapshot> snapshot = controller.engineeringSnapshot(
                helper.getLevel(), pidWorld, pidState, side);
        if (snapshot.isEmpty()) {
            throw new IllegalStateException("PID engineering snapshot missing on " + side);
        }
        return snapshot.get();
    }

    private static BlockState reference(Direction facing, int power) {
        return RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, facing)
                .setValue(RedstoneReferenceSourceBlock.POWER, power);
    }
}
