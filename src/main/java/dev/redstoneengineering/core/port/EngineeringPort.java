package dev.redstoneengineering.core.port;

import dev.redstoneengineering.core.domain.EngineeringDomain;
import net.minecraft.core.Direction;

import java.util.Objects;

/**
 * Static contract for one physical engineering port.
 *
 * <p>Runtime values and quality are deliberately kept out of BlockState and
 * represented by {@link EngineeringPortSnapshot}. This prevents high-cardinality
 * measurements from exploding block-state variants.</p>
 */
public record EngineeringPort(
        String label,
        Direction side,
        EngineeringDomain domain,
        PortKind kind,
        PortDirection direction,
        boolean redstoneConnectable,
        String unit
) {
    public EngineeringPort {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(direction, "direction");
        unit = unit == null || unit.isBlank() ? "unitless" : unit;
    }

    /** Backward-compatible constructor for the Alpha 1.0.7 descriptor shape. */
    public EngineeringPort(
            String label,
            Direction side,
            PortKind kind,
            PortDirection direction,
            boolean redstoneConnectable
    ) {
        this(label, side, EngineeringDomain.REDSTONE, kind, direction, redstoneConnectable, "signal");
    }

    public boolean canReceive() {
        return direction == PortDirection.INPUT || direction == PortDirection.BIDIRECTIONAL;
    }

    public boolean canTransmit() {
        return direction == PortDirection.OUTPUT || direction == PortDirection.BIDIRECTIONAL;
    }
}
