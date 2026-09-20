package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.AnalogComparatorBlock;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Evidence-hold acceptance tests for the live-reference analog comparator. */
public final class RseAnalogComparatorEvidenceGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseAnalogComparatorEvidenceGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void comparatorHoldsDecisionAcrossMissingProcessEvidence(GameTestHelper helper) {
        BlockPos process = new BlockPos(1, 1, 2);
        BlockPos comparator = new BlockPos(2, 1, 2);
        BlockPos reference = new BlockPos(2, 1, 1);

        helper.setBlock(process, referenceSource(Direction.EAST, 12));
        helper.setBlock(reference, referenceSource(Direction.SOUTH, 8));
        helper.setBlock(comparator, RedstoneEngineering.ANALOG_COMPARATOR.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(DirectionalSignalBlock.INPUT_FACING, Direction.WEST)
                .setValue(AnalogComparatorBlock.MODE, AnalogComparatorBlock.ABOVE)
                .setValue(AnalogComparatorBlock.HYSTERESIS, 1));

        helper.runAfterDelay(5, () -> {
            BlockPos world = helper.absolutePos(comparator);
            BlockState baseline = helper.getBlockState(comparator);
            var baselineOut = RedstoneEngineering.ANALOG_COMPARATOR.get().engineeringSnapshot(
                    helper.getLevel(), world, baseline, Direction.EAST).orElseThrow();
            if (baseline.getValue(DirectionalSignalBlock.OUTPUT) != 15
                    || AnalogComparatorBlock.processQuality(helper.getLevel(), world, baseline) != PortQuality.VALID
                    || AnalogComparatorBlock.referenceQuality(helper.getLevel(), world, baseline) != PortQuality.VALID
                    || baselineOut.quality() != PortQuality.VALID) {
                helper.fail("Comparator did not establish a valid HIGH baseline", comparator);
                return;
            }

            helper.setBlock(process, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(4, () -> {
                BlockState missingProcess = helper.getBlockState(comparator);
                var heldOut = RedstoneEngineering.ANALOG_COMPARATOR.get().engineeringSnapshot(
                        helper.getLevel(), world, missingProcess, Direction.EAST).orElseThrow();
                if (missingProcess.getValue(DirectionalSignalBlock.OUTPUT) != 15
                        || AnalogComparatorBlock.processQuality(helper.getLevel(), world, missingProcess) != PortQuality.NO_SIGNAL
                        || heldOut.quality() != PortQuality.NO_SIGNAL) {
                    helper.fail("Missing PROCESS evidence changed the retained decision or looked healthy", comparator);
                    return;
                }

                helper.setBlock(process, referenceSource(Direction.EAST, 0));
                helper.runAfterDelay(4, () -> {
                    BlockState recovered = helper.getBlockState(comparator);
                    var recoveredOut = RedstoneEngineering.ANALOG_COMPARATOR.get().engineeringSnapshot(
                            helper.getLevel(), world, recovered, Direction.EAST).orElseThrow();
                    if (recovered.getValue(DirectionalSignalBlock.OUTPUT) != 0
                            || AnalogComparatorBlock.processQuality(helper.getLevel(), world, recovered) != PortQuality.VALID
                            || AnalogComparatorBlock.referenceQuality(helper.getLevel(), world, recovered) != PortQuality.VALID
                            || recoveredOut.quality() != PortQuality.VALID) {
                        helper.fail("Comparator did not resume comparison after valid PROCESS evidence returned", comparator);
                        return;
                    }
                    helper.succeed();
                });
            });
        });
    }

    private static BlockState referenceSource(Direction facing, int power) {
        return RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, facing)
                .setValue(RedstoneReferenceSourceBlock.POWER, power);
    }
}
