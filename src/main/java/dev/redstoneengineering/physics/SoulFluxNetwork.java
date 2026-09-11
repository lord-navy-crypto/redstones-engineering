package dev.redstoneengineering.physics;

import dev.redstoneengineering.block.SoulSandReservoirBlock;
import dev.redstoneengineering.block.SoulSoilConduitBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
     * Persistent reservoir charge is committed only after the bounded traversal proves complete.
     */
    public static void inject(ServerLevel level, BlockPos start, int amount) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Map<BlockPos, Integer> pendingFlux = new HashMap<>();
        Map<BlockPos, Integer> pendingStore = new HashMap<>();
        queue.add(start);
        int remaining = Math.max(0, amount);
        boolean truncated = false;

        while (!queue.isEmpty() && remaining > 0) {
            BlockPos pos = queue.removeFirst();
            if (!level.hasChunkAt(pos) || !isNode(level, pos) || seen.contains(pos)) continue;
            if (seen.size() >= NetworkKernel.MAX_NODES) {
                truncated = true;
                break;
            }
            seen.add(pos.immutable());

            var block = level.getBlockState(pos).getBlock();
            if (block instanceof SoulSandReservoirBlock) {
                InformationRuntime.Snapshot stored = InformationRuntime.snapshot(level, STORE_KEY, pos);
                int old = Math.max(0, Math.min(100, stored.value()));
                int added = Math.min(100 - old, remaining);
                pendingStore.put(pos.immutable(), old + added);
                remaining -= added;
            }

            if (block instanceof SoulSoilConduitBlock) {
                pendingFlux.put(pos.immutable(), remaining);
            }

            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (!seen.contains(next)) queue.addLast(next);
            }

            if (block instanceof SoulSoilConduitBlock) remaining = Math.max(0, remaining - 1);
            else remaining = Math.max(0, remaining - 3);
        }

        NetworkKernel.recordScan(level, "soul", seen.size(), truncated);
        if (truncated) {
            // The incomplete traversal cannot authorize new transient flux or reservoir mutation.
            // Preserve previously committed reservoir charge and invalidate only touched transient paths.
            for (BlockPos pos : pendingFlux.keySet()) {
                InformationRuntime.write(level, FLUX_KEY, pos, 0, 0, false, 0);
                level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
            }
            return;
        }

        for (Map.Entry<BlockPos, Integer> entry : pendingStore.entrySet()) {
            BlockPos pos = entry.getKey();
            InformationRuntime.write(level, STORE_KEY, pos, entry.getValue(), 0, true, 100);
            level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
        }
        for (Map.Entry<BlockPos, Integer> entry : pendingFlux.entrySet()) {
            BlockPos pos = entry.getKey();
            InformationRuntime.write(level, FLUX_KEY, pos, entry.getValue(), 0, true, 100);
            level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
        }
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
