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

    public static Observation observe(Level level, BlockPos sinkPos, Direction inputSide) {
        BlockPos sourcePos = sinkPos.relative(inputSide);
        if (!level.hasChunkAt(sourcePos)) {
            return new Observation(0, PortQuality.STALE);
        }

        int value = EngineeringMath.clamp(level.getSignal(sourcePos, inputSide), 0, 15);
        if (value > 0) return new Observation(value, PortQuality.VALID);

        BlockState sourceState = level.getBlockState(sourcePos);
        if (sourceState.isAir()) return new Observation(0, PortQuality.NO_SIGNAL);

        if (sourceState.getBlock() instanceof EngineeringPortProvider provider) {
            Optional<EngineeringPort> sourcePort = provider.engineeringPort(sourceState, inputSide.getOpposite());
            if (sourcePort.isPresent()) {
                EngineeringPort port = sourcePort.get();
                if (port.domain() == EngineeringDomain.REDSTONE
                        && port.redstoneConnectable()
                        && port.direction() != PortDirection.INPUT) {
                    return new Observation(0, PortQuality.VALID);
                }
            }
        }

        if (sourceState.getBlock().canConnectRedstone(sourceState, level, sourcePos, inputSide)) {
            return new Observation(0, PortQuality.VALID);
        }
        return new Observation(0, PortQuality.NO_SIGNAL);
    }
}
