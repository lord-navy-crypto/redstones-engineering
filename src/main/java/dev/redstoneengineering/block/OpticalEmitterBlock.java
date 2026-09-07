package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.*;
import dev.redstoneengineering.physics.DomainNetwork;
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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Six-face optical source. The source itself is the physical emitter terminal. */
public class OpticalEmitterBlock extends DomainBlock implements EngineeringPortProvider {
    public static final IntegerProperty INTENSITY = IntegerProperty.create("intensity", 0, 15);
    public static final IntegerProperty CHANNEL = IntegerProperty.create("channel", 0, 15);

    public OpticalEmitterBlock(Properties p) {
        super(p);
        registerDefaultState(defaultBlockState().setValue(INTENSITY, 8).setValue(CHANNEL, 0));
    }

    @Override public MapCodec<OpticalEmitterBlock> codec() { return RedstoneEngineering.OPTICAL_EMITTER_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(INTENSITY, CHANNEL); }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        List<EngineeringPort> ports = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            ports.add(new EngineeringPort("OPTICAL EMISSION", direction,
                    EngineeringDomain.OPTICAL, PortKind.BUS, PortDirection.OUTPUT, false, "intensity"));
        }
        return ports;
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        // A configured zero-intensity source is still a valid source setting; downstream
        // passive fiber correctly reports DARK/NO_SIGNAL because no carrier is present.
        return engineeringPort(state, side).map(port -> new EngineeringPortSnapshot(
                port, state.getValue(INTENSITY), 0.0, 15.0, PortQuality.VALID));
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (level instanceof ServerLevel serverLevel) DomainNetwork.recomputeOptical(serverLevel, pos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean moved) {
        if (level instanceof ServerLevel serverLevel) DomainNetwork.recomputeOptical(serverLevel, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock()) && level instanceof ServerLevel serverLevel) {
            DomainNetwork.recomputeOpticalAround(serverLevel, pos);
        }
        super.onRemove(state, level, pos, next, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && !player.isShiftKeyDown()) {
            FieldDeviceUi.open(serverPlayer, pos);
        } else if (!level.isClientSide) {
            BlockState next = player.isShiftKeyDown()
                    ? state.setValue(CHANNEL, (state.getValue(CHANNEL) + 1) % 16)
                    : state.setValue(INTENSITY, state.getValue(INTENSITY) >= 15 ? 0 : state.getValue(INTENSITY) + 1);
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            if (level instanceof ServerLevel serverLevel) DomainNetwork.recomputeOptical(serverLevel, pos);
            player.displayClientMessage(Component.literal(
                    "Optical emitter | intensity=" + next.getValue(INTENSITY) + "/15 | channel=" + next.getValue(CHANNEL)), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
