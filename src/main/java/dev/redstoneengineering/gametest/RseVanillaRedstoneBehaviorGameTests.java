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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Acceptance tests for conservative pulse/feedback/QC/order-sensitivity behavior evidence. */
public final class RseVanillaRedstoneBehaviorGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseVanillaRedstoneBehaviorGameTests() {}

    private static void support(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos.below(), Blocks.STONE.defaultBlockState());
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void behaviorReportMeasuresCompleteObservedPulseWidth(GameTestHelper helper) {
        BlockPos lamp = new BlockPos(2, 1, 2);
        helper.setBlock(lamp, Blocks.REDSTONE_LAMP.defaultBlockState());

        helper.runAfterDelay(2, () -> {
            var level = helper.getLevel();
            BlockPos absoluteLamp = helper.absolutePos(lamp);
            VanillaRedstoneRuntimeTelemetry.clearRegion(level, absoluteLamp, 3);

            // Establish an observed inactive baseline, then a rising edge without manufacturing
            // extra neighbor notifications through the fixture state mutation itself.
            level.updateNeighborsAt(absoluteLamp, Blocks.REDSTONE_LAMP);
            level.setBlock(absoluteLamp,
                    Blocks.REDSTONE_LAMP.defaultBlockState().setValue(BlockStateProperties.LIT, true), 2);
            level.updateNeighborsAt(absoluteLamp, Blocks.REDSTONE_LAMP);
        });

        helper.runAfterDelay(6, () -> {
            var level = helper.getLevel();
            BlockPos absoluteLamp = helper.absolutePos(lamp);
            level.setBlock(absoluteLamp,
                    Blocks.REDSTONE_LAMP.defaultBlockState().setValue(BlockStateProperties.LIT, false), 2);
            BlockState beforeInspection = level.getBlockState(absoluteLamp);
            level.updateNeighborsAt(absoluteLamp, Blocks.REDSTONE_LAMP);

            var behavior = VanillaRedstoneRuntimeTelemetry.inspectBehavior(level, absoluteLamp, 3, 20);
            if (behavior.completeObservedPulses() < 1) {
                helper.fail("Behavior report did not retain a complete observed lamp pulse", lamp); return;
            }
            if (behavior.minObservedPulseWidthTicks() != 4L || behavior.maxObservedPulseWidthTicks() != 4L) {
                helper.fail("Observed pulse width did not match the controlled four-game-tick fixture", lamp); return;
            }
            if (!behavior.summary().contains("PULSE=OBSERVED_COMPLETE_ONLY")) {
                helper.fail("Pulse report did not qualify complete-observation semantics", lamp); return;
            }
            if (!level.getBlockState(absoluteLamp).equals(beforeInspection)) {
                helper.fail("Behavior inspection mutated the vanilla lamp", lamp); return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void topologyDebuggerFlagsObserverFeedbackCandidateOnly(GameTestHelper helper) {
        BlockPos observer = new BlockPos(1, 1, 2);
        BlockPos debugger = new BlockPos(2, 1, 2);
        BlockPos intervening = new BlockPos(1, 1, 3);
        support(helper, observer);
        support(helper, debugger);
        helper.setBlock(observer, Blocks.OBSERVER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        helper.setBlock(intervening, Blocks.REDSTONE_LAMP.defaultBlockState());
        helper.setBlock(debugger, EngineeringSystemsModule.TOPOLOGY_DEBUGGER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));

        helper.runAfterDelay(3, () -> {
            var level = helper.getLevel();
            BlockPos absoluteObserver = helper.absolutePos(observer);
            BlockPos absoluteIntervening = helper.absolutePos(intervening);
            BlockPos absoluteDebugger = helper.absolutePos(debugger);
            BlockState debuggerState = level.getBlockState(absoluteDebugger);
            VanillaRedstoneRuntimeTelemetry.clearRegion(level, absoluteObserver, 4);

            // A -> B -> A in one server tick is a real listener-order pattern. It is deliberately
            // classified only as a feedback candidate, not proof of a causal feedback loop.
            level.updateNeighborsAt(absoluteObserver, Blocks.OBSERVER);
            level.updateNeighborsAt(absoluteIntervening, Blocks.REDSTONE_LAMP);
            level.updateNeighborsAt(absoluteObserver, Blocks.OBSERVER);

            var behavior = TopologyDebuggerBlock.inspectVanillaBehavior(level, absoluteDebugger, debuggerState);
            String summary = TopologyDebuggerBlock.vanillaDiagnosticSummary(level, absoluteDebugger, debuggerState);
            if (behavior.observerFeedbackCandidates() < 1
                    || !behavior.advisories().contains("OBSERVER_FEEDBACK_CANDIDATE")) {
                helper.fail("Debugger did not expose the bounded observer-return candidate", debugger); return;
            }
            if (!summary.contains("BEHAVIOR |") || !summary.contains("ORDER=OBSERVED_EVENT_ORDER_ONLY")) {
                helper.fail("Debugger did not preserve behavior/order evidence semantics", debugger); return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void behaviorReportCorrelatesQcCandidateWithoutClaimingCausation(GameTestHelper helper) {
        BlockPos piston = new BlockPos(2, 1, 2);
        BlockPos diagonalPower = new BlockPos(3, 2, 2);
        support(helper, piston);
        helper.setBlock(piston, Blocks.PISTON.defaultBlockState());
        helper.setBlock(diagonalPower, Blocks.REDSTONE_BLOCK.defaultBlockState());

        helper.runAfterDelay(3, () -> {
            var level = helper.getLevel();
            BlockPos absolutePiston = helper.absolutePos(piston);
            VanillaRedstoneRuntimeTelemetry.clearRegion(level, absolutePiston, 4);
            level.updateNeighborsAt(absolutePiston, Blocks.PISTON);

            var behavior = VanillaRedstoneRuntimeTelemetry.inspectBehavior(level, absolutePiston, 4, 20);
            if (behavior.possibleQcDependencyCount() < 1 || !behavior.hasQcActivityCorrelation()) {
                helper.fail("Behavior report did not correlate the QC structural candidate with runtime activity", piston); return;
            }
            if (!behavior.advisories().contains("QC_ACTIVITY_CORRELATION")
                    || !behavior.summary().contains("QC=CORRELATION_NOT_CAUSATION")) {
                helper.fail("QC evidence was not explicitly qualified as correlation", piston); return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void behaviorReportScoresRecurrentObservedOrderConservatively(GameTestHelper helper) {
        BlockPos first = new BlockPos(1, 1, 2);
        BlockPos second = new BlockPos(3, 1, 2);
        BlockPos anchor = new BlockPos(2, 1, 2);
        helper.setBlock(first, Blocks.REDSTONE_LAMP.defaultBlockState());
        helper.setBlock(second, Blocks.REDSTONE_LAMP.defaultBlockState());

        helper.runAfterDelay(2, () -> {
            var level = helper.getLevel();
            BlockPos absoluteAnchor = helper.absolutePos(anchor);
            VanillaRedstoneRuntimeTelemetry.clearRegion(level, absoluteAnchor, 4);
            level.updateNeighborsAt(helper.absolutePos(first), Blocks.REDSTONE_LAMP);
            level.updateNeighborsAt(helper.absolutePos(second), Blocks.REDSTONE_LAMP);
        });

        helper.runAfterDelay(5, () -> {
            var level = helper.getLevel();
            BlockPos absoluteAnchor = helper.absolutePos(anchor);
            level.updateNeighborsAt(helper.absolutePos(first), Blocks.REDSTONE_LAMP);
            level.updateNeighborsAt(helper.absolutePos(second), Blocks.REDSTONE_LAMP);

            var behavior = VanillaRedstoneRuntimeTelemetry.inspectBehavior(level, absoluteAnchor, 4, 20);
            if (behavior.recurrentObservedOrderPairs() < 1) {
                helper.fail("Behavior report did not retain the repeated observed A-to-B order pair", anchor); return;
            }
            if (!behavior.hasOrderSensitivityCandidate()
                    || !behavior.advisories().contains("ORDER_SENSITIVITY_CANDIDATE")) {
                helper.fail("Repeated listener-order evidence did not produce a conservative candidate", anchor); return;
            }
            if (!behavior.summary().contains("ORDER=OBSERVED_EVENT_ORDER_ONLY")) {
                helper.fail("Order-sensitivity report overstated listener-order semantics", anchor); return;
            }
            helper.succeed();
        });
    }
}
