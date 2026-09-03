package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.ThermalPhysics;
import net.minecraft.core.BlockPos;
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

/** Lumped thermal mass: temperature is a physical state, not a wire signal. */
public class ThermalMassBlock extends DomainBlock {
    public static final IntegerProperty TEMPERATURE = IntegerProperty.create("temperature", 0, 100);
    public static final IntegerProperty HEAT_CAPACITY = IntegerProperty.create("heat_capacity", 1, 4);

    public ThermalMassBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(TEMPERATURE, ThermalPhysics.AMBIENT).setValue(HEAT_CAPACITY, 2));
    }

    @Override public MapCodec<ThermalMassBlock> codec() { return RedstoneEngineering.THERMAL_MASS_CODEC.value(); }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TEMPERATURE, HEAT_CAPACITY);
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) level.scheduleTick(pos, this, 5);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int current = state.getValue(TEMPERATURE);
        int env = ThermalPhysics.environmentTarget(level, pos);
        int neighbors = ThermalPhysics.neighborThermalAverage(level, pos, current);
        int target = (env * 2 + neighbors) / 3;
        int capacity = state.getValue(HEAT_CAPACITY);
        int maxStep = Math.max(1, 5 - capacity);
        int next = ThermalPhysics.approach(current, target, maxStep);
        if (next != current) level.setBlock(pos, state.setValue(TEMPERATURE, next), Block.UPDATE_CLIENTS);
        level.scheduleTick(pos, this, 5 * capacity);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            if (!player.isShiftKeyDown()) {
                int c = state.getValue(HEAT_CAPACITY);
                c = c >= 4 ? 1 : c + 1;
                state = state.setValue(HEAT_CAPACITY, c);
                level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            }
            player.displayClientMessage(Component.literal("Thermal mass | T-index=" + state.getValue(TEMPERATURE) + "/100 | heat-capacity index=" + state.getValue(HEAT_CAPACITY)), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
