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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/**
 * Three-port signal copier. "Non-invasive" means the TAP port never back-drives or
 * changes the main IN -> THROUGH transfer. The TAP is still an active 0..15 output,
 * not an idealized zero-loading measurement probe.
 */
public class SignalTapBlock extends DirectionalSignalBlock {
    private static final String RUNTIME_KEY = "signal_tap";
    private static final int EVIDENCE_HOLD_ACTIVE = 0;
    private static final int BAD_EVIDENCE_EPISODES = 1;
    private static final int RUNTIME_SIZE = 2;

    public SignalTapBlock(Properties properties) { super(properties); }
    @Override public MapCodec<SignalTapBlock> codec() { return RedstoneEngineering.SIGNAL_TAP_CODEC.value(); }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        Direction facing = state.getValue(FACING);
        return List.of(
                new EngineeringPort("SIGNAL IN", inputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.REDSTONE_ANALOG, PortDirection.INPUT, true, "signal"),
                new EngineeringPort("THROUGH OUT", outputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.REDSTONE_ANALOG, PortDirection.OUTPUT, true, "signal"),
                new EngineeringPort("NON-INVASIVE TAP", leftOf(facing), EngineeringDomain.REDSTONE,
                        PortKind.TAP, PortDirection.OUTPUT, true, "signal")
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> descriptor = engineeringPort(state, side);
        if (descriptor.isEmpty()) return Optional.empty();
        var input = RedstoneObservationSupport.observe(level, pos, inputSide(state));
        int value = side == inputSide(state) ? input.value() : state.getValue(OUTPUT);
        return Optional.of(EngineeringPortSnapshot.redstone(descriptor.get(), value, input.quality()));
    }

    private static boolean unusableButNotAbsent(PortQuality quality) {
        return quality == PortQuality.STALE
                || quality == PortQuality.FAULT
                || quality == PortQuality.DOMAIN_MISMATCH
                || quality == PortQuality.TOPOLOGY_ERROR;
    }

    public static boolean evidenceHoldActive(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, RUNTIME_KEY, pos);
        return runtime != null && runtime.length >= RUNTIME_SIZE && runtime[EVIDENCE_HOLD_ACTIVE] != 0;
    }

    public static int badEvidenceEpisodes(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, RUNTIME_KEY, pos);
        return runtime == null || runtime.length < RUNTIME_SIZE
                ? 0 : Math.max(0, runtime[BAD_EVIDENCE_EPISODES]);
    }

    public static PortQuality inputQuality(Level level, BlockPos pos, BlockState state) {
        return RedstoneObservationSupport.observe(level, pos, seriesInputSide(state)).quality();
    }

    @Override
    protected boolean isEngineeringPort(BlockState state, Direction side) {
        Direction facing = state.getValue(FACING);
        return side == inputSide(state) || side == outputSide(state) || side == leftOf(facing);
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        Direction facing = state.getValue(FACING);
        if (direction == outputSide(state).getOpposite() || direction == leftOf(facing).getOpposite()) return state.getValue(OUTPUT);
        return 0;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        var input = RedstoneObservationSupport.observe(level, pos, inputSide(state));
        int[] runtime = RuntimeIntStore.get(level, RUNTIME_KEY, pos, RUNTIME_SIZE);

        if (input.valid()) {
            runtime[EVIDENCE_HOLD_ACTIVE] = 0;
            updateOutput(level, pos, state, input.value());
        } else if (input.quality() == PortQuality.NO_SIGNAL) {
            // A physically absent upstream source is a real de-energized condition for a tap.
            runtime[EVIDENCE_HOLD_ACTIVE] = 0;
            updateOutput(level, pos, state, 0);
        } else if (unusableButNotAbsent(input.quality())) {
            if (runtime[EVIDENCE_HOLD_ACTIVE] == 0 && runtime[BAD_EVIDENCE_EPISODES] < Integer.MAX_VALUE) {
                runtime[BAD_EVIDENCE_EPISODES]++;
            }
            runtime[EVIDENCE_HOLD_ACTIVE] = 1;
            // Faulted/stale evidence is not a new numerical zero; retain the last trustworthy copy.
        }

        level.updateNeighborsAt(pos.relative(leftOf(state.getValue(FACING))), this);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, RUNTIME_KEY, pos);
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!player.isShiftKeyDown()) {
                FieldDeviceUi.open(serverPlayer, pos);
                return InteractionResult.CONSUME;
            }
            player.displayClientMessage(Component.literal(
                    "Signal Tap | IN=" + inputSide(state).getName()
                            + " | THROUGH=" + outputSide(state).getName()
                            + " | TAP COPY=" + leftOf(state.getValue(FACING)).getName()
                            + " | value=" + state.getValue(OUTPUT) + "/15"
                            + " | quality=" + inputQuality(level, pos, state)
                            + " | evidence=" + (evidenceHoldActive(level, pos) ? "HOLD LAST" : "LIVE")
                            + " | badEvidenceEpisodes=" + badEvidenceEpisodes(level, pos)
                            + " | main path preserved; tap cannot back-drive IN/THROUGH"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
