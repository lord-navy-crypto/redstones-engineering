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
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.EngineeringDeviceParameters;
import dev.redstoneengineering.physics.EngineeringMath;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/** First-order discrete low-pass filter with observer-neutral runtime readback. */
public class LapisLowPassFilterBlock extends DirectionalDomainBlock implements EngineeringPortProvider {
    public static final int FILTER_SAMPLE_TICKS = 2;
    public static final IntegerProperty ALPHA = IntegerProperty.create("alpha", 0, 3);
    private static final String KEY = "lapis_lpf";
    private static final int OUTPUT_SLOT = 0;
    private static final int VALID_SLOT = 1;
    private static final int QUALITY_SLOT = 2;
    private static final int HISTORY_SLOT = 3;
    private static final int RUNTIME_SIZE = 4;

    public record FilterState(int output, boolean valid, PortQuality quality) {}

    public LapisLowPassFilterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ALPHA, 1));
    }

    @Override public MapCodec<LapisLowPassFilterBlock> codec() { return RedstoneEngineering.LAPIS_LOW_PASS_FILTER_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { super.createBlockStateDefinition(builder); builder.add(ALPHA); }

    public static double alpha(int index) {
        return switch (index) {
            case 0 -> 0.10;
            case 1 -> 0.25;
            case 2 -> 0.50;
            default -> 0.75;
        };
    }

    public static int defaultAlphaPercent(BlockState state) {
        return (int) Math.round(alpha(state.getValue(ALPHA)) * 100.0);
    }

    public static int alphaPercent(Level level, BlockPos pos, BlockState state) {
        int fallback = defaultAlphaPercent(state);
        if (level instanceof ServerLevel serverLevel) {
            return EngineeringDeviceParameters.get(serverLevel)
                    .lapisLowPassAlphaPercent(serverLevel, pos, fallback);
        }
        return fallback;
    }

    public static double configuredAlpha(Level level, BlockPos pos, BlockState state) {
        return alphaPercent(level, pos, state) / 100.0;
    }

    public static boolean adjustAlpha(ServerLevel level, BlockPos pos, int deltaPercent) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof LapisLowPassFilterBlock)) return false;
        int current = alphaPercent(level, pos, state);
        int next = EngineeringMath.clamp(current + deltaPercent, 1, 99);
        boolean changed = EngineeringDeviceParameters.get(level)
                .setLapisLowPassAlphaPercent(level, pos, next);
        if (changed) level.scheduleTick(pos, state.getBlock(), 1);
        return changed;
    }

    public static boolean resetAlpha(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof LapisLowPassFilterBlock)) return false;
        boolean changed = EngineeringDeviceParameters.get(level)
                .setLapisLowPassAlphaPercent(level, pos, defaultAlphaPercent(state));
        if (changed) level.scheduleTick(pos, state.getBlock(), 1);
        return changed;
    }

    public static FilterState filterState(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        if (runtime == null || runtime.length != RUNTIME_SIZE) return new FilterState(0, false, PortQuality.NO_SIGNAL);
        int qualityIndex = EngineeringMath.clamp(runtime[QUALITY_SLOT], 0, PortQuality.values().length - 1);
        return new FilterState(
                EngineeringMath.clamp(runtime[OUTPUT_SLOT], 0, 100),
                runtime[VALID_SLOT] == 1,
                PortQuality.values()[qualityIndex]);
    }

    public static boolean runtimePresent(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime != null && runtime.length == RUNTIME_SIZE;
    }

    public static boolean retainedHistory(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime != null && runtime.length == RUNTIME_SIZE && runtime[HISTORY_SLOT] != 0;
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("LAPIS FILTER IN", inputSide(state), EngineeringDomain.LAPIS,
                        PortKind.BUS, PortDirection.INPUT, false, "precision"),
                new EngineeringPort("LAPIS FILTER OUT", outputSide(state), EngineeringDomain.LAPIS,
                        PortKind.BUS, PortDirection.OUTPUT, false, "precision")
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        if (side == inputSide(state)) {
            BlockPos input = inputPos(pos, state);
            DomainNetwork.LapisSample sample = DomainNetwork.sampleLapis(level, input);
            PortQuality quality = inputQuality(level, input, sample);
            return Optional.of(new EngineeringPortSnapshot(port.get(), sample.value(), 0.0, 100.0, quality));
        }
        FilterState runtime = filterState(level, pos);
        return Optional.of(new EngineeringPortSnapshot(
                port.get(), runtime.output(), 0.0, 100.0, runtime.quality()));
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, movedByPiston);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (level instanceof ServerLevel serverLevel) {
                DomainNetwork.driveLapis(serverLevel, outputPos(pos, state), pos, 0, false);
                DomainNetwork.recomputeLapisAround(serverLevel, pos);
            }
            RuntimeIntStore.remove(level, KEY, pos);
            if (level instanceof ServerLevel serverLevel) {
                EngineeringDeviceParameters.get(serverLevel).removeLapisLowPass(serverLevel, pos);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos inputPos = inputPos(pos, state);
        DomainNetwork.LapisSample input = DomainNetwork.sampleLapis(level, inputPos);
        PortQuality inputQuality = inputQuality(level, inputPos, input);
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        if (input.valid() && inputQuality == PortQuality.VALID) {
            int previous = runtime[HISTORY_SLOT] == 0 ? input.value() : runtime[OUTPUT_SLOT];
            runtime[OUTPUT_SLOT] = EngineeringMath.clamp(
                    (int) Math.round(previous + configuredAlpha(level, pos, state) * (input.value() - previous)), 0, 100);
            runtime[HISTORY_SLOT] = 1;
            runtime[VALID_SLOT] = 1;
            runtime[QUALITY_SLOT] = PortQuality.VALID.ordinal();
            DomainNetwork.driveLapis(level, outputPos(pos, state), pos, runtime[OUTPUT_SLOT], true);
        } else {
            // Loss of upstream evidence invalidates the driver, but it does not erase the filter's
            // internal state. On reacquisition the first-order response continues from the last
            // trustworthy physical output instead of fabricating a zero-valued history.
            runtime[VALID_SLOT] = 0;
            runtime[QUALITY_SLOT] = inputQuality.ordinal();
            DomainNetwork.driveLapis(level, outputPos(pos, state), pos, 0, false);
        }
        level.scheduleTick(pos, this, FILTER_SAMPLE_TICKS);
    }

    private static PortQuality inputQuality(Level level, BlockPos inputPos, DomainNetwork.LapisSample sample) {
        return level.getBlockState(inputPos).getBlock() instanceof LapisSignalLineBlock
                ? LapisSignalLineBlock.quality(level, inputPos)
                : (sample.valid() ? PortQuality.VALID : PortQuality.NO_SIGNAL);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(
                    new SimpleMenuProvider(
                            (containerId, inventory, ignored) ->
                                    new dev.redstoneengineering.ui.menu.LapisLowPassFilterMenu(containerId, inventory, pos),
                            Component.translatable("block.redstoneengineering.lapis_low_pass_filter")
                    ),
                    data -> data.writeBlockPos(pos)
            );
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
