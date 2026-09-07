package dev.redstoneengineering.metrology;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Level-scoped transient metrology state.
 *
 * Like RuntimeIntStore, this intentionally keeps high-cardinality diagnostics
 * out of BlockState. The weak level key prevents world/session retention.
 */
public final class MetrologyStore {
    private static final Map<Level, Map<String, Map<Long, MetrologyTracker>>> DATA = new WeakHashMap<>();

    private MetrologyStore() {}

    /** Authoritative sampling path. Creating a tracker is allowed only when physics actually samples. */
    public static synchronized MetrologyTracker tracker(
            Level level,
            String channel,
            BlockPos pos,
            double resolution,
            long staleAfterTicks
    ) {
        Map<String, Map<Long, MetrologyTracker>> byChannel = DATA.computeIfAbsent(level, ignored -> new HashMap<>());
        Map<Long, MetrologyTracker> byPos = byChannel.computeIfAbsent(channel, ignored -> new HashMap<>());
        return byPos.computeIfAbsent(pos.asLong(), ignored -> new MetrologyTracker(resolution, staleAfterTicks));
    }

    /**
     * Observer-only lookup. Unlike {@link #tracker}, this never allocates a level,
     * channel, position entry or tracker merely because a HUD/UI asks for a snapshot.
     */
    static synchronized MetrologyTracker peek(Level level, String channel, BlockPos pos) {
        Map<String, Map<Long, MetrologyTracker>> byChannel = DATA.get(level);
        if (byChannel == null) return null;
        Map<Long, MetrologyTracker> byPos = byChannel.get(channel);
        if (byPos == null) return null;
        return byPos.get(pos.asLong());
    }

    /** Read-only cardinality evidence used by observer-neutrality regression tests. */
    public static synchronized int entryCount(Level level) {
        Map<String, Map<Long, MetrologyTracker>> byChannel = DATA.get(level);
        if (byChannel == null) return 0;
        int total = 0;
        for (Map<Long, MetrologyTracker> byPos : byChannel.values()) total += byPos.size();
        return total;
    }

    public static synchronized void remove(Level level, String channel, BlockPos pos) {
        Map<String, Map<Long, MetrologyTracker>> byChannel = DATA.get(level);
        if (byChannel == null) return;
        Map<Long, MetrologyTracker> byPos = byChannel.get(channel);
        if (byPos == null) return;
        byPos.remove(pos.asLong());
        if (byPos.isEmpty()) byChannel.remove(channel);
        if (byChannel.isEmpty()) DATA.remove(level);
    }

    public static synchronized void clear(Level level) {
        DATA.remove(level);
    }
}
