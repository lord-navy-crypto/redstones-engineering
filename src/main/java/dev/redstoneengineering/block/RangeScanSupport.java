package dev.redstoneengineering.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.function.BiPredicate;

/**
 * Shared bounded, read-only ray scan for engineering range instruments.
 * Does not load chunks or fabricate a CLEAR result for incomplete coverage.
 *
 * Detection remains device-specific: the redstone sensor can test entities,
 * while the Lapis transducer intentionally measures blocking solids/fluids.
 */
final class RangeScanSupport {
    private RangeScanSupport() {}

    enum Status { TARGET, CLEAR, INCOMPLETE_UNLOADED }

    record RayResult(int distance, int scannedCells, int maxRange, Status status) {
        boolean complete() { return status != Status.INCOMPLETE_UNLOADED; }
    }

    static RayResult scan(Level level, BlockPos origin, Direction direction, int maxRange,
                          BiPredicate<Level, BlockPos> detects) {
        if (maxRange < 1 || maxRange > 64) {
            throw new IllegalArgumentException("range outside bounded 1..64");
        }
        for (int distance = 1; distance <= maxRange; distance++) {
            BlockPos cell = origin.relative(direction, distance);
            if (!level.hasChunkAt(cell)) {
                return new RayResult(0, distance - 1, maxRange, Status.INCOMPLETE_UNLOADED);
            }
            if (detects.test(level, cell)) {
                return new RayResult(distance, distance, maxRange, Status.TARGET);
            }
        }
        return new RayResult(0, maxRange, maxRange, Status.CLEAR);
    }

    static boolean solidOrFluid(Level level, BlockPos pos) {
        return !level.getBlockState(pos).isAir() || !level.getFluidState(pos).isEmpty();
    }
}
