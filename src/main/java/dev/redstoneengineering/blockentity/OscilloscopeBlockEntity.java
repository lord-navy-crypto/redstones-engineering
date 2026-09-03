package dev.redstoneengineering.blockentity;

import dev.redstoneengineering.RedstoneEngineering;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Two-channel scope with trigger, cursors and timing measurements. Runtime/history stays in the block entity. */
public class OscilloscopeBlockEntity extends BlockEntity {
    private static final int CHANNELS=2, CAPACITY=32;
    private final int[][] history=new int[CHANNELS][CAPACITY];
    private int index=0,count=0;
    private int triggerLevel=8, triggerChannel=0, triggerMode=1; // 0 free, 1 rising, 2 falling
    private boolean armed=true, triggered=false;
    private int cursorA=0, cursorB=8;
    private int lastA=-1,lastB=-1, samplesSinceTrigger=0;

    public OscilloscopeBlockEntity(BlockPos pos, BlockState state){super(RedstoneEngineering.OSCILLOSCOPE_BLOCK_ENTITY.get(),pos,state);clearHistoryOnly();}

    public void addSample(int a,int b){
        int na=normalize(a), nb=normalize(b), current=triggerChannel==0?na:nb, before=triggerChannel==0?lastA:lastB;
        boolean edge=before>=0&&current>=0&&((triggerMode==1&&before<triggerLevel&&current>=triggerLevel)||(triggerMode==2&&before>=triggerLevel&&current<triggerLevel));
        if(triggerMode==0||armed||triggered){
            history[0][index]=na; history[1][index]=nb;
            if(armed&&triggerMode!=0&&edge){triggered=true;armed=false;samplesSinceTrigger=0;}
            if(triggered)samplesSinceTrigger++;
            index=(index+1)%CAPACITY; count=Math.min(CAPACITY,count+1);
            if(triggered&&samplesSinceTrigger>=CAPACITY/2)triggered=false;
        }
        lastA=na;lastB=nb;setChanged();
    }
    private static int normalize(int v){return v<0?-1:Math.max(0,Math.min(15,v));}
    private void clearHistoryOnly(){for(int c=0;c<CHANNELS;c++)for(int i=0;i<CAPACITY;i++)history[c][i]=-1;index=0;count=0;lastA=-1;lastB=-1;samplesSinceTrigger=0;}
    public void clear(){clearHistoryOnly();armed=true;triggered=false;setChanged();}
    public void arm(){armed=true;triggered=false;samplesSinceTrigger=0;setChanged();}
    public void cycleTriggerMode(){triggerMode=(triggerMode+1)%3;arm();}
    public void cycleTriggerChannel(){triggerChannel=(triggerChannel+1)%2;arm();}
    public void cycleTriggerLevel(){triggerLevel=triggerLevel>=15?1:triggerLevel+1;arm();}
    public void moveCursorA(){cursorA=(cursorA+1)%16;setChanged();}
    public void moveCursorB(){cursorB=(cursorB+1)%16;setChanged();}
    public String triggerStatus(){String m=triggerMode==0?"FREE":triggerMode==1?"RISING":"FALLING";return m+" CH"+(triggerChannel==0?"A":"B")+" @"+triggerLevel+" "+(armed?"ARMED":triggered?"TRIGGERED":"HOLD");}
    public int cursorDeltaSamples(){return Math.abs(cursorB-cursorA);}
    public int cursorValue(int channel,boolean second){int[] v=recent(channel);if(v.length==0)return -1;int base=Math.max(0,v.length-16),idx=base+(second?cursorB:cursorA);idx=Math.min(v.length-1,idx);return v[idx];}
    public int current(int c){if(count==0)return-1;return history[c][(index-1+CAPACITY)%CAPACITY];}
    public int minimum(int c){int m=16;for(int v:recent(c))if(v>=0)m=Math.min(m,v);return m==16?-1:m;}
    public int maximum(int c){int m=-1;for(int v:recent(c))if(v>=0)m=Math.max(m,v);return m;}
    public int peakToPeak(int c){int lo=minimum(c),hi=maximum(c);return lo<0||hi<0?-1:hi-lo;}
    public int estimatedPeriodSamples(int c){int[] v=recent(c);if(v.length<4)return-1;int threshold=8,last=-1,total=0,n=0;for(int i=1;i<v.length;i++)if(v[i-1]>=0&&v[i]>=0&&v[i-1]<threshold&&v[i]>=threshold){if(last>=0){total+=i-last;n++;}last=i;}return n==0?-1:Math.max(1,total/n);}
    public String waveform(int c){String[] bars={"▁","▂","▃","▄","▅","▆","▇","█"};int[] v=recent(c);if(v.length==0)return"∅";StringBuilder b=new StringBuilder();int s=Math.max(0,v.length-16);for(int i=s;i<v.length;i++){if(v[i]<0)b.append("·");else b.append(bars[Math.max(0,Math.min(7,(int)Math.round(v[i]/15.0*7.0)))]);}return b.toString();}
    private int[] recent(int c){int[] v=new int[count];for(int i=0;i<count;i++)v[i]=history[c][(index-count+i+CAPACITY)%CAPACITY];return v;}

    @Override protected void loadAdditional(CompoundTag t, HolderLookup.Provider r){super.loadAdditional(t,r);for(int c=0;c<CHANNELS;c++){int[]s=t.getIntArray("history"+c);for(int i=0;i<CAPACITY;i++)history[c][i]=i<s.length?s[i]:-1;}index=Math.max(0,Math.min(CAPACITY-1,t.getInt("index")));count=Math.max(0,Math.min(CAPACITY,t.getInt("count")));triggerLevel=Math.max(1,Math.min(15,t.getInt("triggerLevel")));triggerChannel=Math.max(0,Math.min(1,t.getInt("triggerChannel")));triggerMode=Math.max(0,Math.min(2,t.getInt("triggerMode")));armed=t.getBoolean("armed");triggered=t.getBoolean("triggered");cursorA=Math.max(0,Math.min(15,t.getInt("cursorA")));cursorB=Math.max(0,Math.min(15,t.getInt("cursorB")));lastA=t.getInt("lastA");lastB=t.getInt("lastB");}
    @Override protected void saveAdditional(CompoundTag t, HolderLookup.Provider r){super.saveAdditional(t,r);for(int c=0;c<CHANNELS;c++)t.putIntArray("history"+c,history[c]);t.putInt("index",index);t.putInt("count",count);t.putInt("triggerLevel",triggerLevel);t.putInt("triggerChannel",triggerChannel);t.putInt("triggerMode",triggerMode);t.putBoolean("armed",armed);t.putBoolean("triggered",triggered);t.putInt("cursorA",cursorA);t.putInt("cursorB",cursorB);t.putInt("lastA",lastA);t.putInt("lastB",lastB);}
}
