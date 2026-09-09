package dev.redstoneengineering.physics;

import dev.redstoneengineering.block.AirReservoirBlock;
import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Observer-only pneumatic pressure evidence. Numeric zero is a valid solved state. */
public final class PneumaticObservationSupport {
    private PneumaticObservationSupport() {}

    public record Observation(int pressure, PortQuality quality) {
        public boolean valid() {
            return quality == PortQuality.VALID || quality == PortQuality.SATURATED;
        }
    }

    public static Observation observe(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return new Observation(0, PortQuality.STALE);
        if (PneumaticNetwork.truncated(level, pos)) return new Observation(0, PortQuality.STALE);

        InformationRuntime.Snapshot line = InformationRuntime.snapshot(level, "pneumatic", pos);
        InformationRuntime.Snapshot stored = level.getBlockState(pos).getBlock() instanceof AirReservoirBlock
                ? InformationRuntime.snapshot(level, "air_reservoir", pos)
                : null;

        boolean lineSeen = line.ageTicks() >= 0;
        boolean storedSeen = stored != null && stored.ageTicks() >= 0;
        if (!lineSeen && !storedSeen) return new Observation(0, PortQuality.STALE);
        if (lineSeen && !line.valid()) return new Observation(line.value(), PortQuality.FAULT);
        if (storedSeen && !stored.valid()) {
            return new Observation(Math.max(lineSeen ? line.value() : 0, stored.value()), PortQuality.FAULT);
        }

        int pressure = Math.max(lineSeen ? line.value() : 0, storedSeen ? stored.value() : 0);
        return new Observation(Math.max(0, Math.min(100, pressure)), PortQuality.VALID);
    }
}
