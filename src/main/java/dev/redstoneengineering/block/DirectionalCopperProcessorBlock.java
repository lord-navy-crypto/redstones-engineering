package dev.redstoneengineering.block;

import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.CopperObservationSupport;
import dev.redstoneengineering.physics.DomainNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

/**
 * Shared engineering-port contract for axial copper-domain processors.
 *
 * <p>The physical topology is BACK input to FRONT output. Processor physics owns
 * runtime writes; this class only exposes read-only input/output evidence. Copper
 * voltage values continue to come from the authoritative DomainNetwork sampler,
 * while CopperObservationSupport carries the independent quality classification.</p>
 */
public abstract class DirectionalCopperProcessorBlock extends DirectionalDomainBlock implements EngineeringPortProvider {
    protected DirectionalCopperProcessorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort(
                        "INPUT", inputSide(state), EngineeringDomain.COPPER,
                        PortKind.ELECTRICAL, PortDirection.INPUT, false, "V-eq"),
                new EngineeringPort(
                        "OUTPUT", outputSide(state), EngineeringDomain.COPPER,
                        PortKind.ELECTRICAL, PortDirection.OUTPUT, false, "V-eq")
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level,
            BlockPos pos,
            BlockState state,
            Direction side
    ) {
        Optional<EngineeringPort> descriptor = engineeringPort(state, side);
        if (descriptor.isEmpty()) return Optional.empty();

        if (side == inputSide(state)) {
            BlockPos inputPos = inputPos(pos, state);
            CopperObservationSupport.Observation input = CopperObservationSupport.observe(level, inputPos, pos);
            int voltage = DomainNetwork.sampleCopperVoltage(level, inputPos, pos);
            return Optional.of(new EngineeringPortSnapshot(
                    descriptor.get(),
                    Math.max(0, Math.min(15, voltage)),
                    0.0,
                    15.0,
                    input.quality()
            ));
        }

        return Optional.of(new EngineeringPortSnapshot(
                descriptor.get(),
                Math.max(0, Math.min(15, observedOutputVoltage(level, pos, state))),
                0.0,
                15.0,
                observedOutputQuality(level, pos, state)
        ));
    }

    /** Runtime output value owned by the concrete component simulation. */
    protected abstract int observedOutputVoltage(Level level, BlockPos pos, BlockState state);

    /**
     * Runtime output quality owned by the concrete component simulation. The default
     * preserves compatibility for any future subclass; audited processors override it.
     */
    protected PortQuality observedOutputQuality(Level level, BlockPos pos, BlockState state) {
        return PortQuality.VALID;
    }
}
