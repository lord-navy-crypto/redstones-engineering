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

public class AmethystFrequencyFilterBlock extends DirectionalDomainBlock implements EngineeringPortProvider {
    public static final IntegerProperty TARGET = IntegerProperty.create("target", 1, 15);

    public AmethystFrequencyFilterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(TARGET, 1));
    }

    @Override
    public MapCodec<AmethystFrequencyFilterBlock> codec() {
        return RedstoneEngineering.AMETHYST_FREQUENCY_FILTER_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(TARGET);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("RESONANCE IN", inputSide(state), EngineeringDomain.AMETHYST,
                        PortKind.CONVERTER, PortDirection.INPUT, false, "amplitude"),
                new EngineeringPort("FILTERED OUT", outputSide(state), EngineeringDomain.AMETHYST,
                        PortKind.CONVERTER, PortDirection.OUTPUT, false, "amplitude")
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        DomainNetwork.AmethystSample signal = DomainNetwork.sampleAmethyst(
                level, side == inputSide(state) ? inputPos(pos, state) : outputPos(pos, state));
        return Optional.of(new EngineeringPortSnapshot(
                port.get(), Math.max(0, Math.min(15, signal.amplitude())), 0.0, 15.0,
                signal.active() ? PortQuality.VALID : PortQuality.NO_SIGNAL));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) level.scheduleTick(pos, this, 2);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        var input = DomainNetwork.sampleAmethyst(level, inputPos(pos, state));
        boolean pass = input.active() && input.frequency() == state.getValue(TARGET);
        DomainNetwork.driveAmethyst(level, outputPos(pos, state), pass,
                input.frequency(), Math.max(0, input.amplitude() - 1));
        level.scheduleTick(pos, this, 2);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            DomainNetwork.driveAmethyst(serverLevel, outputPos(pos, state), false, 0, 0);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit
    ) {
        if (!level.isClientSide) {
            int frequency = state.getValue(TARGET);
            frequency = frequency >= 15 ? 1 : frequency + 1;
            BlockState next = state.setValue(TARGET, frequency);
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            player.displayClientMessage(Component.literal(
                    "Amethyst frequency filter | pass f=" + frequency + " | insertion loss=1 amplitude"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
