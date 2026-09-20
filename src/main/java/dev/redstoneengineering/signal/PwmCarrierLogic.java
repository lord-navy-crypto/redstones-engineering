package dev.redstoneengineering.signal;

/**
 * Pure edge-aligned PWM carrier with period-boundary command latching.
 *
 * <p>Partial-duty commands are sampled into a shadow/active pair only at the start of a carrier
 * cycle, preventing mid-cycle command changes from truncating or stretching the pulse already in
 * progress. Endpoint commands remain immediate so 0% and 100% do not wait a full long period.</p>
 */
public final class PwmCarrierLogic {
    private PwmCarrierLogic() {}

    public record State(boolean initialized, int phase, int latchedCommand, int completedCycles) {
        public State {
            phase = Math.max(0, phase);
            latchedCommand = clamp(latchedCommand, 0, 15);
            completedCycles = Math.max(0, completedCycles);
        }
    }

    public record Result(
            State state,
            boolean outputHigh,
            int onTicks,
            boolean commandLatched,
            boolean pendingUpdate
    ) {}

    public static Result step(int requestedCommand, int periodTicks, State previous) {
        int requested = clamp(requestedCommand, 0, 15);
        int period = Math.max(1, periodTicks);
        State prior = previous == null ? new State(false, 0, requested, 0) : previous;

        // Endpoint commands are useful shutdown/full-drive states and should not wait for a long carrier.
        if (requested <= 0 || requested >= 15) {
            int onTicks = requested <= 0 ? 0 : period;
            return new Result(
                    new State(true, 0, requested, prior.completedCycles()),
                    requested >= 15,
                    onTicks,
                    prior.latchedCommand() != requested || !prior.initialized(),
                    false
            );
        }

        int phase = Math.floorMod(prior.phase(), period);
        int latched = prior.latchedCommand();
        boolean latchedNow = false;
        if (!prior.initialized() || phase == 0) {
            latched = requested;
            latchedNow = !prior.initialized() || prior.latchedCommand() != requested;
        }

        int onTicks = quantizedOnTicks(latched, period);
        boolean high = phase < onTicks;
        int nextPhase = (phase + 1) % period;
        int cycles = prior.completedCycles();
        if (nextPhase == 0 && cycles < Integer.MAX_VALUE) cycles++;

        return new Result(
                new State(true, nextPhase, latched, cycles),
                high,
                onTicks,
                latchedNow,
                requested != latched
        );
    }

    public static int quantizedOnTicks(int command, int periodTicks) {
        int boundedCommand = clamp(command, 0, 15);
        int boundedPeriod = Math.max(1, periodTicks);
        return clamp((int) Math.round((boundedCommand / 15.0) * boundedPeriod), 0, boundedPeriod);
    }

    private static int clamp(int value, int lo, int hi) {
        return Math.max(lo, Math.min(hi, value));
    }
}
