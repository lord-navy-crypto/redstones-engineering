package dev.redstoneengineering.physics;

import dev.redstoneengineering.block.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import java.util.*;

/** Guided solid-vibration model: slime is low damping, honey is high damping. */
public final class VibrationNetwork {
    private VibrationNetwork(){}
    public record Wave(int amplitude,int frequency,boolean valid){}
    public static void propagate(ServerLevel level, BlockPos source, int amplitude, int frequency){
        record Node(BlockPos p,int a){}
        ArrayDeque<Node>q=new ArrayDeque<>();Map<BlockPos,Integer>best=new HashMap<>();
        for(Direction d:Direction.values())q.add(new Node(source.relative(d),amplitude));
        int visited=0;
        while(!q.isEmpty()&&visited<NetworkKernel.MAX_NODES){Node n=q.removeFirst();if(n.a<=0||!level.hasChunkAt(n.p))continue;
            int prev=best.getOrDefault(n.p,-1);if(prev>=n.a)continue;best.put(n.p,n.a);visited++;
            var b=level.getBlockState(n.p).getBlock();int loss;
            if(b instanceof SlimeVibrationConduitBlock)loss=1;else if(b instanceof HoneyVibrationDamperBlock)loss=4;else if(b instanceof MechanicalVibrationReceiverBlock){InformationRuntime.write(level,"mech_wave",n.p,n.a,frequency,true,100);level.scheduleTick(n.p,b,1);continue;}else continue;
            int next=n.a-loss;for(Direction d:Direction.values())q.addLast(new Node(n.p.relative(d),next));
        }
        NetworkKernel.recordScan(level,"mechanical",visited,visited>=NetworkKernel.MAX_NODES);
    }
    public static Wave sample(Level l,BlockPos p){return new Wave(InformationRuntime.value(l,"mech_wave",p),InformationRuntime.aux(l,"mech_wave",p),InformationRuntime.valid(l,"mech_wave",p));}
}
