package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

public class QuartzOscillatorBlock extends DomainBlock implements EngineeringPortProvider {
    public static final BooleanProperty ACTIVE=BooleanProperty.create("active");
    public static final IntegerProperty PERIOD_INDEX=IntegerProperty.create("period",0,4);
    public QuartzOscillatorBlock(Properties p){super(p);registerDefaultState(defaultBlockState().setValue(ACTIVE,false).setValue(PERIOD_INDEX,2));}
    @Override public MapCodec<QuartzOscillatorBlock> codec(){return RedstoneEngineering.QUARTZ_OSCILLATOR_CODEC.value();}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){b.add(ACTIVE,PERIOD_INDEX);}
    private static EngineeringPort port(Direction side){return new EngineeringPort("QUARTZ CLOCK OUT",side, EngineeringDomain.QUARTZ, PortKind.TRIGGER, PortDirection.OUTPUT,false,"clock");}
    @Override public List<EngineeringPort> engineeringPorts(BlockState s){return List.of(port(Direction.NORTH),port(Direction.SOUTH),port(Direction.WEST),port(Direction.EAST));}
    @Override public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level l,BlockPos p,BlockState s,Direction side){Optional<EngineeringPort>d=engineeringPort(s,side);return d.map(port->new EngineeringPortSnapshot(port,s.getValue(ACTIVE)?1.0:0.0,0.0,1.0,PortQuality.VALID));}
    @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moved){super.onPlace(s,l,p,old,moved);if(!l.isClientSide)l.scheduleTick(p,this,1);}
    @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState ns,boolean moved){if(l instanceof ServerLevel sl&&!s.is(ns.getBlock()))DomainNetwork.recomputeQuartzAround(sl,p);super.onRemove(s,l,p,ns,moved);}
    @Override protected void tick(BlockState s,ServerLevel l,BlockPos p,RandomSource r){int period=QuartzTimingLineBlock.periodTicks(s.getValue(PERIOD_INDEX));BlockState n=s.setValue(ACTIVE,!s.getValue(ACTIVE));l.setBlock(p,n,Block.UPDATE_CLIENTS);DomainNetwork.recomputeQuartz(l,p);l.scheduleTick(p,this,Math.max(1,period/2));}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult hit){if(!l.isClientSide&&pl instanceof ServerPlayer serverPlayer){if(!pl.isShiftKeyDown()){FieldDeviceUi.open(serverPlayer,p);return InteractionResult.CONSUME;}int i=(s.getValue(PERIOD_INDEX)+1)%5;BlockState n=s.setValue(PERIOD_INDEX,i);l.setBlock(p,n,Block.UPDATE_CLIENTS);if(l instanceof ServerLevel sl)DomainNetwork.recomputeQuartz(sl,p);pl.displayClientMessage(Component.literal("Quartz oscillator period = "+QuartzTimingLineBlock.periodTicks(i)+" ticks"),true);}return InteractionResult.sidedSuccess(l.isClientSide);}
}
