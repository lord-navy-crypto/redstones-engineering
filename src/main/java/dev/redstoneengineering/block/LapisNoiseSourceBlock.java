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
import dev.redstoneengineering.diagnostics.FaultInjectionModel;
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

/**
 * Repeatable Lapis-domain noise source used both as a source and as a commissioning fault injector.
 * Configuration stays small; the changing sample is transient runtime data.
 */
public class LapisNoiseSourceBlock extends DomainBlock implements EngineeringPortProvider {
    public static final IntegerProperty BASELINE = IntegerProperty.create("baseline", 0, 20);
    public static final IntegerProperty NOISE = IntegerProperty.create("noise", 0, 10);
    private static final String KEY = "lapis_noise";

    public LapisNoiseSourceBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BASELINE, 10).setValue(NOISE, 3));
    }

    @Override public MapCodec<LapisNoiseSourceBlock> codec() { return RedstoneEngineering.LAPIS_NOISE_SOURCE_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(BASELINE, NOISE); }

    private static EngineeringPort port(Direction side) {
        return new EngineeringPort(
                "LAPIS NOISE OUT",
                side,
                EngineeringDomain.LAPIS,
                PortKind.BUS,
                PortDirection.OUTPUT,
                false,
                "precision"
        );
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(port(Direction.NORTH), port(Direction.SOUTH), port(Direction.WEST), port(Direction.EAST));
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        return engineeringPort(state, side).map(port -> new EngineeringPortSnapshot(
                port, currentValue(level, pos, state), 0.0, 100.0, PortQuality.VALID));
    }

    public static int currentValue(Level level, BlockPos pos, BlockState state) {
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, 1);
        if (runtime[0] == 0 && state.getValue(BASELINE) > 0) runtime[0] = state.getValue(BASELINE) * 5;
        return EngineeringMath.clamp(runtime[0], 0, 100);
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide) {
            RuntimeIntStore.get(level, KEY, pos, 1)[0] = state.getValue(BASELINE) * 5;
            if (level instanceof ServerLevel serverLevel) DomainNetwork.recomputeLapis(serverLevel, pos);
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            RuntimeIntStore.remove(level, KEY, pos);
            if (level instanceof ServerLevel serverLevel) DomainNetwork.recomputeLapisAround(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int base = state.getValue(BASELINE) * 5;
        int noise = state.getValue(NOISE) * 2;
        int sample = FaultInjectionModel.addDeterministicNoise(base, noise, level.getGameTime(), pos.asLong(), 0, 100);
        RuntimeIntStore.get(level, KEY, pos, 1)[0] = sample;
        DomainNetwork.recomputeLapis(level, pos);
        level.scheduleTick(pos, this, 4);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockState next;
            if (player.isShiftKeyDown()) {
                int noise = state.getValue(NOISE);
                next = state.setValue(NOISE, noise >= 10 ? 0 : noise + 1);
            } else {
                int baseline = state.getValue(BASELINE);
                next = state.setValue(BASELINE, baseline >= 20 ? 0 : baseline + 1);
            }
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            RuntimeIntStore.get(level, KEY, pos, 1)[0] = next.getValue(BASELINE) * 5;
            if (level instanceof ServerLevel serverLevel) DomainNetwork.recomputeLapis(serverLevel, pos);
            level.scheduleTick(pos, this, 1);
            int current = currentValue(level, pos, next);
            player.displayClientMessage(Component.literal(
                    "Fault injection [NOISE] | four-way LAPIS source | baseline=" + String.format("%.2f", next.getValue(BASELINE) * 0.05)
                            + " | noise=±" + String.format("%.2f", next.getValue(NOISE) * 0.02)
                            + " | now=" + String.format("%.2f", current / 100.0)
                            + " | shift-click=noise, click=baseline"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
