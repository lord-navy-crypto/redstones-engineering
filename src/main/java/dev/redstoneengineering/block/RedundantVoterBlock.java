package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** 2-out-of-3 analog voter: median output plus disagreement diagnostics. */
public class RedundantVoterBlock extends PassiveDirectionalSignalBlock {
    public static final IntegerProperty TOLERANCE = IntegerProperty.create("tolerance",0,3);
    private static final int[] TOL = {0,1,2,4};
    private static final String KEY="redundant_voter";

    public RedundantVoterBlock(Properties p){ super(p); registerDefaultState(defaultBlockState().setValue(TOLERANCE,1)); }
    @Override public MapCodec<RedundantVoterBlock> codec(){return RedstoneEngineering.REDUNDANT_VOTER_CODEC.value();}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){super.createBlockStateDefinition(b);b.add(TOLERANCE);}
    @Override protected boolean isEngineeringPort(BlockState s, Direction side){return super.isEngineeringPort(s,side)||side==leftOf(outputSide(s))||side==rightOf(outputSide(s));}

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        Direction front = outputSide(state);
        return List.of(
                analogInput("CHANNEL A", inputSide(state)),
                analogInput("CHANNEL B", leftOf(front)),
                analogInput("CHANNEL C", rightOf(front)),
                new EngineeringPort("VOTED OUT", front, EngineeringDomain.REDSTONE,
                        PortKind.SAFETY, PortDirection.OUTPUT, true, "signal")
        );
    }

    private static EngineeringPort analogInput(String name, Direction side) {
        return new EngineeringPort(name, side, EngineeringDomain.REDSTONE,
                PortKind.MEASUREMENT, PortDirection.INPUT, true, "signal");
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        Direction front = outputSide(state);
        int value = side == front ? state.getValue(OUTPUT) : readInputFrom(level, pos, side);
        int[] rt = RuntimeIntStore.peek(level, KEY, pos);
        boolean degraded = rt != null && rt.length > 1 && rt[1] != 0;
        return Optional.of(EngineeringPortSnapshot.redstone(
                port.get(), value, degraded ? PortQuality.FAULT : PortQuality.VALID));
    }

    private int[] inputs(Level l,BlockPos p,BlockState s){Direction f=outputSide(s),a=inputSide(s),b=leftOf(f),c=rightOf(f);return new int[]{readInputFrom(l,p,a),readInputFrom(l,p,b),readInputFrom(l,p,c)};}
    @Override protected int computeOutput(Level l,BlockPos p,BlockState s){
        int[] raw=inputs(l,p,s); int[] v=raw.clone(); Arrays.sort(v); int spread=v[2]-v[0]; int[]rt=RuntimeIntStore.get(l,KEY,p,4);
        rt[0]=spread; rt[1]=spread>TOL[s.getValue(TOLERANCE)]?1:0; rt[2]=Math.max(rt[2],spread); if(rt[1]!=0)rt[3]++;
        return v[1];
    }

    public static int toleranceValue(int index) { return TOL[Math.max(0, Math.min(TOL.length - 1, index))]; }
    public static int spread(Level level, BlockPos pos) { int[] rt=RuntimeIntStore.peek(level,KEY,pos); return rt==null||rt.length<1?0:rt[0]; }
    public static boolean degraded(Level level, BlockPos pos) { int[] rt=RuntimeIntStore.peek(level,KEY,pos); return rt!=null&&rt.length>1&&rt[1]!=0; }
    public static int maxSpread(Level level, BlockPos pos) { int[] rt=RuntimeIntStore.peek(level,KEY,pos); return rt==null||rt.length<3?0:rt[2]; }
    public static int disagreementCount(Level level, BlockPos pos) { int[] rt=RuntimeIntStore.peek(level,KEY,pos); return rt==null||rt.length<4?0:rt[3]; }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, KEY, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult h){
        if(!l.isClientSide && pl instanceof ServerPlayer serverPlayer){
            if(pl.isShiftKeyDown()){RuntimeIntStore.remove(l,KEY,p);pl.displayClientMessage(net.minecraft.network.chat.Component.literal("Voter diagnostics reset"),true);}
            else FieldDeviceUi.open(serverPlayer,p);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
