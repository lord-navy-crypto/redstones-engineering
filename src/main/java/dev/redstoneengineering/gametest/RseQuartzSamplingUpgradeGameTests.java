package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.QuartzLabOscillatorBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.SampleHoldBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Focused runtime witnesses; CI compilation is not a replacement for running GameTests. */
public final class RseQuartzSamplingUpgradeGameTests {
    private static final String TEMPLATE = "empty5x4x5";
    private RseQuartzSamplingUpgradeGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void quartzLabPortReportsPhaseNotNominalPeriod(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        BlockState low = RedstoneEngineering.QUARTZ_LAB_OSCILLATOR.get().defaultBlockState()
                .setValue(QuartzLabOscillatorBlock.FACING, Direction.EAST)
                .setValue(QuartzLabOscillatorBlock.ACTIVE, false)
                .setValue(QuartzLabOscillatorBlock.PERIOD_INDEX, 4);
        helper.setBlock(pos, low);
        BlockPos world = helper.absolutePos(pos);
        var device = RedstoneEngineering.QUARTZ_LAB_OSCILLATOR.get();
        var lowPort = device.engineeringSnapshot(helper.getLevel(), world,
                helper.getBlockState(pos), Direction.EAST).orElseThrow();
        helper.setBlock(pos, low.setValue(QuartzLabOscillatorBlock.ACTIVE, true));
        var highPort = device.engineeringSnapshot(helper.getLevel(), world,
                helper.getBlockState(pos), Direction.EAST).orElseThrow();
        if (lowPort.value() != 0.0 || highPort.value() != 1.0
                || lowPort.maximum() != 1.0 || highPort.maximum() != 1.0
                || lowPort.quality() != PortQuality.VALID || highPort.quality() != PortQuality.VALID) {
            helper.fail("Lab Quartz port must publish phase 0/1, never nominal period", pos);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void sampleHoldSeparatesUnconnectedInputFromDrivenZeroWithoutRuntimeMutation(GameTestHelper helper) {
        BlockPos inputPos = new BlockPos(1, 1, 2);
        BlockPos holdPos = new BlockPos(2, 1, 2);
        BlockState hold = RedstoneEngineering.SAMPLE_HOLD.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(DirectionalSignalBlock.INPUT_FACING, Direction.WEST);
        helper.setBlock(holdPos, hold);
        var block = RedstoneEngineering.SAMPLE_HOLD.get();
        var world = helper.absolutePos(holdPos);
        int entriesBefore = RuntimeIntStore.entryCount(helper.getLevel());
        var missing = block.engineeringSnapshot(helper.getLevel(), world,
                helper.getBlockState(holdPos), Direction.WEST).orElseThrow();
        if (missing.value() != 0.0 || missing.quality() != PortQuality.NO_SIGNAL
                || RuntimeIntStore.entryCount(helper.getLevel()) != entriesBefore) {
            helper.fail("Sample & Hold fabricated a valid input or created runtime during readout", holdPos);
            return;
        }
        helper.setBlock(inputPos, RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.EAST)
                .setValue(RedstoneReferenceSourceBlock.POWER, 0));
        int entriesBeforeConnectedRead = RuntimeIntStore.entryCount(helper.getLevel());
        var drivenZero = block.engineeringSnapshot(helper.getLevel(), world,
                helper.getBlockState(holdPos), Direction.WEST).orElseThrow();
        if (drivenZero.value() != 0.0 || drivenZero.quality() != PortQuality.VALID
                || RuntimeIntStore.entryCount(helper.getLevel()) != entriesBeforeConnectedRead) {
            helper.fail("Configured driven zero must be VALID and read-only", holdPos);
            return;
        }
        helper.succeed();
    }
}
