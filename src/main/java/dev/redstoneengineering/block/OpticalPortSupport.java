package dev.redstoneengineering.block;

import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.*;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** Small shared descriptor helpers for the guided-optical audit. */
final class OpticalPortSupport {
    private OpticalPortSupport() {}
    static List<EngineeringPort> connected(BlockState state, String label, PortKind kind, PortDirection direction) {
        List<EngineeringPort> result = new ArrayList<>();
        for (Direction side : Direction.values()) if (ConnectedCableBlock.connected(state, side))
            result.add(new EngineeringPort(label, side, EngineeringDomain.OPTICAL, kind, direction, false, "intensity"));
        return result;
    }
    static List<EngineeringPort> allFaces(String label, PortKind kind, PortDirection direction) {
        List<EngineeringPort> result = new ArrayList<>();
        for (Direction side : Direction.values()) result.add(new EngineeringPort(label, side, EngineeringDomain.OPTICAL, kind, direction, false, "intensity"));
        return result;
    }
}
