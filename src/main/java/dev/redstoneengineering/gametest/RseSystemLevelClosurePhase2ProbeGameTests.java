package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.SampleHoldBlock;
import dev.redstoneengineering.block.ServoActuatorBlock;
import dev.redstoneengineering.block.ServoPositionSensorBlock;
import dev.redstoneengineering.block.SignalConditionerBlock;
import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * System-Level Closure Phase 2 diagnostic bisection.
 *
 * <p>This probe deliberately removes PID and the feedback return. It tests only the acquisition half:
 * fixed command -> Servo -> Position Sensor -> lossy transport -> Conditioner -> Sample & Hold.
 * If this remains bounded, the Phase 2 hang is isolated to PID/feedback closure rather than the
 * plant, sensor, transport, conditioning, or sampled-data boundary.</p>
 */
public final class RseSystemLevelClosurePhase2ProbeGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseSystemLevelClosurePhase2ProbeGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void feedbackAcquisitionHalfChainIsBounded(GameTestHelper helper) {
        BlockPos source = new BlockPos(0, 1, 2);
        BlockPos servo = new BlockPos(1, 1, 2);
        BlockPos sensor = new BlockPos(2, 1, 2);
        BlockPos dustA = new BlockPos(3, 1, 2);
        BlockPos dustB = new BlockPos(3, 1, 3);
        BlockPos conditioner = new BlockPos(2, 1, 3);
        BlockPos sampler = new BlockPos(1, 1, 3);
        BlockPos trigger = new BlockPos(1, 1, 4);

        helper.setBlock(dustA.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(dustB.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(dustA, Blocks.REDSTONE_WIRE.defaultBlockState());
        helper.setBlock(dustB, Blocks.REDSTONE_WIRE.defaultBlockState());

        helper.setBlock(source, reference(Direction.EAST, 8));
        helper.setBlock(servo, RedstoneEngineering.SERVO_ACTUATOR.get().defaultBlockState()
                .setValue(ServoActuatorBlock.FACING, Direction.EAST)
                .setValue(ServoActuatorBlock.SLEW, 1));
        helper.setBlock(sensor, RedstoneEngineering.SERVO_POSITION_SENSOR.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));
        helper.setBlock(conditioner, RedstoneEngineering.SIGNAL_CONDITIONER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.WEST)
                .setValue(SignalConditionerBlock.MODE, 1)
                .setValue(SignalConditionerBlock.PARAM, 6)); // OFFSET +1 compensates one dust step.
        helper.setBlock(sampler, RedstoneEngineering.SAMPLE_HOLD.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.WEST)
                .setValue(SampleHoldBlock.TRIGGER_MODE, 0));
        helper.setBlock(trigger, Blocks.AIR.defaultBlockState());

        pulseSample(helper, trigger, 28);
        pulseSample(helper, trigger, 44);
        pulseSample(helper, trigger, 60);

        helper.runAfterDelay(72, () -> {
            int position = ServoActuatorBlock.position(helper.getLevel(), helper.absolutePos(servo));
            int sensorOutput = helper.getBlockState(sensor).getValue(DirectionalSignalBlock.OUTPUT);
            int dust1 = helper.getBlockState(dustA).getValue(RedStoneWireBlock.POWER);
            int dust2 = helper.getBlockState(dustB).getValue(RedStoneWireBlock.POWER);
            int conditionerInput = SignalConditionerBlock.inspectInput(
                    helper.getLevel(), helper.absolutePos(conditioner), helper.getBlockState(conditioner));
            int conditionerOutput = helper.getBlockState(conditioner).getValue(DirectionalSignalBlock.OUTPUT);
            int held = helper.getBlockState(sampler).getValue(DirectionalSignalBlock.OUTPUT);
            int captures = SampleHoldBlock.captureCount(helper.getLevel(), helper.absolutePos(sampler));
            PortQuality mechanical = ServoPositionSensorBlock.sourceQuality(
                    helper.getLevel(), helper.absolutePos(sensor), helper.getBlockState(sensor));

            if (position != 8
                    || mechanical != PortQuality.VALID
                    || Math.abs(sensorOutput - position) > 1
                    || dust1 != sensorOutput
                    || dust2 != Math.max(0, dust1 - 1)
                    || conditionerInput != dust2
                    || conditionerOutput != Math.min(15, conditionerInput + 1)
                    || held != conditionerOutput
                    || captures < 2) {
                helper.fail("Feedback acquisition half-chain mismatch"
                        + " | servo=" + position
                        + " sensor=" + sensorOutput + "/" + mechanical
                        + " dust=" + dust1 + "->" + dust2
                        + " condIn=" + conditionerInput
                        + " condOut=" + conditionerOutput
                        + " held=" + held
                        + " captures=" + captures,
                        conditioner);
                return;
            }
            helper.succeed();
        });
    }

    private static void pulseSample(GameTestHelper helper, BlockPos trigger, int delay) {
        helper.runAfterDelay(delay, () -> {
            helper.setBlock(trigger, Blocks.REDSTONE_BLOCK.defaultBlockState());
            helper.runAfterDelay(2, () -> helper.setBlock(trigger, Blocks.AIR.defaultBlockState()));
        });
    }

    private static net.minecraft.world.level.block.state.BlockState reference(Direction facing, int power) {
        return RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, facing)
                .setValue(RedstoneReferenceSourceBlock.POWER, power);
    }
}
