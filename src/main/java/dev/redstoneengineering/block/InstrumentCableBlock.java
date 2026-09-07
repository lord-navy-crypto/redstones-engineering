package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.instrument.InstrumentNetwork;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Measurement-bus cable carrying probe channels rather than redstone power.
 * Direct cable runs and branches are planar; UP/DOWN transitions require the
 * unified Signal Junction Point.
 */
public class InstrumentCableBlock extends ConnectedCableBlock implements EngineeringPortProvider {
    public InstrumentCableBlock(Properties properties) { super(properties); }

    @Override public MapCodec<? extends InstrumentCableBlock> codec() { return RedstoneEngineering.INSTRUMENT_CABLE_CODEC.value(); }
    @Override protected boolean canConnectTo(BlockGetter level, BlockPos self, Direction direction, BlockState neighbor) {
        return TransmissionTopology.instrumentCablePort(level, self, direction, neighbor);
    }
    @Override protected int maxConnections() { return 6; }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        List<EngineeringPort> ports = new ArrayList<>();
        for (Direction side : Direction.values()) {
            // Planar faces advertise connectable measurement-bus capability even when open.
            // A vertical port exists only when the visible arm was created by a junction.
            if (side.getAxis() == Direction.Axis.Y && !connected(state, side)) continue;
            ports.add(new EngineeringPort(
                    "INSTRUMENT BUS", side, EngineeringDomain.INSTRUMENT_BUS,
                    PortKind.BUS, PortDirection.BIDIRECTIONAL, false, "channel"));
        }
        return List.copyOf(ports);
    }

    /**
     * A cable port reports logical channel health, not electrical power. The value is the
     * number of uniquely owned probe channels visible on the bounded bus; duplicate channel
     * ownership or a truncated scan is surfaced as TOPOLOGY_ERROR instead of silently choosing
     * a probe. Shielding remains a separate integrity dimension.
     */
    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        InstrumentNetwork.ProbeSnapshot bus = InstrumentNetwork.scan(level, pos);
        return Optional.of(new EngineeringPortSnapshot(
                port.get(), bus.validChannelsInMask(0xF), 0.0, 4.0, bus.qualityForMask(0xF)));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                String type = this instanceof ShieldedInstrumentCableBlock ? "Shielded Instrument Bus" : "Instrument Bus Cable";
                InstrumentNetwork.ProbeSnapshot bus = InstrumentNetwork.scan(level, pos);
                player.displayClientMessage(Component.literal(
                        type + " | " + PortDiagnostics.connectedCable(level, pos, state, PortDiagnostics.Domain.INSTRUMENT)
                                + " | engineeringPorts=" + engineeringPorts(state).size()
                                + " | ports=" + connectionCount(state)
                                + " | routing=PLANAR; vertical via Signal Junction Point"
                                + " | channels=" + bus.validChannels() + "/" + bus.activeChannels()
                                + " valid/active | integrity=" + bus.integrity()
                ), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
