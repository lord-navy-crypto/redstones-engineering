package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.blockentity.PrecisionFilterBlockEntity;
import dev.redstoneengineering.signal.PrecisionFilterLogic;
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
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Asymmetric slew-rate limiter.
 *
 * RATE remains the historical rise-rate setting for world compatibility. Fall rate is persisted
 * in a small block entity so independent up/down dynamics do not multiply BlockState variants.
 */
public class PrecisionFilterBlock extends DirectionalSignalBlock implements EntityBlock {
    public static final IntegerProperty RATE = IntegerProperty.create("rate", 1, 4);

    public PrecisionFilterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(RATE, 1));
    }

    @Override
    public MapCodec<PrecisionFilterBlock> codec() {
        return RedstoneEngineering.PRECISION_FILTER_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RATE);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PrecisionFilterBlockEntity(pos, state);
    }

    private static PrecisionFilterBlockEntity persistentState(Level level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof PrecisionFilterBlockEntity entity) return entity;
        if (level.isClientSide || !(state.getBlock() instanceof PrecisionFilterBlock)) return null;

        // Legacy worlds may contain filters placed before this block gained persistent fall-rate state.
        // The entity constructor seeds fallRate from the old symmetric RATE value.
        PrecisionFilterBlockEntity created = new PrecisionFilterBlockEntity(pos, state);
        level.setBlockEntity(created);
        created.setChanged();
        return created;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int input = readBackInput(level, pos, state);
        int current = state.getValue(OUTPUT);
        int nextValue = PrecisionFilterLogic.step(
                current,
                input,
                state.getValue(RATE),
                fallRate(level, pos, state)
        );
        updateOutput(level, pos, state, nextValue);
        if (nextValue != input) level.scheduleTick(pos, this, 1);
    }

    public static int input(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof PrecisionFilterBlock filter)) return 0;
        return filter.readBackInput(level, pos, state);
    }

    /** Signed tracking error: positive means the output still needs to rise, negative means fall. */
    public static int trackingError(Level level, BlockPos pos, BlockState state) {
        return input(level, pos, state) - state.getValue(OUTPUT);
    }

    /** Absolute distance still to travel before the bounded slew response reaches the input. */
    public static int lag(Level level, BlockPos pos, BlockState state) {
        return Math.abs(trackingError(level, pos, state));
    }

    public static boolean settled(Level level, BlockPos pos, BlockState state) {
        return trackingError(level, pos, state) == 0;
    }

    public static int fallRate(Level level, BlockPos pos, BlockState state) {
        PrecisionFilterBlockEntity entity = persistentState(level, pos, state);
        return entity == null ? state.getValue(RATE) : entity.fallRate();
    }

    public static int settleTicks(Level level, BlockPos pos, BlockState state) {
        int in = input(level, pos, state);
        int out = state.getValue(OUTPUT);
        return PrecisionFilterLogic.settleTicks(out, in, state.getValue(RATE), fallRate(level, pos, state));
    }

    /** -1 falling, 0 settled, +1 rising. */
    public static int responseDirection(Level level, BlockPos pos, BlockState state) {
        return Integer.compare(trackingError(level, pos, state), 0);
    }

    /** Shared authoritative operator action for rise-rate configuration. */
    public static boolean stepRate(Level level, BlockPos pos, boolean forward) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PrecisionFilterBlock filter)) return false;
        int rate = state.getValue(RATE);
        int nextRate = forward ? (rate >= 4 ? 1 : rate + 1) : (rate <= 1 ? 4 : rate - 1);
        level.setBlock(pos, state.setValue(RATE, nextRate), Block.UPDATE_CLIENTS);
        level.scheduleTick(pos, filter, 1);
        return true;
    }

    /** Independent fall-rate configuration without increasing BlockState cardinality. */
    public static boolean stepFallRate(Level level, BlockPos pos, boolean forward) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PrecisionFilterBlock filter)) return false;
        PrecisionFilterBlockEntity entity = persistentState(level, pos, state);
        if (entity == null) return false;
        entity.stepFallRate(forward);
        level.scheduleTick(pos, filter, 1);
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                if (stepRate(level, pos, true)) {
                    BlockState next = level.getBlockState(pos);
                    int rise = next.getValue(RATE);
                    int fall = fallRate(level, pos, next);
                    int error = trackingError(level, pos, next);
                    player.displayClientMessage(
                            Component.literal(
                                    "Precision Filter | rise=" + rise
                                            + " fall=" + fall
                                            + " level/tick | error=" + error
                                            + " | settleETA=" + settleTicks(level, pos, next) + "t"
                            ),
                            true
                    );
                }
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
