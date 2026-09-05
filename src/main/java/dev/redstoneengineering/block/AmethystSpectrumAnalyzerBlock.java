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

/** Non-contact, read-only frequency-domain observer for nearby amethyst resonance media. */
public class AmethystSpectrumAnalyzerBlock extends DomainBlock implements EngineeringPortProvider {
    private static final int RADIUS = 6;
    private static final int SAMPLE_PERIOD_TICKS = 10;
    private static final String KEY = "amethyst_spectrum";

    public record Spectrum(int dominantFrequency, int energy, int activeBands, int samples) {}

    public AmethystSpectrumAnalyzerBlock(Properties properties) {
        super(properties);
    }

    @Override public MapCodec<AmethystSpectrumAnalyzerBlock> codec() {
        return RedstoneEngineering.AMETHYST_SPECTRUM_ANALYZER_CODEC.value();
    }

    @Override public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(new EngineeringPort("SPECTRUM APERTURE", Direction.UP, EngineeringDomain.AMETHYST,
                PortKind.MEASUREMENT, PortDirection.INPUT, false, "energy"));
    }

    @Override public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        Spectrum spectrum = spectrum(level, pos);
        return Optional.of(new EngineeringPortSnapshot(port.get(), Math.max(0, spectrum.energy()), 0.0,
                Math.max(15.0, spectrum.energy()), spectrum.samples() > 0 ? PortQuality.VALID : PortQuality.NO_SIGNAL));
    }

    public static Spectrum spectrum(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        if (runtime == null || runtime.length != 4) return new Spectrum(0, 0, 0, 0);
        return new Spectrum(runtime[0], runtime[1], runtime[2], runtime[3]);
    }

    private static Spectrum scan(ServerLevel level, BlockPos pos) {
        int[] energy = new int[16];
        int events = 0;
        int radiusSquared = RADIUS * RADIUS;
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dy = -RADIUS; dy <= RADIUS; dy++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radiusSquared) continue;
                    BlockPos samplePos = pos.offset(dx, dy, dz);
                    if (!level.hasChunkAt(samplePos)) continue;
                    var sampleState = level.getBlockState(samplePos);
                    if (sampleState.getBlock() instanceof AmethystResonanceDustBlock
                            && AmethystResonanceDustBlock.active(level, samplePos)) {
                        int frequency = AmethystResonanceDustBlock.frequency(level, samplePos);
                        if (frequency >= 1 && frequency <= 15) {
                            energy[frequency] += AmethystResonanceDustBlock.amplitude(level, samplePos);
                        }
                        events++;
                    }
                }
            }
        }
        int dominantFrequency = 0;
        int dominantEnergy = 0;
        int bands = 0;
        for (int frequency = 1; frequency <= 15; frequency++) {
            if (energy[frequency] > 0) bands++;
            if (energy[frequency] > dominantEnergy) {
                dominantEnergy = energy[frequency];
                dominantFrequency = frequency;
            }
        }
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, 4);
        runtime[0] = dominantFrequency;
        runtime[1] = dominantEnergy;
        runtime[2] = bands;
        runtime[3] = events;
        return new Spectrum(dominantFrequency, dominantEnergy, bands, events);
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel serverLevel && !state.is(oldState.getBlock())) {
            scan(serverLevel, pos);
            serverLevel.scheduleTick(pos, this, SAMPLE_PERIOD_TICKS);
        }
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        scan(level, pos);
        level.scheduleTick(pos, this, SAMPLE_PERIOD_TICKS);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, KEY, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            Spectrum spectrum = scan((ServerLevel) level, pos);
            if (player.isShiftKeyDown()) {
                player.displayClientMessage(Component.literal("Amethyst spectrum | dominant f="
                        + spectrum.dominantFrequency() + " | energy=" + spectrum.energy()
                        + " | active bands=" + spectrum.activeBands() + " | samples=" + spectrum.samples()), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
