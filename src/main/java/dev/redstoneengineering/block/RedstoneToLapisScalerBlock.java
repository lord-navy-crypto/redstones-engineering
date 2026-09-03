package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Explicit vanilla Redstone 0..15 -> normalized Lapis 0..100 scaler. */
public class RedstoneToLapisScalerBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final String KEY = "redstone_to_lapis_scaler";

    public RedstoneToLapisScalerBlock(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH)); }
    @Override public MapCodec<RedstoneToLapisScalerBlock> codec(){ return RedstoneEngineering.REDSTONE_TO_LAPIS_SCALER_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){ b.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){ return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite()); }
    private Direction outputSide(BlockState s){ return s.getValue(FACING); }
    private Direction inputSide(BlockState s){ return outputSide(s).getOpposite(); }
    @Override public boolean canConnectRedstone(BlockState s, BlockGetter l, BlockPos p, @Nullable Direction d){ return d!=null && d==inputSide(s).getOpposite(); }
    @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moved){ super.onPlace(s,l,p,old,moved); if(!l.isClientSide)l.scheduleTick(p,this,1); }
    @Override protected void neighborChanged(BlockState s,Level l,BlockPos p,Block nb,BlockPos np,boolean moved){ if(!l.isClientSide)l.scheduleTick(p,this,1); }
    @Override protected void tick(BlockState s,ServerLevel l,BlockPos p,RandomSource r){
        Direction in=inputSide(s);
        int red=Math.max(0,Math.min(15,l.getSignal(p.relative(in),in)));
        int value=Math.round(red*100.0f/15.0f);
        RuntimeIntStore.get(l,KEY,p,1)[0]=value;
        DomainNetwork.driveLapis(l,p.relative(outputSide(s)),p,value,true);
        l.scheduleTick(p,this,2);
    }
    @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState ns,boolean moved){ if(!s.is(ns.getBlock())){ if(l instanceof ServerLevel sl)DomainNetwork.driveLapis(sl,p.relative(outputSide(s)),p,0,false); RuntimeIntStore.remove(l,KEY,p);} super.onRemove(s,l,p,ns,moved); }
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult h){ if(!l.isClientSide){int v=RuntimeIntStore.get(l,KEY,p,1)[0];pl.displayClientMessage(Component.literal("Redstone → Lapis Scaler | output="+String.format("%.2f",v/100.0)),true);}return InteractionResult.sidedSuccess(l.isClientSide); }
}
