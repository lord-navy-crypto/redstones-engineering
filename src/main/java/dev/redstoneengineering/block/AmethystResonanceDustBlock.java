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
import dev.redstoneengineering.physics.DomainNetwork;
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

/** Redstone-like resonance dust: automatic N/E/S/W topology, runtime frequency/amplitude payload. */
public class AmethystResonanceDustBlock extends SurfaceTraceBlock implements EngineeringPortProvider {
    private static final String KEY = "amethyst_trace";

    public AmethystResonanceDustBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<AmethystResonanceDustBlock> codec() {
        return RedstoneEngineering.AMETHYST_RESONANCE_DUST_CODEC.value();
    }

    @Override
    protected boolean canConnectTo(BlockGetter level, BlockPos pos, Direction direction, BlockState neighbor) {
        return direction.getAxis() != Direction.Axis.Y && TransmissionTopology.amethystPort(neighbor, direction);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        List<EngineeringPort> ports = new ArrayList<>();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (SurfaceTraceBlock.connected(state, side)) ports.add(resonancePort(side));
        }
        return List.copyOf(ports);
    }

    private static EngineeringPort resonancePort(Direction side) {
        return new EngineeringPort("RESONANCE TRACE " + side.getName().toUpperCase(), side, EngineeringDomain.AMETHYST,
                PortKind.BUS, PortDirection.BIDIRECTIONAL, false, "amplitude");
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        int amplitude = amplitude(level, pos);
        return Optional.of(new EngineeringPortSnapshot(
                port.get(), amplitude, 0.0, 15.0,
                active(level, pos) ? PortQuality.VALID : PortQuality.NO_SIGNAL));
    }

    public static void setResonance(Level level, BlockPos pos, int frequency, int amplitude) {
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, 2);
        runtime[0] = amplitude > 0 ? Math.max(1, Math.min(15, frequency)) : 0;
        runtime[1] = Math.max(0, Math.min(15, amplitude));
    }

    public static boolean active(Level level, BlockPos pos) {
        return RuntimeIntStore.get(level, KEY, pos, 2)[1] > 0;
    }

    public static int frequency(Level level, BlockPos pos) {
        return RuntimeIntStore.get(level, KEY, pos, 2)[0];
    }

    public static int amplitude(Level level, BlockPos pos) {
        return RuntimeIntStore.get(level, KEY, pos, 2)[1];
    }

    @Override
    protected void neighborChanged(
            BlockState state, Level level, BlockPos pos, net.minecraft.world.level.block.Block block,
            BlockPos neighborPos, boolean movedByPiston
    ) {
        super.neighborChanged(state, level, pos, block, neighborPos, movedByPiston);
        if (level instanceof ServerLevel serverLevel) DomainNetwork.recomputeAmethyst(serverLevel, pos);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel serverLevel) DomainNetwork.recomputeAmethyst(serverLevel, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        boolean removed = !state.is(newState.getBlock());
        if (removed) RuntimeIntStore.remove(level, KEY, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (removed && level instanceof ServerLevel serverLevel) recomputeAround(serverLevel, pos);
    }

    /** Re-evaluate every horizontal component created when a trace is cut. */
    private static void recomputeAround(ServerLevel level, BlockPos changedPos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = changedPos.relative(direction);
            var block = level.getBlockState(neighbor).getBlock();
            if (block instanceof AmethystResonanceDustBlock || block instanceof AmethystResonatorBlock) {
                DomainNetwork.recomputeAmethyst(level, neighbor);
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                player.displayClientMessage(Component.literal(
                        "Amethyst resonance dust | " + (active(level, pos) ? "EVENT" : "idle")
                                + " | f=" + frequency(level, pos) + " | A=" + amplitude(level, pos) + "/15"), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
