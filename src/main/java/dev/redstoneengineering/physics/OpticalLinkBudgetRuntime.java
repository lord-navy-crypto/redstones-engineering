package dev.redstoneengineering.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Observer-only retained evidence derived from the authoritative optical solve.
 *
 * <p>This is not another propagation model. Receiver writes reconstruct the already-applied
 * segment budget from the solver's retained arrival and bounded scan length. Passive optical
 * segments cannot branch without an explicit splitter boundary, so the segment node count is
 * a deterministic path-length witness rather than a second graph search.</p>
 */
public final class OpticalLinkBudgetRuntime {
    private static final String KEY = "optical_link_budget";
    private static final int LAUNCH = 0;
    private static final int COMPONENT_LOSS = 1;
    private static final int PATH_HOPS = 2;
    private static final int FIBER_LOSS = 3;
    private static final int RECEIVED = 4;
    private static final int CHANNEL = 5;
    private static final int VALID = 6;
    private static final int RUNTIME_SIZE = 7;

    private OpticalLinkBudgetRuntime() {}

    public record Snapshot(
            int launchIntensity,
            int componentLoss,
            int pathHops,
            int fiberLoss,
            int receivedIntensity,
            int channel,
            boolean valid
    ) {
        /** Current receiver physics accepts any positive carrier, so threshold is exactly one level. */
        public int receiverMargin() {
            return valid ? Math.max(0, receivedIntensity - 1) : 0;
        }

        public String marginClass() {
            if (!valid || receivedIntensity <= 0) return "FAILED";
            if (receiverMargin() <= 2) return "MARGINAL";
            return "HEALTHY";
        }

        public int totalLoss() {
            return Math.max(0, componentLoss + fiberLoss);
        }
    }

    /**
     * Retain the solved receiver segment budget without traversing the graph again.
     * DomainNetwork currently applies one intensity level of attenuation per 16 path edges.
     */
    public static void recordReceiverSegment(
            Level level,
            BlockPos receiverPos,
            int receivedIntensity,
            int channel,
            boolean valid
    ) {
        NetworkKernel.ScanStats stats = NetworkKernel.stats(level, "optical");
        boolean trustworthy = valid && !stats.lastTruncated() && stats.activeDrivers() <= 1;
        int hops = Math.max(0, stats.lastNodes() - 1);
        int fiberLoss = hops / 16;
        int received = clamp15(receivedIntensity);
        int launch = trustworthy ? clamp15(received + fiberLoss) : 0;
        record(level, receiverPos, launch, 0, hops, received, channel, trustworthy);
    }

    public static void record(
            Level level,
            BlockPos pos,
            int launchIntensity,
            int componentLoss,
            int pathHops,
            int receivedIntensity,
            int channel,
            boolean valid
    ) {
        int launch = clamp15(launchIntensity);
        int component = Math.max(0, Math.min(15, componentLoss));
        int received = clamp15(receivedIntensity);
        int postComponent = Math.max(0, launch - component);
        int fiber = Math.max(0, postComponent - received);
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        runtime[LAUNCH] = launch;
        runtime[COMPONENT_LOSS] = component;
        runtime[PATH_HOPS] = Math.max(0, pathHops);
        runtime[FIBER_LOSS] = fiber;
        runtime[RECEIVED] = received;
        runtime[CHANNEL] = Math.max(0, Math.min(15, channel));
        runtime[VALID] = valid && received > 0 ? 1 : 0;
    }

    public static Snapshot snapshot(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        if (runtime == null || runtime.length < RUNTIME_SIZE) {
            return new Snapshot(0, 0, 0, 0, 0, 0, false);
        }
        return new Snapshot(
                runtime[LAUNCH], runtime[COMPONENT_LOSS], runtime[PATH_HOPS], runtime[FIBER_LOSS],
                runtime[RECEIVED], runtime[CHANNEL], runtime[VALID] == 1);
    }

    public static void clear(Level level, BlockPos pos) {
        RuntimeIntStore.remove(level, KEY, pos);
    }

    private static int clamp15(int value) {
        return Math.max(0, Math.min(15, value));
    }
}
