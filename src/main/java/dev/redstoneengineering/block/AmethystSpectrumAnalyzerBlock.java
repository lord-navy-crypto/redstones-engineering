package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class AmethystSpectrumAnalyzerBlock extends DomainBlock {
    private static final int RADIUS = 6;
    public AmethystSpectrumAnalyzerBlock(Properties p){super(p);}
    @Override public MapCodec<AmethystSpectrumAnalyzerBlock> codec(){return RedstoneEngineering.AMETHYST_SPECTRUM_ANALYZER_CODEC.value();}

    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult hit){
        if(!l.isClientSide){
            int[] energy=new int[16];
            int events=0;
            int r2=RADIUS*RADIUS;
            for(int dx=-RADIUS;dx<=RADIUS;dx++) for(int dy=-RADIUS;dy<=RADIUS;dy++) for(int dz=-RADIUS;dz<=RADIUS;dz++){
                if(dx*dx+dy*dy+dz*dz>r2) continue;
                BlockPos q=p.offset(dx,dy,dz);
                if(!l.hasChunkAt(q)) continue;
                var st=l.getBlockState(q);
                if(st.getBlock() instanceof AmethystResonanceDustBlock && AmethystResonanceDustBlock.active(l,q)){
                    int f=AmethystResonanceDustBlock.frequency(l,q);
                    if(f>=1&&f<=15) energy[f]+=AmethystResonanceDustBlock.amplitude(l,q);
                    events++;
                }
            }
            int bestF=0,best=0,bands=0;
            for(int f=1;f<=15;f++){if(energy[f]>0)bands++;if(energy[f]>best){best=energy[f];bestF=f;}}
            pl.displayClientMessage(Component.literal("Amethyst spectrum | dominant f="+bestF+" | energy="+best+" | active bands="+bands+" | samples="+events),true);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
