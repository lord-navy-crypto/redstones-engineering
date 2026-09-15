package dev.redstoneengineering.operations.world;

import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Persisted explicit membership of world resources in one Operations workcell.
 * High-cardinality identity stays server-side and is never encoded in BlockState/redstone.
 */
public record OperationWorkcellBinding(
        String workcellId,
        List<ResourceBinding> resourceBindings
) {
    public record ResourceBinding(BlockPos position, String expectedResourceId) {
        public ResourceBinding {
            position = position == null ? BlockPos.ZERO : position.immutable();
            expectedResourceId = expectedResourceId == null ? "" : expectedResourceId.trim();
        }

        public boolean validBinding() {
            return !expectedResourceId.isBlank();
        }
    }

    public OperationWorkcellBinding {
        workcellId = workcellId == null ? "" : workcellId.trim();
        resourceBindings = resourceBindings == null ? List.of() : List.copyOf(resourceBindings);
    }

    public boolean validBinding() {
        if (workcellId.isBlank()) return false;
        Set<BlockPos> positions = new HashSet<>();
        Set<String> resourceIds = new HashSet<>();
        for (ResourceBinding resource : resourceBindings) {
            if (resource == null || !resource.validBinding()) return false;
            if (!positions.add(resource.position())) return false;
            if (!resourceIds.add(resource.expectedResourceId())) return false;
        }
        return true;
    }
}
