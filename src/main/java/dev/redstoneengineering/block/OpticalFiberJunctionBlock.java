package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.core.port.*;
import dev.redstoneengineering.ui.FieldDeviceUi;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Fiber splice box: permits any bend/orientation but remains a two-ended
 * passive optical path. Use Optical Splitter for 1→2 branching.
 */
public class OpticalFiberJunctionBlock extends ConnectedCableBlock implements EngineeringPortProvider {
    private static final String KEY = "optical_junction";

    public OpticalFiberJunctionBlock(Properties p){super(p);}
    @Override public MapCodec<OpticalFiberJunctionBlock> codec(){return RedstoneEngineering.OPTICAL_FIBER_JUNCTION_CODEC.value();}
    @Override protected boolean canConnectTo(BlockGetter l,BlockPos p,Direction d,BlockState n){return TransmissionTopology.opticalPort(n,d);}

    public static void setOptical(Level level, BlockPos pos, int intensity, int channel, boolean valid) {
        int[] rt = RuntimeIntStore.get(level, KEY, pos, 3);
        rt[0] = valid ? Math.max(0, Math.min(15, intensity)) : 0;
        rt[1] = valid ? Math.max(0, Math.min(15, channel)) : 0;
        rt[2] = valid && rt[0] > 0 ? 1 : 0;
    }
    public static int intensity(Level level, BlockPos pos) { int[] r=RuntimeIntStore.peek(level,KEY,pos); return r==null?0:r[0]; }
    public static int channel(Level level, BlockPos pos) { int[] r=RuntimeIntStore.peek(level,KEY,pos); return r==null?0:r[1]; }
    public static boolean valid(Level level, BlockPos pos) { int[] r=RuntimeIntStore.peek(level,KEY,pos); return r!=null&&r.length>2&&r[2]==1; }

    @Override public java.util.List<EngineeringPort> engineeringPorts(BlockState state) {
        return OpticalPortSupport.connected(state, "OPTICAL SPLICE", PortKind.BUS, PortDirection.BIDIRECTIONAL);
    }
    @Override public java.util.Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        return engineeringPort(state, side).map(port -> new EngineeringPortSnapshot(port, intensity(level,pos), 0.0, 15.0,
                valid(level,pos) ? PortQuality.VALID : PortQuality.NO_SIGNAL));
    }

    @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState o,boolean m){
        super.onPlace(s,l,p,o,m);
        if(l instanceof ServerLevel sl)DomainNetwork.recomputeOptical(sl,p);
    }
    @Override protected void neighborChanged(BlockState s,Level l,BlockPos p,net.minecraft.world.level.block.Block b,BlockPos np,boolean m){
        super.neighborChanged(s,l,p,b,np,m);
        if(l instanceof ServerLevel sl)DomainNetwork.recomputeOptical(sl,p);
    }
    @Override protected void onRemove(BlockState s, Level l, BlockPos p, BlockState ns, boolean moved) {
        if (!s.is(ns.getBlock())) { RuntimeIntStore.remove(l, KEY, p); if (l instanceof ServerLevel sl) DomainNetwork.recomputeOpticalAround(sl,p); }
        super.onRemove(s, l, p, ns, moved);
    }
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult h){
        if(!l.isClientSide && pl instanceof ServerPlayer sp && !pl.isShiftKeyDown()) {
            FieldDeviceUi.open(sp,p);
        } else if(!l.isClientSide) {
            String payload = valid(l,p) ? " | I="+intensity(l,p)+"/15 | channel="+channel(l,p) : " | DARK";
            pl.displayClientMessage(Component.literal(topologyValid(s)?"Optical splice | ports="+connectionCount(s)+payload:"OPTICAL TOPOLOGY ERROR — use Optical Splitter for branching"),true);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
