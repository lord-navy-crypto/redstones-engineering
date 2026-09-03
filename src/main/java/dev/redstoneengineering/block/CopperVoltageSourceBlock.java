package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.DomainNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

public class CopperVoltageSourceBlock extends DomainBlock {
    public static final IntegerProperty VOLTAGE=IntegerProperty.create("voltage",0,15);
    public CopperVoltageSourceBlock(Properties p){super(p);registerDefaultState(defaultBlockState().setValue(VOLTAGE,12));}
    @Override public MapCodec<CopperVoltageSourceBlock> codec(){return RedstoneEngineering.COPPER_VOLTAGE_SOURCE_CODEC.value();}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){b.add(VOLTAGE);}
    @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moved){super.onPlace(s,l,p,old,moved);if(l instanceof ServerLevel sl)DomainNetwork.recomputeCopper(sl,p);}
    @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState ns,boolean moved){if(l instanceof ServerLevel sl&&!s.is(ns.getBlock()))DomainNetwork.recomputeCopper(sl,p);super.onRemove(s,l,p,ns,moved);}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult hit){if(!l.isClientSide){int v=s.getValue(VOLTAGE);v=pl.isShiftKeyDown()?Math.max(0,v-1):(v>=15?0:v+1);BlockState n=s.setValue(VOLTAGE,v);l.setBlock(p,n,Block.UPDATE_CLIENTS);if(l instanceof ServerLevel sl)DomainNetwork.recomputeCopper(sl,p);pl.displayClientMessage(Component.literal("Copper source | V-level="+v+"/15"),true);}return InteractionResult.sidedSuccess(l.isClientSide);}
}
