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
    private static final int DOMINANT_FREQUENCY = 0;
    private static final int DOMINANT_ENERGY = 1;
    private static final int ACTIVE_BANDS = 2;
    private static final int ACTIVE_SAMPLES = 3;
    private static final int CONFLICT_SAMPLES = 4;
    private static final int SCANNED_CELLS = 5;
    private static final int EXPECTED_CELLS = 6;
    private static final int RUNTIME_SIZE = 7;

    public record Spectrum(
            int dominantFrequency,
            int energy,
            int activeBands,
            int samples,
            int conflicts,
            int scannedCells,
            int expectedCells
    ) {
        public boolean complete() {
            return expectedCells > 0 && scannedCells == expectedCells;
        }
    }

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

    public static Spectrum spectrum(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        if (runtime == null || runtime.length != RUNTIME_SIZE) return new Spectrum(0, 0, 0, 0, 0, 0, 0);
        return new Spectrum(
                runtime[DOMINANT_FREQUENCY],
                runtime[DOMINANT_ENERGY],
                runtime[ACTIVE_BANDS],
                runtime[ACTIVE_SAMPLES],
                runtime[CONFLICT_SAMPLES],
                runtime[SCANNED_CELLS],
                runtime[EXPECTED_CELLS]);
    }

    public static PortQuality quality(Level level, BlockPos pos) {
        Spectrum spectrum = spectrum(level, pos);
        if (spectrum.conflicts() > 0) return PortQuality.TOPOLOGY_ERROR;
        if (!spectrum.complete()) return PortQuality.NO_SIGNAL;
        return spectrum.samples() > 0 ? PortQuality.VALID : PortQuality.NO_SIGNAL;
    }

    @Override public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        Spectrum spectrum = spectrum(level, pos);
        return Optional.of(new EngineeringPortSnapshot(
                port.get(), Math.max(0, spectrum.energy()), 0.0,
                Math.max(15.0, spectrum.energy()), quality(level, pos)));
    }

    private static Spectrum scan(ServerLevel level, BlockPos pos) {
        int[] energy = new int[16];
        int events = 0;
        int conflicts = 0;
        int scanned = 0;
        int expected = 0;
        int radiusSquared = RADIUS * RADIUS;
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dy = -RADIUS; dy <= RADIUS; dy++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radiusSquared) continue;
                    expected++;
                    BlockPos samplePos = pos.offset(dx, dy, dz);
                    if (!level.hasChunkAt(samplePos)) continue;
                    scanned++;
                    var sampleState = level.getBlockState(samplePos);
                    if (!(sampleState.getBlock() instanceof AmethystResonanceDustBlock)) continue;

                    AmethystResonanceDustBlock.ResonanceStatus status = AmethystResonanceDustBlock.status(level, samplePos);
                    if (status == AmethystResonanceDustBlock.ResonanceStatus.FREQUENCY_CONFLICT) {
                        conflicts++;
                        continue;
                    }
                    if (status != AmethystResonanceDustBlock.ResonanceStatus.ACTIVE) continue;

                    int frequency = AmethystResonanceDustBlock.frequency(level, samplePos);
                    if (frequency >= 1 && frequency <= 15) {
                        energy[frequency] += AmethystResonanceDustBlock.amplitude(level, samplePos);
                    }
                    events++;
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
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        runtime[DOMINANT_FREQUENCY] = dominantFrequency;
        runtime[DOMINANT_ENERGY] = dominantEnergy;
        runtime[ACTIVE_BANDS] = bands;
        runtime[ACTIVE_SAMPLES] = events;
        runtime[CONFLICT_SAMPLES] = conflicts;
        runtime[SCANNED_CELLS] = scanned;
        runtime[EXPECTED_CELLS] = expected;
        return new Spectrum(dominantFrequency, dominantEnergy, bands, events, conflicts, scanned, expected);
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel serverLevel && !state.is(oldState.getBlock())) {
            scan(serverLevel, pos);
            serverLevel.scheduleTick(pos, this, 1);
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
            // Inspection is strictly observer-only: scheduled server sampling owns all runtime writes.
            if (!player.isShiftKeyDown()) {
                FieldDeviceUi.open(serverPlayer, pos);
            } else {
                Spectrum spectrum = spectrum(level, pos);
                player.displayClientMessage(Component.literal(
                        "Amethyst spectrum | quality=" + quality(level, pos)
                                + " | dominant f=" + spectrum.dominantFrequency()
                                + " | energy=" + spectrum.energy()
                                + " | active bands=" + spectrum.activeBands()
                                + " | samples=" + spectrum.samples()
                                + " | conflicts=" + spectrum.conflicts()
                                + " | coverage=" + spectrum.scannedCells() + "/" + spectrum.expectedCells()), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
