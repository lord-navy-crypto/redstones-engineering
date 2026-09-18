package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.SignalSelectorBlock;
import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Evidence-provenance acceptance tests for the two-input signal selector. */
public final class RseSignalSelectorEvidenceGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseSignalSelectorEvidenceGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 90)
    public static void selectorDistinguishesMissingSelectFromExplicitLow(GameTestHelper helper) {
        BlockPos inputA = new BlockPos(1, 1, 2);
        BlockPos selector = new BlockPos(2, 1, 2);
        BlockPos inputB = new BlockPos(2, 1, 1);
        BlockPos select = new BlockPos(2, 1, 3);

        helper.setBlock(inputA, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(inputB, Blocks.REDSTONE_WIRE.defaultBlockState());
        helper.setBlock(selector, RedstoneEngineering.SIGNAL_SELECTOR.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(DirectionalSignalBlock.INPUT_FACING, Direction.WEST)
                .setValue(SignalSelectorBlock.INVERT_SELECT, false));

        helper.runAfterDelay(4, () -> {
            BlockPos world = helper.absolutePos(selector);
            BlockState missingSelect = helper.getBlockState(selector);
            var missingOut = RedstoneEngineering.SIGNAL_SELECTOR.get().engineeringSnapshot(
                    helper.getLevel(), world, missingSelect, Direction.EAST).orElseThrow();
            if (SignalSelectorBlock.selectedB(helper.getLevel(), world, missingSelect)
                    || missingSelect.getValue(DirectionalSignalBlock.OUTPUT) != 15
                    || SignalSelectorBlock.selectQuality(helper.getLevel(), world, missingSelect) != PortQuality.NO_SIGNAL
                    || missingOut.quality() != PortQuality.NO_SIGNAL) {
                helper.fail("Missing SELECT was hidden as an explicit healthy LOW command", selector);
                return;
            }

            // A connected zero-power SELECT is a real LOW command and must restore VALID provenance.
            helper.setBlock(select, Blocks.REDSTONE_WIRE.defaultBlockState());
            helper.runAfterDelay(4, () -> {
                BlockState explicitLow = helper.getBlockState(selector);
                var lowOut = RedstoneEngineering.SIGNAL_SELECTOR.get().engineeringSnapshot(
                        helper.getLevel(), world, explicitLow, Direction.EAST).orElseThrow();
                if (SignalSelectorBlock.selectedB(helper.getLevel(), world, explicitLow)
                        || explicitLow.getValue(DirectionalSignalBlock.OUTPUT) != 15
                        || SignalSelectorBlock.selectQuality(helper.getLevel(), world, explicitLow) != PortQuality.VALID
                        || lowOut.quality() != PortQuality.VALID) {
                    helper.fail("Explicit LOW SELECT did not become trustworthy A-selection evidence", selector);
                    return;
                }

                helper.setBlock(select, Blocks.REDSTONE_BLOCK.defaultBlockState());
                helper.runAfterDelay(4, () -> {
                    BlockState highSelect = helper.getBlockState(selector);
                    var highOut = RedstoneEngineering.SIGNAL_SELECTOR.get().engineeringSnapshot(
                            helper.getLevel(), world, highSelect, Direction.EAST).orElseThrow();
                    if (!SignalSelectorBlock.selectedB(helper.getLevel(), world, highSelect)
                            || highSelect.getValue(DirectionalSignalBlock.OUTPUT) != 0
                            || SignalSelectorBlock.selectedPayloadQuality(helper.getLevel(), world, highSelect) != PortQuality.VALID
                            || highOut.quality() != PortQuality.VALID) {
                        helper.fail("Valid HIGH SELECT did not route the valid B payload", selector);
                        return;
                    }
                    helper.succeed();
                });
            });
        });
    }
}
