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

/** Runtime timing metrology: measures input period and absolute error without driving the clock domain. */
public class QuartzStabilityMonitorBlock extends DirectionalDomainBlock implements EngineeringPortProvider {
    private static final String KEY = "quartz_stability";

    public QuartzStabilityMonitorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<QuartzStabilityMonitorBlock> codec() {
        return RedstoneEngineering.QUARTZ_STABILITY_MONITOR_CODEC.value();
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(new EngineeringPort(
                "QUARTZ TIMING MEASUREMENT",
                inputSide(state),
                EngineeringDomain.QUARTZ,
                PortKind.MEASUREMENT,
                PortDirection.INPUT,
                false,
                "ticks"
        ));
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        DomainNetwork.QuartzSample input = DomainNetwork.sampleQuartz(level, inputPos(pos, state));
        int measured = measuredPeriod(level, pos);
        return Optional.of(new EngineeringPortSnapshot(
                port.get(), measured, 0.0, 4096.0,
                input.valid() && measured > 0 ? PortQuality.VALID : PortQuality.NO_SIGNAL));
    }

    public int measuredPeriod(Level level, BlockPos pos) {
        return RuntimeIntStore.get(level, KEY, pos, 4)[2];
    }

    public int nominalError(Level level, BlockPos pos) {
        return RuntimeIntStore.get(level, KEY, pos, 4)[3];
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, KEY, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        var input = DomainNetwork.sampleQuartz(level, inputPos(pos, state));
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, 4); // prev, elapsed, last, error
        runtime[1] = Math.min(4096, runtime[1] + 1);
        if (input.valid() && input.active() && runtime[0] == 0) {
            runtime[2] = runtime[1];
            int nominal = input.periodTicks();
            runtime[3] = Math.min(4096, Math.abs(runtime[2] - nominal));
            runtime[1] = 0;
        }
        runtime[0] = input.active() ? 1 : 0;
        level.scheduleTick(pos, this, 1);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                RuntimeIntStore.remove(level, KEY, pos);
                player.displayClientMessage(Component.literal("Quartz stability measurements reset"), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
