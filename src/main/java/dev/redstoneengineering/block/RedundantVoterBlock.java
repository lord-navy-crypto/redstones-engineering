package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Arrays;

/** 2-out-of-3 analog voter: median output plus disagreement diagnostics. */
public class RedundantVoterBlock extends PassiveDirectionalSignalBlock {
    public static final IntegerProperty TOLERANCE = IntegerProperty.create("tolerance",0,3);
    private static final int[] TOL = {0,1,2,4};
    private static final String KEY="redundant_voter";
    public RedundantVoterBlock(Properties p){ super(p); registerDefaultState(defaultBlockState().setValue(TOLERANCE,1)); }
    @Override public MapCodec<RedundantVoterBlock> codec(){return RedstoneEngineering.REDUNDANT_VOTER_CODEC.value();}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){super.createBlockStateDefinition(b);b.add(TOLERANCE);}
    @Override protected boolean isEngineeringPort(BlockState s, Direction side){return super.isEngineeringPort(s,side)||side==leftOf(outputSide(s))||side==rightOf(outputSide(s));}

    private int[] inputs(Level l,BlockPos p,BlockState s){Direction f=outputSide(s),a=inputSide(s),b=leftOf(f),c=rightOf(f);return new int[]{readInputFrom(l,p,a),readInputFrom(l,p,b),readInputFrom(l,p,c)};}
    @Override protected int computeOutput(Level l,BlockPos p,BlockState s){
        int[] raw=inputs(l,p,s); int[] v=raw.clone(); Arrays.sort(v); int spread=v[2]-v[0]; int[]rt=RuntimeIntStore.get(l,KEY,p,4);
        rt[0]=spread; rt[1]=spread>TOL[s.getValue(TOLERANCE)]?1:0; rt[2]=Math.max(rt[2],spread); if(rt[1]!=0)rt[3]++;
        return v[1];
    }
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult h){
        if(!l.isClientSide){
            if(pl.isShiftKeyDown()){RuntimeIntStore.remove(l,KEY,p);pl.displayClientMessage(Component.literal("Voter diagnostics reset"),true);}else{
                int next=(s.getValue(TOLERANCE)+1)%4;BlockState ns=s.setValue(TOLERANCE,next);l.setBlock(p,ns,Block.UPDATE_CLIENTS);int[]in=inputs(l,p,ns);int[]rt=RuntimeIntStore.get(l,KEY,p,4);
                pl.displayClientMessage(Component.literal("2oo3 voter A/B/C="+in[0]+"/"+in[1]+"/"+in[2]+" median="+outputValue(l,p,ns)+" tolerance=±"+TOL[next]+" spread="+rt[0]+" "+(rt[1]!=0?"DEGRADED":"OK")),true);
            }
        }return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
