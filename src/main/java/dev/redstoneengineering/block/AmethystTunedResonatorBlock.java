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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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

    public record ResponseEvidence(int inputFrequency, int inputAmplitude, int naturalFrequency,
                                   int qIndex, int bandwidth, int frequencyError,
                                   int outputAmplitude, boolean saturated, boolean responding) {}

    public AmethystTunedResonatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(NATURAL, 8).setValue(Q_INDEX, 2));
    }

    @Override public MapCodec<AmethystTunedResonatorBlock> codec() { return RedstoneEngineering.AMETHYST_TUNED_RESONATOR_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { super.createBlockStateDefinition(builder); builder.add(NATURAL, Q_INDEX); }

    @Override public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("RESONANCE IN", inputSide(state), EngineeringDomain.AMETHYST,
                        PortKind.CONVERTER, PortDirection.INPUT, false, "amplitude"),
                new EngineeringPort("RESONANT OUT", outputSide(state), EngineeringDomain.AMETHYST,
                        PortKind.CONVERTER, PortDirection.OUTPUT, false, "amplitude"));
    }

    public static ResponseEvidence response(Level level, BlockPos pos, BlockState state) {
        DomainNetwork.AmethystSample input = DomainNetwork.sampleAmethyst(level, inputPos(pos, state));
        int natural = state.getValue(NATURAL);
        int q = state.getValue(Q_INDEX);
        int bandwidth = 5 - q;
        int diff = input.active() ? Math.abs(input.frequency() - natural) : 99;
        int raw = 0;
        if (input.active()) {
            if (diff == 0) raw = input.amplitude() + q * 2;
            else if (diff <= bandwidth) raw = input.amplitude() - Math.max(1, diff * q);
        }
        int output = EngineeringMath.clamp(raw, 0, 15);
        return new ResponseEvidence(
                input.frequency(), input.amplitude(), natural, q, bandwidth, diff,
                output, raw > 15, input.active() && output > 0);
    }

    private static PortQuality qualityAt(Level level, BlockPos samplePos, DomainNetwork.AmethystSample sample) {
        if (level.getBlockState(samplePos).getBlock() instanceof AmethystResonanceDustBlock) {
            return switch (AmethystResonanceDustBlock.status(level, samplePos)) {
                case ACTIVE -> PortQuality.VALID;
                case FREQUENCY_CONFLICT -> PortQuality.TOPOLOGY_ERROR;
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
        if (side == outputSide(state) && quality == PortQuality.VALID && response(level, pos, state).saturated()) {
            quality = PortQuality.SATURATED;
        }
        return Optional.of(new EngineeringPortSnapshot(
                port.get(), Math.max(0, Math.min(15, signal.amplitude())), 0.0, 15.0, quality));
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) level.scheduleTick(pos, this, 2);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        ResponseEvidence response = response(level, pos, state);
        DomainNetwork.driveAmethyst(
                level, outputPos(pos, state), response.responding(), response.inputFrequency(), response.outputAmplitude());
        level.scheduleTick(pos, this, 2);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            DomainNetwork.driveAmethyst(serverLevel, outputPos(pos, state), false, 0, 0);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockState next;
            if (player.isShiftKeyDown()) {
                int q = state.getValue(Q_INDEX);
                next = state.setValue(Q_INDEX, q >= 4 ? 1 : q + 1);
            } else {
                int frequency = state.getValue(NATURAL);
                next = state.setValue(NATURAL, frequency >= 15 ? 1 : frequency + 1);
            }
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            level.scheduleTick(pos, this, 1);
            ResponseEvidence response = response(level, pos, next);
            player.displayClientMessage(Component.literal(
                    "Tuned amethyst resonator | f0=" + response.naturalFrequency()
                            + " | Q-index=" + response.qIndex() + " | bandwidth=±" + response.bandwidth()
                            + (response.inputFrequency() > 0 ? " | input f=" + response.inputFrequency()
                            + " Δf=" + response.frequencyError() + " | expected Aout=" + response.outputAmplitude()
                            + (response.saturated() ? " SATURATED" : "") : " | no active input")), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
