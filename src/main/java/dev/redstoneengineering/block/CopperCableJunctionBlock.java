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
import dev.redstoneengineering.physics.CopperNetworkSupport;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Explicit multi-port copper splice/branch box. */
public class CopperCableJunctionBlock extends ConnectedCableBlock implements EngineeringPortProvider {
    private static final String KEY = "copper_junction";

    public CopperCableJunctionBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected int maxConnections() {
        return 6;
    }

    @Override
    public MapCodec<CopperCableJunctionBlock> codec() {
        return RedstoneEngineering.COPPER_CABLE_JUNCTION_CODEC.value();
    }

    @Override
    protected boolean canConnectTo(BlockGetter level, BlockPos pos, Direction direction, BlockState neighbor) {
        return TransmissionTopology.copperPort(neighbor, direction);
    }

    public static void setVoltage(Level level, BlockPos pos, int voltage) {
        RuntimeIntStore.get(level, KEY, pos, 1)[0] = Math.max(0, Math.min(15, voltage));
    }

    public static int voltage(Level level, BlockPos pos) {
        return RuntimeIntStore.get(level, KEY, pos, 1)[0];
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        List<EngineeringPort> ports = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            if (!connected(state, direction)) continue;
            ports.add(new EngineeringPort(
                    "COPPER " + direction.getName().toUpperCase(),
                    direction,
                    EngineeringDomain.COPPER,
                    PortKind.BUS,
                    PortDirection.BIDIRECTIONAL,
                    false,
                    "V-eq"
            ));
        }
        return List.copyOf(ports);
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level,
            BlockPos pos,
            BlockState state,
            Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        return Optional.of(new EngineeringPortSnapshot(
                port.get(),
                voltage(level, pos),
                0.0,
                15.0,
                topologyValid(state) ? PortQuality.VALID : PortQuality.TOPOLOGY_ERROR
        ));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (level instanceof ServerLevel server) DomainNetwork.recomputeCopper(server, pos);
    }

    @Override
    protected void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            net.minecraft.world.level.block.Block neighbor,
            BlockPos neighborPos,
            boolean moved
    ) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, moved);
        if (level instanceof ServerLevel server) DomainNetwork.recomputeCopper(server, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, KEY, pos);
        super.onRemove(state, level, pos, newState, moved);
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            CopperNetworkSupport.recomputeAround(server, pos);
        }
    }
}
