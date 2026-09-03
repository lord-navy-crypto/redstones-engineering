package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.DomainNetwork;
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

/** Auto-connecting floor precision trace. Live value is runtime data, not BlockState. */
public class LapisSignalLineBlock extends SurfaceTraceBlock {
    private static final String KEY="lapis_trace";
    public LapisSignalLineBlock(Properties p){super(p);}
    @Override public MapCodec<LapisSignalLineBlock> codec(){return RedstoneEngineering.LAPIS_SIGNAL_LINE_CODEC.value();}
    @Override protected boolean canConnectTo(BlockGetter l,BlockPos p,Direction d,BlockState n){return d.getAxis()!=Direction.Axis.Y&&TransmissionTopology.lapisPort(n,d);}
    public static void setSignal(Level l,BlockPos p,int value,boolean valid){int[]r=RuntimeIntStore.get(l,KEY,p,2);r[0]=valid?Math.max(0,Math.min(100,value)):0;r[1]=valid?1:0;}
    public static int value(Level l,BlockPos p){return RuntimeIntStore.get(l,KEY,p,2)[0];}
    public static boolean valid(Level l,BlockPos p){return RuntimeIntStore.get(l,KEY,p,2)[1]==1;}
    @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moved){super.onPlace(s,l,p,old,moved);if(l instanceof ServerLevel sl)DomainNetwork.recomputeLapis(sl,p);}
    @Override protected void neighborChanged(BlockState s,Level l,BlockPos p,net.minecraft.world.level.block.Block nb,BlockPos np,boolean moved){super.neighborChanged(s,l,p,nb,np,moved);if(l instanceof ServerLevel sl)DomainNetwork.recomputeLapis(sl,p);}
    @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState ns,boolean moved){if(!s.is(ns.getBlock()))RuntimeIntStore.remove(l,KEY,p);super.onRemove(s,l,p,ns,moved);}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult hit){if(!l.isClientSide)pl.displayClientMessage(Component.literal(valid(l,p)?"Lapis precision trace = "+String.format("%.2f",value(l,p)/100.0):"Lapis precision trace = INVALID / source conflict"),true);return InteractionResult.sidedSuccess(l.isClientSide);}
}
