package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.physics.MagneticPhysics;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

public class MagneticFieldSensorBlock extends DomainBlock implements EngineeringPortProvider {
    public static final IntegerProperty FIELD=IntegerProperty.create("field",0,15);
    public MagneticFieldSensorBlock(Properties p){super(p);registerDefaultState(defaultBlockState().setValue(FIELD,0));}
    @Override public MapCodec<MagneticFieldSensorBlock> codec(){return RedstoneEngineering.MAGNETIC_FIELD_SENSOR_CODEC.value();}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){b.add(FIELD);}
    /** Free-space scalar sensing has no wired endpoint. */
    @Override public List<EngineeringPort> engineeringPorts(BlockState s){return List.of();}
    @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moved){super.onPlace(s,l,p,old,moved);if(!l.isClientSide)l.scheduleTick(p,this,5);}
    @Override protected void tick(BlockState s,ServerLevel l,BlockPos p,RandomSource r){int f=MagneticPhysics.fieldAt(l,p,6);if(f!=s.getValue(FIELD))l.setBlock(p,s.setValue(FIELD,f),Block.UPDATE_CLIENTS);l.scheduleTick(p,this,5);}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult hit){if(!l.isClientSide&&pl instanceof ServerPlayer sp){if(!pl.isShiftKeyDown()){FieldDeviceUi.open(sp,p);return InteractionResult.CONSUME;}pl.displayClientMessage(Component.literal("Magnetic field sensor | B-level="+s.getValue(FIELD)+"/15 | free-space radius=6 | no wired output"),true);}return InteractionResult.sidedSuccess(l.isClientSide);}
}
