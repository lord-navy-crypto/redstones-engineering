package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

public class PermanentMagnetBlock extends DomainBlock implements EngineeringPortProvider {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty STRENGTH=IntegerProperty.create("strength",1,15);
    public PermanentMagnetBlock(Properties p){super(p);registerDefaultState(defaultBlockState().setValue(FACING,Direction.NORTH).setValue(STRENGTH,8));}
    @Override public MapCodec<PermanentMagnetBlock> codec(){return RedstoneEngineering.PERMANENT_MAGNET_CODEC.value();}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){b.add(FACING,STRENGTH);}
    /** The magnetic field is free-space, not a wired adjacency port. */
    @Override public List<EngineeringPort> engineeringPorts(BlockState s){return List.of();}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult hit){if(!l.isClientSide&&pl instanceof ServerPlayer sp){if(!pl.isShiftKeyDown()){FieldDeviceUi.open(sp,p);return InteractionResult.CONSUME;}int x=s.getValue(STRENGTH);BlockState n=s.setValue(STRENGTH,x>=15?1:x+1);l.setBlock(p,n,Block.UPDATE_CLIENTS);pl.displayClientMessage(Component.literal("Permanent magnet | N-marker="+n.getValue(FACING)+" | scalar B-source="+n.getValue(STRENGTH)+"/15"),true);}return InteractionResult.sidedSuccess(l.isClientSide);}
}
