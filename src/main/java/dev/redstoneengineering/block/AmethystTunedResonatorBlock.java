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

public class AmethystTunedResonatorBlock extends DirectionalDomainBlock implements EngineeringPortProvider {
    public static final IntegerProperty NATURAL = IntegerProperty.create("natural", 1, 15);
    public static final IntegerProperty Q_INDEX = IntegerProperty.create("q", 1, 4);

    public AmethystTunedResonatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(NATURAL, 8).setValue(Q_INDEX, 2));
    }

    @Override public MapCodec<AmethystTunedResonatorBlock> codec() { return RedstoneEngineering.AMETHYST_TUNED_RESONATOR_CODEC.value(); }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(NATURAL, Q_INDEX);
    }

    @Override public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("RESONANCE IN", inputSide(state), EngineeringDomain.AMETHYST,
                        PortKind.CONVERTER, PortDirection.INPUT, false, "amplitude"),
                new EngineeringPort("RESONANT OUT", outputSide(state), EngineeringDomain.AMETHYST,
                        PortKind.CONVERTER, PortDirection.OUTPUT, false, "amplitude")
        );
    }

    @Override public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        DomainNetwork.AmethystSample signal = DomainNetwork.sampleAmethyst(
                level, side == inputSide(state) ? inputPos(pos, state) : outputPos(pos, state));
        return Optional.of(new EngineeringPortSnapshot(port.get(), Math.max(0, Math.min(15, signal.amplitude())),
                0.0, 15.0, signal.active() ? PortQuality.VALID : PortQuality.NO_SIGNAL));
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) level.scheduleTick(pos, this, 2);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        var input = DomainNetwork.sampleAmethyst(level, inputPos(pos, state));
        int diff = input.active() ? Math.abs(input.frequency() - state.getValue(NATURAL)) : 99;
        int q = state.getValue(Q_INDEX);
        int bandwidth = 5 - q;
        int amplitude = 0;
        if (input.active()) {
            if (diff == 0) amplitude = EngineeringMath.clamp(input.amplitude() + q * 2, 0, 15);
            else if (diff <= bandwidth) amplitude = EngineeringMath.clamp(input.amplitude() - Math.max(1, diff * q), 0, 15);
        }
        DomainNetwork.driveAmethyst(level, outputPos(pos, state), amplitude > 0, input.frequency(), amplitude);
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
            player.displayClientMessage(Component.literal("Tuned amethyst resonator | f0=" + next.getValue(NATURAL)
                    + " | Q-index=" + next.getValue(Q_INDEX) + " | higher Q = narrower selectivity"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
