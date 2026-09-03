package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.RuntimeIntStore;
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

public class EdgeDetectorBlock extends DirectionalSignalBlock {
    public static final IntegerProperty MODE = IntegerProperty.create("mode", 0, 2);
    private static final String KEY = "redstone_edge_detector";

    public EdgeDetectorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(MODE, 0));
    }

    @Override public MapCodec<EdgeDetectorBlock> codec() { return RedstoneEngineering.EDGE_DETECTOR_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder); builder.add(MODE);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean now = readBackInput(level, pos, state) > 0;
        int[] rt = RuntimeIntStore.get(level, KEY, pos, 3); // last, remaining, initialized
        if (rt[2] == 0) {
            rt[0] = now ? 1 : 0;
            rt[1] = 0;
            rt[2] = 1;
            updateOutput(level, pos, state, 0);
            return;
        }

        boolean last = rt[0] == 1;
        int remaining = rt[1];
        boolean edge = switch (state.getValue(MODE)) {
            case 0 -> !last && now;
            case 1 -> last && !now;
            case 2 -> last != now;
            default -> false;
        };
        if (edge) remaining = 2;

        updateOutput(level, pos, state, remaining > 0 ? 15 : 0);
        rt[0] = now ? 1 : 0;
        rt[1] = Math.max(0, remaining - 1);
        if (remaining > 0) level.scheduleTick(pos, this, 1);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, KEY, pos);
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            BlockState next = state.setValue(MODE, (state.getValue(MODE) + 1) % 3);
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            player.displayClientMessage(Component.literal("Edge Detector | mode=" + modeName(next.getValue(MODE))), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static String modeName(int mode) {
        return switch (mode) { case 0 -> "RISING"; case 1 -> "FALLING"; case 2 -> "BOTH"; default -> "RISING"; };
    }
}
