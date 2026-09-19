package dev.redstoneengineering.signal;

/**
 * Pure finite-travel state machine for a binary isolation valve.
 *
 * <p>The valve remains an on/off topology element. Travel timing only delays when the actual
 * flow path changes; it does not invent a proportional-flow model.</p>
 */
public final class PneumaticIsolationValveLogic {
    private PneumaticIsolationValveLogic() {}

    public record State(boolean actualOpen, boolean pendingTargetOpen, int remainingTicks) {
        public State {
            remainingTicks = Math.max(0, remainingTicks);
        }
    }

    public record Result(State state, boolean transitioned) {
        public boolean moving() {
            return state.remainingTicks() > 0;
        }
    }

    public static Result step(
            boolean commandedOpen,
            int openingTravelTicks,
            int closingTravelTicks,
            State previous
    ) {
        State prior = previous == null ? new State(commandedOpen, commandedOpen, 0) : previous;
        if (commandedOpen == prior.actualOpen()) {
            return new Result(new State(prior.actualOpen(), prior.actualOpen(), 0), false);
        }

        int travel = Math.max(0, commandedOpen ? openingTravelTicks : closingTravelTicks);
        boolean continuing = prior.remainingTicks() > 0
                && prior.pendingTargetOpen() == commandedOpen;

        if (!continuing) {
            if (travel == 0) {
                return new Result(new State(commandedOpen, commandedOpen, 0), true);
            }
            return new Result(new State(prior.actualOpen(), commandedOpen, travel), false);
        }

        int remaining = prior.remainingTicks() - 1;
        if (remaining <= 0) {
            return new Result(new State(commandedOpen, commandedOpen, 0), true);
        }
        return new Result(new State(prior.actualOpen(), commandedOpen, remaining), false);
    }
}
