package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.MagneticPhysics;
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

public class MagneticFieldSensorBlock extends DomainBlock {
    public static final IntegerProperty FIELD=IntegerProperty.create("field",0,15);
    public MagneticFieldSensorBlock(Properties p){super(p);registerDefaultState(defaultBlockState().setValue(FIELD,0));}
    @Override public MapCodec<MagneticFieldSensorBlock> codec(){return RedstoneEngineering.MAGNETIC_FIELD_SENSOR_CODEC.value();}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){b.add(FIELD);}
    @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moved){super.onPlace(s,l,p,old,moved);if(!l.isClientSide)l.scheduleTick(p,this,5);}
    @Override protected void tick(BlockState s,ServerLevel l,BlockPos p,RandomSource r){int f=MagneticPhysics.fieldAt(l,p,6);if(f!=s.getValue(FIELD))l.setBlock(p,s.setValue(FIELD,f),Block.UPDATE_CLIENTS);l.scheduleTick(p,this,5);}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult hit){if(!l.isClientSide)pl.displayClientMessage(Component.literal("Magnetic field sensor | B-level="+s.getValue(FIELD)+"/15 | radius=6"),true);return InteractionResult.sidedSuccess(l.isClientSide);}
}
