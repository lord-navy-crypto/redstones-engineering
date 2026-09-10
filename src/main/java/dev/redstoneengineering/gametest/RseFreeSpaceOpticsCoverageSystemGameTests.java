package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.FreeSpaceOpticalReceiverBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.FreeSpaceOpticsKernel;
import dev.redstoneengineering.physics.InformationRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Coverage-completeness regression for the free-space optical medium. */
public final class RseFreeSpaceOpticsCoverageSystemGameTests {
    private static final String TEMPLATE = "empty5x4x5";
    private static final int CHANNEL = 2;
    private static final int POWER = 15;

    private RseFreeSpaceOpticsCoverageSystemGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void unknownIntermediateCoverageInvalidatesRetainedReceiverEvidence(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BeamFixture fixture = findFixture(level, helper.absolutePos(new BlockPos(2, 1, 2)));
        if (fixture == null) {
            helper.fail("Precondition failed: could not find loaded optical endpoints separated by an unavailable LOS chunk");
            return;
        }

        BlockPos source = fixture.source();
        BlockPos receiver = fixture.receiver();

        level.setBlock(receiver, RedstoneEngineering.FREE_SPACE_OPTICAL_RECEIVER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(FreeSpaceOpticalReceiverBlock.CHANNEL, CHANNEL)
                .setValue(DirectionalSignalBlock.OUTPUT, POWER), Block.UPDATE_CLIENTS);

        // Seed the endpoint with the same authoritative envelope produced by a previous complete beam.
        // The regression asks what happens when the next emission can no longer prove the LOS coverage.
        InformationRuntime.write(level, "free_optical", receiver, POWER, CHANNEL, true, 100);

        var before = RedstoneEngineering.FREE_SPACE_OPTICAL_RECEIVER.get()
                .engineeringSnapshot(level, receiver, level.getBlockState(receiver), Direction.WEST)
                .orElseThrow();
        if (before.quality() != PortQuality.VALID || Math.round(before.value()) != POWER) {
            cleanup(level, source, receiver);
            helper.fail("Precondition failed: retained optical receiver evidence was not VALID before coverage loss");
            return;
        }

        if (level.hasChunkAt(fixture.gap())) {
            cleanup(level, source, receiver);
            helper.fail("Precondition failed: optical LOS gap became loaded before the coverage assertion");
            return;
        }

        FreeSpaceOpticsKernel.emit(level, source, Direction.EAST, POWER, CHANNEL);

        var after = RedstoneEngineering.FREE_SPACE_OPTICAL_RECEIVER.get()
                .engineeringSnapshot(level, receiver, level.getBlockState(receiver), Direction.WEST)
                .orElseThrow();
        int output = level.getBlockState(receiver).getValue(DirectionalSignalBlock.OUTPUT);

        cleanup(level, source, receiver);

        if (after.quality() != PortQuality.STALE || Math.round(after.value()) != 0 || output != 0) {
            helper.fail("Unknown free-space optical LOS coverage retained authoritative receiver evidence"
                    + " | quality=" + after.quality()
                    + " value=" + Math.round(after.value())
                    + " output=" + output
                    + " gap=" + fixture.gap());
            return;
        }

        helper.succeed();
    }

    private static BeamFixture findFixture(ServerLevel level, BlockPos origin) {
        for (int chunks = 24; chunks <= 96; chunks += 8) {
            BlockPos candidate = origin.offset(chunks * 16, 0, 0);
            BlockPos source = new BlockPos((candidate.getX() & ~15) + 1, origin.getY(), (candidate.getZ() & ~15) + 8);
            if (level.hasChunkAt(source)) continue;

            for (int distance = 48; distance >= 32; distance--) {
                BlockPos receiver = source.east(distance);
                if (level.hasChunkAt(receiver)) continue;

                // Load exactly the two endpoints synchronously. A useful fixture must retain at least
                // one unavailable intermediate position after both endpoint chunks are present.
                level.getChunkAt(source);
                level.getChunkAt(receiver);
                BlockPos gap = firstUnavailableBetween(level, source, receiver);
                if (gap != null) return new BeamFixture(source, receiver, gap);
            }
        }
        return null;
    }

    private static BlockPos firstUnavailableBetween(ServerLevel level, BlockPos source, BlockPos receiver) {
        int distance = receiver.getX() - source.getX();
        for (int i = 1; i < distance; i++) {
            BlockPos sample = source.east(i);
            if (!level.hasChunkAt(sample)) return sample;
        }
        return null;
    }

    private static void cleanup(ServerLevel level, BlockPos source, BlockPos receiver) {
        InformationRuntime.clear(level, "free_optical", receiver);
        if (level.hasChunkAt(source)) level.setBlock(source, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        if (level.hasChunkAt(receiver)) level.setBlock(receiver, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private record BeamFixture(BlockPos source, BlockPos receiver, BlockPos gap) {}
}
