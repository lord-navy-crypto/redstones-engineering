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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

public class LapisPrecisionSourceBlock extends DomainBlock implements EngineeringPortProvider {
    public static final IntegerProperty VALUE = IntegerProperty.create("value",0,100);
    public LapisPrecisionSourceBlock(Properties p){ super(p); registerDefaultState(defaultBlockState().setValue(VALUE,50)); }
    @Override public MapCodec<LapisPrecisionSourceBlock> codec(){ return RedstoneEngineering.LAPIS_PRECISION_SOURCE_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){ b.add(VALUE); }
    private static EngineeringPort port(Direction side){return new EngineeringPort("LAPIS PRECISION OUT",side, EngineeringDomain.LAPIS, PortKind.BUS, PortDirection.OUTPUT,false,"precision");}
    @Override public List<EngineeringPort> engineeringPorts(BlockState s){return List.of(port(Direction.NORTH),port(Direction.SOUTH),port(Direction.WEST),port(Direction.EAST));}
    @Override public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level l,BlockPos p,BlockState s,Direction side){Optional<EngineeringPort>d=engineeringPort(s,side);return d.map(port->new EngineeringPortSnapshot(port,s.getValue(VALUE),0.0,100.0,PortQuality.VALID));}
    @Override protected void onPlace(BlockState s, Level l, BlockPos p, BlockState old, boolean moved){ super.onPlace(s,l,p,old,moved); if(l instanceof ServerLevel sl) DomainNetwork.recomputeLapis(sl,p); }
    @Override protected void onRemove(BlockState s, Level l, BlockPos p, BlockState ns, boolean moved){ if(l instanceof ServerLevel sl && !s.is(ns.getBlock())) DomainNetwork.recomputeLapisAround(sl,p); super.onRemove(s,l,p,ns,moved); }
    @Override protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player pl, BlockHitResult hit){ if(!l.isClientSide && pl instanceof ServerPlayer serverPlayer){if(!pl.isShiftKeyDown()){FieldDeviceUi.open(serverPlayer,p);return InteractionResult.CONSUME;}int v=s.getValue(VALUE);v=hit.getDirection()==Direction.DOWN?Math.max(0,v-5):(v>=100?0:v+5);BlockState n=s.setValue(VALUE,v);l.setBlock(p,n,Block.UPDATE_CLIENTS);if(l instanceof ServerLevel sl)DomainNetwork.recomputeLapis(sl,p);pl.displayClientMessage(Component.literal("Lapis precision source = "+String.format("%.2f",v/100.0)),true);}return InteractionResult.sidedSuccess(l.isClientSide);}
}
