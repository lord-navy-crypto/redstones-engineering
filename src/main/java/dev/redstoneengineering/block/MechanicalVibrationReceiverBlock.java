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
import dev.redstoneengineering.physics.VibrationNetwork;
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

/** Directional vibration-to-redstone receiver: BACK mechanical input, FRONT redstone output. */
public class MechanicalVibrationReceiverBlock extends PassiveDirectionalSignalBlock {
    public MechanicalVibrationReceiverBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<MechanicalVibrationReceiverBlock> codec() {
        return RedstoneEngineering.MECHANICAL_VIBRATION_RECEIVER_CODEC.value();
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("VIBRATION IN", inputSide(state), EngineeringDomain.MECHANICAL_VIBRATION,
                        PortKind.SENSOR, PortDirection.INPUT, false, "amplitude"),
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
        if (side == outputSide(state)) {
            return Optional.of(EngineeringPortSnapshot.redstone(
                    port.get(), state.getValue(OUTPUT), PortQuality.VALID));
        }
        VibrationNetwork.Wave wave = VibrationNetwork.sample(level, pos);
        return Optional.of(new EngineeringPortSnapshot(
                port.get(), Math.max(0, Math.min(15, wave.amplitude())), 0.0, 15.0,
                wave.valid() && wave.amplitude() > 0 ? PortQuality.VALID : PortQuality.NO_SIGNAL));
    }

    @Override
    protected int computeOutput(Level level, BlockPos pos, BlockState state) {
        VibrationNetwork.Wave wave = VibrationNetwork.sample(level, pos);
        return wave.valid() ? Math.min(15, wave.amplitude()) : 0;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel serverLevel) serverLevel.scheduleTick(pos, this, 4);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int value = InformationRuntime.value(level, "mech_wave", pos);
        if (value > 0) {
            int next = Math.max(0, value - 2);
            if (next == 0) {
                InformationRuntime.clear(level, "mech_wave", pos);
            } else {
                InformationRuntime.write(level, "mech_wave", pos, next,
                        InformationRuntime.aux(level, "mech_wave", pos), true,
                        Math.max(0, InformationRuntime.quality(level, "mech_wave", pos) - 5));
            }
        }
        updateOutput(level, pos, state, outputValue(level, pos, state));
        level.scheduleTick(pos, this, 4);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) InformationRuntime.clear(level, "mech_wave", pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                VibrationNetwork.Wave wave = VibrationNetwork.sample(level, pos);
                player.displayClientMessage(Component.literal(
                        "Mechanical wave A=" + wave.amplitude() + " f=" + wave.frequency()
                                + " valid=" + wave.valid()), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
