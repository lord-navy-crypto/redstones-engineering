package dev.redstoneengineering.gametest;

import dev.redstoneengineering.EngineeringSystemsModule;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.AlarmProcessorBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.FaultInjectorBlock;
import dev.redstoneengineering.block.SafetyInterlockBlock;
import dev.redstoneengineering.block.SequenceControllerBlock;
import dev.redstoneengineering.block.TopologyDebuggerBlock;
import dev.redstoneengineering.diagnostics.events.FirstOutAnalysis;
import dev.redstoneengineering.diagnostics.events.SystemEventKind;
import dev.redstoneengineering.diagnostics.events.SystemEventScope;
import dev.redstoneengineering.diagnostics.events.SystemEventTimeline;
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
        BlockPos controller = new BlockPos(2, 1, 2); BlockPos run = new BlockPos(1, 1, 2);
        BlockPos advance = new BlockPos(2, 1, 1); BlockPos reset = new BlockPos(2, 1, 3);
        helper.setBlock(controller, EngineeringSystemsModule.SEQUENCE_CONTROLLER.get().defaultBlockState().setValue(DirectionalSignalBlock.FACING, Direction.EAST));
        helper.setBlock(run, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.runAfterDelay(4, () -> {
            if (helper.getBlockState(controller).getValue(DirectionalSignalBlock.OUTPUT) != 1) { helper.fail("Sequence controller did not enter STEP 1", controller); return; }
            helper.setBlock(advance, Blocks.REDSTONE_BLOCK.defaultBlockState());
            helper.runAfterDelay(3, () -> {
                if (helper.getBlockState(controller).getValue(DirectionalSignalBlock.OUTPUT) != 2) { helper.fail("Sequence controller did not advance", controller); return; }
                helper.setBlock(advance, Blocks.AIR.defaultBlockState());
                helper.runAfterDelay(2, () -> { helper.setBlock(advance, Blocks.REDSTONE_BLOCK.defaultBlockState()); helper.runAfterDelay(3, () -> {
                    if (helper.getBlockState(controller).getValue(DirectionalSignalBlock.OUTPUT) != 3) { helper.fail("Sequence controller did not accept second edge", controller); return; }
                    helper.setBlock(reset, Blocks.REDSTONE_BLOCK.defaultBlockState()); helper.runAfterDelay(3, () -> {
                        if (helper.getBlockState(controller).getValue(DirectionalSignalBlock.OUTPUT) != 0 || SequenceControllerBlock.step(helper.getLevel(), helper.absolutePos(controller)) != 0) { helper.fail("RESET did not force IDLE", controller); return; }
                        helper.succeed();
                    });
                }); });
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void safetyInterlockRequiresAllPermissives(GameTestHelper helper) {
        BlockPos interlock = new BlockPos(2, 1, 2); BlockPos a = new BlockPos(1, 1, 2); BlockPos b = new BlockPos(2, 1, 1); BlockPos c = new BlockPos(2, 1, 3);
        helper.setBlock(interlock, EngineeringSystemsModule.SAFETY_INTERLOCK.get().defaultBlockState().setValue(DirectionalSignalBlock.FACING, Direction.EAST));
        helper.setBlock(a, Blocks.REDSTONE_BLOCK.defaultBlockState()); helper.setBlock(b, Blocks.REDSTONE_BLOCK.defaultBlockState()); helper.setBlock(c, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.runAfterDelay(4, () -> { if (helper.getBlockState(interlock).getValue(DirectionalSignalBlock.OUTPUT) != 15) { helper.fail("Interlock did not issue PERMIT", interlock); return; }
            helper.setBlock(b, Blocks.AIR.defaultBlockState()); helper.runAfterDelay(3, () -> { if (helper.getBlockState(interlock).getValue(DirectionalSignalBlock.OUTPUT) != 0 || SafetyInterlockBlock.failedMask(helper.getLevel(), helper.absolutePos(interlock)) != 2) { helper.fail("Interlock did not identify B", interlock); return; } helper.succeed(); }); });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void faultInjectorIsArmedBoundedAndRecoverable(GameTestHelper helper) {
        BlockPos injector = new BlockPos(2, 1, 2); BlockPos input = new BlockPos(1, 1, 2); BlockPos arm = new BlockPos(2, 1, 3);
        helper.setBlock(injector, EngineeringSystemsModule.FAULT_INJECTOR.get().defaultBlockState().setValue(DirectionalSignalBlock.FACING, Direction.EAST).setValue(FaultInjectorBlock.MODE, 3));
        helper.setBlock(input, Blocks.REDSTONE_BLOCK.defaultBlockState()); helper.setBlock(arm, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.runAfterDelay(4, () -> { if (helper.getBlockState(injector).getValue(DirectionalSignalBlock.OUTPUT) != 11 || !FaultInjectorBlock.active(helper.getLevel(), helper.absolutePos(injector))) { helper.fail("Fault injector armed behavior wrong", injector); return; }
            helper.setBlock(arm, Blocks.AIR.defaultBlockState()); helper.runAfterDelay(3, () -> { if (helper.getBlockState(injector).getValue(DirectionalSignalBlock.OUTPUT) != 15 || FaultInjectorBlock.active(helper.getLevel(), helper.absolutePos(injector))) { helper.fail("Fault injector did not recover", injector); return; } helper.succeed(); }); });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void alarmProcessorLatchesAndRequiresHealthyReset(GameTestHelper helper) {
        BlockPos alarm = new BlockPos(2, 1, 2); BlockPos condition = new BlockPos(1, 1, 2); BlockPos reset = new BlockPos(2, 1, 3);
        helper.setBlock(alarm, EngineeringSystemsModule.ALARM_PROCESSOR.get().defaultBlockState().setValue(DirectionalSignalBlock.FACING, Direction.EAST).setValue(AlarmProcessorBlock.SEVERITY, 2));
        helper.setBlock(condition, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.runAfterDelay(4, () -> {
            if (helper.getBlockState(alarm).getValue(DirectionalSignalBlock.OUTPUT) != 10 || !AlarmProcessorBlock.latched(helper.getLevel(), helper.absolutePos(alarm)) || !AlarmProcessorBlock.unacknowledged(helper.getLevel(), helper.absolutePos(alarm))) { helper.fail("Alarm did not latch severity-2 event", alarm); return; }
            helper.setBlock(reset, Blocks.REDSTONE_BLOCK.defaultBlockState());
            helper.runAfterDelay(3, () -> {
                if (!AlarmProcessorBlock.latched(helper.getLevel(), helper.absolutePos(alarm))) { helper.fail("Alarm reset while fault condition was still active", alarm); return; }
                helper.setBlock(reset, Blocks.AIR.defaultBlockState()); helper.setBlock(condition, Blocks.AIR.defaultBlockState());
                helper.runAfterDelay(2, () -> { helper.setBlock(reset, Blocks.REDSTONE_BLOCK.defaultBlockState()); helper.runAfterDelay(3, () -> {
                    if (helper.getBlockState(alarm).getValue(DirectionalSignalBlock.OUTPUT) != 0 || AlarmProcessorBlock.latched(helper.getLevel(), helper.absolutePos(alarm))) { helper.fail("Healthy reset did not clear latched alarm", alarm); return; }
                    helper.succeed();
                }); });
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void topologyDebuggerFlagsDanglingEngineeringTarget(GameTestHelper helper) {
        BlockPos debugger = new BlockPos(2, 1, 2); BlockPos target = new BlockPos(1, 1, 2);
        helper.setBlock(debugger, EngineeringSystemsModule.TOPOLOGY_DEBUGGER.get().defaultBlockState().setValue(DirectionalSignalBlock.FACING, Direction.EAST));
        helper.setBlock(target, EngineeringSystemsModule.SAFETY_INTERLOCK.get().defaultBlockState().setValue(DirectionalSignalBlock.FACING, Direction.NORTH));
        helper.runAfterDelay(5, () -> {
            if (helper.getBlockState(debugger).getValue(DirectionalSignalBlock.OUTPUT) != 15 || !TopologyDebuggerBlock.inspectTarget(helper.getLevel(), helper.absolutePos(debugger), helper.getBlockState(debugger)).hasIssue()) { helper.fail("Topology debugger did not flag dangling target ports", debugger); return; }
            helper.setBlock(target, Blocks.STONE.defaultBlockState());
            helper.runAfterDelay(4, () -> {
                if (helper.getBlockState(debugger).getValue(DirectionalSignalBlock.OUTPUT) != 0 || TopologyDebuggerBlock.scanCount(helper.getLevel(), helper.absolutePos(debugger)) == 0) { helper.fail("Topology debugger did not return clear state for non-port target", debugger); return; }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 120)
    public static void systemTimelineCapturesAlarmLifecycleAndFirstOut(GameTestHelper helper) {
        BlockPos alarm = new BlockPos(2, 1, 2);
        BlockPos condition = new BlockPos(1, 1, 2);
        BlockPos reset = new BlockPos(2, 1, 3);
        BlockPos alarmWorld = helper.absolutePos(alarm);
        SystemEventScope scope = new SystemEventScope(alarmWorld, 1);
        helper.setBlock(alarm, EngineeringSystemsModule.ALARM_PROCESSOR.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(AlarmProcessorBlock.SEVERITY, 2));
        helper.setBlock(condition, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.runAfterDelay(4, () -> {
            var firstOut = FirstOutAnalysis.latestWithin(helper.getLevel(), scope);
            if (firstOut.isEmpty() || firstOut.get().firstOut().kind() != SystemEventKind.ALARM_RAISED
                    || !alarmWorld.equals(firstOut.get().firstOut().source())) {
                helper.fail("Scoped first-out did not identify the raised alarm", alarm); return;
            }
            helper.setBlock(condition, Blocks.AIR.defaultBlockState());
            helper.setBlock(reset, Blocks.REDSTONE_BLOCK.defaultBlockState());
            helper.runAfterDelay(4, () -> {
                boolean cleared = SystemEventTimeline.within(helper.getLevel(), scope).stream()
                        .anyMatch(event -> event.kind() == SystemEventKind.ALARM_CLEARED
                                && alarmWorld.equals(event.source()));
                if (!cleared) { helper.fail("Alarm clear was not written to scoped system timeline", alarm); return; }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void firstOutPreservesEarliestAbnormalEventInIncident(GameTestHelper helper) {
        BlockPos first = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos second = helper.absolutePos(new BlockPos(3, 1, 3));
        SystemEventScope scope = new SystemEventScope(first, 3);
        SystemEventTimeline.record(helper.getLevel(), first, SystemEventKind.INTERLOCK_TRIPPED, 3,
                "PRESSURE_LOW", "Pneumatic permissive lost");
        helper.runAfterDelay(5, () -> {
            SystemEventTimeline.record(helper.getLevel(), second, SystemEventKind.ALARM_RAISED, 2,
                    "CYLINDER_TIMEOUT", "Cylinder did not reach commanded position");
            var firstOut = FirstOutAnalysis.latestWithin(helper.getLevel(), scope);
            if (firstOut.isEmpty() || !"PRESSURE_LOW".equals(firstOut.get().firstOut().code())) {
                helper.fail("Scoped first-out did not preserve earliest abnormal evidence", new BlockPos(1, 1, 1)); return;
            }
            if (firstOut.get().downstreamObservations().stream().noneMatch(event -> "CYLINDER_TIMEOUT".equals(event.code()))) {
                helper.fail("Scoped first-out incident did not retain downstream observation", new BlockPos(3, 1, 3)); return;
            }
            helper.succeed();
        });
    }
}
