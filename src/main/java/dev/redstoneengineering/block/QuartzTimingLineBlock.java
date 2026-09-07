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
import dev.redstoneengineering.physics.NetworkKernel;
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

/** Auto-connecting planar timing trace. Live clock data and source-ownership evidence are transient runtime state. */
public class QuartzTimingLineBlock extends SurfaceTraceBlock implements EngineeringPortProvider {
    private static final String KEY="quartz_trace";
    public QuartzTimingLineBlock(Properties p){super(p);}
    @Override public MapCodec<QuartzTimingLineBlock> codec(){return RedstoneEngineering.QUARTZ_TIMING_LINE_CODEC.value();}
    @Override protected boolean canConnectTo(BlockGetter l,BlockPos p,Direction d,BlockState n){return d.getAxis().isHorizontal()&&TransmissionTopology.quartzPort(n,d);}
    public static int periodTicks(int index){return switch(index){case 0->2;case 1->4;case 2->8;case 3->16;default->32;};}
    public static void setTiming(Level l,BlockPos p,boolean active,int periodTicks,boolean valid){
        NetworkKernel.ScanStats stats=NetworkKernel.stats(l,"quartz");
        int sources=valid?1:(stats.driverConflict()?stats.activeDrivers():0);
        setTiming(l,p,active,periodTicks,valid,sources);
    }
    public static void setTiming(Level l,BlockPos p,boolean active,int periodTicks,boolean valid,int sourceCount){int[]r=RuntimeIntStore.get(l,KEY,p,4);r[0]=active&&valid?1:0;r[1]=valid?Math.max(1,Math.min(4096,periodTicks)):0;r[2]=valid?1:0;r[3]=Math.max(0,sourceCount);}
    private static int[] snapshot(Level l,BlockPos p){int[]r=RuntimeIntStore.peek(l,KEY,p);return r!=null&&r.length==4?r:null;}
    public static boolean active(Level l,BlockPos p){int[]r=snapshot(l,p);return r!=null&&r[0]==1;}
    public static int period(Level l,BlockPos p){int[]r=snapshot(l,p);return r==null?0:r[1];}
    public static boolean valid(Level l,BlockPos p){int[]r=snapshot(l,p);return r!=null&&r[2]==1;}
    public static int sourceCount(Level l,BlockPos p){int[]r=snapshot(l,p);return r==null?0:r[3];}
    public static PortQuality quality(Level l,BlockPos p){int n=sourceCount(l,p);if(n==0)return PortQuality.NO_SIGNAL;if(n>1)return PortQuality.TOPOLOGY_ERROR;return valid(l,p)?PortQuality.VALID:PortQuality.NO_SIGNAL;}
    private static EngineeringPort port(Direction side){return new EngineeringPort("QUARTZ TIMING TRACE "+side.getName().toUpperCase(),side, EngineeringDomain.QUARTZ, PortKind.BUS, PortDirection.BIDIRECTIONAL,false,"clock");}
    @Override public List<EngineeringPort> engineeringPorts(BlockState s){List<EngineeringPort>ports=new ArrayList<>();for(Direction side:Direction.Plane.HORIZONTAL)if(SurfaceTraceBlock.connected(s,side))ports.add(port(side));return List.copyOf(ports);}
    @Override public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level l,BlockPos p,BlockState s,Direction side){Optional<EngineeringPort>d=engineeringPort(s,side);return d.map(port->new EngineeringPortSnapshot(port,active(l,p)?1.0:0.0,0.0,1.0,quality(l,p)));}
    @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState o,boolean m){super.onPlace(s,l,p,o,m);if(l instanceof ServerLevel sl)DomainNetwork.recomputeQuartz(sl,p);}
    @Override protected void neighborChanged(BlockState s,Level l,BlockPos p,net.minecraft.world.level.block.Block b,BlockPos np,boolean m){super.neighborChanged(s,l,p,b,np,m);if(l instanceof ServerLevel sl)DomainNetwork.recomputeQuartz(sl,p);}
    @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState ns,boolean m){if(!s.is(ns.getBlock())){RuntimeIntStore.remove(l,KEY,p);if(l instanceof ServerLevel sl)DomainNetwork.recomputeQuartzAround(sl,p);}super.onRemove(s,l,p,ns,m);}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult h){
        if(!l.isClientSide&&pl instanceof ServerPlayer serverPlayer){
            if(!pl.isShiftKeyDown()){FieldDeviceUi.open(serverPlayer,p);return InteractionResult.CONSUME;}
            int drivers=sourceCount(l,p);String timing=drivers==0?"NO CLOCK SOURCE":drivers>1?"CLOCK CONFLICT x"+drivers:(active(l,p)?"HIGH":"LOW")+" period="+period(l,p)+"t";
            pl.displayClientMessage(Component.literal("Quartz Timing Trace | "+timing+" | "+PortDiagnostics.surfaceTrace(l,p,s,PortDiagnostics.Domain.QUARTZ)),true);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
