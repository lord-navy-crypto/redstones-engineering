package dev.redstoneengineering.physics;

import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * Observer-only redstone input evidence.
 *
 * <p>A numerical zero is not enough to decide whether an engineering input is connected:
 * configured zero-output sources are real signals, while air or a non-redstone neighbor is
 * NO_SIGNAL. Missing chunk coverage is STALE. This helper intentionally performs no runtime writes.</p>
 */
public final class RedstoneObservationSupport {
    private RedstoneObservationSupport() {}

    public record Observation(int value, PortQuality quality) {
        public boolean valid() {
            return quality == PortQuality.VALID || quality == PortQuality.SATURATED;
        }
    }

    /** Worst-of combiner for a derived redstone decision/output. */
    public static PortQuality combineQuality(PortQuality... qualities) {
        PortQuality result = PortQuality.VALID;
        int rank = 0;
        for (PortQuality quality : qualities) {
            if (quality == null) continue;
            int candidate = switch (quality) {
                case VALID -> 0;
                case SATURATED -> 1;
                case NO_SIGNAL -> 2;
                case STALE -> 3;
                case FAULT -> 4;
                case DOMAIN_MISMATCH -> 5;
                case TOPOLOGY_ERROR -> 6;
            };
            if (candidate > rank) {
                rank = candidate;
                result = quality;
            }
        }
        return result;
    }

    public static Observation observe(Level level, BlockPos sinkPos, Direction inputSide) {
        BlockPos sourcePos = sinkPos.relative(inputSide);
        if (!level.hasChunkAt(sourcePos)) {
            return new Observation(0, PortQuality.STALE);
        }

        int value = EngineeringMath.clamp(level.getSignal(sourcePos, inputSide), 0, 15);
        BlockState sourceState = level.getBlockState(sourcePos);
        if (sourceState.isAir()) return new Observation(0, PortQuality.NO_SIGNAL);

        if (sourceState.getBlock() instanceof EngineeringPortProvider provider) {
            Optional<EngineeringPort> sourcePort = provider.engineeringPort(sourceState, inputSide.getOpposite());
            if (sourcePort.isPresent()) {
                EngineeringPort port = sourcePort.get();
                if (port.domain() == EngineeringDomain.REDSTONE
                        && port.redstoneConnectable()
                        && port.direction() != PortDirection.INPUT) {
                    var snapshot = provider.engineeringSnapshot(
                            level, sourcePos, sourceState, inputSide.getOpposite());
                    if (snapshot.isPresent()) {
                        return new Observation(
                                EngineeringMath.clamp((int) Math.round(snapshot.get().value()), 0, 15),
                                snapshot.get().quality());
                    }
                    return new Observation(value, PortQuality.VALID);
                }
            }
        }

        if (value > 0) return new Observation(value, PortQuality.VALID);
        if (sourceState.getBlock().canConnectRedstone(sourceState, level, sourcePos, inputSide)) {
            return new Observation(0, PortQuality.VALID);
        }
        return new Observation(0, PortQuality.NO_SIGNAL);
    }
}
