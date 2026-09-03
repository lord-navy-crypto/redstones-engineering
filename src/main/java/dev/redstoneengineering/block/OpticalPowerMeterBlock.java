package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.DomainNetwork;
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

public class OpticalPowerMeterBlock extends DomainBlock {
    public static final DirectionProperty FACING=BlockStateProperties.FACING;
    public OpticalPowerMeterBlock(Properties p){super(p);registerDefaultState(defaultBlockState().setValue(FACING,Direction.NORTH));}
    @Override public MapCodec<OpticalPowerMeterBlock> codec(){return RedstoneEngineering.OPTICAL_POWER_METER_CODEC.value();}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getClickedFace().getOpposite());}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){b.add(FACING);}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult hit){if(!l.isClientSide){var x=DomainNetwork.sampleOptical(l,p.relative(s.getValue(FACING)));pl.displayClientMessage(Component.literal(x.valid()?"Optical power meter | P-index="+x.intensity()+"/15 | channel="+x.channel()+" | approximate loss observable":"Optical power meter | DARK / invalid channel"),true);}return InteractionResult.sidedSuccess(l.isClientSide);}
}
