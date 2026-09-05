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
import net.minecraft.world.phys.BlockHitResult;

public class OpticalSplitterBlock extends DirectionalDomainBlock implements EngineeringPortProvider {
    public OpticalSplitterBlock(Properties p){super(p);}
    @Override public MapCodec<OpticalSplitterBlock> codec(){return RedstoneEngineering.OPTICAL_SPLITTER_CODEC.value();}
    @Override public java.util.List<EngineeringPort> engineeringPorts(BlockState s){return java.util.List.of(new EngineeringPort("OPTICAL INPUT",inputSide(s),EngineeringDomain.OPTICAL,PortKind.BUS,PortDirection.INPUT,false,"intensity"),new EngineeringPort("OPTICAL OUTPUT A",outputSide(s),EngineeringDomain.OPTICAL,PortKind.BUS,PortDirection.OUTPUT,false,"intensity"),new EngineeringPort("OPTICAL OUTPUT B",leftOf(outputSide(s)),EngineeringDomain.OPTICAL,PortKind.BUS,PortDirection.OUTPUT,false,"intensity"));}
    @Override public java.util.Optional<EngineeringPortSnapshot> engineeringSnapshot(Level l,BlockPos p,BlockState s,Direction side){return engineeringPort(s,side).map(port->{BlockPos sample=side==inputSide(s)?inputPos(p,s):p.relative(side);var x=DomainNetwork.sampleOptical(l,sample);return new EngineeringPortSnapshot(port,x.intensity(),0.0,15.0,x.valid()?PortQuality.VALID:PortQuality.NO_SIGNAL);});}
    @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moved){super.onPlace(s,l,p,old,moved);if(!l.isClientSide)l.scheduleTick(p,this,2);}
    @Override protected void tick(BlockState s,ServerLevel l,BlockPos p,RandomSource r){var in=DomainNetwork.sampleOptical(l,inputPos(p,s));int out=in.valid()?in.intensity()/2:0;DomainNetwork.driveOptical(l,outputPos(p,s),p,out,in.channel(),in.valid()&&out>0);DomainNetwork.driveOptical(l,p.relative(leftOf(outputSide(s))),p,out,in.channel(),in.valid()&&out>0);l.scheduleTick(p,this,2);}
    @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState ns,boolean moved){if(!s.is(ns.getBlock())&&l instanceof ServerLevel sl){DomainNetwork.driveOptical(sl,outputPos(p,s),p,0,0,false);DomainNetwork.driveOptical(sl,p.relative(leftOf(outputSide(s))),p,0,0,false);DomainNetwork.recomputeOpticalAround(sl,p);}super.onRemove(s,l,p,ns,moved);}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult hit){if(!l.isClientSide&&pl instanceof ServerPlayer sp&&!pl.isShiftKeyDown())FieldDeviceUi.open(sp,p);else if(!l.isClientSide)pl.displayClientMessage(Component.literal("Optical 1x2 splitter | each branch ≈ 1/2 input power (3 dB idealized split)"),true);return InteractionResult.sidedSuccess(l.isClientSide);}
}
