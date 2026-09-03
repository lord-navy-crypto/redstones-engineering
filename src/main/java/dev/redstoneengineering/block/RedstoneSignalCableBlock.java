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
import dev.redstoneengineering.physics.NetworkKernel;
import dev.redstoneengineering.physics.RedstoneCableNetwork;
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

/** 3-D insulated 0..15 signal cable. Bends automatically; explicit Junctions provide branches. */
public class RedstoneSignalCableBlock extends ConnectedCableBlock implements EngineeringPortProvider {
    private static final String KEY = "redstone_cable";

    public RedstoneSignalCableBlock(Properties properties) { super(properties); }
    @Override public MapCodec<RedstoneSignalCableBlock> codec() { return RedstoneEngineering.REDSTONE_SIGNAL_CABLE_CODEC.value(); }
    @Override protected boolean canConnectTo(BlockGetter level, BlockPos pos, Direction direction, BlockState neighbor) { return TransmissionTopology.redstoneCablePort(neighbor, direction); }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        List<EngineeringPort> ports = new ArrayList<>();
        for (Direction side : Direction.values()) {
            if (connected(state, side)) {
                ports.add(new EngineeringPort(
                        "CABLE",
                        side,
                        EngineeringDomain.REDSTONE,
                        PortKind.REDSTONE_ANALOG,
                        PortDirection.BIDIRECTIONAL,
                        false,
                        "signal"
                ));
            }
        }
        return List.copyOf(ports);
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        return engineeringPort(state, side)
                .map(port -> EngineeringPortSnapshot.redstone(port, power(level, pos), PortQuality.VALID));
    }

    public static void setPower(Level level, BlockPos pos, int power) {
        RuntimeIntStore.get(level, KEY, pos, 1)[0] = Math.max(0, Math.min(15, power));
    }

    public static int power(Level level, BlockPos pos) {
        return RuntimeIntStore.get(level, KEY, pos, 1)[0];
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (level instanceof ServerLevel serverLevel) RedstoneCableNetwork.recompute(serverLevel, pos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, net.minecraft.world.level.block.Block block, BlockPos neighborPos, boolean moved) {
        super.neighborChanged(state, level, pos, block, neighborPos, moved);
        if (level instanceof ServerLevel serverLevel) RedstoneCableNetwork.recompute(serverLevel, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, KEY, pos);
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) player.displayClientMessage(Component.literal(
                (topologyValid(state) ? "Insulated Redstone Cable" : "TOPOLOGY ERROR — use Cable Junction for branches")
                        + " | " + PortDiagnostics.connectedCable(level, pos, state, PortDiagnostics.Domain.INSULATED_REDSTONE)
                        + " | engineeringPorts=" + engineeringPorts(state).size()
                        + " | signal=" + power(level, pos) + "/15 | " + NetworkKernel.summary(level, "redstone_cable")
        ), true);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
