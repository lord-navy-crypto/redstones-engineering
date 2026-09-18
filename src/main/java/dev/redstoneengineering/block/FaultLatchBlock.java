package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.operations.world.OperationWorldResourceProvider;
import dev.redstoneengineering.operations.world.OperationWorldResourceSnapshot;
import dev.redstoneengineering.physics.RedstoneObservationSupport;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Persistent fault memory. BACK=fault signal, RIGHT=electrical reset, FRONT=fault output. */
public class FaultLatchBlock extends PassiveDirectionalSignalBlock implements OperationWorldResourceProvider {
    public static final IntegerProperty THRESHOLD = IntegerProperty.create("threshold",0,3);
    private static final int[] LEVELS={1,4,8,12};
    private static final String KEY="fault_latch";
    private static final int LATCHED = 0;
    private static final int TRIP_COUNT = 1;
    private static final int RESET_COUNT = 2;
    private static final int PREVIOUS_RESET = 3;
    private static final int RESET_REACQUIRE = 4;
    private static final int RUNTIME_SIZE = 5;

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

    private static RedstoneObservationSupport.Observation observeInput(Level level, BlockPos pos, Direction side) {
        return RedstoneObservationSupport.observe(level, pos, side);
    }

    private static boolean evidenceUnusable(PortQuality quality) {
        return quality == PortQuality.STALE
                || quality == PortQuality.FAULT
                || quality == PortQuality.DOMAIN_MISMATCH
                || quality == PortQuality.TOPOLOGY_ERROR;
    }

    private static boolean faultClearForReset(
            RedstoneObservationSupport.Observation faultObservation, int threshold
    ) {
        if (evidenceUnusable(faultObservation.quality())) return false;
        if (faultObservation.quality() == PortQuality.NO_SIGNAL) return true;
        return faultObservation.valid() && faultObservation.value() < threshold;
    }

    public static boolean resetPermitted(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof FaultLatchBlock latch)) return false;
        var fault = observeInput(level, pos, latch.inputSide(state));
        return faultClearForReset(fault, thresholdValue(state.getValue(THRESHOLD)));
    }

    private static PortQuality operationQuality(
            RedstoneObservationSupport.Observation fault,
            RedstoneObservationSupport.Observation reset
    ) {
        PortQuality quality = fault.quality();
        if (quality == PortQuality.NO_SIGNAL) quality = PortQuality.VALID;
        if (evidenceUnusable(reset.quality())) {
            quality = RedstoneObservationSupport.combineQuality(quality, reset.quality());
        }
        return quality;
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        Direction front = outputSide(state);
        if (side == front) {
            // The alarm is authoritative evidence even while the device health is FAULT.
            // Operational health is projected separately by EngineeringDeviceMenu.
            return Optional.of(EngineeringPortSnapshot.redstone(port.get(), state.getValue(OUTPUT), PortQuality.VALID));
        }
        RedstoneObservationSupport.Observation observation = observeInput(level, pos, side);
        return Optional.of(EngineeringPortSnapshot.redstone(port.get(), observation.value(), observation.quality()));
    }

    @Override
    public OperationWorldResourceSnapshot operationResourceSnapshot(Level level, BlockPos pos, BlockState state) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        boolean latched = latched(level, pos);
        var fault = observeInput(level, pos, inputSide(state));
        var reset = observeInput(level, pos, rightOf(outputSide(state)));
        PortQuality quality = runtime == null || runtime.length < RUNTIME_SIZE
                ? PortQuality.STALE
                : operationQuality(fault, reset);
        return new OperationWorldResourceSnapshot(
                "fault_latch:" + pos.asLong(),
                Set.of("fault_memory"),
                !latched,
                false,
                false,
                latched,
                quality,
                Map.of(
                        "trip_count", (long) tripCount(level, pos),
                        "reset_count", (long) resetCount(level, pos),
                        "reset_active", resetActive(level, pos) ? 1L : 0L,
                        "threshold", (long) thresholdValue(state.getValue(THRESHOLD))
                )
        );
    }

    @Override
    protected int computeOutput(Level level, BlockPos pos, BlockState state) {
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        int threshold = thresholdValue(state.getValue(THRESHOLD));
        var resetObservation = observeInput(level, pos, rightOf(outputSide(state)));
        var faultObservation = observeInput(level, pos, inputSide(state));

        boolean resetEvidenceBad = evidenceUnusable(resetObservation.quality());
        boolean resetHigh = resetObservation.valid() && resetObservation.value() > 0;
        boolean resetRising = false;

        if (resetEvidenceBad) {
            runtime[RESET_REACQUIRE] = 1;
        } else if (runtime[RESET_REACQUIRE] != 0) {
            // Re-establish the electrical reset level after evidence recovery without inventing an edge.
            runtime[PREVIOUS_RESET] = resetHigh ? 1 : 0;
            runtime[RESET_REACQUIRE] = 0;
        } else {
            resetRising = resetHigh && runtime[PREVIOUS_RESET] == 0;
            runtime[PREVIOUS_RESET] = resetHigh ? 1 : 0;
        }

        if (resetRising && faultClearForReset(faultObservation, threshold)) {
            if (runtime[LATCHED] != 0) {
                runtime[LATCHED] = 0;
                if (runtime[RESET_COUNT] < Integer.MAX_VALUE) runtime[RESET_COUNT]++;
            }
        }

        boolean faultActive = evidenceUnusable(faultObservation.quality())
                || (faultObservation.valid() && faultObservation.value() >= threshold);
        if (faultActive && runtime[LATCHED] == 0) {
            runtime[LATCHED] = 1;
            if (runtime[TRIP_COUNT] < Integer.MAX_VALUE) runtime[TRIP_COUNT]++;
        }

        return runtime[LATCHED] != 0 ? 15 : 0;
    }

    public static int thresholdValue(int index) { return LEVELS[Math.max(0, Math.min(LEVELS.length - 1, index))]; }

    public static boolean stepThreshold(Level level, BlockPos pos, boolean forward) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof FaultLatchBlock latch)) return false;
        int current = state.getValue(THRESHOLD);
        int next = Math.floorMod(current + (forward ? 1 : -1), LEVELS.length);
        level.setBlock(pos, state.setValue(THRESHOLD, next), Block.UPDATE_CLIENTS);
        if (level instanceof ServerLevel server) server.scheduleTick(pos, latch, 1);
        return true;
    }
    public static boolean latched(Level level, BlockPos pos) { int[]rt=RuntimeIntStore.peek(level,KEY,pos); return rt!=null&&rt.length>0&&rt[LATCHED]!=0; }
    public static int tripCount(Level level, BlockPos pos) { int[]rt=RuntimeIntStore.peek(level,KEY,pos); return rt==null||rt.length<2?0:rt[TRIP_COUNT]; }
    public static int resetCount(Level level, BlockPos pos) { int[]rt=RuntimeIntStore.peek(level,KEY,pos); return rt==null||rt.length<3?0:rt[RESET_COUNT]; }
    public static boolean resetActive(Level level, BlockPos pos) { int[]rt=RuntimeIntStore.peek(level,KEY,pos); return rt!=null&&rt.length>3&&rt[PREVIOUS_RESET]!=0; }

    public boolean manualReset(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(this) || !resetPermitted(level, pos, state)) return false;
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        if (runtime[LATCHED] != 0) {
            runtime[LATCHED] = 0;
            if (runtime[RESET_COUNT] < Integer.MAX_VALUE) runtime[RESET_COUNT]++;
        }
        runtime[RESET_REACQUIRE] = 1;
        updateOutput(level, pos, state, 0);
        if (level instanceof ServerLevel server) server.scheduleTick(pos, this, 2);
        return true;
    }

    @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState o,boolean m){super.onPlace(s,l,p,o,m);if(l instanceof ServerLevel sl)sl.scheduleTick(p,this,2);}
    @Override protected void tick(BlockState s,ServerLevel l,BlockPos p,RandomSource rnd){updateOutput(l,p,s,outputValue(l,p,s));l.scheduleTick(p,this,2);}
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) { if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, KEY, pos); super.onRemove(state, level, pos, newState, movedByPiston); }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if(!level.isClientSide && player instanceof ServerPlayer serverPlayer){
            if(player.isShiftKeyDown()){
                boolean reset = manualReset(level, pos);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        reset ? "Fault latch manual reset" : "Fault latch reset blocked: fault not proven clear"), true);
            } else FieldDeviceUi.open(serverPlayer,pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
