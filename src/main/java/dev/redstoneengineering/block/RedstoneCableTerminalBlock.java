package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.RedstoneCableNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Explicit Vanilla-redstone ↔ insulated-cable boundary. */
public class RedstoneCableTerminalBlock extends Block {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OUTPUT_MODE=BooleanProperty.create("output_mode");
    public static final IntegerProperty POWER=IntegerProperty.create("power",0,15);
    public RedstoneCableTerminalBlock(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(OUTPUT_MODE,false).setValue(POWER,0));}
    @Override public MapCodec<RedstoneCableTerminalBlock> codec(){return RedstoneEngineering.REDSTONE_CABLE_TERMINAL_CODEC.value();}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){b.add(FACING,OUTPUT_MODE,POWER);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
    public Direction vanillaSide(BlockState s){return s.getValue(FACING);} public Direction cableSide(BlockState s){return vanillaSide(s).getOpposite();}
    public int externalInput(Level l,BlockPos p,BlockState s){Direction d=vanillaSide(s);return Math.max(0,Math.min(15,l.getSignal(p.relative(d),d)));}
    @Override public boolean canConnectRedstone(BlockState s,BlockGetter l,BlockPos p,@Nullable Direction d){return d!=null&&d==vanillaSide(s).getOpposite();}
    @Override protected boolean isSignalSource(BlockState s){return s.getValue(OUTPUT_MODE);}
    @Override protected int getSignal(BlockState s,BlockGetter l,BlockPos p,Direction d){return s.getValue(OUTPUT_MODE)&&d==vanillaSide(s).getOpposite()?s.getValue(POWER):0;}
    @Override protected void neighborChanged(BlockState s,Level l,BlockPos p,Block nb,BlockPos np,boolean moved){if(l instanceof ServerLevel sl)RedstoneCableNetwork.recompute(sl,p);}
    @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moved){super.onPlace(s,l,p,old,moved);if(l instanceof ServerLevel sl)RedstoneCableNetwork.recompute(sl,p);}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult hit){
        if(!l.isClientSide){
            BlockState n=s.setValue(OUTPUT_MODE,!s.getValue(OUTPUT_MODE));
            l.setBlock(p,n,Block.UPDATE_CLIENTS);
            if(l instanceof ServerLevel sl)RedstoneCableNetwork.recompute(sl,p);
            // Mode changes can remove as well as create Vanilla output. Always
            // notify the physical Vanilla side so stale powered dust cannot remain.
            l.updateNeighborsAt(p,this);
            l.updateNeighborsAt(p.relative(vanillaSide(n)),this);
            pl.displayClientMessage(Component.literal("Redstone Cable Terminal → "+(n.getValue(OUTPUT_MODE)?"OUTPUT cable→vanilla":"INPUT vanilla→cable")+" | "+n.getValue(POWER)+"/15"),true);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
    @Override protected void onRemove(BlockState s, Level l, BlockPos p, BlockState ns, boolean moved){
        if(!s.is(ns.getBlock())){
            l.updateNeighborsAt(p,this);
            l.updateNeighborsAt(p.relative(vanillaSide(s)),this);
            if(l instanceof ServerLevel sl)RedstoneCableNetwork.recompute(sl,p.relative(cableSide(s)));
        }
        super.onRemove(s,l,p,ns,moved);
    }
}
