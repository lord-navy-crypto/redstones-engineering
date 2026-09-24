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
import dev.redstoneengineering.physics.EngineeringMath;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.signal.AmethystTunedResonatorLogic;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/** Tuned resonance response: finite bandwidth and possible gain, deliberately distinct from exact filtering. */
public class AmethystTunedResonatorBlock extends DirectionalDomainBlock implements EngineeringPortProvider {
    public static final IntegerProperty NATURAL = IntegerProperty.create("natural", 1, 15);
    public static final IntegerProperty Q_INDEX = IntegerProperty.create("q", 1, 4);
    public static final IntegerProperty COUPLING = IntegerProperty.create("coupling", 1, 4);
    public static final IntegerProperty DECAY_RATE = IntegerProperty.create("decay_rate", 1, 4);

    private static final String KEY = "amethyst_tuned_resonator";
    private static final int ACTUAL_AMPLITUDE = 0;
    private static final int ACTUAL_FREQUENCY = 1;
    private static final int DRIVEN_SLOT = 2;
    private static final int RUNTIME_SIZE = 3;

    public record ResponseEvidence(int inputFrequency, int inputAmplitude, int naturalFrequency,
                                   int qIndex, int couplingIndex, int decayRate,
                                   int bandwidth, int frequencyError,
                                   PortQuality inputQuality, int targetAmplitude,
                                   int actualAmplitude, int outputFrequency,
                                   boolean saturated, boolean responding, boolean ringDown) {
        /** Legacy steady-state transfer readback; runtime propagation uses actualAmplitude instead. */
        public int outputAmplitude() {
            return targetAmplitude;
        }
    }

    public AmethystTunedResonatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(NATURAL, 8)
                .setValue(Q_INDEX, 2)
                .setValue(COUPLING, 2)
                .setValue(DECAY_RATE, 1));
    }

    @Override public MapCodec<AmethystTunedResonatorBlock> codec() { return RedstoneEngineering.AMETHYST_TUNED_RESONATOR_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(NATURAL, Q_INDEX, COUPLING, DECAY_RATE);
    }

    @Override public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("RESONANCE IN", inputSide(state), EngineeringDomain.AMETHYST,
                        PortKind.CONVERTER, PortDirection.INPUT, false, "amplitude"),
                new EngineeringPort("RESONANT OUT", outputSide(state), EngineeringDomain.AMETHYST,
                        PortKind.CONVERTER, PortDirection.OUTPUT, false, "amplitude"));
    }

    public static ResponseEvidence response(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(DirectionalDomainBlock.FACING);
        BlockPos samplePos = pos.relative(facing.getOpposite());
        DomainNetwork.AmethystSample input = DomainNetwork.sampleAmethyst(level, samplePos);
        PortQuality inputQuality = qualityAt(level, samplePos, input);
        int natural = state.getValue(NATURAL);
        int q = state.getValue(Q_INDEX);
        int coupling = state.getValue(COUPLING);
        int decayRate = state.getValue(DECAY_RATE);
        int bandwidth = 5 - q;
        boolean usableInput = inputQuality == PortQuality.VALID && input.active();
        int diff = usableInput ? Math.abs(input.frequency() - natural) : 99;
        int raw = 0;
        if (usableInput) {
            // Nominal coupling C=2 preserves the previous transfer curve. C then becomes a
            // separate, bounded drive-coupling experiment instead of overloading Q.
            if (diff == 0) raw = input.amplitude() + q * coupling;
            else if (diff <= bandwidth) {
                raw = input.amplitude() - Math.max(1, diff * q) + (coupling - 2);
            }
        }
        int target = EngineeringMath.clamp(raw, 0, 15);
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        int actualAmplitude = runtime == null || runtime.length < RUNTIME_SIZE
                ? 0 : EngineeringMath.clamp(runtime[ACTUAL_AMPLITUDE], 0, 15);
        int outputFrequency = runtime == null || runtime.length < RUNTIME_SIZE
                ? 0 : EngineeringMath.clamp(runtime[ACTUAL_FREQUENCY], 0, 15);
        boolean responding = usableInput && target > 0;
        boolean inputStateKnown = inputQuality == PortQuality.VALID || inputQuality == PortQuality.NO_SIGNAL;
        boolean ringDown = actualAmplitude > 0 && inputStateKnown && !responding;
        return new ResponseEvidence(input.frequency(), input.amplitude(), natural, q, coupling, decayRate,
                bandwidth, diff, inputQuality, target, actualAmplitude, outputFrequency,
                raw > 15, responding, ringDown);
    }

    private static boolean inputEvidenceUnknown(PortQuality quality) {
        return quality == PortQuality.STALE
                || quality == PortQuality.FAULT
                || quality == PortQuality.DOMAIN_MISMATCH
                || quality == PortQuality.TOPOLOGY_ERROR;
    }

    private static PortQuality qualityAt(Level level, BlockPos samplePos, DomainNetwork.AmethystSample sample) {
        if (level.getBlockState(samplePos).getBlock() instanceof AmethystResonanceDustBlock) {
            return switch (AmethystResonanceDustBlock.status(level, samplePos)) {
                case ACTIVE -> PortQuality.VALID;
                case FREQUENCY_CONFLICT -> PortQuality.TOPOLOGY_ERROR;
                case STALE -> PortQuality.STALE;
                case IDLE -> PortQuality.NO_SIGNAL;
            };
        }
        return sample.active() ? PortQuality.VALID : PortQuality.NO_SIGNAL;
    }

    @Override public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        BlockPos samplePos = side == inputSide(state) ? inputPos(pos, state) : outputPos(pos, state);
        DomainNetwork.AmethystSample signal = DomainNetwork.sampleAmethyst(level, samplePos);
        PortQuality quality = qualityAt(level, samplePos, signal);
        if (side == outputSide(state)) {
            ResponseEvidence response = response(level, pos, state);
            if (inputEvidenceUnknown(response.inputQuality())) {
                // The stored resonator state is retained, but its present evolution is not
                // trustworthy while the forcing evidence is incomplete or contradictory.
                quality = response.inputQuality();
            } else if (response.actualAmplitude() > 0
                    && response.ringDown()
                    && quality == PortQuality.NO_SIGNAL) {
                // Internal stored energy can explain a momentary empty output readback, but it
                // must never mask STALE coverage or a real downstream frequency conflict.
                quality = PortQuality.VALID;
            } else if (quality == PortQuality.VALID && response.saturated()) {
                quality = PortQuality.SATURATED;
            }
        }
        return Optional.of(new EngineeringPortSnapshot(port.get(), Math.max(0, Math.min(15, signal.amplitude())), 0.0, 15.0, quality));
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) level.scheduleTick(pos, this, 2);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        ResponseEvidence response = response(level, pos, state);
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);

        if (inputEvidenceUnknown(response.inputQuality())) {
            // Unknown drive evidence is neither a confirmed drive nor a confirmed removal.
            // Preserve the last physical resonator state and withhold the network driver until
            // trustworthy evidence returns. Reacquisition resumes from this retained state.
            DomainNetwork.driveAmethyst(
                    level, outputPos(pos, state), pos,
                    false,
                    EngineeringMath.clamp(runtime[ACTUAL_FREQUENCY], 0, 15),
                    EngineeringMath.clamp(runtime[ACTUAL_AMPLITUDE], 0, 15));
            level.scheduleTick(pos, this, 2);
            return;
        }

        AmethystTunedResonatorLogic.State next = AmethystTunedResonatorLogic.step(
                response.targetAmplitude(),
                response.inputFrequency(),
                response.naturalFrequency(),
                response.qIndex(),
                response.decayRate(),
                response.responding(),
                new AmethystTunedResonatorLogic.State(
                        runtime[ACTUAL_AMPLITUDE],
                        runtime[ACTUAL_FREQUENCY],
                        runtime[DRIVEN_SLOT] != 0)
        );

        runtime[ACTUAL_AMPLITUDE] = next.amplitude();
        runtime[ACTUAL_FREQUENCY] = next.frequency();
        runtime[DRIVEN_SLOT] = next.driven() ? 1 : 0;

        DomainNetwork.driveAmethyst(
                level, outputPos(pos, state), pos,
                next.amplitude() > 0,
                next.frequency(),
                next.amplitude());

        level.scheduleTick(pos, this, 2);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            RuntimeIntStore.remove(level, KEY, pos);
            if (level instanceof ServerLevel serverLevel) {
                DomainNetwork.driveAmethyst(serverLevel, outputPos(pos, state), pos, false, 0, 0);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!player.isShiftKeyDown()) {
                FieldDeviceUi.open(serverPlayer, pos);
                return InteractionResult.CONSUME;
            }
            int frequency = state.getValue(NATURAL);
            BlockState next = state.setValue(NATURAL, frequency >= 15 ? 1 : frequency + 1);
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            level.scheduleTick(pos, this, 1);
            ResponseEvidence response = response(level, pos, next);
            player.displayClientMessage(Component.literal(
                    "Tuned resonator | natural=" + response.naturalFrequency()
                            + " Q=" + response.qIndex()
                            + " coupling=" + response.couplingIndex()
                            + " decay=" + response.decayRate()
                            + " bandwidth=±" + response.bandwidth()
                            + " | targetA=" + response.targetAmplitude()
                            + " actualA=" + response.actualAmplitude()
                            + " outputF=" + response.outputFrequency()
                            + (response.ringDown() ? " FREE RING-DOWN" : response.responding() ? " DRIVEN" : " IDLE")
                            + " | driven step=" + AmethystTunedResonatorLogic.responseStep(response.qIndex())
                            + " free-decay step=" + AmethystTunedResonatorLogic.freeDecayStep(response.decayRate())
                            + " | normal right-click opens Engineering UI"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
