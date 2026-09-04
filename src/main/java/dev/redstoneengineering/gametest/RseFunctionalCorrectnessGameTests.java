package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.AnalogIndicatorBlock;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.SignalConditionerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-world functional correctness tests for the player-facing redstone processing path.
 *
 * <p>These tests deliberately exercise actual block placement, scheduled ticks,
 * directional redstone queries, neighbor notifications, state propagation and the
 * vanilla 0..15 boundary. They complement algorithm/reference tests rather than
 * replacing them.</p>
 */
public final class RseFunctionalCorrectnessGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseFunctionalCorrectnessGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void conditionerChainProducesExpectedWorldOutput(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(0, 1, 2);
        BlockPos clampPos = new BlockPos(1, 1, 2);
        BlockPos gainPos = new BlockPos(2, 1, 2);
        BlockPos indicatorPos = new BlockPos(3, 1, 2);

        helper.setBlock(sourcePos, reference(Direction.EAST, 15));
        helper.setBlock(clampPos, conditioner(Direction.EAST, 2, 6));
        helper.setBlock(gainPos, conditioner(Direction.EAST, 0, 2));
        helper.setBlock(indicatorPos, indicator(Direction.EAST));

        helper.runAfterDelay(10, () -> {
            assertConditionerOutput(helper, clampPos, 6, "CLAMP stage must reduce 15 to 6");
            assertConditionerOutput(helper, gainPos, 12, "GAIN stage must amplify 6 to 12");
            assertIndicator(helper, indicatorPos, 12, "Indicator must receive the final conditioned output");
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void sourceRemovalClearsDownstreamState(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(0, 1, 2);
        BlockPos firstPos = new BlockPos(1, 1, 2);
        BlockPos secondPos = new BlockPos(2, 1, 2);
        BlockPos indicatorPos = new BlockPos(3, 1, 2);

        helper.setBlock(sourcePos, reference(Direction.EAST, 10));
        helper.setBlock(firstPos, conditioner(Direction.EAST, 0, 1));
        helper.setBlock(secondPos, conditioner(Direction.EAST, 0, 1));
        helper.setBlock(indicatorPos, indicator(Direction.EAST));

        helper.runAfterDelay(8, () -> {
            if (helper.getBlockState(indicatorPos).getValue(AnalogIndicatorBlock.LEVEL) != 10) {
                helper.fail("Precondition failed: live chain never propagated the source", indicatorPos);
                return;
            }

            helper.setBlock(sourcePos, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(8, () -> {
                assertConditionerOutput(helper, firstPos, 0, "First conditioner retained stale output after source removal");
                assertConditionerOutput(helper, secondPos, 0, "Second conditioner retained stale output after source removal");
                assertIndicator(helper, indicatorPos, 0, "Indicator retained stale output after source removal");
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void conditionerGainSaturatesAtVanillaBoundary(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(0, 1, 2);
        BlockPos conditionerPos = new BlockPos(1, 1, 2);
        BlockPos indicatorPos = new BlockPos(2, 1, 2);

        helper.setBlock(sourcePos, reference(Direction.EAST, 15));
        helper.setBlock(conditionerPos, conditioner(Direction.EAST, 0, 4));
        helper.setBlock(indicatorPos, indicator(Direction.EAST));

        helper.runAfterDelay(8, () -> {
            assertConditionerOutput(helper, conditionerPos, 15, "GAIN x4 escaped the vanilla 0..15 boundary");
            assertIndicator(helper, indicatorPos, 15, "Downstream device observed a value outside the intended saturated result");
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void conditionerRejectsSideFeed(GameTestHelper helper) {
        BlockPos conditionerPos = new BlockPos(2, 1, 2);
        BlockPos sideSourcePos = new BlockPos(2, 1, 1);
        BlockPos indicatorPos = new BlockPos(3, 1, 2);

        helper.setBlock(sideSourcePos, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(conditionerPos, conditioner(Direction.EAST, 0, 2));
        helper.setBlock(indicatorPos, indicator(Direction.EAST));

        helper.runAfterDelay(8, () -> {
            assertConditionerOutput(helper, conditionerPos, 0, "Conditioner incorrectly accepted a SIDE redstone feed");
            assertIndicator(helper, indicatorPos, 0, "SIDE feed leaked through to the downstream indicator");
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void conditionerOffsetAndThresholdModesMatchConfiguredSemantics(GameTestHelper helper) {
        // OFFSET: 4 + 5 = 9.
        helper.setBlock(new BlockPos(0, 1, 0), reference(Direction.EAST, 4));
        helper.setBlock(new BlockPos(1, 1, 0), conditioner(Direction.EAST, 1, 10));
        helper.setBlock(new BlockPos(2, 1, 0), indicator(Direction.EAST));

        // THRESHOLD below boundary: 7 < 8 -> 0.
        helper.setBlock(new BlockPos(0, 1, 2), reference(Direction.EAST, 7));
        helper.setBlock(new BlockPos(1, 1, 2), conditioner(Direction.EAST, 3, 8));
        helper.setBlock(new BlockPos(2, 1, 2), indicator(Direction.EAST));

        // THRESHOLD at boundary: 8 >= 8 -> 8.
        helper.setBlock(new BlockPos(0, 1, 4), reference(Direction.EAST, 8));
        helper.setBlock(new BlockPos(1, 1, 4), conditioner(Direction.EAST, 3, 8));
        helper.setBlock(new BlockPos(2, 1, 4), indicator(Direction.EAST));

        helper.runAfterDelay(10, () -> {
            assertConditionerOutput(helper, new BlockPos(1, 1, 0), 9, "OFFSET +5 did not produce 9 from input 4");
            assertIndicator(helper, new BlockPos(2, 1, 0), 9, "OFFSET result did not propagate to its indicator");
            assertConditionerOutput(helper, new BlockPos(1, 1, 2), 0, "THRESHOLD emitted below its configured boundary");
            assertConditionerOutput(helper, new BlockPos(1, 1, 4), 8, "THRESHOLD rejected a value exactly at its configured boundary");
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void conditionerDeadbandRetainsAndReleasesOutputDeterministically(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(0, 1, 2);
        BlockPos conditionerPos = new BlockPos(1, 1, 2);
        BlockPos indicatorPos = new BlockPos(2, 1, 2);

        helper.setBlock(sourcePos, reference(Direction.EAST, 5));
        helper.setBlock(conditionerPos, conditioner(Direction.EAST, 4, 2));
        helper.setBlock(indicatorPos, indicator(Direction.EAST));

        helper.runAfterDelay(8, () -> {
            assertConditionerOutput(helper, conditionerPos, 5, "DEADBAND failed to acquire the initial input");

            // |6 - 5| = 1 < band 2, so output must remain 5.
            helper.setBlock(sourcePos, reference(Direction.EAST, 6));
            helper.runAfterDelay(5, () -> {
                assertConditionerOutput(helper, conditionerPos, 5, "DEADBAND changed output inside the configured band");
                assertIndicator(helper, indicatorPos, 5, "Indicator did not preserve the held deadband output");

                // |7 - 5| = 2 >= band 2, so the output must move to 7.
                helper.setBlock(sourcePos, reference(Direction.EAST, 7));
                helper.runAfterDelay(5, () -> {
                    assertConditionerOutput(helper, conditionerPos, 7, "DEADBAND failed to release at the configured boundary");
                    assertIndicator(helper, indicatorPos, 7, "Released deadband value did not propagate downstream");
                    helper.succeed();
                });
            });
        });
    }

    private static BlockState reference(Direction facing, int power) {
        return RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, facing)
                .setValue(RedstoneReferenceSourceBlock.POWER, power);
    }

    private static BlockState conditioner(Direction facing, int mode, int param) {
        return RedstoneEngineering.SIGNAL_CONDITIONER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, facing)
                .setValue(SignalConditionerBlock.MODE, mode)
                .setValue(SignalConditionerBlock.PARAM, param);
    }

    private static BlockState indicator(Direction facing) {
        return RedstoneEngineering.ANALOG_INDICATOR.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, facing);
    }

    private static void assertConditionerOutput(GameTestHelper helper, BlockPos pos, int expected, String message) {
        int actual = helper.getBlockState(pos).getValue(DirectionalSignalBlock.OUTPUT);
        if (actual != expected) {
            helper.fail(message + " (expected=" + expected + ", actual=" + actual + ")", pos);
        }
    }

    private static void assertIndicator(GameTestHelper helper, BlockPos pos, int expected, String message) {
        int actual = helper.getBlockState(pos).getValue(AnalogIndicatorBlock.LEVEL);
        if (actual != expected) {
            helper.fail(message + " (expected=" + expected + ", actual=" + actual + ")", pos);
        }
    }
}
