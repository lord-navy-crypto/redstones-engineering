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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

public class PwmControllerBlock extends DirectionalSignalBlock {
    public static final IntegerProperty PERIOD_MODE = IntegerProperty.create("period_mode", 0, 3);
    public static final BooleanProperty INVERT = BooleanProperty.create("invert");
    private static final String KEY = "redstone_pwm";

    public PwmControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(PERIOD_MODE, 2).setValue(INVERT, false));
    }
    @Override public MapCodec<PwmControllerBlock> codec() { return RedstoneEngineering.PWM_CONTROLLER_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder); builder.add(PERIOD_MODE, INVERT);
    }
    @Override protected boolean isEngineeringPort(BlockState state, Direction side) {
        Direction facing = state.getValue(FACING);
        return side == inputSide(state) || side == outputSide(state) || side == leftOf(facing);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Direction inhibitSide = leftOf(state.getValue(FACING));
        int input = readBackInput(level, pos, state);
        boolean inhibited = readInputFrom(level, pos, inhibitSide) > 0;

        int period = periodFor(state.getValue(PERIOD_MODE));
        int[] rt = RuntimeIntStore.get(level, KEY, pos, 1);
        int phase = Math.floorMod(rt[0], period);
        int onTicks = (int) Math.round((input / 15.0) * period);
        int output = phase < onTicks ? 15 : 0;
        if (input <= 0) output = 0;
        if (input >= 15) output = 15;
        if (state.getValue(INVERT)) output = output > 0 ? 0 : 15;
        if (inhibited) output = 0; // safety/control input always wins over inversion

        updateOutput(level, pos, state, output);
        if (!inhibited && input > 0 && input < 15) {
            rt[0] = (phase + 1) % period;
            level.scheduleTick(pos, this, 1);
        } else {
            rt[0] = 0;
        }
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, KEY, pos);
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            BlockState next;
            if (player.isShiftKeyDown()) next = state.setValue(INVERT, !state.getValue(INVERT));
            else next = state.setValue(PERIOD_MODE, (state.getValue(PERIOD_MODE) + 1) % 4);
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            RuntimeIntStore.get(level, KEY, pos, 1)[0] = 0;
            level.scheduleTick(pos, this, 1);
            int input = readBackInput(level, pos, next);
            int duty = (int) Math.round((input / 15.0) * 100.0);
            Direction inhibit = leftOf(next.getValue(FACING));
            player.displayClientMessage(Component.literal(
                    "PWM | command=" + input + "/15 | duty≈" + duty + "% | period=" + periodFor(next.getValue(PERIOD_MODE))
                            + "t | invert=" + next.getValue(INVERT) + " | INHIBIT=" + inhibit.getName() + " (>0 forces OFF)"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static int periodFor(int mode) {
        return switch (mode) { case 0 -> 4; case 1 -> 8; case 2 -> 16; case 3 -> 32; default -> 16; };
    }
}
