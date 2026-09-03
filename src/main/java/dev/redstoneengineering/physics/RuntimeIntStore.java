package dev.redstoneengineering.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Lightweight transient runtime storage for alpha devices.
 *
 * High-cardinality measurements must NOT be encoded into BlockState. This cache
 * stores derived runtime values without multiplying model/state variants.
 * Persistent player configuration belongs in small BlockState properties or,
 * when it grows, a dedicated BlockEntity.
 */
public final class RuntimeIntStore {
    private static final Map<Level, Map<String, Map<Long, int[]>>> DATA = new WeakHashMap<>();

    private RuntimeIntStore() {}

    public static synchronized int[] get(Level level, String key, BlockPos pos, int size) {
        if (size < 1) throw new IllegalArgumentException("runtime state size must be >= 1");
        Map<String, Map<Long, int[]>> byKey = DATA.computeIfAbsent(level, l -> new HashMap<>());
        Map<Long, int[]> byPos = byKey.computeIfAbsent(key, k -> new HashMap<>());
        int[] existing = byPos.get(pos.asLong());
        if (existing != null) {
            if (existing.length != size) {
                // Development-safe migration: replace stale alpha runtime layout.
                existing = new int[size];
                byPos.put(pos.asLong(), existing);
            }
            return existing;
        }
        int[] created = new int[size];
        byPos.put(pos.asLong(), created);
        return created;
    }

    /**
     * Read-only snapshot for visualization/diagnostics.
     *
     * Unlike get(), this never creates, resizes or exposes the backing array.
     * That one-way boundary is what keeps render FPS and renderer lifecycle from
     * becoming an accidental physics input.
     */
    public static synchronized int[] peek(Level level, String key, BlockPos pos) {
        Map<String, Map<Long, int[]>> byKey = DATA.get(level);
        if (byKey == null) return null;
        Map<Long, int[]> byPos = byKey.get(key);
        if (byPos == null) return null;
        int[] existing = byPos.get(pos.asLong());
        return existing == null ? null : existing.clone();
    }

    public static synchronized void remove(Level level, String key, BlockPos pos) {
        Map<String, Map<Long, int[]>> byKey = DATA.get(level);
        if (byKey == null) return;
        Map<Long, int[]> byPos = byKey.get(key);
        if (byPos == null) return;
        byPos.remove(pos.asLong());
        if (byPos.isEmpty()) byKey.remove(key);
        if (byKey.isEmpty()) DATA.remove(level);
    }

    public static synchronized int entryCount(Level level) {
        Map<String, Map<Long, int[]>> byKey = DATA.get(level);
        if (byKey == null) return 0;
        int total = 0;
        for (Map<Long, int[]> byPos : byKey.values()) total += byPos.size();
        return total;
    }

    public static synchronized void clear(Level level) {
        DATA.remove(level);
    }
}
