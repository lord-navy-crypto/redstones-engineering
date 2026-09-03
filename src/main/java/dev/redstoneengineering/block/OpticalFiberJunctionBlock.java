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

/**
 * Fiber splice box: permits any bend/orientation but remains a two-ended
 * passive optical path. Use Optical Splitter for 1→2 branching.
 */
public class OpticalFiberJunctionBlock extends ConnectedCableBlock {
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
    public static int intensity(Level level, BlockPos pos) { return RuntimeIntStore.get(level, KEY, pos, 3)[0]; }
    public static int channel(Level level, BlockPos pos) { return RuntimeIntStore.get(level, KEY, pos, 3)[1]; }
    public static boolean valid(Level level, BlockPos pos) { return RuntimeIntStore.get(level, KEY, pos, 3)[2] == 1; }

    @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState o,boolean m){
        super.onPlace(s,l,p,o,m);
        if(l instanceof ServerLevel sl)DomainNetwork.recomputeOptical(sl,p);
    }
    @Override protected void neighborChanged(BlockState s,Level l,BlockPos p,net.minecraft.world.level.block.Block b,BlockPos np,boolean m){
        super.neighborChanged(s,l,p,b,np,m);
        if(l instanceof ServerLevel sl)DomainNetwork.recomputeOptical(sl,p);
    }
    @Override protected void onRemove(BlockState s, Level l, BlockPos p, BlockState ns, boolean moved) {
        if (!s.is(ns.getBlock())) RuntimeIntStore.remove(l, KEY, p);
        super.onRemove(s, l, p, ns, moved);
    }
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult h){
        if(!l.isClientSide) {
            String payload = valid(l,p) ? " | I="+intensity(l,p)+"/15 | channel="+channel(l,p) : " | DARK";
            pl.displayClientMessage(Component.literal(topologyValid(s)?"Optical splice | ports="+connectionCount(s)+payload:"OPTICAL TOPOLOGY ERROR — use Optical Splitter for branching"),true);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
