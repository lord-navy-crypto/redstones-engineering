package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/** Persistent fault memory. BACK=fault signal, RIGHT=electrical reset, FRONT=fault output. */
public class FaultLatchBlock extends PassiveDirectionalSignalBlock {
    public static final IntegerProperty THRESHOLD = IntegerProperty.create("threshold",0,3);
    private static final int[] LEVELS={1,4,8,12};
    private static final String KEY="fault_latch";
    public FaultLatchBlock(Properties p){super(p);registerDefaultState(defaultBlockState().setValue(THRESHOLD,0));}
    @Override public MapCodec<FaultLatchBlock> codec(){return RedstoneEngineering.FAULT_LATCH_CODEC.value();}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){super.createBlockStateDefinition(b);b.add(THRESHOLD);}
    @Override protected boolean isEngineeringPort(BlockState s, Direction side){return super.isEngineeringPort(s,side)||side==rightOf(outputSide(s));}
    @Override protected int computeOutput(Level l,BlockPos p,BlockState s){int[]rt=RuntimeIntStore.get(l,KEY,p,3);int reset=readInputFrom(l,p,rightOf(outputSide(s)));if(reset>0){rt[0]=0;rt[2]++;}int fault=readBackInput(l,p,s);if(fault>=LEVELS[s.getValue(THRESHOLD)]&&rt[0]==0){rt[0]=1;rt[1]++;}return rt[0]!=0?15:0;}
    @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState o,boolean m){super.onPlace(s,l,p,o,m);if(l instanceof ServerLevel sl)sl.scheduleTick(p,this,2);}
    @Override protected void tick(BlockState s,ServerLevel l,BlockPos p,RandomSource rnd){updateOutput(l,p,s,outputValue(l,p,s));l.scheduleTick(p,this,2);}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult h){if(!l.isClientSide){if(pl.isShiftKeyDown()){RuntimeIntStore.get(l,KEY,p,3)[0]=0;updateOutput(l,p,s,0);pl.displayClientMessage(Component.literal("Fault latch manual reset"),true);}else{int n=(s.getValue(THRESHOLD)+1)%4;BlockState ns=s.setValue(THRESHOLD,n);l.setBlock(p,ns,Block.UPDATE_CLIENTS);int[]rt=RuntimeIntStore.get(l,KEY,p,3);pl.displayClientMessage(Component.literal("Fault latch threshold="+LEVELS[n]+" | trips="+rt[1]+" resets="+rt[2]+" | RIGHT=reset"),true);}}return InteractionResult.sidedSuccess(l.isClientSide);}
}
