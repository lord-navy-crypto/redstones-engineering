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
import dev.redstoneengineering.physics.RedstoneCableNetwork;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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

/** Explicit multi-port splice/branch for the insulated 0..15 redstone domain. */
public class RedstoneCableJunctionBlock extends ConnectedCableBlock implements EngineeringPortProvider {
    private static final String KEY = "redstone_junction";

    public RedstoneCableJunctionBlock(Properties properties) { super(properties); }
    @Override protected int maxConnections() { return 6; }
    @Override public MapCodec<RedstoneCableJunctionBlock> codec() { return RedstoneEngineering.REDSTONE_CABLE_JUNCTION_CODEC.value(); }
    @Override protected boolean canConnectTo(BlockGetter level, BlockPos pos, Direction direction, BlockState neighbor) {
        return TransmissionTopology.redstoneCablePort(neighbor, direction);
    }

    public static void setPower(Level level, BlockPos pos, int power) {
        RuntimeIntStore.get(level, KEY, pos, 1)[0] = Math.max(0, Math.min(15, power));
    }

    public static int power(Level level, BlockPos pos) { return RuntimeIntStore.get(level, KEY, pos, 1)[0]; }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        List<EngineeringPort> ports = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            if (!connected(state, direction)) continue;
            ports.add(new EngineeringPort(
                    "INSULATED BRANCH " + direction.getName().toUpperCase(), direction,
                    EngineeringDomain.REDSTONE, PortKind.REDSTONE_ANALOG,
                    PortDirection.BIDIRECTIONAL, false, "signal"));
        }
        return List.copyOf(ports);
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        return port.map(value -> EngineeringPortSnapshot.redstone(value, power(level, pos), PortQuality.VALID));
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (level instanceof ServerLevel server) RedstoneCableNetwork.recompute(server, pos);
    }

    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, net.minecraft.world.level.block.Block neighbor, BlockPos neighborPos, boolean moved) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, moved);
        if (level instanceof ServerLevel server) RedstoneCableNetwork.recompute(server, pos);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        boolean removed = !state.is(newState.getBlock());
        if (removed) RuntimeIntStore.remove(level, KEY, pos);
        super.onRemove(state, level, pos, newState, moved);
        if (removed && level instanceof ServerLevel server) RedstoneCableNetwork.recomputeAround(server, pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                player.displayClientMessage(Component.literal(
                        "Insulated Redstone Junction | ports=" + connectionCount(state)
                                + " | signal=" + power(level, pos) + "/15"), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
