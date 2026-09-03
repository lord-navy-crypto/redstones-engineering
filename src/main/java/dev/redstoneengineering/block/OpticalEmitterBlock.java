package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.DomainNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

public class OpticalEmitterBlock extends DomainBlock {
    public static final IntegerProperty INTENSITY=IntegerProperty.create("intensity",0,15);
    public static final IntegerProperty CHANNEL=IntegerProperty.create("channel",0,15);
    public OpticalEmitterBlock(Properties p){super(p);registerDefaultState(defaultBlockState().setValue(INTENSITY,8).setValue(CHANNEL,0));}
    @Override public MapCodec<OpticalEmitterBlock> codec(){return RedstoneEngineering.OPTICAL_EMITTER_CODEC.value();}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){b.add(INTENSITY,CHANNEL);}
    @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moved){super.onPlace(s,l,p,old,moved);if(l instanceof ServerLevel sl)DomainNetwork.recomputeOptical(sl,p);}
    @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState ns,boolean moved){if(l instanceof ServerLevel sl&&!s.is(ns.getBlock()))DomainNetwork.recomputeOptical(sl,p);super.onRemove(s,l,p,ns,moved);}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult hit){if(!l.isClientSide){BlockState n;if(pl.isShiftKeyDown()){int c=s.getValue(CHANNEL);n=s.setValue(CHANNEL,(c+1)%16);}else{int i=s.getValue(INTENSITY);n=s.setValue(INTENSITY,i>=15?0:i+1);}l.setBlock(p,n,Block.UPDATE_CLIENTS);if(l instanceof ServerLevel sl)DomainNetwork.recomputeOptical(sl,p);pl.displayClientMessage(Component.literal("Optical emitter | intensity="+n.getValue(INTENSITY)+"/15 | channel="+n.getValue(CHANNEL)),true);}return InteractionResult.sidedSuccess(l.isClientSide);}
}
