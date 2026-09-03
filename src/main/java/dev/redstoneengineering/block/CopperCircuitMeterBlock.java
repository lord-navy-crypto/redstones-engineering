package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.CircuitPhysics;
import dev.redstoneengineering.physics.DomainNetwork;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.phys.BlockHitResult;

public class CopperCircuitMeterBlock extends DomainBlock {
    public static final DirectionProperty FACING=BlockStateProperties.FACING;
    public CopperCircuitMeterBlock(Properties p){super(p);registerDefaultState(defaultBlockState().setValue(FACING,Direction.NORTH));}
    @Override public MapCodec<CopperCircuitMeterBlock> codec(){return RedstoneEngineering.COPPER_CIRCUIT_METER_CODEC.value();}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getClickedFace().getOpposite());}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){b.add(FACING);}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult hit){if(!l.isClientSide){BlockPos t=p.relative(s.getValue(FACING));var ts=l.getBlockState(t);int v=DomainNetwork.sampleCopperVoltage(l,t,p);double r=ts.getBlock() instanceof CopperResistiveLoadBlock?ts.getValue(CopperResistiveLoadBlock.RESISTANCE):CircuitPhysics.equivalentLoadResistance(l,t,128);double i=CircuitPhysics.current(v,r),power=v*i;pl.displayClientMessage(Component.literal(String.format("Copper circuit meter | V=%.2f | Req=%.2f | I≈%.3f | P≈%.3f",(double)v,r,i,power)),true);}return InteractionResult.sidedSuccess(l.isClientSide);}
}
