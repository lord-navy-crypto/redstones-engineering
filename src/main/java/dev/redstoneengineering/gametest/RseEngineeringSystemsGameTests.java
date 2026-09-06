package dev.redstoneengineering.gametest;

import dev.redstoneengineering.EngineeringSystemsModule;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.FaultInjectorBlock;
import dev.redstoneengineering.block.SafetyInterlockBlock;
import dev.redstoneengineering.block.SequenceControllerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Executable acceptance tests for the systems-level automation primitives. */
public final class RseEngineeringSystemsGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseEngineeringSystemsGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 120)
    public static void sequenceControllerAdvancesOnEdgesAndResets(GameTestHelper helper) {
        BlockPos controller = new BlockPos(2, 1, 2);
        BlockPos run = new BlockPos(1, 1, 2);
        BlockPos advance = new BlockPos(2, 1, 1);
        BlockPos reset = new BlockPos(2, 1, 3);

        helper.setBlock(controller, EngineeringSystemsModule.SEQUENCE_CONTROLLER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));
        helper.setBlock(run, Blocks.REDSTONE_BLOCK.defaultBlockState());

        helper.runAfterDelay(4, () -> {
            if (helper.getBlockState(controller).getValue(DirectionalSignalBlock.OUTPUT) != 1) {
                helper.fail("Sequence controller did not enter STEP 1 on RUN rising edge", controller);
                return;
            }
            helper.setBlock(advance, Blocks.REDSTONE_BLOCK.defaultBlockState());
            helper.runAfterDelay(3, () -> {
                if (helper.getBlockState(controller).getValue(DirectionalSignalBlock.OUTPUT) != 2) {
                    helper.fail("Sequence controller did not advance exactly once on trigger edge", controller);
                    return;
                }
                helper.setBlock(advance, Blocks.AIR.defaultBlockState());
                helper.runAfterDelay(2, () -> {
                    helper.setBlock(advance, Blocks.REDSTONE_BLOCK.defaultBlockState());
                    helper.runAfterDelay(3, () -> {
                        if (helper.getBlockState(controller).getValue(DirectionalSignalBlock.OUTPUT) != 3) {
                            helper.fail("Sequence controller did not accept the second distinct trigger edge", controller);
                            return;
                        }
                        helper.setBlock(reset, Blocks.REDSTONE_BLOCK.defaultBlockState());
                        helper.runAfterDelay(3, () -> {
                            if (helper.getBlockState(controller).getValue(DirectionalSignalBlock.OUTPUT) != 0
                                    || SequenceControllerBlock.step(helper.getLevel(), helper.absolutePos(controller)) != 0) {
                                helper.fail("RESET did not force the sequencer to IDLE", controller);
                                return;
                            }
                            helper.succeed();
                        });
                    });
                });
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void safetyInterlockRequiresAllPermissives(GameTestHelper helper) {
        BlockPos interlock = new BlockPos(2, 1, 2);
        BlockPos a = new BlockPos(1, 1, 2);
        BlockPos b = new BlockPos(2, 1, 1);
        BlockPos c = new BlockPos(2, 1, 3);

        helper.setBlock(interlock, EngineeringSystemsModule.SAFETY_INTERLOCK.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));
        helper.setBlock(a, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(b, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(c, Blocks.REDSTONE_BLOCK.defaultBlockState());

        helper.runAfterDelay(4, () -> {
            if (helper.getBlockState(interlock).getValue(DirectionalSignalBlock.OUTPUT) != 15) {
                helper.fail("Interlock did not issue PERMIT with all three permissives valid", interlock);
                return;
            }
            helper.setBlock(b, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(3, () -> {
                if (helper.getBlockState(interlock).getValue(DirectionalSignalBlock.OUTPUT) != 0
                        || SafetyInterlockBlock.failedMask(helper.getLevel(), helper.absolutePos(interlock)) != 2) {
                    helper.fail("Interlock did not revoke PERMIT and identify failed channel B", interlock);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void faultInjectorIsArmedBoundedAndRecoverable(GameTestHelper helper) {
        BlockPos injector = new BlockPos(2, 1, 2);
        BlockPos input = new BlockPos(1, 1, 2);
        BlockPos arm = new BlockPos(2, 1, 3);

        helper.setBlock(injector, EngineeringSystemsModule.FAULT_INJECTOR.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(FaultInjectorBlock.MODE, 3));
        helper.setBlock(input, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(arm, Blocks.REDSTONE_BLOCK.defaultBlockState());

        helper.runAfterDelay(4, () -> {
            if (helper.getBlockState(injector).getValue(DirectionalSignalBlock.OUTPUT) != 11
                    || !FaultInjectorBlock.active(helper.getLevel(), helper.absolutePos(injector))
                    || FaultInjectorBlock.lastInput(helper.getLevel(), helper.absolutePos(injector)) != 15
                    || FaultInjectorBlock.lastOutput(helper.getLevel(), helper.absolutePos(injector)) != 11) {
                helper.fail("Armed BIAS -4 fault did not produce the expected bounded output", injector);
                return;
            }
            helper.setBlock(arm, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(3, () -> {
                if (helper.getBlockState(injector).getValue(DirectionalSignalBlock.OUTPUT) != 15
                        || FaultInjectorBlock.active(helper.getLevel(), helper.absolutePos(injector))) {
                    helper.fail("Disarming the fault injector did not restore pass-through behavior", injector);
                    return;
                }
                helper.succeed();
            });
        });
    }
}
