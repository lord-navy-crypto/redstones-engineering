package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.RedstoneObservationSupport;
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

/** 2-out-of-3 analog voter: source-aware median/consensus output plus disagreement diagnostics. */
public class RedundantVoterBlock extends PassiveDirectionalSignalBlock {
    public static final IntegerProperty TOLERANCE = IntegerProperty.create("tolerance",0,3);
    private static final int[] TOL = {0,1,2,4};
    private static final String KEY="redundant_voter";
    // [spread, degraded, maxSpread, disagreementEvents, previousDisagreement]
    private static final int RUNTIME_SIZE = 5;

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

    public record Vote(int value, PortQuality quality, int validInputs, int spread) {}

    private RedstoneObservationSupport.Observation observation(Level level, BlockPos pos, Direction side) {
        return RedstoneObservationSupport.observe(level, pos, side);
    }

    /** Observer-only 2oo3 decision; source presence and numerical zero remain separate facts. */
    public Vote vote(Level level, BlockPos pos, BlockState state) {
        Direction front = outputSide(state);
        RedstoneObservationSupport.Observation[] observations = {
                observation(level, pos, inputSide(state)),
                observation(level, pos, leftOf(front)),
                observation(level, pos, rightOf(front))
        };

        int[] values = new int[3];
        int valid = 0;
        boolean anyStale = false;
        for (RedstoneObservationSupport.Observation observation : observations) {
            if (observation.quality() == PortQuality.STALE) anyStale = true;
            if (observation.valid()) values[valid++] = observation.value();
        }
        if (valid < 2) {
            return new Vote(0, anyStale ? PortQuality.STALE : PortQuality.NO_SIGNAL, valid, 0);
        }

        Arrays.sort(values, 0, valid);
        int spread = values[valid - 1] - values[0];
        int voted = valid == 3 ? values[1] : (values[0] + values[1] + 1) / 2;
        boolean healthy = valid == 3 && spread <= toleranceValue(state.getValue(TOLERANCE));
        return new Vote(voted, healthy ? PortQuality.VALID : PortQuality.FAULT, valid, spread);
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        Direction front = outputSide(state);
        if (side == front) {
            Vote vote = vote(level, pos, state);
            return Optional.of(EngineeringPortSnapshot.redstone(
                    port.get(), state.getValue(OUTPUT), vote.quality()));
        }
        RedstoneObservationSupport.Observation observation = observation(level, pos, side);
        return Optional.of(EngineeringPortSnapshot.redstone(
                port.get(), observation.value(), observation.quality()));
    }

    @Override
    protected int computeOutput(Level level, BlockPos pos, BlockState state) {
        Vote vote = vote(level, pos, state);
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        boolean degradedNow = vote.quality() != PortQuality.VALID;
        boolean disagreementNow = vote.quality() == PortQuality.FAULT;

        runtime[0] = vote.spread();
        runtime[1] = degradedNow ? 1 : 0;
        runtime[2] = Math.max(runtime[2], vote.spread());
        if (disagreementNow && runtime[4] == 0) runtime[3]++;
        runtime[4] = disagreementNow ? 1 : 0;
        return vote.value();
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

    @Override
    protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(!level.isClientSide && player instanceof ServerPlayer serverPlayer){
            if(player.isShiftKeyDown()){
                RuntimeIntStore.remove(level,KEY,pos);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("Voter diagnostics reset"),true);
            } else FieldDeviceUi.open(serverPlayer,pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
