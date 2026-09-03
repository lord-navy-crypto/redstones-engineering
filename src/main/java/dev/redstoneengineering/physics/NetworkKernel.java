package dev.redstoneengineering.physics;

import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Shared safety/diagnostic kernel for all RSE graph-based transmission domains.
 *
 * Design rules:
 * - network scans are hard-bounded;
 * - unloaded chunks are never traversed by callers;
 * - runtime measurements are not encoded into high-cardinality BlockStates;
 * - the most recent scan statistics stay available for diagnostics.
 */
public final class NetworkKernel {
    public static final int MAX_NODES = 128;

    private static final Map<Level, Map<String, MutableStats>> STATS = new WeakHashMap<>();

    private NetworkKernel() {}

    public record ScanStats(long scans, int lastNodes, int maxObservedNodes, boolean lastTruncated, long truncatedScans, int activeDrivers, boolean driverConflict) {}

    public static synchronized void recordScan(Level level, String domain, int nodes, boolean truncated) {
        Map<String, MutableStats> byDomain = STATS.computeIfAbsent(level, l -> new HashMap<>());
        MutableStats s = byDomain.computeIfAbsent(domain, d -> new MutableStats());
        s.scans++;
        s.lastNodes = nodes;
        s.maxObservedNodes = Math.max(s.maxObservedNodes, nodes);
        s.lastTruncated = truncated;
        if (truncated) s.truncatedScans++;
    }

    public static synchronized ScanStats stats(Level level, String domain) {
        Map<String, MutableStats> byDomain = STATS.get(level);
        MutableStats s = byDomain == null ? null : byDomain.get(domain);
        if (s == null) return new ScanStats(0, 0, 0, false, 0, 0, false);
        return new ScanStats(s.scans, s.lastNodes, s.maxObservedNodes, s.lastTruncated, s.truncatedScans, s.activeDrivers, s.driverConflict);
    }

    public static synchronized String summary(Level level, String domain) {
        ScanStats s = stats(level, domain);
        return "nodes=" + s.lastNodes()
                + "/" + MAX_NODES
                + (s.lastTruncated() ? " | BUDGET-LIMITED" : "")
                + (s.driverConflict() ? " | DRIVER-CONFLICT(" + s.activeDrivers() + ")" : "")
                + " | scans=" + s.scans();
    }


    public static synchronized void recordDriverState(Level level, String domain, int activeDrivers) {
        Map<String, MutableStats> byDomain = STATS.computeIfAbsent(level, l -> new HashMap<>());
        MutableStats s = byDomain.computeIfAbsent(domain, d -> new MutableStats());
        s.activeDrivers = Math.max(0, activeDrivers);
        s.driverConflict = activeDrivers > 1;
    }

    public static synchronized void clear(Level level) {
        STATS.remove(level);
        DomainDriverRegistry.clear(level);
    }

    private static final class MutableStats {
        long scans;
        int lastNodes;
        int maxObservedNodes;
        boolean lastTruncated;
        long truncatedScans;
        int activeDrivers;
        boolean driverConflict;
    }
}
