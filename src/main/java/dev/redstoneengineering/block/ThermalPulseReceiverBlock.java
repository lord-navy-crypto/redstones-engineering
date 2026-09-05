package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.InformationRuntime;
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

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/** PHONON_THERMAL BACK input -> deliberately low-bandwidth vanilla redstone FRONT output. */
public class ThermalPulseReceiverBlock extends PassiveDirectionalSignalBlock {
    public ThermalPulseReceiverBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<ThermalPulseReceiverBlock> codec() {
        return RedstoneEngineering.THERMAL_PULSE_RECEIVER_CODEC.value();
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("THERMAL PULSE IN", inputSide(state), EngineeringDomain.PHONON_THERMAL,
                        PortKind.SENSOR, PortDirection.INPUT, false, "pulse"),
                new EngineeringPort("REDSTONE OUT", outputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.CONVERTER, PortDirection.OUTPUT, true, "signal")
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        boolean valid = InformationRuntime.valid(level, "thermal_pulse", pos);
        int pulse = InformationRuntime.value(level, "thermal_pulse", pos);
        if (side == inputSide(state)) {
            return Optional.of(new EngineeringPortSnapshot(
                    port.get(), pulse, 0.0, 15.0,
                    valid && pulse > 0 ? PortQuality.VALID : PortQuality.NO_SIGNAL));
        }
        return Optional.of(EngineeringPortSnapshot.redstone(
                port.get(), state.getValue(OUTPUT),
                valid && pulse > 0 ? PortQuality.VALID : PortQuality.NO_SIGNAL));
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction
    ) {
        return direction != null && direction.getOpposite() == outputSide(state);
    }

    @Override
    protected int computeOutput(Level level, BlockPos pos, BlockState state) {
        return InformationRuntime.valid(level, "thermal_pulse", pos)
                ? Math.min(15, InformationRuntime.value(level, "thermal_pulse", pos)) : 0;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel serverLevel) serverLevel.scheduleTick(pos, this, 8);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int value = InformationRuntime.value(level, "thermal_pulse", pos);
        if (value > 0) {
            InformationRuntime.write(level, "thermal_pulse", pos, Math.max(0, value - 1), 0,
                    value > 1, Math.max(0, InformationRuntime.quality(level, "thermal_pulse", pos) - 5));
        }
        updateOutput(level, pos, state, outputValue(level, pos, state));
        level.scheduleTick(pos, this, 8);
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
            if (player.isShiftKeyDown()) {
                player.displayClientMessage(Component.literal(
                        "Thermal pulse receiver=" + outputValue(level, pos, state)
                                + "/15 | deliberately low bandwidth"), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
