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

public class CopperResistiveLoadBlock extends DomainBlock {
    public static final IntegerProperty RESISTANCE=IntegerProperty.create("resistance",1,15);
    public static final IntegerProperty VOLTAGE=IntegerProperty.create("voltage",0,15);
    public CopperResistiveLoadBlock(Properties p){super(p);registerDefaultState(defaultBlockState().setValue(RESISTANCE,4).setValue(VOLTAGE,0));}
    @Override public MapCodec<CopperResistiveLoadBlock> codec(){return RedstoneEngineering.COPPER_RESISTIVE_LOAD_CODEC.value();}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){b.add(RESISTANCE,VOLTAGE);}
    @Override protected void neighborChanged(BlockState s,Level l,BlockPos p,Block nb,BlockPos np,boolean moved){if(l instanceof ServerLevel sl)DomainNetwork.recomputeCopper(sl,p);}
    @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moved){super.onPlace(s,l,p,old,moved);if(l instanceof ServerLevel sl)DomainNetwork.recomputeCopper(sl,p);}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult hit){if(!l.isClientSide){BlockState n=s;if(!pl.isShiftKeyDown()){int r=s.getValue(RESISTANCE);n=s.setValue(RESISTANCE,r>=15?1:r+1);l.setBlock(p,n,Block.UPDATE_CLIENTS);}double v=n.getValue(VOLTAGE);double r=n.getValue(RESISTANCE);double current=v/r;double power=v*current;pl.displayClientMessage(Component.literal(String.format("Electrical load | V=%.1f | R=%.1f | I=V/R=%.2f | P=VI=%.2f",v,r,current,power)),true);}return InteractionResult.sidedSuccess(l.isClientSide);}
}
