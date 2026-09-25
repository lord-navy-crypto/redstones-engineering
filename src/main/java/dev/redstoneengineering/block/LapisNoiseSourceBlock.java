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
import dev.redstoneengineering.diagnostics.FaultInjectionModel;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.EngineeringDeviceParameters;
import dev.redstoneengineering.physics.EngineeringMath;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.signal.LapisNoiseSourceLogic;
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

import java.util.List;
import java.util.Optional;

/**
 * Repeatable Lapis-domain noise source used both as a source and as a commissioning fault injector.
 * Configuration stays small; the changing sample is transient runtime data.
 *
 * <p>A numeric sample of zero is a legitimate generated value. Initialization therefore has its
 * own runtime flag instead of abusing zero as an "unset" sentinel. Observer APIs use peek() and
 * can never create or rewrite the source's physical sample.</p>
 */
public class LapisNoiseSourceBlock extends DirectionalDomainSourceBlock implements EngineeringPortProvider {
    public static final IntegerProperty BASELINE = IntegerProperty.create(
            "baseline",
            LapisNoiseSourceLogic.MIN_LEGACY_BASELINE_INDEX,
            LapisNoiseSourceLogic.MAX_LEGACY_BASELINE_INDEX);
    public static final IntegerProperty NOISE = IntegerProperty.create(
            "noise",
            LapisNoiseSourceLogic.MIN_LEGACY_NOISE_INDEX,
            LapisNoiseSourceLogic.MAX_LEGACY_NOISE_INDEX);
    public static final IntegerProperty RATE = IntegerProperty.create(
            "rate",
            LapisNoiseSourceLogic.MIN_LEGACY_RATE_INDEX,
            LapisNoiseSourceLogic.MAX_LEGACY_RATE_INDEX);
    private static final String KEY = "lapis_noise";
    private static final int SAMPLE_SLOT = 0;
    private static final int INITIALIZED_SLOT = 1;
    private static final int RUNTIME_SIZE = 2;

    public LapisNoiseSourceBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(BASELINE, LapisNoiseSourceLogic.DEFAULT_LEGACY_BASELINE_INDEX)
                .setValue(NOISE, LapisNoiseSourceLogic.DEFAULT_LEGACY_NOISE_INDEX)
                .setValue(RATE, LapisNoiseSourceLogic.DEFAULT_LEGACY_RATE_INDEX));
    }

    @Override public MapCodec<LapisNoiseSourceBlock> codec() { return RedstoneEngineering.LAPIS_NOISE_SOURCE_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BASELINE, NOISE, RATE);
    }

    private static EngineeringPort port(Direction side) {
        return new EngineeringPort(
                "LAPIS NOISE OUT", side, EngineeringDomain.LAPIS,
                PortKind.BUS, PortDirection.OUTPUT, false, "precision");
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(port(outputSide(state)));
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        return engineeringPort(state, side).map(port -> new EngineeringPortSnapshot(
                port, currentValue(level, pos, state),
                LapisNoiseSourceLogic.MIN_BASELINE, LapisNoiseSourceLogic.MAX_BASELINE,
                PortQuality.VALID));
    }

    /** Observer-neutral current sample. Before the first server write, configuration is the readback fallback only. */
    public static int currentValue(Level level, BlockPos pos, BlockState state) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        if (runtime != null && runtime.length == RUNTIME_SIZE && runtime[INITIALIZED_SLOT] == 1) {
            return LapisNoiseSourceLogic.boundedBaseline(runtime[SAMPLE_SLOT]);
        }
        return configuredParameters(level, pos, state).a();
    }

    public static boolean sampleInitialized(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime != null && runtime.length == RUNTIME_SIZE && runtime[INITIALIZED_SLOT] == 1;
    }

    public static int samplePeriodTicks(BlockState state) {
        return LapisNoiseSourceLogic.samplePeriodForLegacyRate(state.getValue(RATE));
    }

    public static EngineeringDeviceParameters.ExtendedParameters configuredParameters(Level level, BlockPos pos, BlockState state) {
        var fallback = new EngineeringDeviceParameters.ExtendedParameters(
                LapisNoiseSourceLogic.baselineForLegacyIndex(state.getValue(BASELINE)),
                LapisNoiseSourceLogic.noiseForLegacyIndex(state.getValue(NOISE)),
                samplePeriodTicks(state),
                0);
        if (level instanceof ServerLevel serverLevel) {
            var stored = EngineeringDeviceParameters.get(serverLevel)
                    .extendedParameters(serverLevel, pos, fallback);
            // Baseline/noise may legitimately be zero. Period zero is the only impossible value,
            // so treat it as an older-save "unset" slot and fall back to the legacy rate preset.
            int period = stored.c() <= 0
                    ? fallback.c()
                    : LapisNoiseSourceLogic.boundedSamplePeriod(stored.c());
            return new EngineeringDeviceParameters.ExtendedParameters(
                    LapisNoiseSourceLogic.boundedBaseline(stored.a()),
                    LapisNoiseSourceLogic.boundedNoiseAmplitude(stored.b()),
                    period,
                    0);
        }
        return fallback;
    }

    public static boolean setEngineeringParameters(ServerLevel level, BlockPos pos, int baseline, int noiseAmplitude, int samplePeriod) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof LapisNoiseSourceBlock source)) return false;
        var next = new EngineeringDeviceParameters.ExtendedParameters(
                LapisNoiseSourceLogic.boundedBaseline(baseline),
                LapisNoiseSourceLogic.boundedNoiseAmplitude(noiseAmplitude),
                LapisNoiseSourceLogic.boundedSamplePeriod(samplePeriod),
                0);
        boolean changed = EngineeringDeviceParameters.get(level).setExtendedParameters(level, pos, next);
        if (changed) {
            setSample(level, pos, next.a());
            DomainNetwork.recomputeLapis(level, pos);
            level.scheduleTick(pos, source, 1);
        }
        return changed;
    }

    public static String rateName(BlockState state) {
        return switch (state.getValue(RATE)) {
            case 0 -> "FAST";
            case 1 -> "MEDIUM";
            case 2 -> "SLOW";
            case 3 -> "DRIFT";
            default -> "MEDIUM";
        };
    }

    /** Authoritative physics write used by placement, the scheduler and deterministic runtime tests. */
    public static void setSample(Level level, BlockPos pos, int sample) {
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        runtime[SAMPLE_SLOT] = LapisNoiseSourceLogic.boundedBaseline(sample);
        runtime[INITIALIZED_SLOT] = 1;
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide && !state.is(oldState.getBlock())) {
            setSample(level, pos,
                    LapisNoiseSourceLogic.baselineForLegacyIndex(state.getValue(BASELINE)));
            if (level instanceof ServerLevel serverLevel) DomainNetwork.recomputeLapis(serverLevel, pos);
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            RuntimeIntStore.remove(level, KEY, pos);
            if (level instanceof ServerLevel serverLevel) {
                EngineeringDeviceParameters.get(serverLevel).removeExtendedParameters(serverLevel, pos);
                DomainNetwork.recomputeLapisAround(serverLevel, pos);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        var parameters = configuredParameters(level, pos, state);
        int sample = FaultInjectionModel.addDeterministicNoise(
                parameters.a(), parameters.b(), level.getGameTime(), pos.asLong(),
                LapisNoiseSourceLogic.MIN_BASELINE, LapisNoiseSourceLogic.MAX_BASELINE);
        setSample(level, pos, sample);
        DomainNetwork.recomputeLapis(level, pos);
        level.scheduleTick(pos, this, parameters.c());
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit
    ) {
        if (!level.isClientSide) {
            BlockState next = state;
            boolean configurationChanged = false;

            if (player.isShiftKeyDown() && hit.getDirection().getAxis().isHorizontal()) {
                // Route changes topology only. Never rewrite or advance the deterministic sample.
                if (rotateOutput(level, pos, true)) {
                    next = level.getBlockState(pos);
                    if (level instanceof ServerLevel serverLevel) {
                        DomainNetwork.recomputeLapisAround(serverLevel, pos);
                    }
                }
            } else if (player.isShiftKeyDown()) {
                if (hit.getDirection() == Direction.UP) {
                    int noise = state.getValue(NOISE);
                    int nextNoise = noise >= LapisNoiseSourceLogic.MAX_LEGACY_NOISE_INDEX
                            ? LapisNoiseSourceLogic.MIN_LEGACY_NOISE_INDEX : noise + 1;
                    next = state.setValue(NOISE, nextNoise);
                } else {
                    int rate = state.getValue(RATE);
                    int span = LapisNoiseSourceLogic.MAX_LEGACY_RATE_INDEX
                            - LapisNoiseSourceLogic.MIN_LEGACY_RATE_INDEX + 1;
                    int nextRate = LapisNoiseSourceLogic.MIN_LEGACY_RATE_INDEX
                            + Math.floorMod(rate - LapisNoiseSourceLogic.MIN_LEGACY_RATE_INDEX + 1, span);
                    next = state.setValue(RATE, nextRate);
                }
                level.setBlock(pos, next, Block.UPDATE_CLIENTS);
                configurationChanged = true;
            } else {
                int baseline = state.getValue(BASELINE);
                int nextBaseline = baseline >= LapisNoiseSourceLogic.MAX_LEGACY_BASELINE_INDEX
                        ? LapisNoiseSourceLogic.MIN_LEGACY_BASELINE_INDEX : baseline + 1;
                next = state.setValue(BASELINE, nextBaseline);
                level.setBlock(pos, next, Block.UPDATE_CLIENTS);
                configurationChanged = true;
            }

            if (configurationChanged && level instanceof ServerLevel serverLevel) {
                // Legacy quick controls are presets for the same exact server-owned parameters,
                // not a second configuration authority.
                setEngineeringParameters(
                        serverLevel, pos,
                        LapisNoiseSourceLogic.baselineForLegacyIndex(next.getValue(BASELINE)),
                        LapisNoiseSourceLogic.noiseForLegacyIndex(next.getValue(NOISE)),
                        LapisNoiseSourceLogic.samplePeriodForLegacyRate(next.getValue(RATE)));
            }

            int current = currentValue(level, pos, next);
            player.displayClientMessage(Component.literal(
                    "Fault injection [NOISE] | LAPIS OUT=" + outputSide(next).getName().toUpperCase()
                            + " | baseline=" + String.format("%.2f",
                            LapisNoiseSourceLogic.baselineForLegacyIndex(next.getValue(BASELINE)) / 100.0)
                            + " | noise=±" + String.format("%.2f",
                            LapisNoiseSourceLogic.noiseForLegacyIndex(next.getValue(NOISE)) / 100.0)
                            + " | rate=" + rateName(next) + " (" + samplePeriodTicks(next) + "t)"
                            + " | now=" + String.format("%.2f", current / 100.0)
                            + " | zero is valid | shift-side=route, shift-UP=noise, shift-DOWN=rate"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }}
