package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
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

public class LapisPrecisionMeterBlock extends DomainBlock {
    public static final DirectionProperty FACING=BlockStateProperties.FACING;
    public LapisPrecisionMeterBlock(Properties p){super(p);registerDefaultState(defaultBlockState().setValue(FACING,Direction.NORTH));}
    @Override public MapCodec<LapisPrecisionMeterBlock> codec(){return RedstoneEngineering.LAPIS_PRECISION_METER_CODEC.value();}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getClickedFace().getOpposite());}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){b.add(FACING);}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult hit){if(!l.isClientSide){var sample=DomainNetwork.sampleLapis(l,p.relative(s.getValue(FACING)));pl.displayClientMessage(Component.literal(sample.valid()?"Lapis precision meter | value="+String.format("%.3f",sample.value()/100.0)+" | resolution=0.01":"Lapis precision meter | INVALID / no unique source"),true);}return InteractionResult.sidedSuccess(l.isClientSide);}
}
