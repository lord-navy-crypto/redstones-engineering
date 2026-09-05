package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.DomainNetwork;
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

public class OpticalChannelFilterBlock extends DirectionalDomainBlock implements EngineeringPortProvider {
    public static final IntegerProperty TARGET=IntegerProperty.create("target",0,15);
    public OpticalChannelFilterBlock(Properties p){super(p);registerDefaultState(defaultBlockState().setValue(TARGET,0));}
    @Override public MapCodec<OpticalChannelFilterBlock> codec(){return RedstoneEngineering.OPTICAL_CHANNEL_FILTER_CODEC.value();}
    @Override public java.util.List<EngineeringPort> engineeringPorts(BlockState s){return java.util.List.of(new EngineeringPort("OPTICAL INPUT",inputSide(s),EngineeringDomain.OPTICAL,PortKind.CONVERTER,PortDirection.INPUT,false,"intensity"),new EngineeringPort("OPTICAL FILTERED OUTPUT",outputSide(s),EngineeringDomain.OPTICAL,PortKind.CONVERTER,PortDirection.OUTPUT,false,"intensity"));}
    @Override public java.util.Optional<EngineeringPortSnapshot> engineeringSnapshot(Level l,BlockPos p,BlockState s,Direction side){return engineeringPort(s,side).map(port->{BlockPos sample=side==inputSide(s)?inputPos(p,s):outputPos(p,s);var x=DomainNetwork.sampleOptical(l,sample);return new EngineeringPortSnapshot(port,x.intensity(),0.0,15.0,x.valid()?PortQuality.VALID:PortQuality.NO_SIGNAL);});}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){super.createBlockStateDefinition(b);b.add(TARGET);}
    @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moved){super.onPlace(s,l,p,old,moved);if(!l.isClientSide)l.scheduleTick(p,this,2);}
    @Override protected void tick(BlockState s,ServerLevel l,BlockPos p,RandomSource r){var in=DomainNetwork.sampleOptical(l,inputPos(p,s));boolean pass=in.valid()&&in.channel()==s.getValue(TARGET);DomainNetwork.driveOptical(l,outputPos(p,s),p,Math.max(0,in.intensity()-1),in.channel(),pass&&in.intensity()>1);l.scheduleTick(p,this,2);}
    @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState ns,boolean moved){if(!s.is(ns.getBlock())&&l instanceof ServerLevel sl){DomainNetwork.driveOptical(sl,outputPos(p,s),p,0,0,false);DomainNetwork.recomputeOpticalAround(sl,p);}super.onRemove(s,l,p,ns,moved);}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult hit){if(!l.isClientSide&&pl instanceof ServerPlayer sp&&!pl.isShiftKeyDown())FieldDeviceUi.open(sp,p);else if(!l.isClientSide){int c=(s.getValue(TARGET)+1)%16;BlockState n=s.setValue(TARGET,c);l.setBlock(p,n,Block.UPDATE_CLIENTS);pl.displayClientMessage(Component.literal("Optical channel filter | pass channel="+c+" | insertion loss=1"),true);}return InteractionResult.sidedSuccess(l.isClientSide);}
}
