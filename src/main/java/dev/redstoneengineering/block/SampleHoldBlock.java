package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
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

public class SampleHoldBlock extends DirectionalSignalBlock {
    public static final IntegerProperty TRIGGER_MODE = IntegerProperty.create("trigger_mode", 0, 2);
    private static final String KEY = "redstone_sample_hold";

    public SampleHoldBlock(Properties properties) {
        super(properties); registerDefaultState(defaultBlockState().setValue(TRIGGER_MODE, 0));
    }
    @Override public MapCodec<SampleHoldBlock> codec() { return RedstoneEngineering.SAMPLE_HOLD_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder); builder.add(TRIGGER_MODE);
    }
    @Override protected boolean isEngineeringPort(BlockState state, Direction side) {
        Direction facing = state.getValue(FACING);
        return side == inputSide(state) || side == outputSide(state) || side == leftOf(facing) || side == rightOf(facing);
    }

    private int[] runtime(Level level, BlockPos pos, BlockState state, boolean triggerNow) {
        int[] rt = RuntimeIntStore.get(level, KEY, pos, 3); // held, triggered, initialized
        if (rt[2] == 0) {
            rt[0] = state.getValue(OUTPUT); // preserve held output across reload
            rt[1] = triggerNow ? 1 : 0;     // avoid a false edge after reload
            rt[2] = 1;
        }
        return rt;
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Direction facing = state.getValue(FACING);
        boolean triggerNow = readInputFrom(level, pos, leftOf(facing)) > 0;
        boolean resetNow = readInputFrom(level, pos, rightOf(facing)) > 0;
        int[] rt = runtime(level, pos, state, triggerNow);
        boolean triggerBefore = rt[1] == 1;
        boolean rising = !triggerBefore && triggerNow;
        boolean falling = triggerBefore && !triggerNow;
        boolean sample = switch (state.getValue(TRIGGER_MODE)) {
            case 0 -> rising; case 1 -> falling; case 2 -> rising || falling; default -> rising;
        };

        if (resetNow) rt[0] = 0;
        else if (sample) rt[0] = readBackInput(level, pos, state);
        rt[1] = triggerNow ? 1 : 0;
        updateOutput(level, pos, state, rt[0]);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, KEY, pos);
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            Direction facing = state.getValue(FACING);
            boolean triggerNow = readInputFrom(level, pos, leftOf(facing)) > 0;
            int[] rt = runtime(level, pos, state, triggerNow);
            if (player.isShiftKeyDown()) {
                rt[0] = 0; rt[1] = triggerNow ? 1 : 0; rt[2] = 1;
                updateOutput(level, pos, state, 0);
                player.displayClientMessage(Component.literal("Sample & Hold | cleared"), true);
            } else {
                int mode = (state.getValue(TRIGGER_MODE) + 1) % 3;
                BlockState next = state.setValue(TRIGGER_MODE, mode); level.setBlock(pos, next, Block.UPDATE_CLIENTS);
                player.displayClientMessage(Component.literal(
                        "Sample & Hold | mode=" + modeName(mode) + " | held=" + rt[0]
                                + " | VALUE=" + inputSide(next).getName() + " | OUT=" + outputSide(next).getName()
                                + " | TRIGGER=" + leftOf(facing).getName() + " | RESET=" + rightOf(facing).getName()), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static String modeName(int mode) {
        return switch (mode) { case 0 -> "RISING"; case 1 -> "FALLING"; case 2 -> "BOTH"; default -> "RISING"; };
    }
}
