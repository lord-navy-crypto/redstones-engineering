package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Quartz rising-edge triggered sample-and-hold for the Lapis precision domain. */
public class QuartzTriggeredLapisSamplerBlock extends DirectionalDomainBlock {
    private static final String KEY = "quartz_triggered_lapis_sampler";
    public QuartzTriggeredLapisSamplerBlock(Properties p){super(p);}
    @Override public MapCodec<QuartzTriggeredLapisSamplerBlock> codec(){return RedstoneEngineering.QUARTZ_TRIGGERED_LAPIS_SAMPLER_CODEC.value();}
    @Override protected void onPlace(BlockState s, Level l, BlockPos p, BlockState old, boolean moved){super.onPlace(s,l,p,old,moved);if(!l.isClientSide)l.scheduleTick(p,this,1);}
    @Override protected void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource r){
        int[] rt=RuntimeIntStore.get(l,KEY,p,3); // previous clock, held value, held valid
        var clock=DomainNetwork.sampleQuartz(l,p.relative(leftOf(outputSide(s))));
        boolean active=clock.valid()&&clock.active();
        boolean rising=active&&rt[0]==0;
        if(rising){
            var sample=DomainNetwork.sampleLapis(l,inputPos(p,s));
            rt[1]=sample.value();rt[2]=sample.valid()?1:0;
            DomainNetwork.driveLapis(l,outputPos(p,s),p,rt[1],rt[2]==1);
        }
        rt[0]=active?1:0;
        l.scheduleTick(p,this,1);
    }
    @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState ns,boolean moved){if(!s.is(ns.getBlock())){if(l instanceof ServerLevel sl)DomainNetwork.driveLapis(sl,outputPos(p,s),p,0,false);RuntimeIntStore.remove(l,KEY,p);}super.onRemove(s,l,p,ns,moved);}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult h){if(!l.isClientSide){int[]rt=RuntimeIntStore.get(l,KEY,p,3);pl.displayClientMessage(Component.literal("Quartz Triggered Lapis Sampler | held="+(rt[2]==1?String.format("%.2f",rt[1]/100.0):"INVALID")+" | quartz input=LEFT | lapis input=BACK | output=FRONT"),true);}return InteractionResult.sidedSuccess(l.isClientSide);}
}
