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
import dev.redstoneengineering.physics.InformationRuntime;
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
import net.minecraft.world.phys.BlockHitResult;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** End-game high-effective-thermal-conductivity conduit with bounded pulse diagnostics. */
public class PhononConduitBlock extends Block implements EngineeringPortProvider {
    public static final int PACKET_TTL_TICKS = 8;

    public PhononConduitBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<PhononConduitBlock> codec() {
        return RedstoneEngineering.PHONON_CONDUIT_CODEC.value();
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return Arrays.stream(Direction.values())
                .map(side -> new EngineeringPort(
                        "PHONON THERMAL PATH", side, EngineeringDomain.PHONON_THERMAL,
                        PortKind.BUS, PortDirection.BIDIRECTIONAL, false, "pulse"))
                .toList();
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        int pulse = InformationRuntime.value(level, "thermal_pulse", pos);
        boolean valid = InformationRuntime.valid(level, "thermal_pulse", pos) && pulse > 0;
        return Optional.of(new EngineeringPortSnapshot(
                port.get(), pulse, 0.0, 15.0,
                valid ? PortQuality.VALID : PortQuality.NO_SIGNAL));
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int pulse = InformationRuntime.value(level, "thermal_pulse", pos);
        if (pulse <= 0 || !InformationRuntime.valid(level, "thermal_pulse", pos)) {
            InformationRuntime.clear(level, "thermal_pulse", pos);
            return;
        }
        int next = Math.max(0, pulse - 2);
        if (next == 0) {
            InformationRuntime.clear(level, "thermal_pulse", pos);
        } else {
            InformationRuntime.write(level, "thermal_pulse", pos, next, 0, true,
                    Math.max(0, InformationRuntime.quality(level, "thermal_pulse", pos) - 10));
            level.scheduleTick(pos, this, PACKET_TTL_TICKS);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) InformationRuntime.clear(level, "thermal_pulse", pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            FieldDeviceUi.open(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
