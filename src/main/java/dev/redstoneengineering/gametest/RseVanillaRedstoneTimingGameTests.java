package dev.redstoneengineering.gametest;

import dev.redstoneengineering.EngineeringSystemsModule;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.TopologyDebuggerBlock;
import dev.redstoneengineering.diagnostics.redstone.VanillaRedstoneRuntimeTelemetry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Phase-3 acceptance tests for tick timing and listener-observation-order evidence. */
public final class RseVanillaRedstoneTimingGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseVanillaRedstoneTimingGameTests() {}

    private static void support(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos.below(), Blocks.STONE.defaultBlockState());
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void timingReportCapturesCrossTickSpacing(GameTestHelper helper) {
        BlockPos target = new BlockPos(2, 1, 2);
        helper.setBlock(target, Blocks.REDSTONE_LAMP.defaultBlockState());

        helper.runAfterDelay(2, () -> {
            var level = helper.getLevel();
            BlockPos absoluteTarget = helper.absolutePos(target);
            VanillaRedstoneRuntimeTelemetry.clearRegion(level, absoluteTarget, 4);
            level.updateNeighborsAt(absoluteTarget, Blocks.REDSTONE_LAMP);
        });

        helper.runAfterDelay(6, () -> {
            var level = helper.getLevel();
            BlockPos absoluteTarget = helper.absolutePos(target);
            BlockState before = level.getBlockState(absoluteTarget);
            level.updateNeighborsAt(absoluteTarget, Blocks.REDSTONE_LAMP);

            var timing = VanillaRedstoneRuntimeTelemetry.inspectTiming(level, absoluteTarget, 2, 20);
            if (timing.observationCount() < 2 || timing.activeTickCount() < 2) {
                helper.fail("Timing report did not retain observations from two server game ticks", target); return;
            }
            if (!timing.hasInterObservationEvidence() || timing.maxInterObservationTicks() < 3L) {
                helper.fail("Timing report did not preserve cross-tick observation spacing", target); return;
            }
            if (timing.observedSpanTicks() < 4L) {
                helper.fail("Timing report observed span is shorter than the controlled fixture", target); return;
            }
            if (!level.getBlockState(absoluteTarget).equals(before)) {
                helper.fail("Timing inspection mutated the vanilla target", target); return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void timingReportPreservesSameTickObservationOrder(GameTestHelper helper) {
        BlockPos first = new BlockPos(1, 1, 2);
        BlockPos second = new BlockPos(3, 1, 2);
        BlockPos anchor = new BlockPos(2, 1, 2);
        helper.setBlock(first, Blocks.REDSTONE_LAMP.defaultBlockState());
        helper.setBlock(second, Blocks.REDSTONE_LAMP.defaultBlockState());

        helper.runAfterDelay(2, () -> {
            var level = helper.getLevel();
            BlockPos absoluteFirst = helper.absolutePos(first);
            BlockPos absoluteSecond = helper.absolutePos(second);
            BlockPos absoluteAnchor = helper.absolutePos(anchor);
            VanillaRedstoneRuntimeTelemetry.clearRegion(level, absoluteAnchor, 4);

            // These synchronous calls provide listener-observation order evidence only.
            level.updateNeighborsAt(absoluteFirst, Blocks.REDSTONE_LAMP);
            level.updateNeighborsAt(absoluteSecond, Blocks.REDSTONE_LAMP);

            var timing = VanillaRedstoneRuntimeTelemetry.inspectTiming(level, absoluteAnchor, 4, 20);
            if (timing.observationCount() < 2 || timing.sameTickOrderedPairCount() < 1) {
                helper.fail("Timing report did not retain same-tick listener ordering", anchor); return;
            }
            if (timing.sameTickDistinctSourcePairCount() < 1) {
                helper.fail("Timing report did not distinguish same-tick source positions", anchor); return;
            }
            if (timing.firstObservationSequence() < 0L
                    || timing.lastObservationSequence() <= timing.firstObservationSequence()) {
                helper.fail("Observation sequence was not monotonic", anchor); return;
            }
            if (!timing.summary().contains("ORDER=OBSERVED_EVENT_ORDER_ONLY")) {
                helper.fail("Timing report failed to label listener order semantics", anchor); return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void topologyDebuggerIncludesTimingEvidence(GameTestHelper helper) {
        BlockPos target = new BlockPos(1, 1, 2);
        BlockPos debugger = new BlockPos(2, 1, 2);
        support(helper, target);
        support(helper, debugger);
        helper.setBlock(target, Blocks.REPEATER.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
                .setValue(RepeaterBlock.DELAY, 3));
        helper.setBlock(debugger, EngineeringSystemsModule.TOPOLOGY_DEBUGGER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));

        helper.runAfterDelay(3, () -> {
            var level = helper.getLevel();
            BlockPos absoluteTarget = helper.absolutePos(target);
            BlockPos absoluteDebugger = helper.absolutePos(debugger);
            BlockState debuggerState = level.getBlockState(absoluteDebugger);
            VanillaRedstoneRuntimeTelemetry.clearRegion(level, absoluteTarget, 4);
            level.updateNeighborsAt(absoluteTarget, Blocks.REPEATER);

            var timing = TopologyDebuggerBlock.inspectVanillaTiming(level, absoluteDebugger, debuggerState);
            String summary = TopologyDebuggerBlock.vanillaDiagnosticSummary(level, absoluteDebugger, debuggerState);
            if (!timing.hasTimingEvidence() || timing.observationCount() < 1) {
                helper.fail("Topology Debugger did not expose timing evidence", debugger); return;
            }
            if (!summary.contains("delayCfg=6gt") || !summary.contains("TIMING |")) {
                helper.fail("Topology Debugger did not combine configured and observed timing evidence", debugger); return;
            }
            if (!summary.contains("ORDER=OBSERVED_EVENT_ORDER_ONLY")) {
                helper.fail("Topology Debugger did not qualify event-order evidence", debugger); return;
            }
            helper.succeed();
        });
    }
}
