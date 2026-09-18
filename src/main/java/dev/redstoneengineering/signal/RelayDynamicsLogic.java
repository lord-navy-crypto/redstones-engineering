package dev.redstoneengineering.signal;

/**
 * Pure electromechanical transition timing for relay/contact-style devices.
 *
 * <p>Pickup/dropout voltage hysteresis belongs to the coil controller. This class models only
 * the finite mechanical travel between the requested coil state and the actual armature/contact
 * state. It is deliberately a bounded timing abstraction, not a contact-bounce physics model.</p>
 */
public final class RelayDynamicsLogic {
    private RelayDynamicsLogic() {}

    public record State(boolean energized, boolean pendingTarget, int remainingTicks) {
        public State {
            remainingTicks = Math.max(0, remainingTicks);
        }
    }

    public record Result(State state, boolean transitioned) {
        public boolean transitionPending() {
            return state.remainingTicks() > 0;
        }
    }

    public static Result step(
            boolean desiredEnergized,
            int operateDelayTicks,
            int releaseDelayTicks,
            State previous
    ) {
        State prior = previous == null ? new State(false, false, 0) : previous;
        boolean actual = prior.energized();

        if (desiredEnergized == actual) {
            return new Result(new State(actual, actual, 0), false);
        }

        int delay = Math.max(0, desiredEnergized ? operateDelayTicks : releaseDelayTicks);
        boolean samePendingTarget = prior.remainingTicks() > 0
                && prior.pendingTarget() == desiredEnergized;

        if (!samePendingTarget) {
            if (delay == 0) {
                return new Result(new State(desiredEnergized, desiredEnergized, 0), true);
            }
            return new Result(new State(actual, desiredEnergized, delay), false);
        }

        int remaining = prior.remainingTicks() - 1;
        if (remaining <= 0) {
            return new Result(new State(desiredEnergized, desiredEnergized, 0), true);
        }
        return new Result(new State(actual, desiredEnergized, remaining), false);
    }
}
