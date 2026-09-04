package dev.redstoneengineering.diagnostics;

/**
 * Small deterministic fault-injection primitives for engineering tests.
 *
 * All helpers preserve an explicit signal domain and are suitable for repeatable
 * baseline-versus-disturbed commissioning runs. They do not own plant physics.
 */
public final class FaultInjectionModel {
    private FaultInjectionModel() {}

    public static int addBias(int value, int bias, int min, int max) {
        return clamp(value + bias, min, max);
    }

    public static int addDeterministicNoise(int value, int amplitude, long tick, long seed, int min, int max) {
        int a = Math.max(0, amplitude);
        if (a == 0) return clamp(value, min, max);
        long mixed = mix64(tick ^ seed);
        int span = a * 2 + 1;
        int delta = (int) Math.floorMod(mixed, (long) span) - a;
        return clamp(value + delta, min, max);
    }

    /** Returns zero on a scheduled dropout tick; otherwise preserves the bounded signal. */
    public static int applyDropout(int value, int everyTicks, long tick, int min, int max) {
        if (everyTicks > 0 && tick > 0 && Math.floorMod(tick, (long) everyTicks) == 0L) return 0;
        return clamp(value, min, max);
    }

    /** Models an actuator/output ceiling without changing the command source. */
    public static int applySaturation(int command, int ceiling, int min, int max) {
        int boundedCeiling = clamp(ceiling, min, max);
        return Math.min(clamp(command, min, max), boundedCeiling);
    }

    public static int latencyTicks(int requestedTicks, int maximumTicks) {
        return clamp(requestedTicks, 0, Math.max(0, maximumTicks));
    }

    private static int clamp(int value, int min, int max) {
        if (min > max) throw new IllegalArgumentException("min must be <= max");
        return Math.max(min, Math.min(max, value));
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdl;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53l;
        return z ^ (z >>> 33);
    }
}
