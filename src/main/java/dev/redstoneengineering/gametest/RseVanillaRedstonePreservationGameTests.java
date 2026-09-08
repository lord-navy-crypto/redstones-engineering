package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Vanilla-first regression fixtures. These exercise Minecraft's own redstone blocks while RSE's
 * observer telemetry is installed. RSE must never change the expected vanilla result.
 */
public final class RseVanillaRedstonePreservationGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseVanillaRedstonePreservationGameTests() {}

    private static void support(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos.below(), Blocks.STONE.defaultBlockState());
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void vanillaDustAttenuationRemainsFifteenToTwelve(GameTestHelper helper) {
        BlockPos source = new BlockPos(0, 1, 2);
        BlockPos d1 = new BlockPos(1, 1, 2);
        BlockPos d2 = new BlockPos(2, 1, 2);
        BlockPos d3 = new BlockPos(3, 1, 2);
        BlockPos d4 = new BlockPos(4, 1, 2);
        support(helper, d1);
        support(helper, d2);
        support(helper, d3);
        support(helper, d4);
        helper.setBlock(d1, Blocks.REDSTONE_WIRE.defaultBlockState());
        helper.setBlock(d2, Blocks.REDSTONE_WIRE.defaultBlockState());
        helper.setBlock(d3, Blocks.REDSTONE_WIRE.defaultBlockState());
        helper.setBlock(d4, Blocks.REDSTONE_WIRE.defaultBlockState());
        helper.setBlock(source, Blocks.REDSTONE_BLOCK.defaultBlockState());

        helper.runAfterDelay(8, () -> {
            int p1 = helper.getBlockState(d1).getValue(RedStoneWireBlock.POWER);
            int p2 = helper.getBlockState(d2).getValue(RedStoneWireBlock.POWER);
            int p3 = helper.getBlockState(d3).getValue(RedStoneWireBlock.POWER);
            int p4 = helper.getBlockState(d4).getValue(RedStoneWireBlock.POWER);
            if (p1 != 15 || p2 != 14 || p3 != 13 || p4 != 12) {
                helper.fail("Vanilla dust attenuation changed under RSE (expected 15/14/13/12, got "
                        + p1 + "/" + p2 + "/" + p3 + "/" + p4 + ")", d2);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void vanillaRepeaterDelayFourDoesNotFireEarly(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 2);
        BlockPos repeater = new BlockPos(2, 1, 2);
        BlockPos lamp = new BlockPos(3, 1, 2);
        support(helper, repeater);
        support(helper, lamp);
        helper.setBlock(repeater, Blocks.REPEATER.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST)
                .setValue(RepeaterBlock.DELAY, 4));
        helper.setBlock(lamp, Blocks.REDSTONE_LAMP.defaultBlockState());

        helper.runAfterDelay(2, () -> {
            helper.setBlock(source, Blocks.REDSTONE_BLOCK.defaultBlockState());
            helper.runAfterDelay(4, () -> {
                if (helper.getBlockState(lamp).getValue(BlockStateProperties.LIT)) {
                    helper.fail("Vanilla delay-4 repeater fired too early while RSE telemetry was active", repeater);
                    return;
                }
                if (helper.getBlockState(repeater).getValue(RepeaterBlock.DELAY) != 4) {
                    helper.fail("RSE changed the configured vanilla repeater delay", repeater);
                    return;
                }
                helper.runAfterDelay(6, () -> {
                    if (!helper.getBlockState(lamp).getValue(BlockStateProperties.LIT)) {
                        helper.fail("Vanilla delay-4 repeater failed to propagate after its delay window", repeater);
                        return;
                    }
                    if (helper.getBlockState(repeater).getValue(RepeaterBlock.DELAY) != 4) {
                        helper.fail("RSE rewrote repeater delay after propagation", repeater);
                        return;
                    }
                    helper.succeed();
                });
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void vanillaComparatorCompareAndSubtractRemainDistinct(GameTestHelper helper) {
        BlockPos compare = new BlockPos(2, 1, 1);
        BlockPos compareRear = new BlockPos(1, 1, 1);
        BlockPos compareSide = new BlockPos(2, 1, 0);
        BlockPos compareLamp = new BlockPos(3, 1, 1);
        BlockPos subtract = new BlockPos(2, 1, 3);
        BlockPos subtractRear = new BlockPos(1, 1, 3);
        BlockPos subtractSide = new BlockPos(2, 1, 4);
        BlockPos subtractLamp = new BlockPos(3, 1, 3);
        support(helper, compare);
        support(helper, compareLamp);
        support(helper, subtract);
        support(helper, subtractLamp);

        helper.setBlock(compare, Blocks.COMPARATOR.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST)
                .setValue(ComparatorBlock.MODE, ComparatorMode.COMPARE));
        helper.setBlock(compareRear, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(compareSide, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(compareLamp, Blocks.REDSTONE_LAMP.defaultBlockState());

        helper.setBlock(subtract, Blocks.COMPARATOR.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST)
                .setValue(ComparatorBlock.MODE, ComparatorMode.SUBTRACT));
        helper.setBlock(subtractRear, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(subtractSide, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(subtractLamp, Blocks.REDSTONE_LAMP.defaultBlockState());

        helper.runAfterDelay(8, () -> {
            boolean compareLit = helper.getBlockState(compareLamp).getValue(BlockStateProperties.LIT);
            boolean subtractLit = helper.getBlockState(subtractLamp).getValue(BlockStateProperties.LIT);
            if (!compareLit || subtractLit) {
                helper.fail("Vanilla comparator modes changed under RSE (COMPARE lamp=" + compareLit
                        + ", SUBTRACT lamp=" + subtractLit + ")", compare);
                return;
            }
            if (helper.getBlockState(compare).getValue(ComparatorBlock.MODE) != ComparatorMode.COMPARE
                    || helper.getBlockState(subtract).getValue(ComparatorBlock.MODE) != ComparatorMode.SUBTRACT) {
                helper.fail("RSE rewrote a vanilla comparator mode", compare);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void vanillaObserverPulseStillReturnsLow(GameTestHelper helper) {
        BlockPos observed = new BlockPos(1, 1, 2);
        BlockPos observer = new BlockPos(2, 1, 2);
        support(helper, observed);
        support(helper, observer);
        helper.setBlock(observed, Blocks.STONE.defaultBlockState());
        helper.setBlock(observer, Blocks.OBSERVER.defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.WEST));

        helper.runAfterDelay(4, () -> {
            helper.setBlock(observed, Blocks.GOLD_BLOCK.defaultBlockState());
            helper.runAfterDelay(3, () -> {
                if (!helper.getBlockState(observer).getValue(ObserverBlock.POWERED)) {
                    helper.fail("Vanilla observer did not emit its pulse after the observed block changed", observer);
                    return;
                }
                helper.runAfterDelay(3, () -> {
                    if (helper.getBlockState(observer).getValue(ObserverBlock.POWERED)) {
                        helper.fail("Vanilla observer pulse was extended/stuck HIGH while RSE telemetry was active", observer);
                        return;
                    }
                    helper.succeed();
                });
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void vanillaPistonDirectPowerStillExtendsAndRetracts(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 2);
        BlockPos piston = new BlockPos(2, 1, 2);
        support(helper, piston);
        helper.setBlock(piston, Blocks.PISTON.defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.EAST));

        helper.runAfterDelay(3, () -> {
            helper.setBlock(source, Blocks.REDSTONE_BLOCK.defaultBlockState());
            helper.runAfterDelay(4, () -> {
                if (!helper.getBlockState(piston).getValue(BlockStateProperties.EXTENDED)) {
                    helper.fail("Vanilla piston failed to extend under direct power with RSE installed", piston);
                    return;
                }
                helper.setBlock(source, Blocks.AIR.defaultBlockState());
                helper.runAfterDelay(5, () -> {
                    if (helper.getBlockState(piston).getValue(BlockStateProperties.EXTENDED)) {
                        helper.fail("Vanilla piston failed to retract after direct power was removed", piston);
                        return;
                    }
                    helper.succeed();
                });
            });
        });
    }
}
