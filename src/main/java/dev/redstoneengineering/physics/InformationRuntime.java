package dev.redstoneengineering.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Shared transient information envelope for RSE communication media.
 *
 * <p>The envelope answers what information is available without making every medium obey the same
 * propagation physics. Payload, selector, validity, quality and freshness are shared semantics;
 * serial timing, bus contention, radio interference, optical topology and other medium-specific
 * behavior remain owned by their respective network models.</p>
 */
public final class InformationRuntime {
    private static final int RUNTIME_SIZE = 5;
    private static final int VALUE = 0;
    private static final int SELECTOR = 1;
    private static final int VALID = 2;
    private static final int QUALITY = 3;
    private static final int LAST_UPDATE_STAMP = 4;

    private InformationRuntime() {}

    /** Read-only information state. ageTicks=-1 means no write has been observed for this endpoint. */
    public record Snapshot(
            int value,
            int selector,
            boolean valid,
            int qualityPercent,
            int ageTicks
    ) {}

    public static int[] payload(Level level, String medium, BlockPos pos) {
        // [0]=payload, [1]=medium selector, [2]=valid, [3]=quality, [4]=last-update tick + 1.
        return RuntimeIntStore.get(level, "info:" + medium, pos, RUNTIME_SIZE);
    }

    public static void write(
            Level level,
            String medium,
            BlockPos pos,
            int value,
            int selector,
            boolean valid,
            int quality
    ) {
        int[] runtime = payload(level, medium, pos);
        runtime[VALUE] = value;
        runtime[SELECTOR] = selector;
        runtime[VALID] = valid ? 1 : 0;
        runtime[QUALITY] = Math.max(0, Math.min(100, quality));
        runtime[LAST_UPDATE_STAMP] = encodeTick(level.getGameTime());
    }

    public static int value(Level level, String medium, BlockPos pos) {
        return payload(level, medium, pos)[VALUE];
    }

    /** Compatibility alias retained for existing channel/frequency/period callers. */
    public static int aux(Level level, String medium, BlockPos pos) {
        return selector(level, medium, pos);
    }

    public static int selector(Level level, String medium, BlockPos pos) {
        return payload(level, medium, pos)[SELECTOR];
    }

    public static boolean valid(Level level, String medium, BlockPos pos) {
        return payload(level, medium, pos)[VALID] != 0;
    }

    public static int quality(Level level, String medium, BlockPos pos) {
        return payload(level, medium, pos)[QUALITY];
    }

    /**
     * Age of the most recently written information envelope, independent of signal quality.
     * Returns -1 for an endpoint that has never been written.
     */
    public static int ageTicks(Level level, String medium, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, "info:" + medium, pos);
        if (runtime == null || runtime.length <= LAST_UPDATE_STAMP) return -1;
        return decodeAge(level, runtime[LAST_UPDATE_STAMP]);
    }

    /** Observer-neutral snapshot: never creates or resizes runtime physics state. */
    public static Snapshot snapshot(Level level, String medium, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, "info:" + medium, pos);
        if (runtime == null || runtime.length < RUNTIME_SIZE) {
            return new Snapshot(0, 0, false, 0, -1);
        }
        return new Snapshot(
                runtime[VALUE],
                runtime[SELECTOR],
                runtime[VALID] != 0,
                Math.max(0, Math.min(100, runtime[QUALITY])),
                decodeAge(level, runtime[LAST_UPDATE_STAMP])
        );
    }

    public static void clear(Level level, String medium, BlockPos pos) {
        RuntimeIntStore.remove(level, "info:" + medium, pos);
    }

    private static int encodeTick(long gameTime) {
        // Reserve zero for "never written" while keeping the transient layout compact.
        long bounded = Math.min((long) Integer.MAX_VALUE - 1L, Math.max(0L, gameTime));
        return (int) bounded + 1;
    }

    private static int decodeAge(Level level, int stamp) {
        if (stamp <= 0) return -1;
        long lastTick = (long) stamp - 1L;
        long age = Math.max(0L, level.getGameTime() - lastTick);
        return (int) Math.min(Integer.MAX_VALUE, age);
    }
}
