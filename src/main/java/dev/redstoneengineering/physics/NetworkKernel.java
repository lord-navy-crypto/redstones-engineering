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

    /**
     * lastTruncated remains the compatibility fail-closed bit consumed by existing domain endpoints.
     * It is true when either the graph budget was exhausted or authoritative coverage was incomplete.
     * Cause-specific diagnostics are exposed separately so accounting does not confuse the two cases.
     */
    public record ScanStats(long scans, int lastNodes, int maxObservedNodes, boolean lastTruncated, long truncatedScans, int activeDrivers, boolean driverConflict) {}

    /** Record one actual bounded graph traversal. */
    public static synchronized void recordScan(Level level, String domain, int nodes, boolean truncated) {
        Map<String, MutableStats> byDomain = STATS.computeIfAbsent(level, l -> new HashMap<>());
        MutableStats s = byDomain.computeIfAbsent(domain, d -> new MutableStats());
        s.scans++;
        s.lastNodes = nodes;
        s.maxObservedNodes = Math.max(s.maxObservedNodes, nodes);
        s.lastBudgetTruncated = truncated;
        s.lastCoverageIncomplete = false;
        s.lastTruncated = truncated;
        if (truncated) s.truncatedScans++;
    }

    /**
     * Mark the current solve incomplete because required evidence could not be verified.
     * This is not another graph traversal, so scan and budget-truncation counters are unchanged.
     */
    public static synchronized void markCoverageIncomplete(Level level, String domain) {
        Map<String, MutableStats> byDomain = STATS.computeIfAbsent(level, l -> new HashMap<>());
        MutableStats s = byDomain.computeIfAbsent(domain, d -> new MutableStats());
        s.lastCoverageIncomplete = true;
        s.lastTruncated = true;
    }

    public static synchronized ScanStats stats(Level level, String domain) {
        Map<String, MutableStats> byDomain = STATS.get(level);
        MutableStats s = byDomain == null ? null : byDomain.get(domain);
        if (s == null) return new ScanStats(0, 0, 0, false, 0, 0, false);
        return new ScanStats(s.scans, s.lastNodes, s.maxObservedNodes, s.lastTruncated, s.truncatedScans, s.activeDrivers, s.driverConflict);
    }

    public static synchronized boolean lastBudgetTruncated(Level level, String domain) {
        Map<String, MutableStats> byDomain = STATS.get(level);
        MutableStats s = byDomain == null ? null : byDomain.get(domain);
        return s != null && s.lastBudgetTruncated;
    }

    public static synchronized boolean lastCoverageIncomplete(Level level, String domain) {
        Map<String, MutableStats> byDomain = STATS.get(level);
        MutableStats s = byDomain == null ? null : byDomain.get(domain);
        return s != null && s.lastCoverageIncomplete;
    }

    public static synchronized String summary(Level level, String domain) {
        ScanStats s = stats(level, domain);
        boolean budgetTruncated = lastBudgetTruncated(level, domain);
        boolean coverageIncomplete = lastCoverageIncomplete(level, domain);
        return "nodes=" + s.lastNodes()
                + "/" + MAX_NODES
                + (budgetTruncated ? " | BUDGET-LIMITED" : "")
                + (coverageIncomplete ? " | COVERAGE-INCOMPLETE" : "")
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
        boolean lastBudgetTruncated;
        boolean lastCoverageIncomplete;
        long truncatedScans;
        int activeDrivers;
        boolean driverConflict;
    }
}
