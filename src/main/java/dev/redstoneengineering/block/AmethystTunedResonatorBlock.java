package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.EngineeringMath;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

public class AmethystTunedResonatorBlock extends DirectionalDomainBlock {
    public static final IntegerProperty NATURAL=IntegerProperty.create("natural",1,15);
    public static final IntegerProperty Q_INDEX=IntegerProperty.create("q",1,4);
    public AmethystTunedResonatorBlock(Properties p){super(p);registerDefaultState(defaultBlockState().setValue(NATURAL,8).setValue(Q_INDEX,2));}
    @Override public MapCodec<AmethystTunedResonatorBlock> codec(){return RedstoneEngineering.AMETHYST_TUNED_RESONATOR_CODEC.value();}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){super.createBlockStateDefinition(b);b.add(NATURAL,Q_INDEX);}
    @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moved){super.onPlace(s,l,p,old,moved);if(!l.isClientSide)l.scheduleTick(p,this,2);}
    @Override protected void tick(BlockState s,ServerLevel l,BlockPos p,RandomSource r){var in=DomainNetwork.sampleAmethyst(l,inputPos(p,s));int diff=in.active()?Math.abs(in.frequency()-s.getValue(NATURAL)):99;int q=s.getValue(Q_INDEX);int bandwidth=5-q;int amp=0;if(in.active()){if(diff==0)amp=EngineeringMath.clamp(in.amplitude()+q*2,0,15);else if(diff<=bandwidth)amp=EngineeringMath.clamp(in.amplitude()-Math.max(1,diff*q),0,15);}DomainNetwork.driveAmethyst(l,outputPos(p,s),amp>0,in.frequency(),amp);l.scheduleTick(p,this,2);}
    @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState ns,boolean moved){if(!s.is(ns.getBlock())&&l instanceof ServerLevel sl)DomainNetwork.driveAmethyst(sl,outputPos(p,s),false,0,0);super.onRemove(s,l,p,ns,moved);}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult hit){if(!l.isClientSide){BlockState n;if(pl.isShiftKeyDown()){int q=s.getValue(Q_INDEX);n=s.setValue(Q_INDEX,q>=4?1:q+1);}else{int f=s.getValue(NATURAL);n=s.setValue(NATURAL,f>=15?1:f+1);}l.setBlock(p,n,Block.UPDATE_CLIENTS);pl.displayClientMessage(Component.literal("Tuned amethyst resonator | f0="+n.getValue(NATURAL)+" | Q-index="+n.getValue(Q_INDEX)+" | higher Q = narrower selectivity"),true);}return InteractionResult.sidedSuccess(l.isClientSide);}
}
