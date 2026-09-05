package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.*;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;

public class OpticalPowerMeterBlock extends DomainBlock implements EngineeringPortProvider {
    public static final DirectionProperty FACING=BlockStateProperties.FACING;
    public OpticalPowerMeterBlock(Properties p){super(p);registerDefaultState(defaultBlockState().setValue(FACING,Direction.NORTH));}
    @Override public MapCodec<OpticalPowerMeterBlock> codec(){return RedstoneEngineering.OPTICAL_POWER_METER_CODEC.value();}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getClickedFace().getOpposite());}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){b.add(FACING);}
    @Override public java.util.List<EngineeringPort> engineeringPorts(BlockState s){return java.util.List.of(new EngineeringPort("OPTICAL POWER INPUT",s.getValue(FACING),EngineeringDomain.OPTICAL,PortKind.MEASUREMENT,PortDirection.INPUT,false,"intensity"));}
    @Override public java.util.Optional<EngineeringPortSnapshot> engineeringSnapshot(Level l,BlockPos p,BlockState s,Direction side){return engineeringPort(s,side).map(port->{var sample=DomainNetwork.sampleOptical(l,p.relative(s.getValue(FACING)));return new EngineeringPortSnapshot(port,sample.intensity(),0.0,15.0,sample.valid()?PortQuality.VALID:PortQuality.NO_SIGNAL);});}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult hit){if(!l.isClientSide&&pl instanceof ServerPlayer sp&&!pl.isShiftKeyDown())FieldDeviceUi.open(sp,p);else if(!l.isClientSide){var x=DomainNetwork.sampleOptical(l,p.relative(s.getValue(FACING)));pl.displayClientMessage(Component.literal(x.valid()?"Optical power meter | P-index="+x.intensity()+"/15 | channel="+x.channel()+" | approximate loss observable":"Optical power meter | DARK / invalid channel"),true);}return InteractionResult.sidedSuccess(l.isClientSide);}
}
