package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.physics.NetworkKernel;
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

/**
 * Insulated 0..15 signal cable. Direct runs may branch in the horizontal plane;
 * vertical transitions are legal only through the unified Signal Junction Point.
 */
public class RedstoneSignalCableBlock extends ConnectedCableBlock implements EngineeringPortProvider {
    private static final String KEY = "redstone_cable";

    public RedstoneSignalCableBlock(Properties properties) { super(properties); }
    @Override public MapCodec<RedstoneSignalCableBlock> codec() { return RedstoneEngineering.REDSTONE_SIGNAL_CABLE_CODEC.value(); }
    @Override protected boolean canConnectTo(BlockGetter level, BlockPos pos, Direction direction, BlockState neighbor) {
        return TransmissionTopology.redstoneCablePort(level, pos, direction, neighbor);
    }
    @Override protected int maxConnections() { return 6; }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        List<EngineeringPort> ports = new ArrayList<>();
        for (Direction side : Direction.values()) {
            // Open N/E/S/W faces remain legitimate cable ports. UP/DOWN are only
            // exposed when a Signal Junction has created that vertical physical arm.
            if (side.getAxis() == Direction.Axis.Y && !connected(state, side)) continue;
            ports.add(new EngineeringPort(
                    "INSULATED SIGNAL " + side.getName().toUpperCase(), side,
                    EngineeringDomain.REDSTONE, PortKind.REDSTONE_ANALOG,
                    PortDirection.BIDIRECTIONAL, false, "signal"));
        }
        return List.copyOf(ports);
    }

    @Override public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        RedstoneCableNetwork.SourceEvidence evidence = RedstoneCableNetwork.sourceEvidence(level, pos);
        return engineeringPort(state, side).map(port -> EngineeringPortSnapshot.redstone(port, power(level, pos), evidence.quality()));
    }

    public static void setPower(Level level, BlockPos pos, int power) {
        RuntimeIntStore.get(level, KEY, pos, 1)[0] = Math.max(0, Math.min(15, power));
    }

    /** Observer-only cable value. Network recomputation owns creation of runtime state. */
    public static int power(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length < 1 ? 0 : runtime[0];
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (level instanceof ServerLevel serverLevel) RedstoneCableNetwork.recompute(serverLevel, pos);
    }

    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, net.minecraft.world.level.block.Block block, BlockPos neighborPos, boolean moved) {
        super.neighborChanged(state, level, pos, block, neighborPos, moved);
        if (level instanceof ServerLevel serverLevel) RedstoneCableNetwork.recompute(serverLevel, pos);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        boolean removed = !state.is(newState.getBlock());
        if (removed) {
            RuntimeIntStore.remove(level, KEY, pos);
            RedstoneCableNetwork.removeEvidence(level, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
        if (removed && level instanceof ServerLevel serverLevel) RedstoneCableNetwork.recomputeAround(serverLevel, pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                RedstoneCableNetwork.SourceEvidence evidence = RedstoneCableNetwork.sourceEvidence(level, pos);
                player.displayClientMessage(Component.literal(
                        "Insulated Redstone Cable"
                                + " | " + PortDiagnostics.connectedCable(level, pos, state, PortDiagnostics.Domain.INSULATED_REDSTONE)
                                + " | engineeringPorts=" + engineeringPorts(state).size()
                                + " | signal=" + power(level, pos) + "/15"
                                + " | sources=" + evidence.sourceCount() + " quality=" + evidence.quality()
                                + " | routing=PLANAR; vertical via Signal Junction Point"
                                + " | " + NetworkKernel.summary(level, "redstone_cable")
                ), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
