package dev.redstoneengineering.gametest;

import dev.redstoneengineering.EngineeringSystemsModule;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.TopologyDebuggerBlock;
import dev.redstoneengineering.diagnostics.redstone.VanillaRedstoneEngineeringProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Runtime acceptance tests for the diagnostics-only Vanilla Redstone Engineering foundation. */
public final class RseVanillaRedstoneEngineeringGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseVanillaRedstoneEngineeringGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void vanillaProfileFindsPoweredDustAndTimingComponents(GameTestHelper helper) {
        BlockPos source = new BlockPos(0, 1, 2);
        BlockPos dustA = new BlockPos(1, 1, 2);
        BlockPos dustB = new BlockPos(2, 1, 2);
        BlockPos repeater = new BlockPos(3, 1, 2);

        helper.setBlock(source, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(dustA, Blocks.REDSTONE_WIRE.defaultBlockState());
        helper.setBlock(dustB, Blocks.REDSTONE_WIRE.defaultBlockState());
        helper.setBlock(repeater, Blocks.REPEATER.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST)
                .setValue(RepeaterBlock.DELAY, 4));

        helper.runAfterDelay(4, () -> {
            var report = VanillaRedstoneEngineeringProfile.inspect(helper.getLevel(), helper.absolutePos(dustA));
            if (!report.isVanillaRedstone() || report.dustCount() < 2) {
                helper.fail("Vanilla profiler did not discover the dust cluster", dustA); return;
            }
            if (report.poweredDustCount() < 1 || report.maxDustPower() <= 0 || report.maxDustPower() > 15) {
                helper.fail("Vanilla profiler did not retain truthful 0..15 powered-dust evidence", dustA); return;
            }
            if (report.repeaterCount() != 1 || report.configuredRepeaterDelayGameTicks() != 8) {
                helper.fail("Vanilla profiler did not read configured repeater timing", repeater); return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 30)
    public static void vanillaProfileIsObserverOnly(GameTestHelper helper) {
        BlockPos dust = new BlockPos(1, 1, 2);
        BlockPos observer = new BlockPos(2, 1, 2);
        BlockPos piston = new BlockPos(3, 1, 2);
        helper.setBlock(dust, Blocks.REDSTONE_WIRE.defaultBlockState());
        helper.setBlock(observer, Blocks.OBSERVER.defaultBlockState());
        helper.setBlock(piston, Blocks.PISTON.defaultBlockState());

        BlockState dustBefore = helper.getBlockState(dust);
        BlockState observerBefore = helper.getBlockState(observer);
        BlockState pistonBefore = helper.getBlockState(piston);
        var report = VanillaRedstoneEngineeringProfile.inspect(helper.getLevel(), helper.absolutePos(observer));

        if (report.nodeCount() < 3 || report.observerCount() != 1 || report.actuatorCount() != 1) {
            helper.fail("Vanilla profiler did not classify observer/actuator neighborhood", observer); return;
        }
        if (!helper.getBlockState(dust).equals(dustBefore)
                || !helper.getBlockState(observer).equals(observerBefore)
                || !helper.getBlockState(piston).equals(pistonBefore)) {
            helper.fail("Observer-only vanilla profiler mutated the inspected circuit", observer); return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void topologyDebuggerSelectsVanillaRedstoneMode(GameTestHelper helper) {
        BlockPos target = new BlockPos(1, 1, 2);
        BlockPos debugger = new BlockPos(2, 1, 2);
        helper.setBlock(target, Blocks.REDSTONE_WIRE.defaultBlockState());
        helper.setBlock(debugger, EngineeringSystemsModule.TOPOLOGY_DEBUGGER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));

        helper.runAfterDelay(2, () -> {
            BlockPos absoluteDebugger = helper.absolutePos(debugger);
            BlockState debuggerState = helper.getBlockState(debugger);
            if (!TopologyDebuggerBlock.targetsVanillaRedstone(helper.getLevel(), absoluteDebugger, debuggerState)) {
                helper.fail("Topology Debugger did not select vanilla-redstone diagnostic mode", debugger); return;
            }
            var report = TopologyDebuggerBlock.inspectVanillaTarget(helper.getLevel(), absoluteDebugger, debuggerState);
            if (!report.isVanillaRedstone() || !report.summary().startsWith("VANILLA REDSTONE")) {
                helper.fail("Topology Debugger did not expose the vanilla-redstone report", debugger); return;
            }
            helper.succeed();
        });
    }
}
