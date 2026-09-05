package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

public class PulseShaperBlock extends DirectionalSignalBlock {
    public static final IntegerProperty WIDTH = IntegerProperty.create("width", 1, 8);
    private static final String KEY = "redstone_pulse_shaper";

    public PulseShaperBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(WIDTH, 4));
    }
    @Override public MapCodec<PulseShaperBlock> codec() { return RedstoneEngineering.PULSE_SHAPER_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder); builder.add(WIDTH);
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
        if (now && !last) remaining = state.getValue(WIDTH);

        updateOutput(level, pos, state, remaining > 0 ? 15 : 0);
        rt[0] = now ? 1 : 0;
        rt[1] = Math.max(0, remaining - 1);
        if (remaining > 0) level.scheduleTick(pos, this, 1);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, KEY, pos);
        super.onRemove(state, level, pos, newState, moved);
    }

    public static int lastInput(Level level, BlockPos pos) { return RuntimeIntStore.get(level, KEY, pos, 3)[0]; }
    public static int pulseRemaining(Level level, BlockPos pos) { return RuntimeIntStore.get(level, KEY, pos, 3)[1]; }
    public static boolean initialized(Level level, BlockPos pos) { return RuntimeIntStore.get(level, KEY, pos, 3)[2] == 1; }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!player.isShiftKeyDown()) {
                FieldDeviceUi.open(serverPlayer, pos);
                return InteractionResult.CONSUME;
            }
            int width = state.getValue(WIDTH); width = width >= 8 ? 1 : width + 1;
            BlockState next = state.setValue(WIDTH, width); level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            player.displayClientMessage(Component.literal("Pulse Shaper | width=" + width + " ticks"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
