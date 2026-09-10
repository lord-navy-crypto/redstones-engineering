package dev.redstoneengineering.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Active output-driver registry for isolated RSE signal segments.
 *
 * The key includes both the device position and the output-start position so a
 * legitimate multi-output device (for example the optical splitter) can own
 * more than one independent output claim. A single segment is still strictly
 * single-driver in the current alpha; conflicts are surfaced instead of being
 * resolved by scheduler/tick order.
 */
public final class DomainDriverRegistry {
    private static final Map<Level, Map<String, Map<DriverKey, Claim>>> CLAIMS = new WeakHashMap<>();

    private DomainDriverRegistry() {}

    private record DriverKey(long driver, long output) {}
    public record Claim(BlockPos driverPos, BlockPos outputStart, int a, int b, int c, String blockClass) {}

    public static synchronized void claim(Level level, String domain, BlockPos driverPos, BlockPos outputStart, int a, int b, int c) {
        Map<String, Map<DriverKey, Claim>> byDomain = CLAIMS.computeIfAbsent(level, l -> new HashMap<>());
        Map<DriverKey, Claim> byDriver = byDomain.computeIfAbsent(domain, d -> new HashMap<>());
        String blockClass = level.hasChunkAt(driverPos) ? level.getBlockState(driverPos).getBlock().getClass().getName() : "";
        DriverKey key = new DriverKey(driverPos.asLong(), outputStart.asLong());
        byDriver.put(key, new Claim(driverPos.immutable(), outputStart.immutable(), a, b, c, blockClass));
    }

    /** Release one output claim while preserving other outputs of the same device. */
    public static synchronized void release(Level level, String domain, BlockPos driverPos, BlockPos outputStart) {
        Map<String, Map<DriverKey, Claim>> byDomain = CLAIMS.get(level);
        if (byDomain == null) return;
        Map<DriverKey, Claim> byDriver = byDomain.get(domain);
        if (byDriver == null) return;
        byDriver.remove(new DriverKey(driverPos.asLong(), outputStart.asLong()));
        if (byDriver.isEmpty()) byDomain.remove(domain);
        if (byDomain.isEmpty()) CLAIMS.remove(level);
    }

    public static synchronized void releaseAll(Level level, String domain, BlockPos driverPos) {
        Map<String, Map<DriverKey, Claim>> byDomain = CLAIMS.get(level);
        if (byDomain == null) return;
        Map<DriverKey, Claim> byDriver = byDomain.get(domain);
        if (byDriver == null) return;
        byDriver.entrySet().removeIf(e -> e.getValue().driverPos().equals(driverPos));
        if (byDriver.isEmpty()) byDomain.remove(domain);
        if (byDomain.isEmpty()) CLAIMS.remove(level);
    }

    public static synchronized void releaseAll(Level level, BlockPos driverPos) {
        Map<String, Map<DriverKey, Claim>> byDomain = CLAIMS.get(level);
        if (byDomain == null) return;
        for (Iterator<Map.Entry<String, Map<DriverKey, Claim>>> it = byDomain.entrySet().iterator(); it.hasNext();) {
            Map<DriverKey, Claim> byDriver = it.next().getValue();
            byDriver.entrySet().removeIf(e -> e.getValue().driverPos().equals(driverPos));
            if (byDriver.isEmpty()) it.remove();
        }
        if (byDomain.isEmpty()) CLAIMS.remove(level);
    }

    /**
     * Return verifiable active claims whose output medium belongs to the supplied segment.
     *
     * <p>An unavailable driver is not the same thing as an absent driver. Claims whose
     * output belongs to this segment are retained while their owning chunk is unavailable,
     * excluded from numerical resolution, and mark the current domain solve incomplete.
     * This keeps observer reads neutral: no unknown chunk is loaded and no unverifiable
     * lifecycle state is destroyed merely because a segment was inspected.</p>
     */
    public static synchronized List<Claim> activeClaims(Level level, String domain, Set<BlockPos> segmentNodes) {
        Map<String, Map<DriverKey, Claim>> byDomain = CLAIMS.get(level);
        if (byDomain == null) return List.of();
        Map<DriverKey, Claim> byDriver = byDomain.get(domain);
        if (byDriver == null) return List.of();

        List<Claim> out = new ArrayList<>();
        boolean coverageComplete = true;
        for (Iterator<Map.Entry<DriverKey, Claim>> it = byDriver.entrySet().iterator(); it.hasNext();) {
            Claim claim = it.next().getValue();
            if (!segmentNodes.contains(claim.outputStart())) continue;

            BlockPos driver = claim.driverPos();
            if (!level.hasChunkAt(driver)) {
                coverageComplete = false;
                continue;
            }

            String now = level.getBlockState(driver).getBlock().getClass().getName();
            if (!now.equals(claim.blockClass())) {
                it.remove();
                continue;
            }
            out.add(claim);
        }
        if (!coverageComplete) {
            NetworkKernel.recordScan(level, domain, segmentNodes.size(), true);
        }
        if (byDriver.isEmpty()) byDomain.remove(domain);
        if (byDomain.isEmpty()) CLAIMS.remove(level);
        return List.copyOf(out);
    }

    public static synchronized void clear(Level level) { CLAIMS.remove(level); }
}
