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
    private static final int RUNTIME_SIZE = 7;

    public record TimingMeasurement(int period, int nominalError, boolean initialized,
                                    boolean referenceEdgeSeen, boolean currentMeasurement) {}

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
        if (runtime == null || runtime.length != RUNTIME_SIZE) return new TimingMeasurement(0, 0, false, false, false);
        return new TimingMeasurement(
                runtime[MEASURED_SLOT], runtime[ERROR_SLOT], runtime[INITIALIZED_SLOT] == 1,
                runtime[REFERENCE_EDGE_SLOT] == 1, runtime[CURRENT_MEASUREMENT_SLOT] == 1);
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
                runtime[ERROR_SLOT] = Math.min(4096, Math.abs(runtime[MEASURED_SLOT] - Math.max(1, input.periodTicks())));
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
                player.displayClientMessage(Component.literal("Quartz stability measurements reset; two real rising edges are required for a new period"), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
