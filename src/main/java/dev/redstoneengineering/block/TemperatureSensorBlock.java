package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.ThermalPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

public class TemperatureSensorBlock extends DomainBlock {
    public static final IntegerProperty TEMPERATURE=IntegerProperty.create("temperature",0,100);
    public TemperatureSensorBlock(Properties p){super(p);registerDefaultState(defaultBlockState().setValue(TEMPERATURE,20));}
    @Override public MapCodec<TemperatureSensorBlock> codec(){return RedstoneEngineering.TEMPERATURE_SENSOR_CODEC.value();}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){b.add(TEMPERATURE);}
    @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moved){super.onPlace(s,l,p,old,moved);if(!l.isClientSide)l.scheduleTick(p,this,10);}
    @Override protected void tick(BlockState s,ServerLevel l,BlockPos p,RandomSource r){int sum=0,count=0;for(Direction d:Direction.values()){var n=l.getBlockState(p.relative(d));if(n.getBlock() instanceof ThermalMassBlock){sum+=n.getValue(ThermalMassBlock.TEMPERATURE);count++;}}int t=count>0?sum/count:ThermalPhysics.environmentTarget(l,p);if(t!=s.getValue(TEMPERATURE))l.setBlock(p,s.setValue(TEMPERATURE,t),Block.UPDATE_CLIENTS);l.scheduleTick(p,this,10);}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult hit){if(!l.isClientSide)pl.displayClientMessage(Component.literal("Temperature sensor | T-index="+s.getValue(TEMPERATURE)+"/100 | physical state only"),true);return InteractionResult.sidedSuccess(l.isClientSide);}
}
