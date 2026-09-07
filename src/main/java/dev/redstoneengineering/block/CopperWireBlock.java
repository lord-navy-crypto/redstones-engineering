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
import dev.redstoneengineering.physics.NetworkKernel;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 3-D copper electrical cable. Bends automatically; explicit Copper Junctions provide branches. */
public class CopperWireBlock extends ConnectedCableBlock implements EngineeringPortProvider {
    private static final String KEY = "copper_cable";
    private static final int VOLTAGE_INDEX = 0;
    private static final int DRIVER_COUNT_INDEX = 1;
    private static final int RUNTIME_SIZE = 2;

    public CopperWireBlock(Properties p) { super(p); }

    @Override public MapCodec<CopperWireBlock> codec() { return RedstoneEngineering.COPPER_WIRE_CODEC.value(); }

    @Override
    protected boolean canConnectTo(BlockGetter level, BlockPos pos, Direction direction, BlockState neighbor) {
        return TransmissionTopology.copperPort(neighbor, direction);
    }

    /** Authoritative solver write. Driver evidence is captured with the resolved node value. */
    public static void setVoltage(Level level, BlockPos pos, int voltage) {
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        runtime[VOLTAGE_INDEX] = Math.max(0, Math.min(15, voltage));
        runtime[DRIVER_COUNT_INDEX] = Math.max(0, NetworkKernel.stats(level, "copper").activeDrivers());
    }

    /** Observer-neutral: inspecting a never-solved cable must not create runtime physics state. */
    public static int voltage(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length <= VOLTAGE_INDEX ? 0 : runtime[VOLTAGE_INDEX];
    }

    public static int driverCount(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length <= DRIVER_COUNT_INDEX ? 0 : runtime[DRIVER_COUNT_INDEX];
    }

    public static PortQuality quality(Level level, BlockPos pos, BlockState state) {
        if (!((CopperWireBlock) state.getBlock()).topologyValid(state)) return PortQuality.TOPOLOGY_ERROR;
        int drivers = driverCount(level, pos);
        if (drivers > 1) return PortQuality.TOPOLOGY_ERROR;
        return drivers == 1 ? PortQuality.VALID : PortQuality.NO_SIGNAL;
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        List<EngineeringPort> ports = new ArrayList<>();
        for (Direction side : Direction.values()) {
            if (!connected(state, side)) continue;
            ports.add(new EngineeringPort(
                    "COPPER " + side.getName().toUpperCase(),
                    side,
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
                quality(level, pos, state)
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

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            player.displayClientMessage(Component.literal(
                    (topologyValid(state) ? "Copper Electrical Cable" : "TOPOLOGY ERROR — use Copper Junction for branches")
                            + " | " + PortDiagnostics.connectedCable(level, pos, state, PortDiagnostics.Domain.COPPER)
                            + " | V=" + voltage(level, pos) + "/15"
                            + " | drivers=" + driverCount(level, pos)
                            + " | quality=" + quality(level, pos, state)
                            + " | ports=" + engineeringPorts(state).size()
                            + " | " + NetworkKernel.summary(level, "copper")
            ), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
