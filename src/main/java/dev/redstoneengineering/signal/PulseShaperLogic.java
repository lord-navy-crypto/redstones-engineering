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
        int boundedInput = Math.max(0, Math.min(15, input));
        int boundedThreshold = Math.max(1, Math.min(15, threshold));
        int boundedWidth = Math.max(1, Math.min(8, widthTicks));
        boolean above = boundedInput >= boundedThreshold;

        if (previous == null || !previous.initialized()) {
            return new Result(new State(true, above, 0), false, false, false);
        }

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
}
