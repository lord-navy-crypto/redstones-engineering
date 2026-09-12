package dev.redstoneengineering.physics;

import dev.redstoneengineering.block.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Lightweight macroscopic magnetic helpers.
 *
 * <p>The model is deliberately bounded and gameplay-oriented: nearby source
 * strengths are accumulated with an inverse-square-style falloff. Coverage is
 * explicit so a real measured zero cannot be confused with an incomplete scan.</p>
 */
public final class MagneticPhysics {
    private MagneticPhysics() {}

    /** Bounded free-space field measurement plus scan-coverage evidence. */
    public record FieldSample(int field, int scannedCells, int expectedCells, boolean complete) {}

    /** Highest legitimate adjacent Copper feed visible to a local sink/coil. */
    public static int adjacentCopperLevel(Level level, BlockPos pos) {
        return CopperNetworkSupport.terminalInput(level, pos).voltage();
    }

    /** Approximate local magnetic-field magnitude in the RSE 0..15 field scale. */
    public static int fieldAt(Level level, BlockPos origin, int radius) {
        return fieldSample(level, origin, radius).field();
    }

    /**
     * Full field measurement used by sensors. Unloaded cells are excluded from
     * the numerical sum and make {@code complete=false} instead of silently
     * turning an unknown region into a confirmed zero field.
     */
    public static FieldSample fieldSample(Level level, BlockPos origin, int radius) {
        return fieldSample(level, origin, radius, true);
    }

    /**
     * External applied-field sample for soft-core actuation. Existing remanent
     * iron cores are excluded, while scan completeness is preserved so callers
     * cannot act on a partial free-space field as definitive evidence.
     */
    public static FieldSample appliedFieldSample(Level level, BlockPos origin, int radius) {
        return fieldSample(level, origin, radius, false);
    }

    /**
     * External applied field magnitude for diagnostics/readback. Actuation code
     * that needs authoritative evidence must use {@link #appliedFieldSample}.
     */
    public static int appliedFieldAt(Level level, BlockPos origin, int radius) {
        return appliedFieldSample(level, origin, radius).field();
    }

    private static FieldSample fieldSample(Level level, BlockPos origin, int radius, boolean includeRemanence) {
        int safeRadius = Math.max(0, Math.min(16, radius));
        int radiusSquared = safeRadius * safeRadius;
        double sum = 0.0;
        int scanned = 0;
        int expected = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -safeRadius; dx <= safeRadius; dx++) {
            for (int dy = -safeRadius; dy <= safeRadius; dy++) {
                for (int dz = -safeRadius; dz <= safeRadius; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    int r2i = dx * dx + dy * dy + dz * dz;
                    if (r2i > radiusSquared) continue;
                    expected++;

                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!level.hasChunkAt(cursor)) continue;
                    scanned++;

                    var state = level.getBlockState(cursor);
                    int source = 0;
                    if (state.getBlock() instanceof ElectromagnetBlock) {
                        source = state.getValue(ElectromagnetBlock.FIELD);
                    } else if (includeRemanence && state.getBlock() instanceof IronCoreBlock
                            && state.getValue(IronCoreBlock.MAGNETIZED)) {
                        source = 6;
                    } else if (state.getBlock() instanceof PermanentMagnetBlock) {
                        source = state.getValue(PermanentMagnetBlock.STRENGTH);
                    }

                    if (source > 0) sum += source / Math.max(1.0, (double) r2i);
                }
            }
        }

        int field = EngineeringMath.clamp((int) Math.round(sum), 0, 15);
        return new FieldSample(field, scanned, expected, scanned == expected);
    }
}
