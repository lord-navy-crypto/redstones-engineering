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
 *
 * <p>Storage is level-wide, but plant-facing consumers should use {@link #within(Level, SystemEventScope)}
 * so unrelated systems in the same dimension do not contaminate incident analysis.</p>
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

    /** Oldest to newest events whose source lies inside the supplied plant scope. */
    public static synchronized List<SystemEventRecord> within(Level level, SystemEventScope scope) {
        if (scope == null) return List.of();
        ArrayDeque<SystemEventRecord> timeline = EVENTS.get(level);
        if (timeline == null || timeline.isEmpty()) return List.of();
        ArrayList<SystemEventRecord> result = new ArrayList<>();
        for (SystemEventRecord event : timeline) {
            if (scope.contains(event.source())) result.add(event);
        }
        return List.copyOf(result);
    }

    /** Oldest to newest among the returned tail. */
    public static synchronized List<SystemEventRecord> recent(Level level, int maxEvents) {
        return tail(snapshot(level), maxEvents);
    }

    /** Oldest to newest among the returned scoped tail. */
    public static synchronized List<SystemEventRecord> recentWithin(Level level, SystemEventScope scope, int maxEvents) {
        return tail(within(level, scope), maxEvents);
    }

    private static List<SystemEventRecord> tail(List<SystemEventRecord> events, int maxEvents) {
        if (maxEvents <= 0 || events.isEmpty()) return List.of();
        int from = Math.max(0, events.size() - maxEvents);
        return List.copyOf(events.subList(from, events.size()));
    }

    public static synchronized int size(Level level) {
        ArrayDeque<SystemEventRecord> timeline = EVENTS.get(level);
        return timeline == null ? 0 : timeline.size();
    }

    public static synchronized void clear(Level level) {
        EVENTS.remove(level);
    }
}
