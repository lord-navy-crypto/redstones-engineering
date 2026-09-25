package dev.redstoneengineering.signal;

/** Pure monostable pulse-conditioning logic shared by the block runtime and semantic verification. */
public final class PulseShaperLogic {
    public static final int MIN_THRESHOLD = 1;
    public static final int MAX_THRESHOLD = 15;
    public static final int MIN_HYSTERESIS = 1;
    public static final int MAX_HYSTERESIS = 4;
    public static final int MIN_WIDTH = 1;
    public static final int MAX_WIDTH = 8;

    public static int boundedThreshold(int value) {
        return Math.max(MIN_THRESHOLD, Math.min(MAX_THRESHOLD, value));
    }

    public static int boundedHysteresis(int value) {
        return Math.max(MIN_HYSTERESIS, Math.min(MAX_HYSTERESIS, value));
    }

    public static int boundedWidth(int value) {
        return Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, value));
    }

    public record State(boolean initialized, boolean lastAboveThreshold, int remainingTicks) {
        public State {
            remainingTicks = Math.max(0, remainingTicks);
        }
    }

    public record Result(State state, boolean outputHigh, boolean acceptedTrigger, boolean suppressedTrigger) {}

    private PulseShaperLogic() {}

    public static Result step(
            int input,
            int threshold,
            int widthTicks,
            boolean retriggerable,
            State previous
    ) {
        return step(input, threshold, 1, widthTicks, retriggerable, previous);
    }

    /**
     * Schmitt-style one-shot input. A trigger arms at {@code threshold}; after a trigger the
     * input must fall to {@code threshold - hysteresis} before another rising crossing exists.
     */
    public static Result step(
            int input,
            int threshold,
            int hysteresis,
            int widthTicks,
            boolean retriggerable,
            State previous
    ) {
        int boundedInput = Math.max(0, Math.min(15, input));
        int boundedThreshold = boundedThreshold(threshold);
        int boundedHysteresis = boundedHysteresis(hysteresis);
        int boundedWidth = boundedWidth(widthTicks);

        if (previous == null || !previous.initialized()) {
            boolean above = boundedInput >= boundedThreshold;
            return new Result(new State(true, above, 0), false, false, false);
        }

        boolean above = schmittAbove(
                boundedInput, boundedThreshold, boundedHysteresis, previous.lastAboveThreshold());
        int remaining = Math.max(0, previous.remainingTicks());
        boolean triggerEvent = above && !previous.lastAboveThreshold();
        boolean accepted = false;
        boolean suppressed = false;

        if (triggerEvent) {
            if (remaining > 0 && !retriggerable) {
                suppressed = true;
            } else {
                accepted = true;
                remaining = boundedWidth;
            }
        }

        boolean output = remaining > 0;
        int nextRemaining = Math.max(0, remaining - 1);
        return new Result(new State(true, above, nextRemaining), output, accepted, suppressed);
    }

    public static int rearmThreshold(int threshold, int hysteresis) {
        int boundedThreshold = boundedThreshold(threshold);
        int boundedHysteresis = boundedHysteresis(hysteresis);
        return Math.max(0, boundedThreshold - boundedHysteresis);
    }

    public static boolean schmittAbove(
            int input, int threshold, int hysteresis, boolean previouslyAbove
    ) {
        int boundedInput = Math.max(0, Math.min(15, input));
        int boundedThreshold = boundedThreshold(threshold);
        if (!previouslyAbove) return boundedInput >= boundedThreshold;
        return boundedInput > rearmThreshold(boundedThreshold, hysteresis);
    }
}
