package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.block.QuartzPhaseDelayBlock;
import dev.redstoneengineering.block.QuartzTimingLineBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.DomainDriverRegistry;
import dev.redstoneengineering.physics.DomainNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Lifecycle regressions for registered domain-driver authority. */
public final class RseDomainDriverLifecycleSystemGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseDomainDriverLifecycleSystemGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void phaseDelayConfigurationResetCannotLeaveAuthoritativeOldPulse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos phase = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos output = phase.east();

        BlockState phaseState = RedstoneEngineering.QUARTZ_PHASE_DELAY.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                .setValue(QuartzPhaseDelayBlock.DELAY, 2);
        BlockState outputLine = RedstoneEngineering.QUARTZ_TIMING_LINE.get().defaultBlockState();

        level.setBlock(phase, phaseState, Block.UPDATE_CLIENTS);
        level.setBlock(output, outputLine, Block.UPDATE_CLIENTS);

        // Establish a reachable currently-authoritative output owned by the real loaded PhaseDelay block.
        DomainDriverRegistry.claim(level, "quartz", phase, output, 1, 8, 0);
        DomainNetwork.recomputeQuartz(level, output);
        if (QuartzTimingLineBlock.quality(level, output) != PortQuality.VALID
                || !QuartzTimingLineBlock.active(level, output)
                || QuartzTimingLineBlock.period(level, output) != 8) {
            cleanup(level, phase, output);
            helper.fail("Precondition failed: PhaseDelay output claim was not authoritative before configuration reset");
            return;
        }

        int beforeDelay = level.getBlockState(phase).getValue(QuartzPhaseDelayBlock.DELAY);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(phase), Direction.UP, phase, false);
        level.getBlockState(phase).useWithoutItem(level, player, hit);
        int afterDelay = level.getBlockState(phase).getValue(QuartzPhaseDelayBlock.DELAY);
        if (afterDelay == beforeDelay) {
            cleanup(level, phase, output);
            helper.fail("Precondition failed: real PhaseDelay configuration interaction did not change delay");
            return;
        }

        // The real interaction clears PhaseDelay runtime immediately. Before its next scheduled tick,
        // downstream recomputation must not be able to resurrect the old HIGH claim as fresh authority.
        DomainNetwork.recomputeQuartz(level, output);
        PortQuality resetQuality = QuartzTimingLineBlock.quality(level, output);
        boolean resetActive = QuartzTimingLineBlock.active(level, output);
        boolean resetValid = QuartzTimingLineBlock.valid(level, output);
        int resetPeriod = QuartzTimingLineBlock.period(level, output);

        if (resetQuality == PortQuality.VALID || resetActive || resetValid || resetPeriod != 0) {
            cleanup(level, phase, output);
            helper.fail("PhaseDelay configuration reset left an old registered Quartz pulse authoritative"
                    + " | quality=" + resetQuality
                    + " active=" + resetActive
                    + " valid=" + resetValid
                    + " period=" + resetPeriod
                    + " delay=" + beforeDelay + "->" + afterDelay);
            return;
        }

        // Exact recovery: a fresh post-reset drive from the same still-loaded device may reclaim authority.
        DomainNetwork.driveQuartz(level, output, phase, true, 16, true);
        PortQuality recoveredQuality = QuartzTimingLineBlock.quality(level, output);
        boolean recoveredActive = QuartzTimingLineBlock.active(level, output);
        boolean recoveredValid = QuartzTimingLineBlock.valid(level, output);
        int recoveredPeriod = QuartzTimingLineBlock.period(level, output);
        cleanup(level, phase, output);

        if (recoveredQuality != PortQuality.VALID || !recoveredActive || !recoveredValid || recoveredPeriod != 16) {
            helper.fail("Fresh post-reset Quartz drive did not recover exact registered authority"
                    + " | quality=" + recoveredQuality
                    + " active=" + recoveredActive
                    + " valid=" + recoveredValid
                    + " period=" + recoveredPeriod);
            return;
        }
        helper.succeed();
    }

    private static void cleanup(ServerLevel level, BlockPos phase, BlockPos output) {
        DomainDriverRegistry.release(level, "quartz", phase, output);
        if (level.hasChunkAt(output)) level.setBlock(output, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        if (level.hasChunkAt(phase)) level.setBlock(phase, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }
}
