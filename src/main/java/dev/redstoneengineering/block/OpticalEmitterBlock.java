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
import net.minecraft.world.level.BlockGetter;
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

    public OpticalEmitterBlock(Properties p) { super(p); registerDefaultState(defaultBlockState().setValue(INTENSITY, 8).setValue(CHANNEL, 0)); }
    @Override public MapCodec<OpticalEmitterBlock> codec() { return RedstoneEngineering.OPTICAL_EMITTER_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(INTENSITY, CHANNEL); }
    @Override public List<EngineeringPort> engineeringPorts(BlockState s) {
        List<EngineeringPort> ports = new ArrayList<>();
        for (Direction d : Direction.values()) ports.add(new EngineeringPort("OPTICAL EMISSION", d,
                EngineeringDomain.OPTICAL, PortKind.BUS, PortDirection.OUTPUT, false, "intensity"));
        return ports;
    }
    @Override public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level l, BlockPos p, BlockState s, Direction side) {
        return engineeringPort(s, side).map(port -> new EngineeringPortSnapshot(port, s.getValue(INTENSITY), 0.0, 15.0,
                s.getValue(INTENSITY) > 0 ? PortQuality.VALID : PortQuality.NO_SIGNAL));
    }
    @Override protected void onPlace(BlockState s, Level l, BlockPos p, BlockState old, boolean moved) {
        super.onPlace(s, l, p, old, moved);
        if (l instanceof ServerLevel sl) DomainNetwork.recomputeOptical(sl, p);
    }
    @Override protected void neighborChanged(BlockState s, Level l, BlockPos p, Block nb, BlockPos np, boolean moved) {
        if (l instanceof ServerLevel sl) DomainNetwork.recomputeOptical(sl, p);
    }
    @Override protected void onRemove(BlockState s, Level l, BlockPos p, BlockState ns, boolean moved) {
        if (!s.is(ns.getBlock()) && l instanceof ServerLevel sl) DomainNetwork.recomputeOpticalAround(sl, p);
        super.onRemove(s, l, p, ns, moved);
    }
    @Override protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player pl, BlockHitResult hit) {
        if (!l.isClientSide && pl instanceof ServerPlayer sp && !pl.isShiftKeyDown()) FieldDeviceUi.open(sp, p);
        else if (!l.isClientSide) {
            BlockState n = pl.isShiftKeyDown()
                    ? s.setValue(CHANNEL, (s.getValue(CHANNEL) + 1) % 16)
                    : s.setValue(INTENSITY, s.getValue(INTENSITY) >= 15 ? 0 : s.getValue(INTENSITY) + 1);
            l.setBlock(p, n, Block.UPDATE_CLIENTS);
            if (l instanceof ServerLevel sl) DomainNetwork.recomputeOptical(sl, p);
            pl.displayClientMessage(Component.literal("Optical emitter | intensity=" + n.getValue(INTENSITY) + "/15 | channel=" + n.getValue(CHANNEL)), true);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
