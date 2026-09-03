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

/** Auto-connecting floor timing trace. Live clock data is runtime state. */
public class QuartzTimingLineBlock extends SurfaceTraceBlock {
    private static final String KEY="quartz_trace";
    public QuartzTimingLineBlock(Properties p){super(p);}
    @Override public MapCodec<QuartzTimingLineBlock> codec(){return RedstoneEngineering.QUARTZ_TIMING_LINE_CODEC.value();}
    @Override protected boolean canConnectTo(BlockGetter l,BlockPos p,Direction d,BlockState n){return d.getAxis().isHorizontal()&&TransmissionTopology.quartzPort(n,d);}

    /** Player-facing oscillator presets. Processed clocks may use any bounded tick period. */
    public static int periodTicks(int index){return switch(index){case 0->2;case 1->4;case 2->8;case 3->16;default->32;};}

    public static void setTiming(Level l,BlockPos p,boolean active,int periodTicks,boolean valid){
        int[]r=RuntimeIntStore.get(l,KEY,p,3);
        r[0]=active&&valid?1:0;
        r[1]=valid?Math.max(1,Math.min(4096,periodTicks)):0;
        r[2]=valid?1:0;
    }
    public static boolean active(Level l,BlockPos p){return RuntimeIntStore.get(l,KEY,p,3)[0]==1;}
    public static int period(Level l,BlockPos p){return RuntimeIntStore.get(l,KEY,p,3)[1];}
    public static boolean valid(Level l,BlockPos p){return RuntimeIntStore.get(l,KEY,p,3)[2]==1;}

    @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState o,boolean m){super.onPlace(s,l,p,o,m);if(l instanceof ServerLevel sl)DomainNetwork.recomputeQuartz(sl,p);}
    @Override protected void neighborChanged(BlockState s,Level l,BlockPos p,net.minecraft.world.level.block.Block b,BlockPos np,boolean m){super.neighborChanged(s,l,p,b,np,m);if(l instanceof ServerLevel sl)DomainNetwork.recomputeQuartz(sl,p);}
    @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState ns,boolean m){if(!s.is(ns.getBlock()))RuntimeIntStore.remove(l,KEY,p);super.onRemove(s,l,p,ns,m);}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult h){
        if(!l.isClientSide)pl.displayClientMessage(Component.literal(valid(l,p)?"Quartz timing trace | "+(active(l,p)?"HIGH":"LOW")+" | period="+period(l,p)+"t":"Quartz timing trace | INVALID / clock-domain conflict"),true);
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
