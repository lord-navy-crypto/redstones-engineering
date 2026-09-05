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

import java.util.List;
import java.util.Optional;

/** Auto-connecting floor precision trace. Live value is runtime data, not BlockState. */
public class LapisSignalLineBlock extends SurfaceTraceBlock implements EngineeringPortProvider {
    private static final String KEY="lapis_trace";
    public LapisSignalLineBlock(Properties p){super(p);}
    @Override public MapCodec<LapisSignalLineBlock> codec(){return RedstoneEngineering.LAPIS_SIGNAL_LINE_CODEC.value();}
    @Override protected boolean canConnectTo(BlockGetter l,BlockPos p,Direction d,BlockState n){return d.getAxis()!=Direction.Axis.Y&&TransmissionTopology.lapisPort(n,d);}
    public static void setSignal(Level l,BlockPos p,int value,boolean valid){int[]r=RuntimeIntStore.get(l,KEY,p,2);r[0]=valid?Math.max(0,Math.min(100,value)):0;r[1]=valid?1:0;}
    public static int value(Level l,BlockPos p){return RuntimeIntStore.get(l,KEY,p,2)[0];}
    public static boolean valid(Level l,BlockPos p){return RuntimeIntStore.get(l,KEY,p,2)[1]==1;}
    private static EngineeringPort port(Direction side){return new EngineeringPort("LAPIS PRECISION BUS",side, EngineeringDomain.LAPIS, PortKind.BUS, PortDirection.BIDIRECTIONAL,false,"precision");}
    @Override public List<EngineeringPort> engineeringPorts(BlockState s){return List.of(port(Direction.NORTH),port(Direction.SOUTH),port(Direction.WEST),port(Direction.EAST));}
    @Override public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level l,BlockPos p,BlockState s,Direction side){Optional<EngineeringPort>d=engineeringPort(s,side);return d.map(port->new EngineeringPortSnapshot(port,value(l,p),0.0,100.0,valid(l,p)?PortQuality.VALID:PortQuality.NO_SIGNAL));}
    @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moved){super.onPlace(s,l,p,old,moved);if(l instanceof ServerLevel sl)DomainNetwork.recomputeLapis(sl,p);}
    @Override protected void neighborChanged(BlockState s,Level l,BlockPos p,net.minecraft.world.level.block.Block nb,BlockPos np,boolean moved){super.neighborChanged(s,l,p,nb,np,moved);if(l instanceof ServerLevel sl)DomainNetwork.recomputeLapis(sl,p);}
    @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState ns,boolean moved){if(!s.is(ns.getBlock())){RuntimeIntStore.remove(l,KEY,p);if(l instanceof ServerLevel sl)DomainNetwork.recomputeLapisAround(sl,p);}super.onRemove(s,l,p,ns,moved);}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult hit){
        if(!l.isClientSide && pl instanceof ServerPlayer serverPlayer){
            if(!pl.isShiftKeyDown()){FieldDeviceUi.open(serverPlayer,p);return InteractionResult.CONSUME;}
            String signal=valid(l,p)?"value="+String.format("%.2f",value(l,p)/100.0):"INVALID / source conflict";
            pl.displayClientMessage(Component.literal("Lapis Precision Trace | "+signal+" | "+PortDiagnostics.surfaceTrace(l,p,s,PortDiagnostics.Domain.LAPIS)),true);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
