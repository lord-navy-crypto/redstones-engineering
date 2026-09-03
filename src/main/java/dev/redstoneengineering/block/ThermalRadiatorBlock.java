package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.ThermalPhysics;
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

/** Passive heat sink: removes heat above ambient, never actively refrigerates below ambient. */
public class ThermalRadiatorBlock extends DomainBlock {
    public static final IntegerProperty COOLING = IntegerProperty.create("cooling", 1, 4);

    public ThermalRadiatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(COOLING, 2));
    }

    @Override public MapCodec<ThermalRadiatorBlock> codec() { return RedstoneEngineering.THERMAL_RADIATOR_CODEC.value(); }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(COOLING); }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) level.scheduleTick(pos, this, 10);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int cooling = state.getValue(COOLING);
        for (Direction direction : Direction.values()) {
            BlockPos targetPos = pos.relative(direction);
            BlockState target = level.getBlockState(targetPos);
            if (target.getBlock() instanceof ThermalMassBlock) {
                int t = target.getValue(ThermalMassBlock.TEMPERATURE);
                if (t > ThermalPhysics.AMBIENT) {
                    int next = Math.max(ThermalPhysics.AMBIENT, t - cooling);
                    level.setBlock(targetPos, target.setValue(ThermalMassBlock.TEMPERATURE, next), Block.UPDATE_CLIENTS);
                }
            }
        }
        level.scheduleTick(pos, this, 10);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            int cooling = state.getValue(COOLING);
            if (!player.isShiftKeyDown()) {
                cooling = cooling >= 4 ? 1 : cooling + 1;
                state = state.setValue(COOLING, cooling);
                level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            }
            player.displayClientMessage(Component.literal("Thermal radiator | passive cooling coefficient=" + cooling + " | ambient floor=" + ThermalPhysics.AMBIENT), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
