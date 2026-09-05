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

/** First-order discrete low-pass filter with runtime output storage. */
public class LapisLowPassFilterBlock extends DirectionalDomainBlock implements EngineeringPortProvider {
    public static final IntegerProperty ALPHA = IntegerProperty.create("alpha", 0, 3);
    private static final String KEY = "lapis_lpf";

    public LapisLowPassFilterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ALPHA, 1));
    }

    @Override public MapCodec<LapisLowPassFilterBlock> codec() { return RedstoneEngineering.LAPIS_LOW_PASS_FILTER_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { super.createBlockStateDefinition(builder); builder.add(ALPHA); }

    private static double alpha(int index) {
        return switch (index) {
            case 0 -> 0.10;
            case 1 -> 0.25;
            case 2 -> 0.50;
            default -> 0.75;
        };
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("LAPIS FILTER IN", inputSide(state), EngineeringDomain.LAPIS,
                        PortKind.BUS, PortDirection.INPUT, false, "precision"),
                new EngineeringPort("LAPIS FILTER OUT", outputSide(state), EngineeringDomain.LAPIS,
                        PortKind.BUS, PortDirection.OUTPUT, false, "precision")
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        if (side == inputSide(state)) {
            DomainNetwork.LapisSample sample = DomainNetwork.sampleLapis(level, inputPos(pos, state));
            return Optional.of(new EngineeringPortSnapshot(
                    port.get(), sample.value(), 0.0, 100.0,
                    sample.valid() ? PortQuality.VALID : PortQuality.NO_SIGNAL));
        }
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, 2);
        return Optional.of(new EngineeringPortSnapshot(
                port.get(), runtime[0], 0.0, 100.0,
                runtime[1] == 1 ? PortQuality.VALID : PortQuality.NO_SIGNAL));
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, movedByPiston);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (level instanceof ServerLevel serverLevel) {
                DomainNetwork.driveLapis(serverLevel, outputPos(pos, state), pos, 0, false);
                DomainNetwork.recomputeLapisAround(serverLevel, pos);
            }
            RuntimeIntStore.remove(level, KEY, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        DomainNetwork.LapisSample input = DomainNetwork.sampleLapis(level, inputPos(pos, state));
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, 2); // output, valid
        if (input.valid()) {
            int previous = runtime[1] == 0 ? input.value() : runtime[0];
            runtime[0] = EngineeringMath.clamp(
                    (int) Math.round(previous + alpha(state.getValue(ALPHA)) * (input.value() - previous)), 0, 100);
            runtime[1] = 1;
            DomainNetwork.driveLapis(level, outputPos(pos, state), pos, runtime[0], true);
        } else {
            runtime[0] = 0;
            runtime[1] = 0;
            DomainNetwork.driveLapis(level, outputPos(pos, state), pos, 0, false);
        }
        level.scheduleTick(pos, this, 2);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            int index = (state.getValue(ALPHA) + 1) % 4;
            BlockState next = state.setValue(ALPHA, index);
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            level.scheduleTick(pos, this, 1);
            int[] runtime = RuntimeIntStore.get(level, KEY, pos, 2);
            player.displayClientMessage(Component.literal(
                    "Lapis low-pass | BACK input → FRONT output | alpha=" + alpha(index)
                            + " | output=" + String.format("%.2f", runtime[0] / 100.0)), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
