package dev.redstoneengineering.core.port;

import net.minecraft.core.Direction;

public record EngineeringPort(
        String label,
        Direction side,
        PortKind kind,
        PortDirection direction,
        boolean redstoneConnectable
) {}
