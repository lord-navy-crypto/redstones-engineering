package dev.redstoneengineering.signal;

/**
 * Pure transfer model for the Signal Conditioner.
 *
 * <p>The block keeps a broad legacy PARAM BlockState for world compatibility, while each mode
 * exposes a smaller engineering-valid range here. Server runtime, HMI readback and semantic
 * verification all share this contract so an out-of-range legacy value cannot create fake
 * parameter depth.</p>
 */
public final class SignalConditionerLogic {
    public static final int MODE_SCALE = 0;
    public static final int MODE_OFFSET = 1;
    public static final int MODE_CLAMP = 2;
    public static final int MODE_THRESHOLD = 3;
    public static final int MODE_DEADBAND = 4;
    public static final int MODE_ATTENUATE = 5;

    private SignalConditionerLogic() {}

    public static int minParameter(int mode) {
        return switch (mode) {
            case MODE_OFFSET -> 0;
            case MODE_ATTENUATE -> 2;
            default -> 1;
        };
    }

    public static int maxParameter(int mode) {
        return switch (mode) {
            case MODE_SCALE, MODE_DEADBAND, MODE_ATTENUATE -> 4;
            case MODE_OFFSET -> 10;
            case MODE_CLAMP, MODE_THRESHOLD -> 15;
            default -> 15;
        };
    }

    public static int defaultParameter(int mode) {
        return switch (mode) {
            case MODE_SCALE -> 2;
            case MODE_OFFSET -> 5;
            case MODE_CLAMP -> 10;
            case MODE_THRESHOLD -> 8;
            case MODE_DEADBAND -> 2;
            case MODE_ATTENUATE -> 2;
            default -> 1;
        };
    }

    public static int boundedParameter(int mode, int parameter) {
        return Math.max(minParameter(mode), Math.min(maxParameter(mode), parameter));
    }

    public static int cycleParameter(int mode, int parameter, int delta) {
        int min = minParameter(mode);
        int max = maxParameter(mode);
        int current = boundedParameter(mode, parameter);
        int step = Integer.signum(delta);
        if (step == 0) return current;
        int next = current + step;
        if (next > max) return min;
        if (next < min) return max;
        return next;
    }

    public static int apply(int input, int previousOutput, int mode, int parameter) {
        int x = EngineeringSignal.clamp(input);
        int yPrevious = EngineeringSignal.clamp(previousOutput);
        int p = boundedParameter(mode, parameter);
        return switch (mode) {
            case MODE_SCALE -> EngineeringSignal.clamp((int) Math.round(x * (double) p));
            case MODE_OFFSET -> EngineeringSignal.clamp(x + (p - 5));
            case MODE_CLAMP -> Math.min(x, p);
            case MODE_THRESHOLD -> x >= p ? x : 0;
            case MODE_DEADBAND -> Math.abs(x - yPrevious) >= p ? x : yPrevious;
            case MODE_ATTENUATE -> EngineeringSignal.clamp((int) Math.round(x / (double) p));
            default -> x;
        };
    }

    /**
     * Boundary limiting is separate from ordinary transfer behavior. Threshold LOW and deadband
     * hold are intentional semantics and therefore never count as saturation episodes.
     */
    public static boolean limiting(int input, int mode, int parameter) {
        int x = EngineeringSignal.clamp(input);
        int p = boundedParameter(mode, parameter);
        return switch (mode) {
            case MODE_SCALE -> x * p > EngineeringSignal.MAX;
            case MODE_OFFSET -> {
                int raw = x + (p - 5);
                yield raw < EngineeringSignal.MIN || raw > EngineeringSignal.MAX;
            }
            case MODE_CLAMP -> x > p;
            default -> false;
        };
    }

    public static boolean usesPreviousOutput(int mode) {
        return mode == MODE_DEADBAND;
    }

    public static boolean reportsBoundaryLimiting(int mode) {
        return mode == MODE_SCALE || mode == MODE_OFFSET || mode == MODE_CLAMP;
    }
}
