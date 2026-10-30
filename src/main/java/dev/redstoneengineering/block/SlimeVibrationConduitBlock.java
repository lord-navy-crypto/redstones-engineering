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
import dev.redstoneengineering.physics.VibrationNetwork;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Low-loss guided mechanical-vibration medium with transient wave-packet diagnostics. */
public class SlimeVibrationConduitBlock extends Block implements EngineeringPortProvider {
    public static final int PACKET_TTL_TICKS = 4;

    public SlimeVibrationConduitBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<SlimeVibrationConduitBlock> codec() {
        return RedstoneEngineering.SLIME_VIBRATION_CONDUIT_CODEC.value();
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return Arrays.stream(Direction.values())
                .map(side -> new EngineeringPort(
                        "VIBRATION PATH", side, EngineeringDomain.MECHANICAL_VIBRATION,
                        PortKind.BUS, PortDirection.BIDIRECTIONAL, false, "amplitude"))
                .toList();
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        VibrationNetwork.Wave wave = VibrationNetwork.sample(level, pos);
        return Optional.of(new EngineeringPortSnapshot(
                port.get(), Math.max(0, Math.min(15, wave.amplitude())), 0.0, 15.0,
                wave.valid() && wave.amplitude() > 0 ? PortQuality.VALID : PortQuality.NO_SIGNAL));
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int amplitude = InformationRuntime.value(level, "mech_wave", pos);
        if (amplitude <= 0 || !InformationRuntime.valid(level, "mech_wave", pos)) {
            InformationRuntime.clear(level, "mech_wave", pos);
            return;
        }
        int next = Math.max(0, amplitude - 2);
        if (next == 0) {
            InformationRuntime.clear(level, "mech_wave", pos);
        } else {
            InformationRuntime.write(level, "mech_wave", pos, next,
                    InformationRuntime.aux(level, "mech_wave", pos), true,
                    Math.max(0, InformationRuntime.quality(level, "mech_wave", pos) - 10));
            level.scheduleTick(pos, this, PACKET_TTL_TICKS);
        }
    }

    /**
     * Universal non-invasive LDLib2 entry for a formerly uninspectable medium.
     * Sneak reads the already available packet snapshot; neither path samples,
     * mutates nor restarts the transient transmission.
     */
    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                VibrationNetwork.Wave wave = VibrationNetwork.sample(level, pos);
                player.displayClientMessage(Component.literal(
                        "Slime Vibration Path | A=" + wave.amplitude()
                                + "/15 | evidence=" + (wave.valid() && wave.amplitude() > 0
                                ? PortQuality.VALID : PortQuality.NO_SIGNAL)
                                + " | six-way packet medium | decay after " + PACKET_TTL_TICKS + "t"), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) InformationRuntime.clear(level, "mech_wave", pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
