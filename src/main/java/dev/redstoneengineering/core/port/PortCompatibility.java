package dev.redstoneengineering.core.port;

/** Pure compatibility rules for two already-resolved physical ports. */
public final class PortCompatibility {
    public enum Status {
        COMPATIBLE,
        DOMAIN_MISMATCH,
        DIRECTION_MISMATCH,
        ISOLATED
    }

    public record Result(Status status, String detail) {
        public boolean compatible() {
            return status == Status.COMPATIBLE;
        }
    }

    private PortCompatibility() {}

    public static Result evaluate(EngineeringPort left, EngineeringPort right) {
        if (left.direction() == PortDirection.NONE || right.direction() == PortDirection.NONE) {
            return new Result(Status.ISOLATED, "one or both ports are isolated");
        }
        if (left.domain() != right.domain()) {
            return new Result(
                    Status.DOMAIN_MISMATCH,
                    left.domain().label() + " != " + right.domain().label()
            );
        }

        boolean flow = (left.canTransmit() && right.canReceive())
                || (right.canTransmit() && left.canReceive());
        if (!flow) {
            return new Result(Status.DIRECTION_MISMATCH, left.direction() + " vs " + right.direction());
        }
        return new Result(Status.COMPATIBLE, left.domain().label());
    }
}
