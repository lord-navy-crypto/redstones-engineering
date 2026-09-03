package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.DomainNetwork;
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

public class AmethystFrequencyFilterBlock extends DirectionalDomainBlock {
    public static final IntegerProperty TARGET=IntegerProperty.create("target",1,15);
    public AmethystFrequencyFilterBlock(Properties p){super(p);registerDefaultState(defaultBlockState().setValue(TARGET,1));}
    @Override public MapCodec<AmethystFrequencyFilterBlock> codec(){return RedstoneEngineering.AMETHYST_FREQUENCY_FILTER_CODEC.value();}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){super.createBlockStateDefinition(b);b.add(TARGET);}
    @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moved){super.onPlace(s,l,p,old,moved);if(!l.isClientSide)l.scheduleTick(p,this,2);}
    @Override protected void tick(BlockState s,ServerLevel l,BlockPos p,RandomSource r){var in=DomainNetwork.sampleAmethyst(l,inputPos(p,s));boolean pass=in.active()&&in.frequency()==s.getValue(TARGET);DomainNetwork.driveAmethyst(l,outputPos(p,s),pass,in.frequency(),Math.max(0,in.amplitude()-1));l.scheduleTick(p,this,2);}
    @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState ns,boolean moved){if(!s.is(ns.getBlock())&&l instanceof ServerLevel sl)DomainNetwork.driveAmethyst(sl,outputPos(p,s),false,0,0);super.onRemove(s,l,p,ns,moved);}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult hit){if(!l.isClientSide){int f=s.getValue(TARGET);f=f>=15?1:f+1;BlockState n=s.setValue(TARGET,f);l.setBlock(p,n,Block.UPDATE_CLIENTS);pl.displayClientMessage(Component.literal("Amethyst frequency filter | pass f="+f+" | insertion loss=1 amplitude"),true);}return InteractionResult.sidedSuccess(l.isClientSide);}
}
