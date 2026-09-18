package dev.redstoneengineering.signal;

/** Pure monostable pulse-conditioning logic shared by the block runtime and semantic verification. */
public final class PulseShaperLogic {
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
        int boundedThreshold = Math.max(1, Math.min(15, threshold));
        int boundedHysteresis = Math.max(1, Math.min(4, hysteresis));
        int boundedWidth = Math.max(1, Math.min(8, widthTicks));

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
        int boundedThreshold = Math.max(1, Math.min(15, threshold));
        int boundedHysteresis = Math.max(1, Math.min(4, hysteresis));
        return Math.max(0, boundedThreshold - boundedHysteresis);
    }

    public static boolean schmittAbove(
            int input, int threshold, int hysteresis, boolean previouslyAbove
    ) {
        int boundedInput = Math.max(0, Math.min(15, input));
        int boundedThreshold = Math.max(1, Math.min(15, threshold));
        if (!previouslyAbove) return boundedInput >= boundedThreshold;
        return boundedInput > rearmThreshold(boundedThreshold, hysteresis);
    }
}
