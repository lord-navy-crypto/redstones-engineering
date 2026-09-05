package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.EngineeringMath;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.*;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

public class OpticalAttenuatorBlock extends DirectionalDomainBlock implements EngineeringPortProvider {
    public static final IntegerProperty LOSS=IntegerProperty.create("loss",0,8);
    public OpticalAttenuatorBlock(Properties p){super(p);registerDefaultState(defaultBlockState().setValue(LOSS,2));}
    @Override public MapCodec<OpticalAttenuatorBlock> codec(){return RedstoneEngineering.OPTICAL_ATTENUATOR_CODEC.value();}
    @Override public java.util.List<EngineeringPort> engineeringPorts(BlockState s){return java.util.List.of(new EngineeringPort("OPTICAL INPUT",inputSide(s),EngineeringDomain.OPTICAL,PortKind.CONVERTER,PortDirection.INPUT,false,"intensity"),new EngineeringPort("OPTICAL ATTENUATED OUTPUT",outputSide(s),EngineeringDomain.OPTICAL,PortKind.CONVERTER,PortDirection.OUTPUT,false,"intensity"));}
    @Override public java.util.Optional<EngineeringPortSnapshot> engineeringSnapshot(Level l,BlockPos p,BlockState s,Direction side){return engineeringPort(s,side).map(port->{BlockPos sample=side==inputSide(s)?inputPos(p,s):outputPos(p,s);var x=DomainNetwork.sampleOptical(l,sample);return new EngineeringPortSnapshot(port,x.intensity(),0.0,15.0,x.valid()?PortQuality.VALID:PortQuality.NO_SIGNAL);});}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){super.createBlockStateDefinition(b);b.add(LOSS);}
    @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moved){super.onPlace(s,l,p,old,moved);if(!l.isClientSide)l.scheduleTick(p,this,2);}
    @Override protected void tick(BlockState s,ServerLevel l,BlockPos p,RandomSource r){var in=DomainNetwork.sampleOptical(l,inputPos(p,s));int out=EngineeringMath.opticalAfterLoss(in.intensity(),s.getValue(LOSS));DomainNetwork.driveOptical(l,outputPos(p,s),p,out,in.channel(),in.valid()&&out>0);l.scheduleTick(p,this,2);}
    @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState ns,boolean moved){if(!s.is(ns.getBlock())&&l instanceof ServerLevel sl){DomainNetwork.driveOptical(sl,outputPos(p,s),p,0,0,false);DomainNetwork.recomputeOpticalAround(sl,p);}super.onRemove(s,l,p,ns,moved);}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult hit){if(!l.isClientSide&&pl instanceof ServerPlayer sp&&!pl.isShiftKeyDown())FieldDeviceUi.open(sp,p);else if(!l.isClientSide){int loss=s.getValue(LOSS);loss=loss>=8?0:loss+1;BlockState n=s.setValue(LOSS,loss);l.setBlock(p,n,Block.UPDATE_CLIENTS);pl.displayClientMessage(Component.literal("Optical attenuator | loss index="+loss),true);}return InteractionResult.sidedSuccess(l.isClientSide);}
}
