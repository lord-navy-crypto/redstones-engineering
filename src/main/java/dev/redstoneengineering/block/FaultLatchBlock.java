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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/** Persistent fault memory. BACK=fault signal, RIGHT=electrical reset, FRONT=fault output. */
public class FaultLatchBlock extends PassiveDirectionalSignalBlock {
    public static final IntegerProperty THRESHOLD = IntegerProperty.create("threshold",0,3);
    private static final int[] LEVELS={1,4,8,12};
    private static final String KEY="fault_latch";

    public FaultLatchBlock(Properties p){super(p);registerDefaultState(defaultBlockState().setValue(THRESHOLD,0));}
    @Override public MapCodec<FaultLatchBlock> codec(){return RedstoneEngineering.FAULT_LATCH_CODEC.value();}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){super.createBlockStateDefinition(b);b.add(THRESHOLD);}
    @Override protected boolean isEngineeringPort(BlockState s, Direction side){return super.isEngineeringPort(s,side)||side==rightOf(outputSide(s));}

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        Direction front = outputSide(state);
        return List.of(
                new EngineeringPort("FAULT IN", inputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.SAFETY, PortDirection.INPUT, true, "signal"),
                new EngineeringPort("RESET", rightOf(front), EngineeringDomain.REDSTONE,
                        PortKind.RESET, PortDirection.INPUT, true, "signal"),
                new EngineeringPort("LATCHED FAULT OUT", front, EngineeringDomain.REDSTONE,
                        PortKind.SAFETY, PortDirection.OUTPUT, true, "alarm")
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        Direction front = outputSide(state);
        int value = side == front ? state.getValue(OUTPUT) : readInputFrom(level, pos, side);
        return Optional.of(EngineeringPortSnapshot.redstone(port.get(), value,
                latched(level, pos) ? PortQuality.FAULT : PortQuality.VALID));
    }

    @Override protected int computeOutput(Level l,BlockPos p,BlockState s){int[]rt=RuntimeIntStore.get(l,KEY,p,3);int reset=readInputFrom(l,p,rightOf(outputSide(s)));if(reset>0){rt[0]=0;rt[2]++;}int fault=readBackInput(l,p,s);if(fault>=LEVELS[s.getValue(THRESHOLD)]&&rt[0]==0){rt[0]=1;rt[1]++;}return rt[0]!=0?15:0;}

    public static int thresholdValue(int index) { return LEVELS[Math.max(0, Math.min(LEVELS.length - 1, index))]; }
    public static boolean latched(Level level, BlockPos pos) { int[]rt=RuntimeIntStore.peek(level,KEY,pos); return rt!=null&&rt.length>0&&rt[0]!=0; }
    public static int tripCount(Level level, BlockPos pos) { int[]rt=RuntimeIntStore.peek(level,KEY,pos); return rt==null||rt.length<2?0:rt[1]; }
    public static int resetCount(Level level, BlockPos pos) { int[]rt=RuntimeIntStore.peek(level,KEY,pos); return rt==null||rt.length<3?0:rt[2]; }

    @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState o,boolean m){super.onPlace(s,l,p,o,m);if(l instanceof ServerLevel sl)sl.scheduleTick(p,this,2);}
    @Override protected void tick(BlockState s,ServerLevel l,BlockPos p,RandomSource rnd){updateOutput(l,p,s,outputValue(l,p,s));l.scheduleTick(p,this,2);}

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, KEY, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult h){
        if(!l.isClientSide && pl instanceof ServerPlayer serverPlayer){
            if(pl.isShiftKeyDown()){int[] rt=RuntimeIntStore.get(l,KEY,p,3);rt[0]=0;rt[2]++;updateOutput(l,p,s,0);pl.displayClientMessage(net.minecraft.network.chat.Component.literal("Fault latch manual reset"),true);}
            else FieldDeviceUi.open(serverPlayer,p);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
