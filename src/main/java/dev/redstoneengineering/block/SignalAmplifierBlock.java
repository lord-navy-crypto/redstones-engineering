package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.physics.RedstoneObservationSupport;
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

import java.util.Optional;

/**
 * Dedicated redstone gain stage.
 *
 * <p>This is a gameplay-scale amplifier: configurable gain, finite 0..15 headroom and retained
 * clipping evidence. It intentionally does not duplicate the conditioner's offset/threshold/deadband
 * transfer functions.</p>
 */
public final class SignalAmplifierBlock extends DirectionalSignalBlock {
    public static final IntegerProperty GAIN_MODE = IntegerProperty.create("gain_mode", 0, 3);
    private static final int[] GAINS = {1, 2, 3, 4};

    private static final String RUNTIME_KEY = "signal_amplifier";
    private static final int CLIP_ACTIVE = 0;
    private static final int CLIP_EPISODES = 1;
    private static final int MAX_RAW = 2;
    private static final int RUNTIME_SIZE = 3;

    public SignalAmplifierBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(GAIN_MODE, 1));
    }

    @Override
    public MapCodec<SignalAmplifierBlock> codec() {
        return RedstoneEngineering.SIGNAL_AMPLIFIER_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(GAIN_MODE);
    }

    public static int gain(BlockState state) {
        return GAINS[Math.max(0, Math.min(GAINS.length - 1, state.getValue(GAIN_MODE)))];
    }

    public static boolean stepGain(Level level, BlockPos pos, boolean forward) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SignalAmplifierBlock amplifier)) return false;
        int next = Math.floorMod(state.getValue(GAIN_MODE) + (forward ? 1 : -1), GAINS.length);
        level.setBlock(pos, state.setValue(GAIN_MODE, next), Block.UPDATE_CLIENTS);
        if (level instanceof ServerLevel server) server.scheduleTick(pos, amplifier, 1);
        return true;
    }

    public static int clippingEpisodes(Level level, BlockPos pos) {
        int[] rt = RuntimeIntStore.peek(level, RUNTIME_KEY, pos);
        return rt == null || rt.length < RUNTIME_SIZE ? 0 : Math.max(0, rt[CLIP_EPISODES]);
    }

    public static int maxRawOutput(Level level, BlockPos pos) {
        int[] rt = RuntimeIntStore.peek(level, RUNTIME_KEY, pos);
        return rt == null || rt.length < RUNTIME_SIZE ? 0 : Math.max(0, rt[MAX_RAW]);
    }

    public static boolean clipping(Level level, BlockPos pos) {
        int[] rt = RuntimeIntStore.peek(level, RUNTIME_KEY, pos);
        return rt != null && rt.length >= RUNTIME_SIZE && rt[CLIP_ACTIVE] != 0;
    }

    public static boolean resetClipEvidence(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SignalAmplifierBlock amplifier)) return false;
        RuntimeIntStore.remove(level, RUNTIME_KEY, pos);
        if (level instanceof ServerLevel server) server.scheduleTick(pos, amplifier, 1);
        return true;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int input = readBackInput(level, pos, state);
        int raw = input * gain(state);
        boolean clipping = raw > 15;
        int[] rt = RuntimeIntStore.get(level, RUNTIME_KEY, pos, RUNTIME_SIZE);
        if (clipping && rt[CLIP_ACTIVE] == 0 && rt[CLIP_EPISODES] < Integer.MAX_VALUE) rt[CLIP_EPISODES]++;
        rt[CLIP_ACTIVE] = clipping ? 1 : 0;
        rt[MAX_RAW] = Math.max(rt[MAX_RAW], raw);
        updateOutput(level, pos, state, Math.min(15, raw));
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, net.minecraft.core.Direction side) {
        Optional<EngineeringPortSnapshot> base = super.engineeringSnapshot(level, pos, state, side);
        if (base.isEmpty()) return base;

        if (side == inputSide(state)) {
            var input = RedstoneObservationSupport.observe(level, pos, inputSide(state));
            return Optional.of(EngineeringPortSnapshot.redstone(base.get().port(), input.value(), input.quality()));
        }

        if (side == outputSide(state)) {
            var input = RedstoneObservationSupport.observe(level, pos, inputSide(state));
            PortQuality quality = clipping(level, pos) ? PortQuality.SATURATED : input.quality();
            return Optional.of(new EngineeringPortSnapshot(
                    base.get().port(), state.getValue(OUTPUT), base.get().minimum(), base.get().maximum(), quality));
        }
        return base;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, RUNTIME_KEY, pos);
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                stepGain(level, pos, true);
                BlockState next = level.getBlockState(pos);
                player.displayClientMessage(Component.literal(
                        "Signal Amplifier | gain=x" + gain(next)
                                + " | clipping=" + (clipping(level, pos) ? "YES" : "NO")
                                + " | clipEpisodes=" + clippingEpisodes(level, pos)
                                + " | maxRaw=" + maxRawOutput(level, pos)), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
