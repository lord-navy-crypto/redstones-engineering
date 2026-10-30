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
import dev.redstoneengineering.physics.EngineeringParameterProfile;
import dev.redstoneengineering.physics.EngineeringMath;
import dev.redstoneengineering.physics.RuntimeIntStore;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/** First-order discrete low-pass filter with observer-neutral runtime readback. */
public class LapisLowPassFilterBlock extends DirectionalDomainBlock implements EngineeringPortProvider {
    public static final IntegerProperty ALPHA = IntegerProperty.create("alpha", 0, EngineeringParameterProfile.LAPIS_FILTER_ALPHA_STEPS - 1);
    private static final String KEY = "lapis_lpf";
    private static final int OUTPUT_SLOT = 0;
    private static final int VALID_SLOT = 1;
    private static final int QUALITY_SLOT = 2;
    private static final int PREVIOUS_OUTPUT_SLOT = 3;
    private static final int RUNTIME_SIZE = 4;

    public record FilterState(int output, boolean valid, PortQuality quality) {}

    public LapisLowPassFilterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ALPHA, EngineeringParameterProfile.LAPIS_FILTER_DEFAULT_INDEX));
    }

    @Override public MapCodec<LapisLowPassFilterBlock> codec() { return RedstoneEngineering.LAPIS_LOW_PASS_FILTER_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { super.createBlockStateDefinition(builder); builder.add(ALPHA); }

    public static double alpha(int index) {
        return EngineeringParameterProfile.lapisFilterAlpha(index);
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

    /** Observer-neutral retained y[n-1] used by the most recent physical update. */
    public static int previousOutput(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length != RUNTIME_SIZE
                ? 0 : EngineeringMath.clamp(runtime[PREVIOUS_OUTPUT_SLOT], 0, 100);
    }

    /** Server-authoritative bounded alpha adjustment shared by the HMI and quick Shift-use path. */
    public static boolean adjustAlpha(Level level, BlockPos pos, int delta) {
        if (level.isClientSide || delta == 0) return false;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof LapisLowPassFilterBlock block)) return false;
        int current = state.getValue(ALPHA);
        int nextIndex = Math.floorMod(current + delta, EngineeringParameterProfile.LAPIS_FILTER_ALPHA_STEPS);
        if (nextIndex == current) return false;
        level.setBlock(pos, state.setValue(ALPHA, nextIndex), Block.UPDATE_CLIENTS);
        if (level instanceof ServerLevel serverLevel) serverLevel.scheduleTick(pos, block, 1);
        return true;
    }

    /** Select an exact supported alpha profile by index while preserving server scheduling semantics. */
    public static boolean setAlphaIndex(Level level, BlockPos pos, int index) {
        if (level.isClientSide || index < 0 || index >= EngineeringParameterProfile.LAPIS_FILTER_ALPHA_STEPS) return false;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof LapisLowPassFilterBlock block)) return false;
        int current = state.getValue(ALPHA);
        if (current == index) return true;
        level.setBlock(pos, state.setValue(ALPHA, index), Block.UPDATE_CLIENTS);
        if (level instanceof ServerLevel serverLevel) serverLevel.scheduleTick(pos, block, 1);
        return true;
    }

    /** Restore the engineering profile's declared default without touching runtime evidence directly. */
    public static boolean resetAlpha(Level level, BlockPos pos) {
        if (level.isClientSide) return false;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof LapisLowPassFilterBlock block)) return false;
        int current = state.getValue(ALPHA);
        if (current == EngineeringParameterProfile.LAPIS_FILTER_DEFAULT_INDEX) return false;
        level.setBlock(pos, state.setValue(ALPHA, EngineeringParameterProfile.LAPIS_FILTER_DEFAULT_INDEX), Block.UPDATE_CLIENTS);
        if (level instanceof ServerLevel serverLevel) serverLevel.scheduleTick(pos, block, 1);
        return true;
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
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos inputPos = inputPos(pos, state);
        DomainNetwork.LapisSample input = DomainNetwork.sampleLapis(level, inputPos);
        PortQuality inputQuality = inputQuality(level, inputPos, input);
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        if (input.valid() && inputQuality == PortQuality.VALID) {
            int previous = runtime[VALID_SLOT] == 0 ? input.value() : runtime[OUTPUT_SLOT];
            runtime[PREVIOUS_OUTPUT_SLOT] = previous;
            runtime[OUTPUT_SLOT] = EngineeringMath.clamp(
                    (int) Math.round(previous + alpha(state.getValue(ALPHA)) * (input.value() - previous)), 0, 100);
            runtime[VALID_SLOT] = 1;
            runtime[QUALITY_SLOT] = PortQuality.VALID.ordinal();
            DomainNetwork.driveLapis(level, outputPos(pos, state), pos, runtime[OUTPUT_SLOT], true);
        } else {
            runtime[PREVIOUS_OUTPUT_SLOT] = runtime[OUTPUT_SLOT];
            runtime[OUTPUT_SLOT] = 0;
            runtime[VALID_SLOT] = 0;
            runtime[QUALITY_SLOT] = inputQuality.ordinal();
            DomainNetwork.driveLapis(level, outputPos(pos, state), pos, 0, false);
        }
        level.scheduleTick(pos, this, EngineeringParameterProfile.LAPIS_FILTER_SAMPLE_PERIOD_TICKS);
    }

    private static PortQuality inputQuality(Level level, BlockPos inputPos, DomainNetwork.LapisSample sample) {
        return level.getBlockState(inputPos).getBlock() instanceof LapisSignalLineBlock
                ? LapisSignalLineBlock.quality(level, inputPos)
                : (sample.valid() ? PortQuality.VALID : PortQuality.NO_SIGNAL);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                adjustAlpha(level, pos, 1);
                BlockState next = level.getBlockState(pos);
                int index = next.getValue(ALPHA);
                double selectedAlpha = alpha(index);
                String response = EngineeringParameterProfile.lapisFilterBypass(index)
                        ? String.format("alpha=%.2f | BYPASS | dt=%dt", selectedAlpha,
                                EngineeringParameterProfile.LAPIS_FILTER_SAMPLE_PERIOD_TICKS)
                        : String.format("alpha=%.2f | tau≈%.2ft | fc≈%.3fHz@20TPS", selectedAlpha,
                                EngineeringParameterProfile.lapisFilterTimeConstantTicks(index),
                                EngineeringParameterProfile.lapisFilterCutoffHzNominal(index));
                player.displayClientMessage(Component.literal(
                        "Lapis low-pass quick adjust | " + response + " | normal right-click opens Engineering HMI"), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
