package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
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

/** Six-direction measurement bus carrying probe channels rather than redstone power. */
public class InstrumentCableBlock extends ConnectedCableBlock implements EngineeringPortProvider {
    public InstrumentCableBlock(Properties properties) { super(properties); }

    @Override public MapCodec<? extends InstrumentCableBlock> codec() { return RedstoneEngineering.INSTRUMENT_CABLE_CODEC.value(); }
    @Override protected boolean canConnectTo(BlockGetter level, BlockPos self, Direction direction, BlockState neighbor) {
        return TransmissionTopology.instrumentPort(neighbor, direction);
    }
    @Override protected int maxConnections() { return 6; }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        List<EngineeringPort> ports = new ArrayList<>();
        for (Direction side : Direction.values()) {
            if (connected(state, side)) {
                ports.add(new EngineeringPort(
                        "INSTRUMENT_BUS", side, EngineeringDomain.INSTRUMENT_BUS,
                        PortKind.BUS, PortDirection.BIDIRECTIONAL, false, "link"));
            }
        }
        return List.copyOf(ports);
    }

    /**
     * Read-only physical-link snapshot for the selected bus face.
     *
     * <p>The cable carries four logical probe channels, so inventing one scalar channel value for
     * the cable itself would be misleading. Instead this snapshot reports whether the selected
     * physical bus edge still resolves to a compatible instrument endpoint. Full channel values
     * remain owned by instruments through {@code InstrumentNetwork}; this surface exists for
     * topology/quality observability only and never mutates or solves the network.</p>
     */
    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level,
            BlockPos pos,
            BlockState state,
            Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();

        BlockPos neighborPos = pos.relative(side);
        PortQuality quality;
        if (!level.hasChunkAt(neighborPos)) {
            quality = PortQuality.STALE;
        } else {
            BlockState neighbor = level.getBlockState(neighborPos);
            quality = TransmissionTopology.instrumentPort(neighbor, side.getOpposite())
                    ? PortQuality.VALID
                    : PortQuality.TOPOLOGY_ERROR;
        }
        return Optional.of(new EngineeringPortSnapshot(
                port.get(), quality == PortQuality.VALID ? 1.0 : 0.0, 0.0, 1.0, quality));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                String type = this instanceof ShieldedInstrumentCableBlock ? "Shielded Instrument Bus" : "Instrument Bus Cable";
                player.displayClientMessage(Component.literal(
                        type + " | " + PortDiagnostics.connectedCable(level, pos, state, PortDiagnostics.Domain.INSTRUMENT)
                                + " | engineeringPorts=" + engineeringPorts(state).size()
                                + " | ports=" + connectionCount(state)
                ), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
