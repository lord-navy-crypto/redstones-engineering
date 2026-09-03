package dev.redstoneengineering.physics;

import dev.redstoneengineering.block.EightBitDataBusBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.*;

/** Bounded 8-bit bus resolver. Multiple different drivers produce BUS-CONFLICT instead of last-writer-wins. */
public final class DataBusNetwork {
    private DataBusNetwork() {}
    public static final int MAX_NODES = NetworkKernel.MAX_NODES;

    public static Set<BlockPos> collect(Level level, BlockPos start){
        Set<BlockPos> seen=new HashSet<>(); ArrayDeque<BlockPos> q=new ArrayDeque<>();
        if(!(level.getBlockState(start).getBlock() instanceof EightBitDataBusBlock)) return seen;
        q.add(start);
        while(!q.isEmpty() && seen.size()<MAX_NODES){
            BlockPos p=q.removeFirst(); if(!seen.add(p)) continue;
            for(Direction d:Direction.values()){
                BlockPos n=p.relative(d);
                if(!level.hasChunkAt(n)) continue;
                if(level.getBlockState(n).getBlock() instanceof EightBitDataBusBlock && !seen.contains(n)) q.addLast(n);
            }
        }
        return seen;
    }

    public static void drive(ServerLevel level, BlockPos start, BlockPos driver, int value, boolean valid){
        Set<BlockPos> nodes=collect(level,start); if(nodes.isEmpty()) return;
        String key="bus8_driver:"+driver.asLong();
        for(BlockPos p:nodes) InformationRuntime.write(level,key,p,value & 0xFF,0,valid,100);
        resolve(level,nodes);
    }

    public static void resolve(ServerLevel level, Set<BlockPos> nodes){
        Set<Integer> values=new HashSet<>();
        // Explicit bus nodes may receive direct driver payloads from adjacent processors.
        for(BlockPos p:nodes){
            for(Direction d:Direction.values()){
                BlockPos n=p.relative(d);
                if(InformationRuntime.valid(level,"bus8_out",n)) values.add(InformationRuntime.value(level,"bus8_out",n)&0xFF);
            }
        }
        boolean ok=values.size()<=1;
        int value=values.isEmpty()?0:values.iterator().next();
        NetworkKernel.recordDriverState(level,"bus8",values.size());
        int now=(int)Math.min(Integer.MAX_VALUE,level.getGameTime());
        for(BlockPos p:nodes){ InformationRuntime.write(level,"bus8",p,ok?value:0,0,ok,ok?100:0); int[]d=RuntimeIntStore.get(level,"bus8_diag",p,8); d[0]++; d[1]=nodes.size(); d[2]=values.size(); d[3]=ok?1:0; if(d[4]>0)d[5]=Math.max(1,now-d[4]); d[4]=now; d[6]=value; d[7]=d[5]==0?0:Math.min(100,100/Math.max(1,d[5])); level.updateNeighborsAt(p, level.getBlockState(p).getBlock()); }
    }

    public static int sample(Level level,BlockPos p){
        if(level.getBlockState(p).getBlock() instanceof EightBitDataBusBlock) return InformationRuntime.value(level,"bus8",p)&0xFF;
        return InformationRuntime.value(level,"bus8_out",p)&0xFF;
    }
    public static String diagnostics(Level level,BlockPos p){int[]d=RuntimeIntStore.get(level,"bus8_diag",p,8);return "updates="+d[0]+" nodes="+d[1]+" drivers="+d[2]+" interarrival="+d[5]+"t activity≈"+d[7]+"%";}
    public static boolean valid(Level level,BlockPos p){
        if(level.getBlockState(p).getBlock() instanceof EightBitDataBusBlock) return InformationRuntime.valid(level,"bus8",p);
        return InformationRuntime.valid(level,"bus8_out",p);
    }
}
