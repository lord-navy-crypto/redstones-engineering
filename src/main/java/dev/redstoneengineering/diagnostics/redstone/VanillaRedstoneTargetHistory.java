package dev.redstoneengineering.diagnostics.redstone;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Read-only bounded target-level timeline derived from the same public NeighborNotifyEvent surface
 * as the existing VRE runtime telemetry. It never schedules ticks or mutates vanilla state.
 */
public final class VanillaRedstoneTargetHistory {
    public static final int MAX_EVENTS_PER_LEVEL = 2048;
    public static final int DISPLAY_SAMPLES = 24;

    private static final Map<ServerLevel, LevelHistory> LEVELS = new WeakHashMap<>();

    private VanillaRedstoneTargetHistory() {}

    public static synchronized void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockState state = event.getState();
        if (!VanillaRedstoneEngineeringProfile.isVanillaRedstoneTarget(state)) return;

        BlockPos pos = event.getPos().immutable();
        int signal = VanillaRedstoneTargetSnapshot.observedSignal(state);
        int kind = VanillaRedstoneTargetSnapshot.kindOf(state);
        LevelHistory history = LEVELS.computeIfAbsent(level, ignored -> new LevelHistory());
        Integer previousSignal = history.lastSignal.put(pos, signal);
        boolean transition = previousSignal != null && previousSignal != signal;
        history.events.addLast(new EventSample(level.getGameTime(), pos, kind, signal, transition));
        while (history.events.size() > MAX_EVENTS_PER_LEVEL) history.events.removeFirst();
        if (history.lastSignal.size() > 1024) history.lastSignal.clear();
    }

    /** Returns only observations emitted by the exact target block, oldest to newest. */
    public static synchronized Snapshot inspect(ServerLevel level, BlockPos target, long requestedWindowTicks) {
        long window = Math.max(1L, Math.min(VanillaRedstoneRuntimeTelemetry.MAX_WINDOW_TICKS, requestedWindowTicks));
        long minimumTick = level.getGameTime() - window;
        LevelHistory history = LEVELS.get(level);
        if (history == null) return Snapshot.empty();

        ArrayList<EventSample> matched = new ArrayList<>();
        for (EventSample sample : history.events) {
            if (sample.gameTime() >= minimumTick && sample.source().equals(target)) matched.add(sample);
        }
        int from = Math.max(0, matched.size() - DISPLAY_SAMPLES);
        List<EventSample> retained = matched.subList(from, matched.size());
        int[] values = new int[DISPLAY_SAMPLES];
        long[] times = new long[DISPLAY_SAMPLES];
        boolean[] transitions = new boolean[DISPLAY_SAMPLES];
        java.util.Arrays.fill(values, -1);
        java.util.Arrays.fill(times, -1L);
        int padding = DISPLAY_SAMPLES - retained.size();
        int transitionCount = 0;
        for (int i = 0; i < retained.size(); i++) {
            EventSample sample = retained.get(i);
            int slot = padding + i;
            values[slot] = sample.signalValue();
            times[slot] = sample.gameTime();
            transitions[slot] = sample.transition();
            if (sample.transition()) transitionCount++;
        }
        long latest = retained.isEmpty() ? -1L : retained.get(retained.size() - 1).gameTime();
        long first = retained.isEmpty() ? -1L : retained.get(0).gameTime();
        int span = retained.size() < 2 ? 0 : (int) Math.min(Integer.MAX_VALUE, Math.max(0L, latest - first));
        int kind = retained.isEmpty() ? -1 : retained.get(retained.size() - 1).kind();
        return new Snapshot(retained.size(), latest, span, transitionCount, kind, values, times, transitions);
    }

    public static synchronized void clear(ServerLevel level) {
        LEVELS.remove(level);
    }

    public record Snapshot(
            int count,
            long latestGameTime,
            int timeSpanTicks,
            int transitionCount,
            int latestKind,
            int[] values,
            long[] sampleTimes,
            boolean[] transitions
    ) {
        private static Snapshot empty() {
            int[] values = new int[DISPLAY_SAMPLES];
            long[] times = new long[DISPLAY_SAMPLES];
            java.util.Arrays.fill(values, -1);
            java.util.Arrays.fill(times, -1L);
            return new Snapshot(0, -1L, 0, 0, -1, values, times, new boolean[DISPLAY_SAMPLES]);
        }
    }

    private record EventSample(long gameTime, BlockPos source, int kind, int signalValue, boolean transition) {}

    private static final class LevelHistory {
        private final ArrayDeque<EventSample> events = new ArrayDeque<>();
        private final HashMap<BlockPos, Integer> lastSignal = new HashMap<>();
    }
}
