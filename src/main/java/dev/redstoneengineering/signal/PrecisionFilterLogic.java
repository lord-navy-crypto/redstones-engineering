package dev.redstoneengineering.signal;

/** Pure asymmetric slew-rate limiter logic used by PrecisionFilterBlock and semantic verification. */
public final class PrecisionFilterLogic {
    private PrecisionFilterLogic() {}

    public static int step(int current, int target, int riseRate, int fallRate) {
        int boundedCurrent = Math.max(0, Math.min(15, current));
        int boundedTarget = Math.max(0, Math.min(15, target));
        int rise = Math.max(1, Math.min(4, riseRate));
        int fall = Math.max(1, Math.min(4, fallRate));
        if (boundedTarget > boundedCurrent) return Math.min(boundedTarget, boundedCurrent + rise);
        if (boundedTarget < boundedCurrent) return Math.max(boundedTarget, boundedCurrent - fall);
        return boundedCurrent;
    }

    public static int settleTicks(int current, int target, int riseRate, int fallRate) {
        int error = target - current;
        if (error == 0) return 0;
        int rate = error > 0 ? Math.max(1, Math.min(4, riseRate)) : Math.max(1, Math.min(4, fallRate));
        int magnitude = Math.abs(error);
        return (magnitude + rate - 1) / rate;
    }
}
