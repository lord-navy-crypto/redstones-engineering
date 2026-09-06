package dev.redstoneengineering.diagnostics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Bounded transient server telemetry for PID trend visualization.
 *
 * <p>The controller remains authoritative. This store only retains already-computed SP/PV/OUT
 * samples for observer UIs; it never computes control output, samples on the client, or drives
 * physics. Retention is insertion-ordered so read-only inspection cannot affect eviction order.</p>
 */
public final class PidTelemetryStore {
    public static final int MAX_CONTROLLERS_PER_LEVEL = 256;
    public static final int MAX_SAMPLES_PER_CONTROLLER = 32;

    private static final Map<Level, LinkedHashMap<Long, ArrayDeque<Integer>>> DATA = new WeakHashMap<>();

    private PidTelemetryStore() {
    }

    public static synchronized void record(Level level, BlockPos pos, int setpoint, int processValue, int controlOutput) {
        if (level == null || level.isClientSide || pos == null) return;
        LinkedHashMap<Long, ArrayDeque<Integer>> byPos = DATA.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
        long key = pos.asLong();
        ArrayDeque<Integer> samples = byPos.get(key);
        if (samples == null) {
            if (byPos.size() >= MAX_CONTROLLERS_PER_LEVEL) {
                Iterator<Long> oldest = byPos.keySet().iterator();
                if (oldest.hasNext()) {
                    oldest.next();
                    oldest.remove();
                }
            }
            samples = new ArrayDeque<>(MAX_SAMPLES_PER_CONTROLLER);
            byPos.put(key, samples);
        }
        samples.addLast(pack(setpoint, processValue, controlOutput));
        while (samples.size() > MAX_SAMPLES_PER_CONTROLLER) samples.removeFirst();
    }

    /** Oldest to newest immutable packed samples. */
    public static synchronized List<Integer> snapshot(Level level, BlockPos pos) {
        LinkedHashMap<Long, ArrayDeque<Integer>> byPos = DATA.get(level);
        if (byPos == null) return List.of();
        ArrayDeque<Integer> samples = byPos.get(pos.asLong());
        return samples == null ? List.of() : List.copyOf(samples);
    }

    public static int pack(int setpoint, int processValue, int controlOutput) {
        return clamp4(setpoint) | (clamp4(processValue) << 4) | (clamp4(controlOutput) << 8);
    }

    public static int setpoint(int packed) {
        return packed < 0 ? -1 : packed & 15;
    }

    public static int processValue(int packed) {
        return packed < 0 ? -1 : (packed >>> 4) & 15;
    }

    public static int controlOutput(int packed) {
        return packed < 0 ? -1 : (packed >>> 8) & 15;
    }

    public static synchronized void clear(Level level, BlockPos pos) {
        LinkedHashMap<Long, ArrayDeque<Integer>> byPos = DATA.get(level);
        if (byPos == null) return;
        byPos.remove(pos.asLong());
        if (byPos.isEmpty()) DATA.remove(level);
    }

    public static synchronized void clear(Level level) {
        DATA.remove(level);
    }

    private static int clamp4(int value) {
        return Math.max(0, Math.min(15, value));
    }
}
