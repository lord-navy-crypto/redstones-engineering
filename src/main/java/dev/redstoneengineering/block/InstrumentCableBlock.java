package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;

/** Six-direction measurement bus carrying probe channels rather than redstone power. */
public class InstrumentCableBlock extends ConnectedCableBlock implements EngineeringPortProvider {
    public InstrumentCableBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends InstrumentCableBlock> codec() {
        return RedstoneEngineering.INSTRUMENT_CABLE_CODEC.value();
    }

    @Override
    protected boolean canConnectTo(BlockGetter level, BlockPos self, Direction direction, BlockState neighbor) {
        return TransmissionTopology.instrumentPort(neighbor, direction);
    }

    @Override
    protected int maxConnections() {
        return 6;
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        List<EngineeringPort> ports = new ArrayList<>();
        for (Direction side : Direction.values()) {
            if (connected(state, side)) {
                ports.add(new EngineeringPort(
                        "INSTRUMENT_BUS",
                        side,
                        EngineeringDomain.INSTRUMENT_BUS,
                        PortKind.BUS,
                        PortDirection.BIDIRECTIONAL,
                        false,
                        "channel"
                ));
            }
        }
        return List.copyOf(ports);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            String type = this instanceof ShieldedInstrumentCableBlock ? "Shielded Instrument Bus" : "Instrument Bus Cable";
            player.displayClientMessage(Component.literal(
                    type + " | " + PortDiagnostics.connectedCable(level, pos, state, PortDiagnostics.Domain.INSTRUMENT)
                            + " | engineeringPorts=" + engineeringPorts(state).size()
                            + " | ports=" + connectionCount(state)
            ), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
