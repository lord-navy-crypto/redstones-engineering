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
                InformationRuntime.Snapshot stored = InformationRuntime.snapshot(level, STORE_KEY, pos);
                int old = Math.max(0, Math.min(100, stored.value()));
                int added = Math.min(100 - old, remaining);
                InformationRuntime.write(level, STORE_KEY, pos, old + added, 0, true, 100);
                level.updateNeighborsAt(pos, block);
                remaining -= added;
            }

            if (!(block instanceof SoulSoilConduitBlock || block instanceof SoulSandReservoirBlock)) continue;

            if (block instanceof SoulSoilConduitBlock) {
                InformationRuntime.write(level, FLUX_KEY, pos, remaining, 0, true, 100);
                level.updateNeighborsAt(pos, block);
            }

            for (Direction direction : Direction.values()) queue.addLast(pos.relative(direction));

            if (block instanceof SoulSoilConduitBlock) remaining = Math.max(0, remaining - 1);
            else remaining = Math.max(0, remaining - 3);
        }

        NetworkKernel.recordScan(level, "soul", seen.size(), seen.size() >= NetworkKernel.MAX_NODES);
    }

    /** Initialize a newly placed storage node as a known, valid empty reservoir. */
    public static void initializeReservoir(Level level, BlockPos pos) {
        InformationRuntime.Snapshot stored = InformationRuntime.snapshot(level, STORE_KEY, pos);
        if (stored.ageTicks() < 0) {
            InformationRuntime.write(level, STORE_KEY, pos, 0, 0, true, 100);
        }
    }

    /** Observer-neutral authoritative charge snapshot for the node type at this position. */
    public static InformationRuntime.Snapshot chargeSnapshot(Level level, BlockPos pos) {
        var block = level.getBlockState(pos).getBlock();
        String key = block instanceof SoulSandReservoirBlock ? STORE_KEY : FLUX_KEY;
        return InformationRuntime.snapshot(level, key, pos);
    }

    public static int charge(Level level, BlockPos pos) {
        return Math.max(0, Math.min(100, chargeSnapshot(level, pos).value()));
    }

    /** Decay transient conduit packets to absence while retaining reservoir zero as known empty storage. */
    public static void decay(Level level, BlockPos pos) {
        var block = level.getBlockState(pos).getBlock();
        InformationRuntime.Snapshot snapshot = chargeSnapshot(level, pos);
        int charge = Math.max(0, Math.min(100, snapshot.value()));

        if (block instanceof SoulSandReservoirBlock) {
            if (snapshot.ageTicks() < 0) {
                initializeReservoir(level, pos);
                return;
            }
            if (charge <= 0) return;
            InformationRuntime.write(level, STORE_KEY, pos, charge - 1, 0, true, 100);
            return;
        }

        if (snapshot.ageTicks() < 0) return;
        if (charge <= 1) {
            InformationRuntime.clear(level, FLUX_KEY, pos);
        } else {
            InformationRuntime.write(level, FLUX_KEY, pos, charge - 1, 0, true, 100);
        }
    }

    /** Remove all transient/persistent Soul-Flux runtime attached to a removed node position. */
    public static void clear(Level level, BlockPos pos) {
        InformationRuntime.clear(level, FLUX_KEY, pos);
        InformationRuntime.clear(level, STORE_KEY, pos);
    }
}
