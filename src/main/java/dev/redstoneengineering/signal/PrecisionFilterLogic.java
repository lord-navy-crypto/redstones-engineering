package dev.redstoneengineering.signal;

/** Pure asymmetric slew-rate limiter logic used by PrecisionFilterBlock and semantic verification. */
public final class PrecisionFilterLogic {
    public static final int MIN_RATE = 1;
    public static final int MAX_RATE = 4;

    private PrecisionFilterLogic() {}

    public static int boundedRate(int rate) {
        return Math.max(MIN_RATE, Math.min(MAX_RATE, rate));
    }

    public static int step(int current, int target, int riseRate, int fallRate) {
        int boundedCurrent = Math.max(0, Math.min(15, current));
        int boundedTarget = Math.max(0, Math.min(15, target));
        int rise = boundedRate(riseRate);
        int fall = boundedRate(fallRate);
        if (boundedTarget > boundedCurrent) return Math.min(boundedTarget, boundedCurrent + rise);
        if (boundedTarget < boundedCurrent) return Math.max(boundedTarget, boundedCurrent - fall);
        return boundedCurrent;
    }

    public static int settleTicks(int current, int target, int riseRate, int fallRate) {
        int error = target - current;
        if (error == 0) return 0;
        int rate = error > 0 ? boundedRate(riseRate) : boundedRate(fallRate);
        int magnitude = Math.abs(error);
        return (magnitude + rate - 1) / rate;
    }
}
