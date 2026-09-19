package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.DomainNetwork;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/**
 * Runtime timing metrology. A complete period requires two genuine rising edges after phase
 * initialization; inspection is read-only, and retained old evidence is STALE until revalidated.
 */
public class QuartzStabilityMonitorBlock extends DirectionalDomainBlock implements EngineeringPortProvider {
    private static final String KEY = "quartz_stability";
    private static final int PREVIOUS_SLOT = 0;
    private static final int ELAPSED_SLOT = 1;
    private static final int MEASURED_SLOT = 2;
    private static final int ERROR_SLOT = 3;
    private static final int INITIALIZED_SLOT = 4;
    private static final int REFERENCE_EDGE_SLOT = 5;
    private static final int CURRENT_MEASUREMENT_SLOT = 6;
    private static final int WINDOW_INDEX_SLOT = 7;
    private static final int WINDOW_COUNT_SLOT = 8;
    private static final int WINDOW_MIN_SLOT = 9;
    private static final int WINDOW_MAX_SLOT = 10;
    private static final int WINDOW_MEAN_X100_SLOT = 11;
    private static final int WINDOW_JITTER_SLOT = 12;
    private static final int WINDOW_MAX_ERROR_SLOT = 13;
    private static final int WINDOW_RESET_PENDING_SLOT = 14;
    private static final int WINDOW_NOMINAL_SLOT = 15;
    private static final int SAMPLE_BASE = 16;
    private static final int WINDOW_SIZE = 8;
    private static final int RUNTIME_SIZE = SAMPLE_BASE + WINDOW_SIZE;

    public record TimingMeasurement(int period, int nominalError, boolean initialized,
                                    boolean referenceEdgeSeen, boolean currentMeasurement,
                                    int sampleCount, int minPeriod, int maxPeriod,
                                    int meanPeriodX100, int jitter, int maxNominalError) {}

    public QuartzStabilityMonitorBlock(Properties properties) { super(properties); }

    @Override public MapCodec<QuartzStabilityMonitorBlock> codec() { return RedstoneEngineering.QUARTZ_STABILITY_MONITOR_CODEC.value(); }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(new EngineeringPort(
                "QUARTZ TIMING MEASUREMENT", inputSide(state), EngineeringDomain.QUARTZ,
                PortKind.MEASUREMENT, PortDirection.INPUT, false, "ticks"));
    }

    public static TimingMeasurement measurement(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        if (runtime == null || runtime.length != RUNTIME_SIZE) {
            return new TimingMeasurement(0, 0, false, false, false,
                    0, 0, 0, 0, 0, 0);
        }
        return new TimingMeasurement(
                runtime[MEASURED_SLOT], runtime[ERROR_SLOT], runtime[INITIALIZED_SLOT] == 1,
                runtime[REFERENCE_EDGE_SLOT] == 1, runtime[CURRENT_MEASUREMENT_SLOT] == 1,
                runtime[WINDOW_COUNT_SLOT], runtime[WINDOW_MIN_SLOT], runtime[WINDOW_MAX_SLOT],
                runtime[WINDOW_MEAN_X100_SLOT], runtime[WINDOW_JITTER_SLOT],
                runtime[WINDOW_MAX_ERROR_SLOT]);
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        BlockPos inputPos = inputPos(pos, state);
        DomainNetwork.QuartzSample input = DomainNetwork.sampleQuartz(level, inputPos);
        TimingMeasurement measurement = measurement(level, pos);
        PortQuality upstream = level.getBlockState(inputPos).getBlock() instanceof QuartzTimingLineBlock
                ? QuartzTimingLineBlock.quality(level, inputPos)
                : (input.valid() ? PortQuality.VALID : PortQuality.NO_SIGNAL);
        PortQuality quality;
        if (upstream == PortQuality.TOPOLOGY_ERROR) quality = PortQuality.TOPOLOGY_ERROR;
        else if (input.valid() && measurement.currentMeasurement()) quality = PortQuality.VALID;
        else if (measurement.period() > 0) quality = PortQuality.STALE;
        else quality = PortQuality.NO_SIGNAL;
        return Optional.of(new EngineeringPortSnapshot(port.get(), measurement.period(), 0.0, 4096.0, quality));
    }

    public int measuredPeriod(Level level, BlockPos pos) { return measurement(level, pos).period(); }
    public int nominalError(Level level, BlockPos pos) { return measurement(level, pos).nominalError(); }

    public static int sampleCount(Level level, BlockPos pos) { return measurement(level, pos).sampleCount(); }
    public static int meanPeriodX100(Level level, BlockPos pos) { return measurement(level, pos).meanPeriodX100(); }
    public static int jitterTicks(Level level, BlockPos pos) { return measurement(level, pos).jitter(); }
    public static int maxNominalError(Level level, BlockPos pos) { return measurement(level, pos).maxNominalError(); }

    private static void resetWindow(int[] runtime, int nominal) {
        runtime[WINDOW_INDEX_SLOT] = 0;
        runtime[WINDOW_COUNT_SLOT] = 0;
        runtime[WINDOW_MIN_SLOT] = 0;
        runtime[WINDOW_MAX_SLOT] = 0;
        runtime[WINDOW_MEAN_X100_SLOT] = 0;
        runtime[WINDOW_JITTER_SLOT] = 0;
        runtime[WINDOW_MAX_ERROR_SLOT] = 0;
        runtime[WINDOW_NOMINAL_SLOT] = Math.max(1, nominal);
        for (int i = 0; i < WINDOW_SIZE; i++) runtime[SAMPLE_BASE + i] = 0;
    }

    private static void recordWindowSample(int[] runtime, int period, int nominal) {
        int boundedPeriod = Math.max(1, Math.min(4096, period));
        int boundedNominal = Math.max(1, Math.min(4096, nominal));
        if (runtime[WINDOW_RESET_PENDING_SLOT] != 0
                || (runtime[WINDOW_COUNT_SLOT] > 0 && runtime[WINDOW_NOMINAL_SLOT] != boundedNominal)) {
            resetWindow(runtime, boundedNominal);
        }
        runtime[WINDOW_RESET_PENDING_SLOT] = 0;
        runtime[WINDOW_NOMINAL_SLOT] = boundedNominal;

        int index = Math.floorMod(runtime[WINDOW_INDEX_SLOT], WINDOW_SIZE);
        runtime[SAMPLE_BASE + index] = boundedPeriod;
        runtime[WINDOW_INDEX_SLOT] = (index + 1) % WINDOW_SIZE;
        runtime[WINDOW_COUNT_SLOT] = Math.min(WINDOW_SIZE, runtime[WINDOW_COUNT_SLOT] + 1);

        int count = runtime[WINDOW_COUNT_SLOT];
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        int maxError = 0;
        long sum = 0L;
        for (int i = 0; i < count; i++) {
            int sample = runtime[SAMPLE_BASE + i];
            min = Math.min(min, sample);
            max = Math.max(max, sample);
            sum += sample;
            maxError = Math.max(maxError, Math.abs(sample - boundedNominal));
        }
        runtime[WINDOW_MIN_SLOT] = min == Integer.MAX_VALUE ? 0 : min;
        runtime[WINDOW_MAX_SLOT] = max == Integer.MIN_VALUE ? 0 : max;
        runtime[WINDOW_MEAN_X100_SLOT] = count <= 0 ? 0 : (int) Math.round((sum * 100.0) / count);
        runtime[WINDOW_JITTER_SLOT] = count <= 0 ? 0 : Math.max(0, max - min);
        runtime[WINDOW_MAX_ERROR_SLOT] = maxError;
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, KEY, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        DomainNetwork.QuartzSample input = DomainNetwork.sampleQuartz(level, inputPos(pos, state));
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);

        if (!input.valid()) {
            runtime[PREVIOUS_SLOT] = 0;
            runtime[ELAPSED_SLOT] = 0;
            runtime[INITIALIZED_SLOT] = 0;
            runtime[REFERENCE_EDGE_SLOT] = 0;
            runtime[CURRENT_MEASUREMENT_SLOT] = 0;
            // Keep the last stability window as STALE evidence; the next complete period
            // starts a fresh window so pre-gap and post-gap timing are never mixed.
            runtime[WINDOW_RESET_PENDING_SLOT] = 1;
            level.scheduleTick(pos, this, 1);
            return;
        }

        if (runtime[INITIALIZED_SLOT] == 0) {
            runtime[PREVIOUS_SLOT] = input.active() ? 1 : 0;
            runtime[ELAPSED_SLOT] = 0;
            runtime[INITIALIZED_SLOT] = 1;
            runtime[REFERENCE_EDGE_SLOT] = 0;
            runtime[CURRENT_MEASUREMENT_SLOT] = 0;
            level.scheduleTick(pos, this, 1);
            return;
        }

        if (runtime[REFERENCE_EDGE_SLOT] == 1) runtime[ELAPSED_SLOT] = Math.min(4096, runtime[ELAPSED_SLOT] + 1);
        boolean rising = input.active() && runtime[PREVIOUS_SLOT] == 0;
        if (rising) {
            if (runtime[REFERENCE_EDGE_SLOT] == 0) {
                runtime[REFERENCE_EDGE_SLOT] = 1;
                runtime[ELAPSED_SLOT] = 0;
                runtime[CURRENT_MEASUREMENT_SLOT] = 0;
            } else {
                runtime[MEASURED_SLOT] = Math.max(1, runtime[ELAPSED_SLOT]);
                int nominal = Math.max(1, input.periodTicks());
                runtime[ERROR_SLOT] = Math.min(4096, Math.abs(runtime[MEASURED_SLOT] - nominal));
                recordWindowSample(runtime, runtime[MEASURED_SLOT], nominal);
                runtime[ELAPSED_SLOT] = 0;
                runtime[CURRENT_MEASUREMENT_SLOT] = 1;
            }
        }
        runtime[PREVIOUS_SLOT] = input.active() ? 1 : 0;
        level.scheduleTick(pos, this, 1);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                RuntimeIntStore.remove(level, KEY, pos);
                player.displayClientMessage(Component.literal(
                        "Quartz stability window reset; two real rising edges are required for a new period, then up to "
                                + WINDOW_SIZE + " periods populate jitter statistics"), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
