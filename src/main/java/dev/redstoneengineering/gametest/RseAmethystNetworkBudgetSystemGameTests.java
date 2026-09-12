package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.AmethystResonanceDustBlock;
import dev.redstoneengineering.block.AmethystResonatorBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.NetworkKernel;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** Strict runtime proof that bounded Amethyst resonance scans fail closed and recover cleanly. */
public final class RseAmethystNetworkBudgetSystemGameTests {
    private static final String TEMPLATE = "empty5x4x5";
    private static final int SHORT_COMPONENT_NODES = 16;

    private RseAmethystNetworkBudgetSystemGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void truncatedAmethystScanMustNotPublishPartialResonance(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 1, 2));
        if (!level.hasChunkAt(anchor)) {
            helper.fail("Precondition failed: GameTest anchor chunk is not loaded");
            return;
        }

        List<BlockPos> path = planarSnake(anchor);
        BlockPos source = path.get(0);
        BlockPos observed = path.get(1);
        if (!allLoaded(level, path, helper)) return;

        placeSource(level, source);
        for (int i = 1; i < path.size(); i++) {
            level.setBlock(path.get(i), RedstoneEngineering.AMETHYST_RESONANCE_DUST.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        wireDustPath(level, path);

        DomainNetwork.recomputeAmethyst(level, observed);
        NetworkKernel.ScanStats stats = NetworkKernel.stats(level, "amethyst");
        ResonanceEvidence evidence = evidence(level, path);
        cleanupPath(level, path);

        if (!stats.lastTruncated() || stats.lastNodes() != NetworkKernel.MAX_NODES) {
            helper.fail("Precondition failed: 135-node Amethyst component did not hit the 128-node budget"
                    + " | nodes=" + stats.lastNodes() + " truncated=" + stats.lastTruncated());
            return;
        }
        if (evidence.quality() != PortQuality.STALE || evidence.amplitude() != 0 || evidence.frequency() != 0) {
            helper.fail("Budget-truncated Amethyst solve published partial resonance instead of fail-closed STALE"
                    + " | quality=" + evidence.quality() + " status=" + evidence.status()
                    + " frequency=" + evidence.frequency() + " amplitude=" + evidence.amplitude()
                    + " pathNodes=" + path.size());
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 140)
    public static void amethystResonanceRecoversAfterBudgetTruncationClears(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 1, 2));
        if (!level.hasChunkAt(anchor)) {
            helper.fail("Precondition failed: GameTest anchor chunk is not loaded");
            return;
        }

        List<BlockPos> path = planarSnake(anchor);
        if (!allLoaded(level, path, helper)) return;
        BlockPos source = path.get(0);
        BlockPos observed = path.get(1);

        placeSource(level, source);
        for (int i = 1; i < SHORT_COMPONENT_NODES; i++) {
            level.setBlock(path.get(i), RedstoneEngineering.AMETHYST_RESONANCE_DUST.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        wireDustPath(level, path.subList(0, SHORT_COMPONENT_NODES));
        DomainNetwork.recomputeAmethyst(level, observed);

        NetworkKernel.ScanStats initialStats = NetworkKernel.stats(level, "amethyst");
        ResonanceEvidence initial = evidence(level, path);
        if (initialStats.lastTruncated()
                || initial.quality() != PortQuality.VALID
                || initial.status() != AmethystResonanceDustBlock.ResonanceStatus.ACTIVE
                || initial.frequency() != 6 || initial.amplitude() <= 0) {
            cleanupPath(level, path);
            helper.fail("Precondition failed: short Amethyst component did not establish trusted active resonance"
                    + " | nodes=" + initialStats.lastNodes() + " truncated=" + initialStats.lastTruncated()
                    + " quality=" + initial.quality() + " status=" + initial.status()
                    + " frequency=" + initial.frequency() + " amplitude=" + initial.amplitude());
            return;
        }

        for (int i = SHORT_COMPONENT_NODES; i < path.size(); i++) {
            level.setBlock(path.get(i), RedstoneEngineering.AMETHYST_RESONANCE_DUST.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        wireDustPath(level, path);
        DomainNetwork.recomputeAmethyst(level, observed);

        NetworkKernel.ScanStats truncatedStats = NetworkKernel.stats(level, "amethyst");
        ResonanceEvidence truncated = evidence(level, path);
        if (!truncatedStats.lastTruncated() || truncatedStats.lastNodes() != NetworkKernel.MAX_NODES
                || truncated.quality() != PortQuality.STALE
                || truncated.status() != AmethystResonanceDustBlock.ResonanceStatus.STALE
                || truncated.frequency() != 0 || truncated.amplitude() != 0) {
            cleanupPath(level, path);
            helper.fail("Amethyst runtime failed to clear trusted resonance when the component exceeded the scan budget"
                    + " | nodes=" + truncatedStats.lastNodes() + " truncated=" + truncatedStats.lastTruncated()
                    + " quality=" + truncated.quality() + " status=" + truncated.status()
                    + " frequency=" + truncated.frequency() + " amplitude=" + truncated.amplitude());
            return;
        }

        for (int i = path.size() - 1; i >= SHORT_COMPONENT_NODES; i--) {
            level.setBlock(path.get(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        wireDustPath(level, path.subList(0, SHORT_COMPONENT_NODES));
        DomainNetwork.recomputeAmethyst(level, observed);

        NetworkKernel.ScanStats recoveredStats = NetworkKernel.stats(level, "amethyst");
        ResonanceEvidence recovered = evidence(level, path);
        cleanupPath(level, path);
        if (recoveredStats.lastTruncated()
                || recovered.quality() != PortQuality.VALID
                || recovered.status() != AmethystResonanceDustBlock.ResonanceStatus.ACTIVE
                || recovered.frequency() != initial.frequency()
                || recovered.amplitude() != initial.amplitude()) {
            helper.fail("Amethyst resonance did not recover exactly after returning below the scan budget"
                    + " | nodes=" + recoveredStats.lastNodes() + " truncated=" + recoveredStats.lastTruncated()
                    + " initial=" + initial.frequency() + "/" + initial.amplitude()
                    + " recovered=" + recovered.frequency() + "/" + recovered.amplitude()
                    + " quality=" + recovered.quality() + " status=" + recovered.status());
            return;
        }
        helper.succeed();
    }

    private static ResonanceEvidence evidence(ServerLevel level, List<BlockPos> path) {
        BlockPos observed = path.get(1);
        BlockState observedState = level.getBlockState(observed);
        Direction snapshotSide = horizontalDirection(observed, path.get(2));
        PortQuality quality = RedstoneEngineering.AMETHYST_RESONANCE_DUST.get()
                .engineeringSnapshot(level, observed, observedState, snapshotSide)
                .orElseThrow().quality();
        return new ResonanceEvidence(
                quality,
                AmethystResonanceDustBlock.status(level, observed),
                AmethystResonanceDustBlock.frequency(level, observed),
                AmethystResonanceDustBlock.amplitude(level, observed));
    }

    private static void placeSource(ServerLevel level, BlockPos source) {
        level.setBlock(source, RedstoneEngineering.AMETHYST_RESONATOR.get().defaultBlockState()
                .setValue(AmethystResonatorBlock.FREQUENCY, 6)
                .setValue(AmethystResonatorBlock.AMPLITUDE, 15), Block.UPDATE_CLIENTS);
        RuntimeIntStore.get(level, "amethyst_resonator", source, 1)[0] = 1;
    }

    private static boolean allLoaded(ServerLevel level, List<BlockPos> path, GameTestHelper helper) {
        for (BlockPos pos : path) {
            if (!level.hasChunkAt(pos)) {
                helper.fail("Precondition failed: single-chunk Amethyst path unexpectedly crosses unloaded terrain at " + pos);
                return false;
            }
        }
        return true;
    }

    private static List<BlockPos> planarSnake(BlockPos anchor) {
        int minX = anchor.getX() & ~15;
        int minZ = anchor.getZ() & ~15;
        int y = Math.min(anchor.getY() + 16, anchor.getY() + 16);
        List<BlockPos> path = new ArrayList<>(135);
        for (int row = 0; row < 8; row++) {
            int z = minZ + row * 2;
            if ((row & 1) == 0) {
                for (int x = minX; x <= minX + 15; x++) path.add(new BlockPos(x, y, z));
                if (row < 7) path.add(new BlockPos(minX + 15, y, z + 1));
            } else {
                for (int x = minX + 15; x >= minX; x--) path.add(new BlockPos(x, y, z));
                if (row < 7) path.add(new BlockPos(minX, y, z + 1));
            }
        }
        return path;
    }

    private static void wireDustPath(ServerLevel level, List<BlockPos> path) {
        for (int i = 1; i < path.size(); i++) {
            BlockPos pos = path.get(i);
            BlockState state = RedstoneEngineering.AMETHYST_RESONANCE_DUST.get().defaultBlockState();
            state = setDustArm(state, horizontalDirection(pos, path.get(i - 1)), true);
            if (i + 1 < path.size()) state = setDustArm(state, horizontalDirection(pos, path.get(i + 1)), true);
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }

    private static Direction horizontalDirection(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (dx == 1 && dz == 0) return Direction.EAST;
        if (dx == -1 && dz == 0) return Direction.WEST;
        if (dx == 0 && dz == 1) return Direction.SOUTH;
        if (dx == 0 && dz == -1) return Direction.NORTH;
        throw new IllegalArgumentException("Non-adjacent planar Amethyst nodes: " + from + " -> " + to);
    }

    private static BlockState setDustArm(BlockState state, Direction direction, boolean value) {
        return switch (direction) {
            case NORTH -> state.setValue(AmethystResonanceDustBlock.NORTH, value);
            case EAST -> state.setValue(AmethystResonanceDustBlock.EAST, value);
            case SOUTH -> state.setValue(AmethystResonanceDustBlock.SOUTH, value);
            case WEST -> state.setValue(AmethystResonanceDustBlock.WEST, value);
            default -> throw new IllegalArgumentException("Vertical direction is invalid for this planar Amethyst regression: " + direction);
        };
    }

    private static void cleanupPath(ServerLevel level, List<BlockPos> path) {
        for (int i = path.size() - 1; i >= 0; i--) {
            level.setBlock(path.get(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private record ResonanceEvidence(
            PortQuality quality,
            AmethystResonanceDustBlock.ResonanceStatus status,
            int frequency,
            int amplitude) {}
}
