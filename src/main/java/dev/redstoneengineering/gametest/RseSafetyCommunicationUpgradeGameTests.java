package dev.redstoneengineering.gametest;

import dev.redstoneengineering.EngineeringSystemsModule;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.SafetyInterlockBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Regression witnesses; registered here, but runtime GameTest acceptance requires actual execution. */
public final class RseSafetyCommunicationUpgradeGameTests {
    private static final String TEMPLATE = "empty5x4x5";
    private RseSafetyCommunicationUpgradeGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void interlockRequiresThreeValidAssertedRedstoneSources(GameTestHelper helper) {
        BlockPos interlock = new BlockPos(2, 1, 2);
        BlockPos west = new BlockPos(1, 1, 2);
        BlockPos north = new BlockPos(2, 1, 1);
        BlockPos south = new BlockPos(2, 1, 3);
        helper.setBlock(interlock, EngineeringSystemsModule.SAFETY_INTERLOCK.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(DirectionalSignalBlock.INPUT_FACING, Direction.WEST));
        helper.setBlock(west, RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.EAST)
                .setValue(RedstoneReferenceSourceBlock.POWER, 15));
        helper.setBlock(north, RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.SOUTH)
                .setValue(RedstoneReferenceSourceBlock.POWER, 15));
        helper.setBlock(south, RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.NORTH)
                .setValue(RedstoneReferenceSourceBlock.POWER, 15));
        BlockPos world = helper.absolutePos(interlock);
        helper.runAfterDelay(5, () -> {
            if (SafetyInterlockBlock.failedMask(helper.getLevel(), world) != 0 ||
                    helper.getBlockState(interlock).getValue(DirectionalSignalBlock.OUTPUT) != 15) {
                helper.fail("All three real asserted permissives must produce the live permit", interlock);
                return;
            }
            helper.setBlock(north, Blocks.AIR);
            helper.runAfterDelay(5, () -> {
                if ((SafetyInterlockBlock.failedMask(helper.getLevel(), world) & 2) == 0
                        || helper.getBlockState(interlock).getValue(DirectionalSignalBlock.OUTPUT) != 0) {
                    helper.fail("Removing permissive B must fail closed; absence is not a valid zero", interlock);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void communicationCableInspectionIsObserverNeutralBeforeNetworkSolve(GameTestHelper helper) {
        BlockPos serial = new BlockPos(1, 1, 2);
        BlockPos differential = new BlockPos(3, 1, 2);
        helper.setBlock(serial, RedstoneEngineering.SERIAL_DATA_LINE.get().defaultBlockState());
        helper.setBlock(differential, RedstoneEngineering.DIFFERENTIAL_DATA_PAIR.get().defaultBlockState());
        int before = RuntimeIntStore.entryCount(helper.getLevel());
        var serialOutput = RedstoneEngineering.SERIAL_DATA_LINE.get().engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(serial),
                helper.getBlockState(serial), Direction.NORTH).orElseThrow();
        var bitOutput = RedstoneEngineering.DIFFERENTIAL_DATA_PAIR.get().engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(differential),
                helper.getBlockState(differential), Direction.NORTH).orElseThrow();
        if (serialOutput.quality() != PortQuality.STALE
                || bitOutput.quality() != PortQuality.STALE
                || serialOutput.maximum() != 255.0 || bitOutput.maximum() != 1.0
                || RuntimeIntStore.entryCount(helper.getLevel()) != before) {
            helper.fail("Read-only serial/differential inspection mutated runtime or lost distinct scales", serial);
            return;
        }
        helper.succeed();
    }
}
