package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.*;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Six-face optical receiver terminal; live payload remains transient runtime state. */
public class OpticalReceiverBlock extends DomainBlock implements EngineeringPortProvider {
    private static final String KEY = "optical_receiver";
    public OpticalReceiverBlock(Properties p) { super(p); }
    @Override public MapCodec<OpticalReceiverBlock> codec() { return RedstoneEngineering.OPTICAL_RECEIVER_CODEC.value(); }
    public static void setOptical(Level l, BlockPos p, int i, int c, boolean v) { int[] r=RuntimeIntStore.get(l,KEY,p,3);r[0]=v?Math.max(0,Math.min(15,i)):0;r[1]=v?Math.max(0,Math.min(15,c)):0;r[2]=v&&r[0]>0?1:0; }
    public static int intensity(Level l, BlockPos p) { int[] r=RuntimeIntStore.peek(l,KEY,p);return r==null?0:r[0]; }
    public static int channel(Level l, BlockPos p) { int[] r=RuntimeIntStore.peek(l,KEY,p);return r==null?0:r[1]; }
    public static boolean valid(Level l, BlockPos p) { int[] r=RuntimeIntStore.peek(l,KEY,p);return r!=null&&r.length>2&&r[2]==1; }
    @Override public List<EngineeringPort> engineeringPorts(BlockState s) { List<EngineeringPort> ports=new ArrayList<>();for(Direction d:Direction.values())ports.add(new EngineeringPort("OPTICAL RECEIVER",d,EngineeringDomain.OPTICAL,PortKind.MEASUREMENT,PortDirection.INPUT,false,"intensity"));return ports; }
    @Override public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level l,BlockPos p,BlockState s,Direction side){return engineeringPort(s,side).map(port->new EngineeringPortSnapshot(port,intensity(l,p),0.0,15.0,valid(l,p)?PortQuality.VALID:PortQuality.NO_SIGNAL));}
    @Override protected void neighborChanged(BlockState s,Level l,BlockPos p,Block nb,BlockPos np,boolean moved){if(l instanceof ServerLevel sl)DomainNetwork.recomputeOptical(sl,p);}
    @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moved){super.onPlace(s,l,p,old,moved);if(l instanceof ServerLevel sl)DomainNetwork.recomputeOptical(sl,p);}
    @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState ns,boolean moved){if(!s.is(ns.getBlock())){RuntimeIntStore.remove(l,KEY,p);if(l instanceof ServerLevel sl)DomainNetwork.recomputeOpticalAround(sl,p);}super.onRemove(s,l,p,ns,moved);}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult hit){if(!l.isClientSide&&pl instanceof ServerPlayer sp&&!pl.isShiftKeyDown())FieldDeviceUi.open(sp,p);else if(!l.isClientSide)pl.displayClientMessage(Component.literal(valid(l,p)?"Optical receiver | I="+intensity(l,p)+"/15 | channel="+channel(l,p):"Optical receiver | no valid light"),true);return InteractionResult.sidedSuccess(l.isClientSide);}
}
