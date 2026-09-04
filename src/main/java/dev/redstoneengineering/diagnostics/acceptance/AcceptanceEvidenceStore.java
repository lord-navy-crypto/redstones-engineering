package dev.redstoneengineering.diagnostics.acceptance;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Bounded transient storage for explicitly captured acceptance records.
 *
 * This is diagnostic runtime state, not BlockState and not durable save data. A level retains at
 * most 256 controller timelines and each controller retains at most 8 records. The explicit limits
 * prevent inspection history from becoming an unbounded memory sink while a future dedicated
 * persistence device can be introduced without changing controller physics.
 */
public final class AcceptanceEvidenceStore {
    public static final int MAX_CONTROLLERS_PER_LEVEL = 256;
    public static final int MAX_RECORDS_PER_CONTROLLER = AcceptanceEvidenceTimeline.DEFAULT_CAPACITY;

    private static final Map<Level, LinkedHashMap<Long, AcceptanceEvidenceTimeline>> DATA = new WeakHashMap<>();

    private AcceptanceEvidenceStore() {}

    public static synchronized AcceptanceEvidenceRecord capture(
            Level level,
            BlockPos pos,
            long gameTick,
            int tuningPreset,
            EngineeringAcceptanceSnapshot acceptance
    ) {
        LinkedHashMap<Long, AcceptanceEvidenceTimeline> byPos = DATA.computeIfAbsent(
                level, ignored -> new LinkedHashMap<>(16, 0.75f, true));
        long key = pos.asLong();
        AcceptanceEvidenceTimeline timeline = byPos.get(key);
        if (timeline == null) {
            if (byPos.size() >= MAX_CONTROLLERS_PER_LEVEL) {
                Iterator<Long> oldest = byPos.keySet().iterator();
                if (oldest.hasNext()) {
                    oldest.next();
                    oldest.remove();
                }
            }
            timeline = new AcceptanceEvidenceTimeline(MAX_RECORDS_PER_CONTROLLER);
            byPos.put(key, timeline);
        }
        return timeline.append(gameTick, tuningPreset, acceptance);
    }

    public static synchronized List<AcceptanceEvidenceRecord> history(Level level, BlockPos pos) {
        AcceptanceEvidenceTimeline timeline = timeline(level, pos);
        return timeline == null ? List.of() : timeline.records();
    }

    public static synchronized Optional<AcceptanceEvidenceRecord> latest(Level level, BlockPos pos) {
        AcceptanceEvidenceTimeline timeline = timeline(level, pos);
        return timeline == null ? Optional.empty() : timeline.latest();
    }

    public static synchronized Optional<AcceptanceEvidenceComparison> compareLatestToPrevious(
            Level level,
            BlockPos pos
    ) {
        AcceptanceEvidenceTimeline timeline = timeline(level, pos);
        return timeline == null ? Optional.empty() : timeline.compareLatestToPrevious();
    }

    public static synchronized int controllerCount(Level level) {
        LinkedHashMap<Long, AcceptanceEvidenceTimeline> byPos = DATA.get(level);
        return byPos == null ? 0 : byPos.size();
    }

    public static synchronized void clear(Level level, BlockPos pos) {
        LinkedHashMap<Long, AcceptanceEvidenceTimeline> byPos = DATA.get(level);
        if (byPos == null) return;
        byPos.remove(pos.asLong());
        if (byPos.isEmpty()) DATA.remove(level);
    }

    public static synchronized void clear(Level level) {
        DATA.remove(level);
    }

    private static AcceptanceEvidenceTimeline timeline(Level level, BlockPos pos) {
        LinkedHashMap<Long, AcceptanceEvidenceTimeline> byPos = DATA.get(level);
        return byPos == null ? null : byPos.get(pos.asLong());
    }
}
