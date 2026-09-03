package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Explicit multi-port copper splice/branch box. */
public class CopperCableJunctionBlock extends ConnectedCableBlock {
    private static final String KEY = "copper_junction";

    public CopperCableJunctionBlock(Properties p){super(p);}
    @Override protected int maxConnections(){return 6;}
    @Override public MapCodec<CopperCableJunctionBlock> codec(){return RedstoneEngineering.COPPER_CABLE_JUNCTION_CODEC.value();}
    @Override protected boolean canConnectTo(BlockGetter l,BlockPos p,Direction d,BlockState n){return TransmissionTopology.copperPort(n,d);}

    public static void setVoltage(Level level, BlockPos pos, int voltage) {
        RuntimeIntStore.get(level, KEY, pos, 1)[0] = Math.max(0, Math.min(15, voltage));
    }
    public static int voltage(Level level, BlockPos pos) {
        return RuntimeIntStore.get(level, KEY, pos, 1)[0];
    }

    @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState o,boolean m){
        super.onPlace(s,l,p,o,m);
        if(l instanceof ServerLevel sl)DomainNetwork.recomputeCopper(sl,p);
    }
    @Override protected void neighborChanged(BlockState s,Level l,BlockPos p,net.minecraft.world.level.block.Block b,BlockPos np,boolean m){
        super.neighborChanged(s,l,p,b,np,m);
        if(l instanceof ServerLevel sl)DomainNetwork.recomputeCopper(sl,p);
    }
    @Override protected void onRemove(BlockState s, Level l, BlockPos p, BlockState ns, boolean moved) {
        if (!s.is(ns.getBlock())) RuntimeIntStore.remove(l, KEY, p);
        super.onRemove(s, l, p, ns, moved);
    }
}
