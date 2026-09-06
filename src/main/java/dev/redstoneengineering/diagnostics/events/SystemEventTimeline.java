package dev.redstoneengineering.diagnostics.events;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Bounded transient system-wide event timeline.
 *
 * <p>Events are evidence emitted by authoritative server transitions. The timeline never drives
 * process physics, controller output, topology solving, or alarm/interlock state. It is deliberately
 * transient in this milestone; durable world-save semantics require a separate persistence contract.</p>
 */
public final class SystemEventTimeline {
    public static final int MAX_EVENTS_PER_LEVEL = 256;
    private static final Map<Level, ArrayDeque<SystemEventRecord>> EVENTS = new WeakHashMap<>();

    private SystemEventTimeline() {}

    public static synchronized void record(
            Level level,
            BlockPos source,
            SystemEventKind kind,
            int severity,
            String code,
            String detail
    ) {
        if (level == null || level.isClientSide || source == null || kind == null) return;
        ArrayDeque<SystemEventRecord> timeline = EVENTS.computeIfAbsent(level, ignored -> new ArrayDeque<>());
        timeline.addLast(new SystemEventRecord(level.getGameTime(), kind, source.immutable(), severity, code, detail));
        while (timeline.size() > MAX_EVENTS_PER_LEVEL) timeline.removeFirst();
    }

    /** Oldest to newest, immutable copy. */
    public static synchronized List<SystemEventRecord> snapshot(Level level) {
        ArrayDeque<SystemEventRecord> timeline = EVENTS.get(level);
        return timeline == null ? List.of() : List.copyOf(timeline);
    }

    /** Oldest to newest among the returned tail. */
    public static synchronized List<SystemEventRecord> recent(Level level, int maxEvents) {
        if (maxEvents <= 0) return List.of();
        ArrayDeque<SystemEventRecord> timeline = EVENTS.get(level);
        if (timeline == null || timeline.isEmpty()) return List.of();
        int skip = Math.max(0, timeline.size() - maxEvents);
        ArrayList<SystemEventRecord> result = new ArrayList<>(Math.min(maxEvents, timeline.size()));
        int index = 0;
        for (SystemEventRecord event : timeline) {
            if (index++ >= skip) result.add(event);
        }
        return List.copyOf(result);
    }

    public static synchronized int size(Level level) {
        ArrayDeque<SystemEventRecord> timeline = EVENTS.get(level);
        return timeline == null ? 0 : timeline.size();
    }

    public static synchronized void clear(Level level) {
        EVENTS.remove(level);
    }
}
