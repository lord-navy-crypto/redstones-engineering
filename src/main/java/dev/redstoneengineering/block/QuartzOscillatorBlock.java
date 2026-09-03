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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

public class QuartzOscillatorBlock extends DomainBlock {
    public static final BooleanProperty ACTIVE=BooleanProperty.create("active");
    public static final IntegerProperty PERIOD_INDEX=IntegerProperty.create("period",0,4);
    public QuartzOscillatorBlock(Properties p){super(p);registerDefaultState(defaultBlockState().setValue(ACTIVE,false).setValue(PERIOD_INDEX,2));}
    @Override public MapCodec<QuartzOscillatorBlock> codec(){return RedstoneEngineering.QUARTZ_OSCILLATOR_CODEC.value();}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){b.add(ACTIVE,PERIOD_INDEX);}
    @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moved){super.onPlace(s,l,p,old,moved);if(!l.isClientSide)l.scheduleTick(p,this,1);}
    @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState ns,boolean moved){if(l instanceof ServerLevel sl&&!s.is(ns.getBlock()))DomainNetwork.recomputeQuartz(sl,p);super.onRemove(s,l,p,ns,moved);}
    @Override protected void tick(BlockState s,ServerLevel l,BlockPos p,RandomSource r){int period=QuartzTimingLineBlock.periodTicks(s.getValue(PERIOD_INDEX));BlockState n=s.setValue(ACTIVE,!s.getValue(ACTIVE));l.setBlock(p,n,Block.UPDATE_CLIENTS);DomainNetwork.recomputeQuartz(l,p);l.scheduleTick(p,this,Math.max(1,period/2));}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult hit){if(!l.isClientSide){int i=(s.getValue(PERIOD_INDEX)+1)%5;BlockState n=s.setValue(PERIOD_INDEX,i);l.setBlock(p,n,Block.UPDATE_CLIENTS);pl.displayClientMessage(Component.literal("Quartz oscillator period = "+QuartzTimingLineBlock.periodTicks(i)+" ticks"),true);}return InteractionResult.sidedSuccess(l.isClientSide);}
}
