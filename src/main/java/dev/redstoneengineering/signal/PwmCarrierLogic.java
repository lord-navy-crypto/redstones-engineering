package dev.redstoneengineering.signal;

/**
 * Pure edge-aligned PWM carrier with period-boundary command latching.
 *
 * <p>Partial-duty commands are sampled into a shadow/active pair only at the start of a carrier
 * cycle, preventing mid-cycle command changes from truncating or stretching the pulse already in
 * progress. Endpoint commands remain immediate so 0% and 100% do not wait a full long period.</p>
 */
public final class PwmCarrierLogic {
    public static final int MIN_COMMAND = 0;
    public static final int MAX_COMMAND = 15;
    public static final int MIN_CONFIGURED_PERIOD_TICKS = 2;
    public static final int MAX_CONFIGURED_PERIOD_TICKS = 64;

    private PwmCarrierLogic() {}

    public static int boundedCommand(int command) {
        return Math.max(MIN_COMMAND, Math.min(MAX_COMMAND, command));
    }

    public static int boundedConfiguredPeriod(int periodTicks) {
        return Math.max(MIN_CONFIGURED_PERIOD_TICKS,
                Math.min(MAX_CONFIGURED_PERIOD_TICKS, periodTicks));
    }

    public static int dutyQuantumPermille(int periodTicks) {
        int period = boundedConfiguredPeriod(periodTicks);
        return (int) Math.round(1000.0 / period);
    }

    public record State(boolean initialized, int phase, int latchedCommand, int completedCycles) {
        public State {
            phase = Math.max(0, phase);
            latchedCommand = boundedCommand(latchedCommand);
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
        int requested = boundedCommand(requestedCommand);
        int period = Math.max(1, periodTicks);
        State prior = previous == null ? new State(false, 0, requested, 0) : previous;

        // Endpoint commands are useful shutdown/full-drive states and should not wait for a long carrier.
        if (requested <= MIN_COMMAND || requested >= MAX_COMMAND) {
            int onTicks = requested <= MIN_COMMAND ? 0 : period;
            return new Result(
                    new State(true, 0, requested, prior.completedCycles()),
                    requested >= MAX_COMMAND,
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
        int boundedCommand = boundedCommand(command);
        int boundedPeriod = Math.max(1, periodTicks);
        return clamp((int) Math.round((boundedCommand / (double) MAX_COMMAND) * boundedPeriod),
                0, boundedPeriod);
    }

    private static int clamp(int value, int lo, int hi) {
        return Math.max(lo, Math.min(hi, value));
    }
}
