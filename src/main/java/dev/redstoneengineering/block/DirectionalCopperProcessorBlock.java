package dev.redstoneengineering.block;

import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
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
 * <p>The physical topology is BACK input to FRONT output. DomainNetwork owns
 * propagation and component physics; this class only standardizes topology and
 * observability for diagnostics such as Jade and GameTest.</p>
 */
public abstract class DirectionalCopperProcessorBlock extends DirectionalDomainBlock implements EngineeringPortProvider {
    protected DirectionalCopperProcessorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort(
                        "INPUT",
                        inputSide(state),
                        EngineeringDomain.COPPER,
                        PortKind.ELECTRICAL,
                        PortDirection.INPUT,
                        false,
                        "V-eq"
                ),
                new EngineeringPort(
                        "OUTPUT",
                        outputSide(state),
                        EngineeringDomain.COPPER,
                        PortKind.ELECTRICAL,
                        PortDirection.OUTPUT,
                        false,
                        "V-eq"
                )
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

        int voltage = side == inputSide(state)
                ? DomainNetwork.sampleCopperVoltage(level, inputPos(pos, state))
                : observedOutputVoltage(level, pos, state);
        return Optional.of(new EngineeringPortSnapshot(
                descriptor.get(),
                Math.max(0, Math.min(15, voltage)),
                0.0,
                15.0,
                PortQuality.VALID
        ));
    }

    /** Runtime output value owned by the concrete component simulation. */
    protected abstract int observedOutputVoltage(Level level, BlockPos pos, BlockState state);
}
