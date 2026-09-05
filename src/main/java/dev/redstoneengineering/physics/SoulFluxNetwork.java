package dev.redstoneengineering.physics;

import dev.redstoneengineering.block.SoulSandReservoirBlock;
import dev.redstoneengineering.block.SoulSoilConduitBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** Minecraft-fictional persistent-state model. Soul soil transports; soul sand stores. */
public final class SoulFluxNetwork {
    private static final String FLUX_KEY = "soul_flux";
    private static final String STORE_KEY = "soul_store";

    private SoulFluxNetwork() {}

    public static boolean isNode(Level level, BlockPos pos) {
        var block = level.getBlockState(pos).getBlock();
        return block instanceof SoulSoilConduitBlock || block instanceof SoulSandReservoirBlock;
    }

    /**
     * Inject a bounded packet into the connected fictional Soul-Flux graph.
     * Conduits cost one unit per hop while reservoirs absorb charge and cost three units.
     */
    public static void inject(ServerLevel level, BlockPos start, int amount) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        int remaining = Math.max(0, amount);

        while (!queue.isEmpty() && seen.size() < NetworkKernel.MAX_NODES && remaining > 0) {
            BlockPos pos = queue.removeFirst();
            if (!seen.add(pos) || !level.hasChunkAt(pos)) continue;

            var block = level.getBlockState(pos).getBlock();
            if (block instanceof SoulSandReservoirBlock) {
                int old = InformationRuntime.value(level, STORE_KEY, pos);
                int added = Math.min(100 - old, remaining);
                InformationRuntime.write(level, STORE_KEY, pos, old + added, 0, true, 100);
                level.updateNeighborsAt(pos, block);
                remaining -= added;
            }

            if (!(block instanceof SoulSoilConduitBlock || block instanceof SoulSandReservoirBlock)) continue;

            InformationRuntime.write(level, FLUX_KEY, pos, Math.max(0, remaining), 0, true, 100);
            level.updateNeighborsAt(pos, block);
            for (Direction direction : Direction.values()) queue.addLast(pos.relative(direction));

            if (block instanceof SoulSoilConduitBlock) remaining = Math.max(0, remaining - 1);
            else remaining = Math.max(0, remaining - 3);
        }

        NetworkKernel.recordScan(level, "soul", seen.size(), seen.size() >= NetworkKernel.MAX_NODES);
    }

    public static int charge(Level level, BlockPos pos) {
        var block = level.getBlockState(pos).getBlock();
        if (block instanceof SoulSandReservoirBlock) return InformationRuntime.value(level, STORE_KEY, pos);
        return InformationRuntime.value(level, FLUX_KEY, pos);
    }

    /** Decay the authoritative value owned by the node at this position by one unit. */
    public static void decay(Level level, BlockPos pos) {
        int charge = charge(level, pos);
        if (charge <= 0) return;
        String key = level.getBlockState(pos).getBlock() instanceof SoulSandReservoirBlock ? STORE_KEY : FLUX_KEY;
        InformationRuntime.write(level, key, pos, charge - 1, 0, true, 100);
    }

    /** Remove all transient/persistent Soul-Flux runtime attached to a removed node position. */
    public static void clear(Level level, BlockPos pos) {
        InformationRuntime.clear(level, FLUX_KEY, pos);
        InformationRuntime.clear(level, STORE_KEY, pos);
    }
}
