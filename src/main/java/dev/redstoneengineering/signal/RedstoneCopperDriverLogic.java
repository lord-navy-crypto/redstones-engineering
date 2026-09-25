package dev.redstoneengineering.signal;

/**
 * Pure finite-slew transfer model for the Redstone -> Copper driver.
 *
 * <p>The legacy BlockState preset remains a compatibility affordance. Exact rise/fall slew,
 * numerical voltage bounds and derived ramp timing all share this authority.</p>
 */
public final class RedstoneCopperDriverLogic {
    public static final int MIN_VOLTAGE = 0;
    public static final int MAX_VOLTAGE = 15;
    public static final int MIN_SLEW = 1;
    public static final int MAX_SLEW = 15;
    public static final int MIN_LEGACY_SLEW_MODE = 0;
    public static final int MAX_LEGACY_SLEW_MODE = 2;
    public static final int DEFAULT_LEGACY_SLEW_MODE = 1;
    public static final int LEGACY_SLEW_SLOW = 1;
    public static final int LEGACY_SLEW_NORMAL = 2;
    public static final int LEGACY_SLEW_FAST = 4;
    public static final int CONTROL_TICK_TICKS = 1;

    private RedstoneCopperDriverLogic() {}

    public static int boundedVoltage(int voltage) {
        return Math.max(MIN_VOLTAGE, Math.min(MAX_VOLTAGE, voltage));
    }

    public static int boundedSlew(int slew) {
        return Math.max(MIN_SLEW, Math.min(MAX_SLEW, slew));
    }

    public static int slewForLegacyMode(int mode) {
        return switch (Math.max(MIN_LEGACY_SLEW_MODE, Math.min(MAX_LEGACY_SLEW_MODE, mode))) {
            case MIN_LEGACY_SLEW_MODE -> LEGACY_SLEW_SLOW;
            case MAX_LEGACY_SLEW_MODE -> LEGACY_SLEW_FAST;
            default -> LEGACY_SLEW_NORMAL;
        };
    }

    public static int moveToward(int current, int target, int riseSlew, int fallSlew) {
        int value = boundedVoltage(current);
        int boundedTarget = boundedVoltage(target);
        int rise = boundedSlew(riseSlew);
        int fall = boundedSlew(fallSlew);
        if (value < boundedTarget) return Math.min(boundedTarget, value + rise);
        if (value > boundedTarget) return Math.max(boundedTarget, value - fall);
        return value;
    }

    public static int trackingError(int target, int actual) {
        return Math.abs(boundedVoltage(target) - boundedVoltage(actual));
    }

    public static int fullScaleRampTicks(int slew) {
        int bounded = boundedSlew(slew);
        return (MAX_VOLTAGE + bounded - 1) / bounded;
    }
}
