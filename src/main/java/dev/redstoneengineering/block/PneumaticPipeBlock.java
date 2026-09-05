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
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.NetworkKernel;
import dev.redstoneengineering.physics.PneumaticNetwork;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/** Six-way pneumatic transport node. Every face is a bidirectional compressed-air bus port. */
public class PneumaticPipeBlock extends Block implements EngineeringPortProvider {
    public PneumaticPipeBlock(Properties properties) { super(properties); }

    @Override public MapCodec<PneumaticPipeBlock> codec() {
        return RedstoneEngineering.PNEUMATIC_PIPE_CODEC.value();
    }

    private static EngineeringPort port(Direction side) {
        return new EngineeringPort(
                "PNEUMATIC " + side.getName().toUpperCase(), side,
                EngineeringDomain.PNEUMATIC, PortKind.BUS, PortDirection.BIDIRECTIONAL,
                false, "pressure"
        );
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                port(Direction.DOWN), port(Direction.UP),
                port(Direction.NORTH), port(Direction.SOUTH),
                port(Direction.WEST), port(Direction.EAST)
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> descriptor = engineeringPort(state, side);
        if (descriptor.isEmpty()) return Optional.empty();
        int pressure = PneumaticNetwork.pressure(level, pos);
        return Optional.of(new EngineeringPortSnapshot(
                descriptor.get(), pressure, 0.0, 100.0,
                pressure > 0 ? PortQuality.VALID : PortQuality.NO_SIGNAL
        ));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (level instanceof ServerLevel server) PneumaticNetwork.recompute(server, pos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean moved) {
        if (level instanceof ServerLevel server) PneumaticNetwork.recompute(server, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            InformationRuntime.clear(level, "pneumatic", pos);
            PneumaticNetwork.recomputeAround(server, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                player.displayClientMessage(Component.literal(
                        "Pneumatic pressure=" + PneumaticNetwork.pressure(level, pos) + "/100 | "
                                + NetworkKernel.summary(level, "pneumatic")
                ), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
