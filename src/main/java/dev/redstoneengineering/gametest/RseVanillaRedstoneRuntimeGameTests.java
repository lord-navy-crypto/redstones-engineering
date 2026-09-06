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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Runtime acceptance tests for observer-only Vanilla Redstone NeighborNotifyEvent telemetry. */
public final class RseVanillaRedstoneRuntimeGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseVanillaRedstoneRuntimeGameTests() {}

    private static void support(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos.below(), Blocks.STONE.defaultBlockState());
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void runtimeTelemetryRecordsRealNeighborNotifications(GameTestHelper helper) {
        BlockPos wire = new BlockPos(2, 1, 2);
        support(helper, wire);
        helper.setBlock(wire, Blocks.REDSTONE_WIRE.defaultBlockState());

        helper.runAfterDelay(2, () -> {
            var level = helper.getLevel();
            BlockPos absoluteWire = helper.absolutePos(wire);
            VanillaRedstoneRuntimeTelemetry.clear(level);
            BlockState before = level.getBlockState(absoluteWire);

            // NeoForge patches Level.updateNeighborsAt to post the real server NeighborNotifyEvent.
            level.updateNeighborsAt(absoluteWire, Blocks.REDSTONE_WIRE);

            var report = VanillaRedstoneRuntimeTelemetry.inspect(level, absoluteWire, 4, 20);
            if (report.neighborNotificationEvents() < 1 || report.notifiedSideTotal() < 1) {
                helper.fail("Runtime telemetry did not receive the real vanilla neighbor-notify event", wire); return;
            }
            if (!level.getBlockState(absoluteWire).equals(before)) {
                helper.fail("Observer-only runtime telemetry mutated the observed redstone wire", wire); return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void runtimeTelemetrySeparatesObservedStateTransitions(GameTestHelper helper) {
        BlockPos lamp = new BlockPos(2, 1, 2);
        helper.setBlock(lamp, Blocks.REDSTONE_LAMP.defaultBlockState());

        helper.runAfterDelay(2, () -> {
            var level = helper.getLevel();
            BlockPos absoluteLamp = helper.absolutePos(lamp);
            VanillaRedstoneRuntimeTelemetry.clear(level);

            level.updateNeighborsAt(absoluteLamp, Blocks.REDSTONE_LAMP);
            BlockState lit = Blocks.REDSTONE_LAMP.defaultBlockState()
                    .setValue(BlockStateProperties.LIT, true);
            // Fixture mutation only: flag 2 updates clients without manufacturing a notify count.
            level.setBlock(absoluteLamp, lit, 2);
            BlockState beforeObservation = level.getBlockState(absoluteLamp);
            level.updateNeighborsAt(absoluteLamp, Blocks.REDSTONE_LAMP);

            var report = VanillaRedstoneRuntimeTelemetry.inspect(level, absoluteLamp, 2, 20);
            if (report.neighborNotificationEvents() < 2) {
                helper.fail("Runtime telemetry did not retain both lamp observations", lamp); return;
            }
            if (report.observedStateTransitions() < 1) {
                helper.fail("Runtime telemetry did not distinguish the observed LIT state transition", lamp); return;
            }
            if (!level.getBlockState(absoluteLamp).equals(beforeObservation)) {
                helper.fail("Runtime telemetry changed the lamp state while observing it", lamp); return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void topologyDebuggerIncludesVanillaRuntimeEvidence(GameTestHelper helper) {
        BlockPos target = new BlockPos(1, 1, 2);
        BlockPos debugger = new BlockPos(2, 1, 2);
        support(helper, target);
        support(helper, debugger);
        helper.setBlock(target, Blocks.REPEATER.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(debugger, EngineeringSystemsModule.TOPOLOGY_DEBUGGER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));

        helper.runAfterDelay(3, () -> {
            var level = helper.getLevel();
            BlockPos absoluteTarget = helper.absolutePos(target);
            BlockPos absoluteDebugger = helper.absolutePos(debugger);
            BlockState debuggerState = level.getBlockState(absoluteDebugger);
            VanillaRedstoneRuntimeTelemetry.clear(level);
            level.updateNeighborsAt(absoluteTarget, Blocks.REPEATER);

            var runtime = TopologyDebuggerBlock.inspectVanillaRuntime(level, absoluteDebugger, debuggerState);
            String summary = TopologyDebuggerBlock.vanillaDiagnosticSummary(level, absoluteDebugger, debuggerState);
            if (!runtime.hasTelemetry() || runtime.neighborNotificationEvents() < 1) {
                helper.fail("Topology Debugger did not expose vanilla runtime telemetry", debugger); return;
            }
            if (!summary.startsWith("VANILLA REDSTONE") || !summary.contains("RUNTIME | notifyEvents=")) {
                helper.fail("Topology Debugger summary did not combine static and runtime evidence", debugger); return;
            }
            helper.succeed();
        });
    }
}
