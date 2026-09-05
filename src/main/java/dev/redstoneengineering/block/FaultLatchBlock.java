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
    // [latched, tripEvents, resetEvents, previousResetLevel]
    private static final int RUNTIME_SIZE = 4;

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
                side == front && latched(level, pos) ? PortQuality.FAULT : PortQuality.VALID));
    }

    @Override
    protected int computeOutput(Level level, BlockPos pos, BlockState state) {
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        int reset = readInputFrom(level, pos, rightOf(outputSide(state)));
        boolean resetHigh = reset > 0;

        // RESET is edge-counted, level-enforced, and has priority over FAULT.
        // A held reset cannot inflate counters or allow same-tick re-latching.
        if (resetHigh) {
            if (runtime[3] == 0) runtime[2]++;
            runtime[3] = 1;
            runtime[0] = 0;
            return 0;
        }
        runtime[3] = 0;

        int fault = readBackInput(level, pos, state);
        if (fault >= thresholdValue(state.getValue(THRESHOLD)) && runtime[0] == 0) {
            runtime[0] = 1;
            runtime[1]++;
        }
        return runtime[0] != 0 ? 15 : 0;
    }

    public static int thresholdValue(int index) { return LEVELS[Math.max(0, Math.min(LEVELS.length - 1, index))]; }
    public static boolean latched(Level level, BlockPos pos) { int[]rt=RuntimeIntStore.peek(level,KEY,pos); return rt!=null&&rt.length>0&&rt[0]!=0; }
    public static int tripCount(Level level, BlockPos pos) { int[]rt=RuntimeIntStore.peek(level,KEY,pos); return rt==null||rt.length<2?0:rt[1]; }
    public static int resetCount(Level level, BlockPos pos) { int[]rt=RuntimeIntStore.peek(level,KEY,pos); return rt==null||rt.length<3?0:rt[2]; }
    public static boolean resetActive(Level level, BlockPos pos) { int[]rt=RuntimeIntStore.peek(level,KEY,pos); return rt!=null&&rt.length>3&&rt[3]!=0; }

    @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState o,boolean m){super.onPlace(s,l,p,o,m);if(l instanceof ServerLevel sl)sl.scheduleTick(p,this,2);}
    @Override protected void tick(BlockState s,ServerLevel l,BlockPos p,RandomSource rnd){updateOutput(l,p,s,outputValue(l,p,s));l.scheduleTick(p,this,2);}

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, KEY, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if(!level.isClientSide && player instanceof ServerPlayer serverPlayer){
            if(player.isShiftKeyDown()){
                int[] runtime=RuntimeIntStore.get(level,KEY,pos,RUNTIME_SIZE);
                runtime[0]=0;
                runtime[2]++;
                runtime[3]=0;
                updateOutput(level,pos,state,0);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("Fault latch manual reset"),true);
            } else FieldDeviceUi.open(serverPlayer,pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
