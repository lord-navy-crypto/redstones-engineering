package dev.redstoneengineering.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Runtime-only payload storage for Alpha 1.0 communication media. */
public final class InformationRuntime {
    private InformationRuntime() {}
    public static int[] payload(Level level, String medium, BlockPos pos) {
        // [0]=value/payload, [1]=aux(channel/frequency), [2]=valid, [3]=age/quality
        return RuntimeIntStore.get(level, "info:" + medium, pos, 4);
    }
    public static void write(Level level, String medium, BlockPos pos, int value, int aux, boolean valid, int quality) {
        int[] r = payload(level, medium, pos);
        r[0]=value; r[1]=aux; r[2]=valid?1:0; r[3]=quality;
    }
    public static int value(Level level,String medium,BlockPos pos){return payload(level,medium,pos)[0];}
    public static int aux(Level level,String medium,BlockPos pos){return payload(level,medium,pos)[1];}
    public static boolean valid(Level level,String medium,BlockPos pos){return payload(level,medium,pos)[2]!=0;}
    public static int quality(Level level,String medium,BlockPos pos){return payload(level,medium,pos)[3];}
    public static void clear(Level level,String medium,BlockPos pos){RuntimeIntStore.remove(level,"info:"+medium,pos);}
}
