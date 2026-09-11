package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.HydroacousticNetwork;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.NetworkKernel;
import dev.redstoneengineering.physics.SoulFluxNetwork;
import dev.redstoneengineering.physics.ThermalPulseKernel;
import dev.redstoneengineering.physics.VibrationNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** Boundary regressions for packet-style bounded propagation kernels. */
public final class RsePropagationBudgetBoundaryGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RsePropagationBudgetBoundaryGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void mechanicalExactBudgetIsCompleteButNextNodeFailsClosed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<BlockPos> nodes = compactVolume(helper.absolutePos(new BlockPos(2, 1, 2)), 129);
        if (!allLoaded(level, nodes, helper)) return;
        place(level, nodes, 128, RedstoneEngineering.SLIME_VIBRATION_CONDUIT.get());
        BlockPos source = nodes.get(0).relative(Direction.WEST);

        VibrationNetwork.propagate(level, source, 15, 5, Direction.EAST);
        NetworkKernel.ScanStats exact = NetworkKernel.stats(level, "mechanical");
        if (exact.lastTruncated() || exact.lastNodes() != NetworkKernel.MAX_NODES
                || !InformationRuntime.valid(level, "mech_wave", nodes.get(0))) {
            cleanup(level, nodes);
            helper.fail("Exactly 128 mechanical nodes must be a complete valid solve"
                    + " | nodes=" + exact.lastNodes() + " truncated=" + exact.lastTruncated());
            return;
        }

        level.setBlock(nodes.get(128), RedstoneEngineering.SLIME_VIBRATION_CONDUIT.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        VibrationNetwork.propagate(level, source, 15, 5, Direction.EAST);
        NetworkKernel.ScanStats overflow = NetworkKernel.stats(level, "mechanical");
        boolean valid = InformationRuntime.valid(level, "mech_wave", nodes.get(0));
        cleanup(level, nodes);
        if (!overflow.lastTruncated() || overflow.lastNodes() != NetworkKernel.MAX_NODES || valid) {
            helper.fail("129-node mechanical solve did not fail closed at the 128-node budget"
                    + " | nodes=" + overflow.lastNodes() + " truncated=" + overflow.lastTruncated()
                    + " valid=" + valid);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void hydroExactBudgetIsCompleteButNextNodeFailsClosed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<BlockPos> nodes = compactVolume(helper.absolutePos(new BlockPos(2, 1, 2)), 129);
        if (!allLoaded(level, nodes, helper)) return;
        place(level, nodes, 128, RedstoneEngineering.HYDROACOUSTIC_TUBE.get());
        BlockPos source = nodes.get(0).relative(Direction.WEST);

        HydroacousticNetwork.propagate(level, source, 15, 5, Direction.EAST);
        NetworkKernel.ScanStats exact = NetworkKernel.stats(level, "hydro");
        if (exact.lastTruncated() || exact.lastNodes() != NetworkKernel.MAX_NODES
                || !InformationRuntime.valid(level, "hydro", nodes.get(0))) {
            cleanup(level, nodes);
            helper.fail("Exactly 128 hydro nodes must be a complete valid solve"
                    + " | nodes=" + exact.lastNodes() + " truncated=" + exact.lastTruncated());
            return;
        }

        level.setBlock(nodes.get(128), RedstoneEngineering.HYDROACOUSTIC_TUBE.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        HydroacousticNetwork.propagate(level, source, 15, 5, Direction.EAST);
        NetworkKernel.ScanStats overflow = NetworkKernel.stats(level, "hydro");
        boolean valid = InformationRuntime.valid(level, "hydro", nodes.get(0));
        cleanup(level, nodes);
        if (!overflow.lastTruncated() || overflow.lastNodes() != NetworkKernel.MAX_NODES || valid) {
            helper.fail("129-node hydro solve did not fail closed at the 128-node budget"
                    + " | nodes=" + overflow.lastNodes() + " truncated=" + overflow.lastTruncated()
                    + " valid=" + valid);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void thermalExactBudgetIsCompleteButNextNodeFailsClosed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<BlockPos> nodes = compactVolume(helper.absolutePos(new BlockPos(2, 1, 2)), 129);
        if (!allLoaded(level, nodes, helper)) return;
        place(level, nodes, 128, RedstoneEngineering.PHONON_CONDUIT.get());
        BlockPos source = nodes.get(0).relative(Direction.WEST);

        ThermalPulseKernel.send(level, source, 15, Direction.EAST);
        NetworkKernel.ScanStats exact = NetworkKernel.stats(level, "phonon");
        if (exact.lastTruncated() || exact.lastNodes() != NetworkKernel.MAX_NODES
                || !InformationRuntime.valid(level, "thermal_pulse", nodes.get(0))) {
            cleanup(level, nodes);
            helper.fail("Exactly 128 thermal nodes must be a complete valid solve"
                    + " | nodes=" + exact.lastNodes() + " truncated=" + exact.lastTruncated());
            return;
        }

        level.setBlock(nodes.get(128), RedstoneEngineering.PHONON_CONDUIT.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        ThermalPulseKernel.send(level, source, 15, Direction.EAST);
        NetworkKernel.ScanStats overflow = NetworkKernel.stats(level, "phonon");
        boolean valid = InformationRuntime.valid(level, "thermal_pulse", nodes.get(0));
        cleanup(level, nodes);
        if (!overflow.lastTruncated() || overflow.lastNodes() != NetworkKernel.MAX_NODES || valid) {
            helper.fail("129-node thermal solve did not fail closed at the 128-node budget"
                    + " | nodes=" + overflow.lastNodes() + " truncated=" + overflow.lastTruncated()
                    + " valid=" + valid);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void soulTruncationDoesNotCommitPendingReservoirCharge(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<BlockPos> nodes = compactVolume(helper.absolutePos(new BlockPos(2, 1, 2)), 129);
        if (!allLoaded(level, nodes, helper)) return;

        level.setBlock(nodes.get(0), RedstoneEngineering.SOUL_SAND_RESERVOIR.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        for (int i = 1; i < nodes.size(); i++) {
            level.setBlock(nodes.get(i), RedstoneEngineering.SOUL_SOIL_CONDUIT.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        InformationRuntime.write(level, "soul_store", nodes.get(0), 40, 0, true, 100);

        SoulFluxNetwork.inject(level, nodes.get(0), 500);
        NetworkKernel.ScanStats stats = NetworkKernel.stats(level, "soul");
        InformationRuntime.Snapshot reservoir = InformationRuntime.snapshot(level, "soul_store", nodes.get(0));
        cleanup(level, nodes);

        if (!stats.lastTruncated() || stats.lastNodes() != NetworkKernel.MAX_NODES) {
            helper.fail("129-node Soul-Flux graph did not report bounded truncation"
                    + " | nodes=" + stats.lastNodes() + " truncated=" + stats.lastTruncated());
            return;
        }
        if (!reservoir.valid() || reservoir.value() != 40) {
            helper.fail("Truncated Soul-Flux injection committed speculative reservoir charge"
                    + " | stored=" + reservoir.value() + " valid=" + reservoir.valid());
            return;
        }
        helper.succeed();
    }

    private static List<BlockPos> compactVolume(BlockPos anchor, int count) {
        int baseX = anchor.getX();
        int baseY = anchor.getY() + 24;
        int baseZ = anchor.getZ();
        List<BlockPos> result = new ArrayList<>(count);
        for (int x = 0; x < 5 && result.size() < count; x++) {
            for (int y = 0; y < 5 && result.size() < count; y++) {
                for (int z = 0; z < 6 && result.size() < count; z++) {
                    result.add(new BlockPos(baseX + x, baseY + y, baseZ + z));
                }
            }
        }
        return result;
    }

    private static void place(ServerLevel level, List<BlockPos> nodes, int count, Block block) {
        for (int i = 0; i < count; i++) {
            level.setBlock(nodes.get(i), block.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static boolean allLoaded(ServerLevel level, List<BlockPos> nodes, GameTestHelper helper) {
        for (BlockPos pos : nodes) {
            if (!level.hasChunkAt(pos)) {
                helper.fail("Precondition failed: propagation budget fixture crossed unloaded terrain at " + pos);
                return false;
            }
        }
        return true;
    }

    private static void cleanup(ServerLevel level, List<BlockPos> nodes) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            level.setBlock(nodes.get(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
