package dev.redstoneengineering.physics;

import dev.redstoneengineering.block.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * Lightweight macroscopic magnetic helpers.
 *
 * The model is deliberately bounded and gameplay-oriented: nearby source
 * strengths are accumulated with an inverse-square-style falloff, while
 * unloaded chunks and positions outside the requested spherical radius are
 * never scanned.
 */
public final class MagneticPhysics {
    private MagneticPhysics() {}

    /** Highest adjacent Copper-domain voltage visible to a local load/coil. */
    public static int adjacentCopperLevel(Level level, BlockPos pos) {
        int best = 0;
        for (Direction d : Direction.values()) {
            BlockPos neighbor = pos.relative(d);
            if (!level.hasChunkAt(neighbor)) continue;
            best = Math.max(best, DomainNetwork.sampleCopperVoltage(level, neighbor));
        }
        return EngineeringMath.clamp(best, 0, 15);
    }

    /**
     * Approximate local magnetic-field magnitude in the RSE 0..15 field scale.
     * Only blocks inside a sphere of {@code radius} are considered.
     */
    public static int fieldAt(Level level, BlockPos origin, int radius) {
        int safeRadius = Math.max(0, Math.min(16, radius));
        int radiusSquared = safeRadius * safeRadius;
        double sum = 0.0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -safeRadius; dx <= safeRadius; dx++) {
            for (int dy = -safeRadius; dy <= safeRadius; dy++) {
                for (int dz = -safeRadius; dz <= safeRadius; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    int r2i = dx * dx + dy * dy + dz * dz;
                    if (r2i > radiusSquared) continue;

                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!level.hasChunkAt(cursor)) continue;

                    var state = level.getBlockState(cursor);
                    int source = 0;
                    if (state.getBlock() instanceof ElectromagnetBlock) {
                        source = state.getValue(ElectromagnetBlock.FIELD);
                    } else if (state.getBlock() instanceof IronCoreBlock
                            && state.getValue(IronCoreBlock.MAGNETIZED)) {
                        source = 6;
                    } else if (state.getBlock() instanceof PermanentMagnetBlock) {
                        source = state.getValue(PermanentMagnetBlock.STRENGTH);
                    }

                    if (source > 0) {
                        sum += source / Math.max(1.0, (double) r2i);
                    }
                }
            }
        }

        return EngineeringMath.clamp((int) Math.round(sum), 0, 15);
    }
}
