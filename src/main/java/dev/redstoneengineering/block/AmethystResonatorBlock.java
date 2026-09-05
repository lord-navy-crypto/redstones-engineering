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
import dev.redstoneengineering.physics.RuntimeIntStore;
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

/** Pulse activity is transient runtime data; frequency/amplitude remain persistent configuration. */
public class AmethystResonatorBlock extends DomainBlock implements EngineeringPortProvider {
    public static final IntegerProperty FREQUENCY = IntegerProperty.create("frequency", 1, 15);
    public static final IntegerProperty AMPLITUDE = IntegerProperty.create("amplitude", 1, 15);
    private static final String KEY = "amethyst_resonator";

    public AmethystResonatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FREQUENCY, 1).setValue(AMPLITUDE, 12));
    }

    @Override
    public MapCodec<AmethystResonatorBlock> codec() {
        return RedstoneEngineering.AMETHYST_RESONATOR_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FREQUENCY, AMPLITUDE);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                sourcePort(Direction.NORTH), sourcePort(Direction.SOUTH),
                sourcePort(Direction.WEST), sourcePort(Direction.EAST)
        );
    }

    private static EngineeringPort sourcePort(Direction side) {
        return new EngineeringPort("RESONANCE OUT", side, EngineeringDomain.AMETHYST,
                PortKind.BUS, PortDirection.OUTPUT, false, "amplitude");
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        int amplitude = isActive(level, pos) ? state.getValue(AMPLITUDE) : 0;
        return Optional.of(new EngineeringPortSnapshot(
                port.get(), amplitude, 0.0, 15.0,
                amplitude > 0 ? PortQuality.VALID : PortQuality.NO_SIGNAL));
    }

    public static boolean isActive(Level level, BlockPos pos) {
        return RuntimeIntStore.get(level, KEY, pos, 1)[0] == 1;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            RuntimeIntStore.remove(level, KEY, pos);
            if (level instanceof ServerLevel serverLevel) DomainNetwork.recomputeAmethyst(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (isActive(level, pos)) {
            RuntimeIntStore.get(level, KEY, pos, 1)[0] = 0;
            DomainNetwork.recomputeAmethyst(level, pos);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit
    ) {
        if (!level.isClientSide) {
            BlockState next = state;
            if (player.isShiftKeyDown()) {
                RuntimeIntStore.get(level, KEY, pos, 1)[0] = 1;
                level.scheduleTick(pos, this, 4);
            } else if (hit.getDirection() == Direction.UP || hit.getDirection() == Direction.DOWN) {
                int amplitude = state.getValue(AMPLITUDE);
                next = state.setValue(AMPLITUDE, amplitude >= 15 ? 1 : amplitude + 1);
                level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            } else {
                int frequency = state.getValue(FREQUENCY);
                next = state.setValue(FREQUENCY, frequency >= 15 ? 1 : frequency + 1);
                level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            }
            if (level instanceof ServerLevel serverLevel) DomainNetwork.recomputeAmethyst(serverLevel, pos);
            player.displayClientMessage(Component.literal(
                    "Amethyst resonator | f=" + next.getValue(FREQUENCY)
                            + " | amplitude=" + next.getValue(AMPLITUDE)
                            + (isActive(level, pos) ? " | PULSE" : "")), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
