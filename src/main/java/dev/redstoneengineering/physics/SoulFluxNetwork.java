package dev.redstoneengineering.physics;

import dev.redstoneengineering.block.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import java.util.*;

/** Minecraft-fictional persistent state model. Soul soil transports; soul sand stores. */
public final class SoulFluxNetwork {
    private SoulFluxNetwork(){}
    public static void inject(ServerLevel level,BlockPos start,int amount){
        Set<BlockPos>seen=new HashSet<>();ArrayDeque<BlockPos>q=new ArrayDeque<>();q.add(start);int remaining=Math.max(0,amount);
        while(!q.isEmpty()&&seen.size()<NetworkKernel.MAX_NODES&&remaining>0){BlockPos p=q.removeFirst();if(!seen.add(p)||!level.hasChunkAt(p))continue;var b=level.getBlockState(p).getBlock();
            if(b instanceof SoulSandReservoirBlock){int old=InformationRuntime.value(level,"soul_store",p);int add=Math.min(100-old,remaining);InformationRuntime.write(level,"soul_store",p,old+add,0,true,100);level.updateNeighborsAt(p,b);remaining-=add;}
            if(!(b instanceof SoulSoilConduitBlock||b instanceof SoulSandReservoirBlock))continue;
            InformationRuntime.write(level,"soul_flux",p,Math.max(0,remaining),0,true,100);level.updateNeighborsAt(p,b);
            for(Direction d:Direction.values())q.addLast(p.relative(d));
            if(b instanceof SoulSoilConduitBlock)remaining=Math.max(0,remaining-1);else remaining=Math.max(0,remaining-3);
        }
        NetworkKernel.recordScan(level,"soul",seen.size(),seen.size()>=NetworkKernel.MAX_NODES);
    }
    public static int charge(Level l,BlockPos p){var b=l.getBlockState(p).getBlock();if(b instanceof SoulSandReservoirBlock)return InformationRuntime.value(l,"soul_store",p);return InformationRuntime.value(l,"soul_flux",p);}
    public static void decay(Level l,BlockPos p){int c=charge(l,p);if(c>0){String k=l.getBlockState(p).getBlock() instanceof SoulSandReservoirBlock?"soul_store":"soul_flux";InformationRuntime.write(l,k,p,c-1,0,true,100);}}
}
