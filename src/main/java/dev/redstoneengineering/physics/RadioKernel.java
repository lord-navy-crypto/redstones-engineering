package dev.redstoneengineering.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Low-bandwidth industrial-style radio abstraction.
 * Payload and link quality are separate. Same-channel collisions invalidate the frame;
 * adjacent-channel aggressors, distance, obstacles and deterministic fading reduce quality.
 */
public final class RadioKernel {
    private RadioKernel() {}
    public static final int RANGE = 32;
    public static final int MIN_DECODE_QUALITY = 20;
    public record Reception(int value,int quality,int drivers,boolean valid,boolean collision,int interference,int obstacles,int latencyTicks){}
    private record Tx(int channel,int payload){}
    private static final Map<Level, Map<Long, Tx>> TX = new WeakHashMap<>();

    public static synchronized void updateTransmitter(Level level, BlockPos pos, int channel, int payload) {
        Map<Long, Tx> m = TX.computeIfAbsent(level, l -> new HashMap<>());
        if (payload > 0) m.put(pos.asLong(), new Tx(channel, Math.max(0,Math.min(15,payload))));
        else m.remove(pos.asLong());
    }
    public static synchronized void removeTransmitter(Level level, BlockPos pos) { Map<Long,Tx>m=TX.get(level);if(m!=null)m.remove(pos.asLong()); }

    private static int obstacleSamples(Level level, BlockPos a, BlockPos b){
        double dx=b.getX()-a.getX(),dy=b.getY()-a.getY(),dz=b.getZ()-a.getZ();
        int steps=Math.max(1,(int)Math.ceil(Math.sqrt(dx*dx+dy*dy+dz*dz))); int hits=0;
        for(int i=1;i<steps;i++){double t=i/(double)steps;BlockPos p=BlockPos.containing(a.getX()+0.5+dx*t,a.getY()+0.5+dy*t,a.getZ()+0.5+dz*t);if(level.hasChunkAt(p)&&!level.getBlockState(p).isAir())hits++;}
        return hits;
    }
    private static int deterministicFade(Level level, BlockPos tx, BlockPos rx){long h=tx.asLong()*31L+rx.asLong()*17L+(level.getGameTime()/20L);return (int)Math.floorMod(h,7L);}

    public static synchronized Reception receivePacket(Level level, BlockPos rx, int channel) {
        Map<Long,Tx>m=TX.get(level);if(m==null)return new Reception(0,0,0,false,false,0,0,0);
        int drivers=0,value=0,bestQuality=0,bestObstacles=0,adjacent=0,bestLatency=0;
        for(var e:m.entrySet()){
            Tx tx=e.getValue();BlockPos p=BlockPos.of(e.getKey());
            long dx=p.getX()-rx.getX(),dy=p.getY()-rx.getY(),dz=p.getZ()-rx.getZ();double dist=Math.sqrt(dx*dx+dy*dy+dz*dz);if(dist>RANGE)continue;
            if(Math.abs(tx.channel-channel)==1){adjacent++;continue;}
            if(tx.channel!=channel)continue;
            drivers++;value=tx.payload;
            int obstacles=obstacleSamples(level,p,rx);
            int distanceLoss=(int)Math.round(55.0*dist/RANGE);
            int obstacleLoss=Math.min(25,obstacles*2);
            int fade=deterministicFade(level,p,rx);
            int q=Math.max(0,100-distanceLoss-obstacleLoss-fade);
            if(q>bestQuality){bestQuality=q;bestObstacles=obstacles;bestLatency=2+(int)Math.ceil(dist/12.0);}
        }
        int interferencePenalty=Math.min(30,adjacent*8);
        int quality=Math.max(0,bestQuality-interferencePenalty);
        NetworkKernel.recordDriverState(level,"radio:"+channel,drivers);
        boolean collision=drivers>1;
        boolean valid=drivers==1&&!collision&&quality>=MIN_DECODE_QUALITY;
        return new Reception(valid?value:0,collision?0:quality,drivers,valid,collision,adjacent,bestObstacles,bestLatency);
    }
    public static int receive(Level level,BlockPos rx,int channel){return receivePacket(level,rx,channel).value();}
}
