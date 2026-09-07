package dev.redstoneengineering.block;

import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.physics.SensorModel;
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
 * Common physical-quantity -> Lapis transducer behavior.
 * BlockState stores only small player configuration; live measurements are runtime data.
 */
public abstract class AbstractLapisTransducerBlock extends DirectionalDomainBlock implements EngineeringPortProvider {
    public static final IntegerProperty PROFILE = IntegerProperty.create("profile", 0, 3);

    private static final int OUTPUT = 0;
    private static final int OUTPUT_QUALITY = 1;
    private static final int PENDING_VALUE = 2;
    private static final int PENDING_QUALITY = 3;
    private static final int INITIALIZED = 4;
    private static final int RUNTIME_SIZE = 5;

    protected AbstractLapisTransducerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(PROFILE, 1));
    }

    /**
     * A physical measurement carries quality independently of its numeric value.
     * The boolean constructor remains for simple sources, while coverage/topology-aware
     * transducers can publish STALE or TOPOLOGY_ERROR without collapsing them into NO_SIGNAL.
     */
    protected record Measurement(int normalized, PortQuality quality, String detail) {
        protected Measurement(int normalized, boolean valid, String detail) {
            this(normalized, valid ? PortQuality.VALID : PortQuality.NO_SIGNAL, detail);
        }
    }

    protected abstract String runtimeKey();
    protected abstract String instrumentName();
    protected abstract String rangeText(BlockState state);
    protected abstract EngineeringDomain inputDomain();
    protected abstract Measurement sense(ServerLevel level, BlockPos pos, BlockState state);

    protected String inputPortLabel() {
        return inputDomain().label() + " INPUT";
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PROFILE);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort(
                        inputPortLabel(),
                        inputSide(state),
                        inputDomain(),
                        PortKind.MEASUREMENT,
                        PortDirection.INPUT,
                        false,
                        "normalized"
                ),
                new EngineeringPort(
                        "LAPIS OUTPUT",
                        outputSide(state),
                        EngineeringDomain.LAPIS,
                        PortKind.SENSOR,
                        PortDirection.OUTPUT,
                        false,
                        "normalized"
                )
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level,
            BlockPos pos,
            BlockState state,
            Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();

        if (side == inputSide(state)) {
            if (level instanceof ServerLevel server) {
                Measurement raw = sense(server, pos, state);
                return Optional.of(new EngineeringPortSnapshot(
                        port.get(),
                        Math.max(0, Math.min(100, raw.normalized())) / 100.0,
                        0.0,
                        1.0,
                        raw.quality()
                ));
            }
            return Optional.of(new EngineeringPortSnapshot(
                    port.get(),
                    0.0,
                    0.0,
                    1.0,
                    PortQuality.STALE
            ));
        }

        return Optional.of(new EngineeringPortSnapshot(
                port.get(),
                output(level, pos) / 100.0,
                0.0,
                1.0,
                outputQuality(level, pos)
        ));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int profile = state.getValue(PROFILE);
        Measurement raw = sense(level, pos, state);
        int measured = signalUsable(raw.quality())
                ? SensorModel.condition(level, pos, raw.normalized(), profile)
                : 0;
        int[] rt = RuntimeIntStore.get(level, runtimeKey(), pos, RUNTIME_SIZE);

        int output;
        PortQuality quality;
        if (SensorModel.latencySamples(profile) == 0 || rt[INITIALIZED] == 0) {
            output = measured;
            quality = raw.quality();
        } else {
            output = rt[PENDING_VALUE];
            quality = decodeQuality(rt[PENDING_QUALITY]);
        }

        rt[OUTPUT] = output;
        rt[OUTPUT_QUALITY] = encodeQuality(quality);
        rt[PENDING_VALUE] = measured;
        rt[PENDING_QUALITY] = encodeQuality(raw.quality());
        rt[INITIALIZED] = 1;

        DomainNetwork.driveLapis(level, outputPos(pos, state), pos, output, signalUsable(quality));
        level.scheduleTick(pos, this, SensorModel.samplePeriod(profile));
    }

    /** Observer-only output readback; server ticks own runtime allocation. */
    public int output(Level level, BlockPos pos) {
        int[] rt = RuntimeIntStore.peek(level, runtimeKey(), pos);
        return rt == null || rt.length <= OUTPUT ? 0 : rt[OUTPUT];
    }

    /** STALE means the transducer has not produced a trustworthy sample yet. */
    public PortQuality outputQuality(Level level, BlockPos pos) {
        int[] rt = RuntimeIntStore.peek(level, runtimeKey(), pos);
        if (rt == null || rt.length <= OUTPUT_QUALITY) return PortQuality.STALE;
        return decodeQuality(rt[OUTPUT_QUALITY]);
    }

    public boolean valid(Level level, BlockPos pos) {
        return signalUsable(outputQuality(level, pos));
    }

    private static boolean signalUsable(PortQuality quality) {
        return quality == PortQuality.VALID || quality == PortQuality.SATURATED;
    }

    private static int encodeQuality(PortQuality quality) {
        return quality.ordinal() + 1;
    }

    private static PortQuality decodeQuality(int encoded) {
        int ordinal = encoded - 1;
        PortQuality[] values = PortQuality.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : PortQuality.STALE;
    }

    private void invalidateOutput(ServerLevel level, BlockPos pos, BlockState state) {
        RuntimeIntStore.remove(level, runtimeKey(), pos);
        DomainNetwork.driveLapis(level, outputPos(pos, state), pos, 0, false);
        level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level instanceof ServerLevel server) DomainNetwork.driveLapis(server, outputPos(pos, state), pos, 0, false);
            RuntimeIntStore.remove(level, runtimeKey(), pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            if (!player.isShiftKeyDown()) {
                int next = (state.getValue(PROFILE) + 1) & 3;
                state = state.setValue(PROFILE, next);
                level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                if (level instanceof ServerLevel server) invalidateOutput(server, pos, state);
            }
            int profile = state.getValue(PROFILE);
            PortQuality quality = outputQuality(level, pos);
            String value = signalUsable(quality) ? String.format("%.2f", output(level, pos) / 100.0) : quality.name();
            String detail = level instanceof ServerLevel server ? sense(server, pos, state).detail() : "";
            player.displayClientMessage(Component.literal(
                    instrumentName() + " | Lapis=" + value
                            + " | quality=" + quality
                            + " | profile=" + SensorModel.profileName(profile)
                            + " | sample=" + SensorModel.samplePeriod(profile) + "t"
                            + " | resolution=" + SensorModel.resolutionStep(profile) + "/100"
                            + " | noise=±" + SensorModel.noiseAmplitude(profile) + "/100"
                            + " | latency=" + SensorModel.latencySamples(profile) + " sample"
                            + " | range=" + rangeText(state)
                            + (detail.isEmpty() ? "" : " | " + detail)), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
