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
import dev.redstoneengineering.physics.MagneticPhysics;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.Direction;
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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class ElectromagnetBlock extends DomainBlock implements EngineeringPortProvider {
    public static final IntegerProperty FIELD=IntegerProperty.create("field",0,15);
    public ElectromagnetBlock(Properties p){super(p);registerDefaultState(defaultBlockState().setValue(FIELD,0));}
    @Override public MapCodec<ElectromagnetBlock> codec(){return RedstoneEngineering.ELECTROMAGNET_CODEC.value();}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){b.add(FIELD);}
    @Override public List<EngineeringPort> engineeringPorts(BlockState s){return Arrays.stream(Direction.values()).map(side ->
            new EngineeringPort("COPPER COIL INPUT",side, EngineeringDomain.COPPER, PortKind.ACTUATOR, PortDirection.INPUT,false,"voltage")).toList();}
    @Override public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level l,BlockPos p,BlockState s,Direction side){
        return engineeringPort(s,side).map(port -> {int voltage=DomainNetwork.sampleCopperVoltage(l,p.relative(side),p);return new EngineeringPortSnapshot(port,voltage,0.0,15.0,voltage>0? PortQuality.VALID:PortQuality.NO_SIGNAL);});
    }
    @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moved){super.onPlace(s,l,p,old,moved);if(!l.isClientSide)l.scheduleTick(p,this,1);}
    @Override protected void neighborChanged(BlockState s,Level l,BlockPos p,Block nb,BlockPos np,boolean moved){if(!l.isClientSide)l.scheduleTick(p,this,1);}
    @Override protected void tick(BlockState s,ServerLevel l,BlockPos p,RandomSource r){
        int field=MagneticPhysics.adjacentCopperLevel(l,p);
        if(field!=s.getValue(FIELD)) l.setBlock(p,s.setValue(FIELD,field),Block.UPDATE_CLIENTS);
    }
    @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState ns,boolean moved){if(!s.is(ns.getBlock())&&l instanceof ServerLevel sl)for(Direction d:Direction.values())DomainNetwork.recomputeCopper(sl,p.relative(d));super.onRemove(s,l,p,ns,moved);}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult hit){if(!l.isClientSide&&pl instanceof ServerPlayer sp){if(!pl.isShiftKeyDown()){FieldDeviceUi.open(sp,p);return InteractionResult.CONSUME;}pl.displayClientMessage(Component.literal("Electromagnet | B-level="+s.getValue(FIELD)+"/15 | driven by adjacent Copper"),true);}return InteractionResult.sidedSuccess(l.isClientSide);}
}
