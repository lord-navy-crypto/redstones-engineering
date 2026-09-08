package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.PidControllerBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.SampleHoldBlock;
import dev.redstoneengineering.block.ServoActuatorBlock;
import dev.redstoneengineering.block.ServoPositionSensorBlock;
import dev.redstoneengineering.block.SignalConditionerBlock;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

/**
 * System-Level Closure Phase 2 probe.
 *
 * <p>This is intentionally a single diagnostic closed loop before the full Phase 2
 * acceptance matrix is committed. The first continuous-feedback fixture exposed a
 * system-level non-terminating GameTest/update interaction. This probe now follows the
 * intended RSE engineering hierarchy and inserts an explicit sampled-data boundary:
 * measurement -> transport -> conditioning -> sample/hold -> control -> actuation.</p>
 *
 * <p>Production behavior is not changed to make this probe pass.</p>
 */
public final class RseSystemLevelClosurePhase2ProbeGameTests {
    private static final String TEMPLATE = "empty5x4x5";
    private static final String BATCH = "systemClosurePhase2";

    private RseSystemLevelClosurePhase2ProbeGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(batch = BATCH, templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 180)
    public static void pidServoSensorSampledConditionedFeedbackLoopConverges(GameTestHelper helper) {
        // Plant line, south -> north: setpoint -> PID -> servo -> position sensor.
        BlockPos setpoint = new BlockPos(3, 1, 4);
        BlockPos pid = new BlockPos(3, 1, 3);
        BlockPos servo = new BlockPos(3, 1, 2);
        BlockPos sensor = new BlockPos(3, 1, 1);

        // Feedback returns around the west edge. Seven dust nodes attenuate by six levels.
        // OFFSET +5 intentionally leaves at most a one-level residual near SP=8, which is
        // inside the PID deadband but remains visible as real transport/calibration error.
        BlockPos[] feedback = {
                new BlockPos(3, 1, 0),
                new BlockPos(2, 1, 0),
                new BlockPos(1, 1, 0),
                new BlockPos(0, 1, 0),
                new BlockPos(0, 1, 1),
                new BlockPos(0, 1, 2),
                new BlockPos(0, 1, 3)
        };
        BlockPos conditioner = new BlockPos(1, 1, 3);
        BlockPos sampler = new BlockPos(2, 1, 3);
        BlockPos trigger = new BlockPos(2, 1, 2);

        for (BlockPos wire : feedback) {
            helper.setBlock(wire.below(), Blocks.STONE.defaultBlockState());
            helper.setBlock(wire, Blocks.REDSTONE_WIRE.defaultBlockState());
        }

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
                .setValue(SignalConditionerBlock.MODE, 1)
                .setValue(SignalConditionerBlock.PARAM, 10)); // OFFSET +5
        helper.setBlock(sampler, RedstoneEngineering.SAMPLE_HOLD.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(SampleHoldBlock.TRIGGER_MODE, 0)); // rising-edge capture
        helper.setBlock(setpoint, reference(Direction.NORTH, 8));
        helper.setBlock(trigger, Blocks.AIR.defaultBlockState());

        // Explicit external sample clock for this probe. Each pulse gives the plant time to
        // respond before the next PV capture, preventing a combinational feedback path.
        for (int tick : new int[] {8, 20, 32, 44, 56, 68, 80, 92, 104, 116, 128}) {
            pulseSample(helper, trigger, tick);
        }

        helper.runAfterDelay(120, () -> {
            if (!closedLoopNearTarget(helper, pid, servo, sensor, conditioner, sampler, feedback, 8, 1)) return;
            if (SampleHoldBlock.captureCount(helper.getLevel(), helper.absolutePos(sampler)) < 8) {
                helper.fail("Sampled loop did not accumulate expected real capture evidence", sampler);
                return;
            }

            // Require the loop to remain bounded across another sampled controller/plant window,
            // not merely cross the target for one lucky tick.
            helper.runAfterDelay(24, () -> {
                if (!closedLoopNearTarget(helper, pid, servo, sensor, conditioner, sampler, feedback, 8, 1)) return;
                helper.succeed();
            });
        });
    }

    private static void pulseSample(GameTestHelper helper, BlockPos trigger, int delay) {
        helper.runAfterDelay(delay, () -> {
            helper.setBlock(trigger, Blocks.REDSTONE_BLOCK.defaultBlockState());
            helper.runAfterDelay(2, () -> helper.setBlock(trigger, Blocks.AIR.defaultBlockState()));
        });
    }

    private static boolean closedLoopNearTarget(
            GameTestHelper helper,
            BlockPos pid,
            BlockPos servo,
            BlockPos sensor,
            BlockPos conditioner,
            BlockPos sampler,
            BlockPos[] feedback,
            int target,
            int tolerance
    ) {
        BlockPos pidWorld = helper.absolutePos(pid);
        BlockPos servoWorld = helper.absolutePos(servo);
        int position = ServoActuatorBlock.position(helper.getLevel(), servoWorld);
        int sensorOutput = helper.getBlockState(sensor).getValue(DirectionalSignalBlock.OUTPUT);
        int conditionerInput = SignalConditionerBlock.inspectInput(
                helper.getLevel(), helper.absolutePos(conditioner), helper.getBlockState(conditioner));
        int conditionerOutput = helper.getBlockState(conditioner).getValue(DirectionalSignalBlock.OUTPUT);
        int heldPv = helper.getBlockState(sampler).getValue(DirectionalSignalBlock.OUTPUT);
        int finalDust = helper.getBlockState(feedback[feedback.length - 1]).getValue(RedStoneWireBlock.POWER);
        int pidOutput = helper.getBlockState(pid).getValue(DirectionalSignalBlock.OUTPUT);

        PidControllerBlock controller = RedstoneEngineering.PID_CONTROLLER.get();
        BlockState pidState = helper.getBlockState(pid);
        EngineeringPortSnapshot sp = snapshot(controller, helper, pidWorld, pidState, Direction.SOUTH);
        EngineeringPortSnapshot pv = snapshot(controller, helper, pidWorld, pidState, Direction.WEST);
        EngineeringPortSnapshot out = snapshot(controller, helper, pidWorld, pidState, Direction.NORTH);
        PortQuality mechanical = ServoPositionSensorBlock.sourceQuality(
                helper.getLevel(), helper.absolutePos(sensor), helper.getBlockState(sensor));

        boolean converged = Math.abs(position - target) <= tolerance
                && Math.abs(sensorOutput - position) <= 1
                && Math.abs(heldPv - target) <= tolerance
                && Math.abs(pv.value() - target) <= tolerance
                && Math.abs(pidOutput - target) <= tolerance
                && sp.quality() == PortQuality.VALID
                && pv.quality() == PortQuality.VALID
                && out.quality() == PortQuality.VALID
                && mechanical == PortQuality.VALID;

        if (!converged) {
            helper.fail("Sampled closed-loop probe did not converge/bound at SP=" + target
                    + " | SP=" + sp.value() + "/" + sp.quality()
                    + " PV=" + pv.value() + "/" + pv.quality()
                    + " OUT=" + pidOutput + "/" + out.quality()
                    + " servo=" + position
                    + " sensor=" + sensorOutput + "/" + mechanical
                    + " dustFinal=" + finalDust
                    + " condIn=" + conditionerInput
                    + " condOut=" + conditionerOutput
                    + " held=" + heldPv
                    + " captures=" + SampleHoldBlock.captureCount(helper.getLevel(), helper.absolutePos(sampler)),
                    pid);
            return false;
        }
        return true;
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
