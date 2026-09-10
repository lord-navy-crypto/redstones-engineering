package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.LapisPrecisionSourceBlock;
import dev.redstoneengineering.block.LapisSignalLineBlock;
import dev.redstoneengineering.block.SurfaceTraceBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.DomainDriverRegistry;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.NetworkKernel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Coverage/lifecycle regressions for shared DomainNetwork driver resolution. */
public final class RseDomainNetworkCoverageSystemGameTests {
    private static final String TEMPLATE = "empty5x4x5";
    private static final int STALE_VALUE = 67;
    private static final int RECOVERY_VALUE = 41;

    private RseDomainNetworkCoverageSystemGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void unloadedRegisteredDriverCannotPublishDefinitiveLapisAndLoadedSourceRecovers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos line = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos staleDriver = findRemoteUnloaded(level, line);
        if (staleDriver == null) {
            helper.fail("Precondition failed: could not find an unloaded remote driver position");
            return;
        }

        BlockState isolatedLine = isolatedLine();
        level.setBlock(line, isolatedLine, Block.UPDATE_CLIENTS);

        if (level.hasChunkAt(staleDriver)) {
            cleanup(level, line, null, staleDriver);
            helper.fail("Precondition failed: stale driver chunk became available before registry assertion");
            return;
        }

        // Model the reachable lifecycle hazard: a driver claim survives while its owning device chunk
        // is unavailable. The local segment must not promote that unverifiable claim to authoritative VALID.
        DomainDriverRegistry.claim(level, "lapis", staleDriver, line, STALE_VALUE, 0, 0);
        DomainNetwork.recomputeLapis(level, line);

        PortQuality staleQuality = LapisSignalLineBlock.quality(level, line);
        int staleValue = LapisSignalLineBlock.value(level, line);
        boolean staleValid = LapisSignalLineBlock.valid(level, line);
        int staleSources = LapisSignalLineBlock.sourceCount(level, line);

        if (staleQuality != PortQuality.STALE || staleValue != 0 || staleValid) {
            cleanup(level, line, null, staleDriver);
            helper.fail("Unloaded registered domain driver was trusted as definitive Lapis evidence"
                    + " | quality=" + staleQuality
                    + " value=" + staleValue
                    + " valid=" + staleValid
                    + " sources=" + staleSources
                    + " driver=" + staleDriver);
            return;
        }

        DomainDriverRegistry.release(level, "lapis", staleDriver, line);

        // Recovery uses a normal loaded in-template source and real DomainNetwork recomputation.
        BlockPos source = line.west();
        level.setBlock(source, RedstoneEngineering.LAPIS_PRECISION_SOURCE.get().defaultBlockState()
                .setValue(LapisPrecisionSourceBlock.VALUE, RECOVERY_VALUE), Block.UPDATE_CLIENTS);
        level.setBlock(line, isolatedLine, Block.UPDATE_CLIENTS);
        DomainNetwork.recomputeLapis(level, source);

        PortQuality recoveredQuality = LapisSignalLineBlock.quality(level, line);
        int recoveredValue = LapisSignalLineBlock.value(level, line);
        boolean recoveredValid = LapisSignalLineBlock.valid(level, line);
        int recoveredSources = LapisSignalLineBlock.sourceCount(level, line);
        cleanup(level, line, source, staleDriver);

        if (recoveredQuality != PortQuality.VALID
                || recoveredValue != RECOVERY_VALUE
                || !recoveredValid
                || recoveredSources != 1) {
            helper.fail("Loaded Lapis source did not recover exact domain-network evidence"
                    + " | quality=" + recoveredQuality
                    + " value=" + recoveredValue
                    + " valid=" + recoveredValid
                    + " sources=" + recoveredSources);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void coverageIncompleteDriverDoesNotDoubleCountOrMasqueradeAsBudgetTruncation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos line = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos staleDriver = findRemoteUnloaded(level, line);
        if (staleDriver == null) {
            helper.fail("Precondition failed: could not find an unloaded remote driver for NetworkKernel accounting");
            return;
        }

        level.setBlock(line, isolatedLine(), Block.UPDATE_CLIENTS);
        if (level.hasChunkAt(staleDriver)) {
            cleanup(level, line, null, staleDriver);
            helper.fail("Precondition failed: accounting driver chunk became available before solve");
            return;
        }

        DomainDriverRegistry.claim(level, "lapis", staleDriver, line, STALE_VALUE, 0, 0);
        NetworkKernel.ScanStats before = NetworkKernel.stats(level, "lapis");
        DomainNetwork.recomputeLapis(level, line);
        NetworkKernel.ScanStats after = NetworkKernel.stats(level, "lapis");
        String summary = NetworkKernel.summary(level, "lapis");

        PortQuality quality = LapisSignalLineBlock.quality(level, line);
        long scanDelta = after.scans() - before.scans();
        cleanup(level, line, null, staleDriver);

        if (quality != PortQuality.STALE) {
            helper.fail("Coverage-incomplete solve did not remain fail-closed while auditing NetworkKernel"
                    + " | quality=" + quality + " summary=" + summary);
            return;
        }
        if (scanDelta != 1L) {
            helper.fail("One domain solve was counted as multiple graph scans"
                    + " | before=" + before.scans()
                    + " after=" + after.scans()
                    + " delta=" + scanDelta
                    + " summary=" + summary);
            return;
        }
        if (!summary.contains("COVERAGE-INCOMPLETE") || summary.contains("BUDGET-LIMITED")) {
            helper.fail("Coverage-incomplete driver evidence was mislabeled as a node-budget truncation"
                    + " | summary=" + summary);
            return;
        }
        helper.succeed();
    }

    private static BlockState isolatedLine() {
        return RedstoneEngineering.LAPIS_SIGNAL_LINE.get().defaultBlockState()
                .setValue(SurfaceTraceBlock.WEST, true)
                .setValue(SurfaceTraceBlock.EAST, false)
                .setValue(SurfaceTraceBlock.NORTH, false)
                .setValue(SurfaceTraceBlock.SOUTH, false);
    }

    private static BlockPos findRemoteUnloaded(ServerLevel level, BlockPos origin) {
        for (int chunks = 16; chunks <= 96; chunks += 8) {
            BlockPos[] candidates = {
                    origin.offset(chunks * 16, 0, 0),
                    origin.offset(-chunks * 16, 0, 0),
                    origin.offset(0, 0, chunks * 16),
                    origin.offset(0, 0, -chunks * 16)
            };
            for (BlockPos candidate : candidates) {
                BlockPos centered = new BlockPos((candidate.getX() & ~15) + 8, origin.getY(), (candidate.getZ() & ~15) + 8);
                if (!level.hasChunkAt(centered)) return centered;
            }
        }
        return null;
    }

    private static void cleanup(ServerLevel level, BlockPos line, BlockPos source, BlockPos staleDriver) {
        DomainDriverRegistry.release(level, "lapis", staleDriver, line);
        if (source != null && level.hasChunkAt(source)) level.setBlock(source, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        if (level.hasChunkAt(line)) level.setBlock(line, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }
}
