package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.IronCoreBlock;
import dev.redstoneengineering.block.PermanentMagnetBlock;
import dev.redstoneengineering.physics.MagneticPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Coverage-completeness regression for free-space magnetic actuation. */
public final class RseMagneticCoverageSystemGameTests {
    private static final String TEMPLATE = "empty5x4x5";
    private static final int APPLIED_RADIUS = 2;
    private static final int MAGNETIZE_THRESHOLD = 8;

    private RseMagneticCoverageSystemGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void incompleteAppliedFieldCannotMagnetizeSoftCoreAndCompleteCoverageRecovers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MagneticFixture fixture = findFixture(level, helper.absolutePos(new BlockPos(2, 1, 2)));
        if (fixture == null) {
            helper.fail("Precondition failed: could not find a loaded magnetic endpoint chunk beside unavailable radius-2 coverage");
            return;
        }

        BlockPos magnet = fixture.magnet();
        BlockPos core = fixture.core();

        level.setBlock(magnet, RedstoneEngineering.PERMANENT_MAGNET.get().defaultBlockState()
                .setValue(PermanentMagnetBlock.STRENGTH, 15), Block.UPDATE_CLIENTS);

        MagneticPhysics.FieldSample before = MagneticPhysics.fieldSample(level, core, APPLIED_RADIUS);
        if (before.complete() || before.field() < MAGNETIZE_THRESHOLD || level.hasChunkAt(fixture.unknown())) {
            cleanup(level, magnet, core);
            helper.fail("Precondition failed: magnetic fixture did not produce strong partial field with incomplete coverage"
                    + " | field=" + before.field()
                    + " scanned=" + before.scannedCells() + "/" + before.expectedCells()
                    + " unknown=" + fixture.unknown());
            return;
        }

        level.setBlock(core, RedstoneEngineering.IRON_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        boolean magnetizedUnderUnknownCoverage = level.getBlockState(core).getValue(IronCoreBlock.MAGNETIZED);
        if (magnetizedUnderUnknownCoverage) {
            cleanup(level, magnet, core);
            helper.fail("Incomplete free-space magnetic coverage produced definitive remanent magnetization"
                    + " | field=" + before.field()
                    + " scanned=" + before.scannedCells() + "/" + before.expectedCells()
                    + " unknown=" + fixture.unknown());
            return;
        }

        // Restore the missing evidence without changing the source/core geometry. A real neighbor
        // update must then allow the same endpoint to recover from incomplete evidence and magnetize.
        level.getChunkAt(fixture.unknown());
        MagneticPhysics.FieldSample recovered = MagneticPhysics.fieldSample(level, core, APPLIED_RADIUS);
        if (!recovered.complete() || recovered.field() < MAGNETIZE_THRESHOLD) {
            cleanup(level, magnet, core);
            helper.fail("Precondition failed: restored magnetic coverage was not complete and strong"
                    + " | field=" + recovered.field()
                    + " scanned=" + recovered.scannedCells() + "/" + recovered.expectedCells());
            return;
        }

        level.setBlock(magnet, level.getBlockState(magnet)
                .setValue(PermanentMagnetBlock.STRENGTH, 14), Block.UPDATE_ALL);
        boolean recoveredMagnetized = level.getBlockState(core).getValue(IronCoreBlock.MAGNETIZED);

        cleanup(level, magnet, core);

        if (!recoveredMagnetized) {
            helper.fail("Complete free-space magnetic evidence did not recover normal soft-core magnetization");
            return;
        }

        helper.succeed();
    }

    private static MagneticFixture findFixture(ServerLevel level, BlockPos origin) {
        for (int chunks = 24; chunks <= 96; chunks += 8) {
            BlockPos candidate = origin.offset(chunks * 16, 0, 0);
            int chunkMinX = candidate.getX() & ~15;
            int chunkMinZ = candidate.getZ() & ~15;
            BlockPos core = new BlockPos(chunkMinX + 15, origin.getY(), chunkMinZ + 8);
            BlockPos magnet = core.west();
            BlockPos unknown = core.east();

            if (level.hasChunkAt(core) || level.hasChunkAt(unknown)) continue;
            level.getChunkAt(core);
            if (!level.hasChunkAt(unknown)) return new MagneticFixture(magnet, core, unknown);
        }
        return null;
    }

    private static void cleanup(ServerLevel level, BlockPos magnet, BlockPos core) {
        if (level.hasChunkAt(magnet)) level.setBlock(magnet, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        if (level.hasChunkAt(core)) level.setBlock(core, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private record MagneticFixture(BlockPos magnet, BlockPos core, BlockPos unknown) {}
}
