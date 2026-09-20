package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.blockentity.PrecisionFilterBlockEntity;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.EngineeringDeviceParameters;
import dev.redstoneengineering.physics.RedstoneObservationSupport;
import dev.redstoneengineering.signal.PrecisionFilterLogic;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

import java.util.Optional;

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
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        var input = RedstoneObservationSupport.observe(level, pos, inputSide(state));
        int value = side == outputSide(state) ? state.getValue(OUTPUT) : input.value();
        return Optional.of(EngineeringPortSnapshot.redstone(port.get(), value, input.quality()));
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        var inputObservation = RedstoneObservationSupport.observe(level, pos, inputSide(state));
        if (!inputObservation.valid()) {
            // Missing or faulty evidence is not a numerical zero; retain the last physical output.
            return;
        }

        int input = inputObservation.value();
        int current = state.getValue(OUTPUT);
        int nextValue = PrecisionFilterLogic.step(
                current,
                input,
                riseRate(level, pos, state),
                fallRate(level, pos, state)
        );
        updateOutput(level, pos, state, nextValue);
        if (nextValue != input) level.scheduleTick(pos, this, 1);
    }

    public static int input(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof PrecisionFilterBlock filter)) return 0;
        return RedstoneObservationSupport.observe(level, pos, filter.inputSide(state)).value();
    }

    public static PortQuality inputQuality(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof PrecisionFilterBlock filter)) return PortQuality.NO_SIGNAL;
        return RedstoneObservationSupport.observe(level, pos, filter.inputSide(state)).quality();
    }

    private static boolean inputUsable(Level level, BlockPos pos, BlockState state) {
        PortQuality quality = inputQuality(level, pos, state);
        return quality == PortQuality.VALID || quality == PortQuality.SATURATED;
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
        return inputUsable(level, pos, state) && trackingError(level, pos, state) == 0;
    }

    public static int riseRate(Level level, BlockPos pos, BlockState state) {
        int fallback = state.getValue(RATE);
        if (level instanceof ServerLevel serverLevel) {
            return Math.max(1, Math.min(15, EngineeringDeviceParameters.get(serverLevel)
                    .extendedParameters(serverLevel, pos,
                            new EngineeringDeviceParameters.ExtendedParameters(fallback, 0, 0, 0)).a()));
        }
        return fallback;
    }

    public static boolean setRiseRate(ServerLevel level, BlockPos pos, int value) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PrecisionFilterBlock filter)) return false;
        int bounded = Math.max(1, Math.min(15, value));
        boolean changed = EngineeringDeviceParameters.get(level).setExtendedParameters(
                level, pos, new EngineeringDeviceParameters.ExtendedParameters(bounded, 0, 0, 0));
        if (changed) level.scheduleTick(pos, filter, 1);
        return changed;
    }

    public static int fallRate(Level level, BlockPos pos, BlockState state) {
        PrecisionFilterBlockEntity entity = persistentState(level, pos, state);
        return entity == null ? state.getValue(RATE) : entity.fallRate();
    }

    public static int settleTicks(Level level, BlockPos pos, BlockState state) {
        if (!inputUsable(level, pos, state)) return -1;
        int in = input(level, pos, state);
        int out = state.getValue(OUTPUT);
        return PrecisionFilterLogic.settleTicks(out, in, riseRate(level, pos, state), fallRate(level, pos, state));
    }

    /** -1 falling, 0 settled, +1 rising. */
    public static int responseDirection(Level level, BlockPos pos, BlockState state) {
        return Integer.compare(trackingError(level, pos, state), 0);
    }

    /** Shared authoritative operator action used by both HMI and Shift-right-click. */
    public static boolean stepRate(Level level, BlockPos pos, boolean forward) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PrecisionFilterBlock filter)) return false;
        int rate = state.getValue(RATE);
        int nextRate = forward ? (rate >= 4 ? 1 : rate + 1) : (rate <= 1 ? 4 : rate - 1);
        BlockState nextState = state.setValue(RATE, nextRate);
        level.setBlock(pos, nextState, Block.UPDATE_CLIENTS);
        if (level instanceof ServerLevel serverLevel) {
            EngineeringDeviceParameters.get(serverLevel).setExtendedParameters(
                    serverLevel, pos, new EngineeringDeviceParameters.ExtendedParameters(nextRate, 0, 0, 0));
        }
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
                    int eta = settleTicks(level, pos, next);
                    player.displayClientMessage(
                            Component.literal(
                                    "Precision Filter | rise=" + rise
                                            + " fall=" + fall
                                            + " level/tick | error=" + error
                                            + " | inputQuality=" + inputQuality(level, pos, next)
                                            + " | settleETA=" + (eta < 0 ? "UNAVAILABLE" : eta + "t")
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
