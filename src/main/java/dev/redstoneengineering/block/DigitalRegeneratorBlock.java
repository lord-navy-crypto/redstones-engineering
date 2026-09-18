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
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.SerialNetwork;
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

/** Re-times/re-shapes a serial payload only when input quality clears a configurable decision threshold. */
public class DigitalRegeneratorBlock extends DirectionalDomainBlock implements EngineeringPortProvider {
    public static final IntegerProperty THRESHOLD = IntegerProperty.create("threshold", 0, 2);
    private static final int[] MIN_QUALITY = {20, 40, 60};
    private static final int WATCHDOG_TICKS = 16;
    private static final String RUNTIME_KEY = "digital_regenerator";
    private static final int LAST_ACCEPTED = 0;
    private static final int ACCEPTED_COUNT = 1;
    private static final int REJECTED_COUNT = 2;
    private static final int RUNTIME_SIZE = 3;

    public DigitalRegeneratorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(THRESHOLD, 1));
    }

    @Override public MapCodec<DigitalRegeneratorBlock> codec() { return RedstoneEngineering.DIGITAL_REGENERATOR_CODEC.value(); }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(THRESHOLD);
    }

    public static int minimumQuality(int thresholdIndex) {
        return MIN_QUALITY[Math.max(0, Math.min(MIN_QUALITY.length - 1, thresholdIndex))];
    }

    public static boolean stepThreshold(Level level, BlockPos pos, boolean forward) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof DigitalRegeneratorBlock regenerator)) return false;
        int next = Math.floorMod(state.getValue(THRESHOLD) + (forward ? 1 : -1), MIN_QUALITY.length);
        BlockState updated = state.setValue(THRESHOLD, next);
        level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
        if (level instanceof ServerLevel server) regenerator.update(server, pos, updated);
        return true;
    }

    public static int acceptedCount(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, RUNTIME_KEY, pos);
        return runtime == null || runtime.length < RUNTIME_SIZE ? 0 : Math.max(0, runtime[ACCEPTED_COUNT]);
    }

    public static int rejectedCount(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, RUNTIME_KEY, pos);
        return runtime == null || runtime.length < RUNTIME_SIZE ? 0 : Math.max(0, runtime[REJECTED_COUNT]);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("SERIAL IN", inputSide(state), EngineeringDomain.SERIAL_DATA,
                        PortKind.CONVERTER, PortDirection.INPUT, false, "byte"),
                new EngineeringPort("REGENERATED SERIAL OUT", outputSide(state), EngineeringDomain.SERIAL_DATA,
                        PortKind.CONVERTER, PortDirection.OUTPUT, false, "byte")
        );
    }

    public static PortQuality inputEvidenceQuality(Level level, BlockPos pos, BlockState state) {
        BlockPos input = pos.relative(DirectionalDomainBlock.seriesInputSide(state));
        if (!level.hasChunkAt(input)) return PortQuality.STALE;
        if (!SerialNetwork.isNode(level, input)) return PortQuality.NO_SIGNAL;
        return SerialNetwork.quality(level, input);
    }

    public static int inputQualityPercent(Level level, BlockPos pos, BlockState state) {
        BlockPos input = pos.relative(DirectionalDomainBlock.seriesInputSide(state));
        return InformationRuntime.snapshot(level, "serial", input).qualityPercent();
    }

    public static PortQuality outputEvidenceQuality(Level level, BlockPos pos, BlockState state) {
        PortQuality upstream = inputEvidenceQuality(level, pos, state);
        if (upstream != PortQuality.VALID && upstream != PortQuality.SATURATED) return upstream;
        BlockPos inputPos = pos.relative(DirectionalDomainBlock.seriesInputSide(state));
        InformationRuntime.Snapshot input = InformationRuntime.snapshot(level, "serial", inputPos);
        if (!input.valid()) return PortQuality.FAULT;
        return input.qualityPercent() >= minimumQuality(state.getValue(THRESHOLD))
                ? PortQuality.VALID : PortQuality.FAULT;
    }

    private PortQuality inputQuality(Level level, BlockPos pos, BlockState state) {
        return inputEvidenceQuality(level, pos, state);
    }

    private PortQuality outputQuality(Level level, BlockPos pos, BlockState state) {
        return outputEvidenceQuality(level, pos, state);
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        if (side == inputSide(state)) {
            InformationRuntime.Snapshot input = InformationRuntime.snapshot(level, "serial", inputPos(pos, state));
            return Optional.of(new EngineeringPortSnapshot(port.get(), input.value() & 0xFF,
                    0.0, 255.0, inputQuality(level, pos, state)));
        }
        InformationRuntime.Snapshot output = InformationRuntime.snapshot(level, "serial", pos);
        return Optional.of(new EngineeringPortSnapshot(port.get(), output.value() & 0xFF,
                0.0, 255.0, outputQuality(level, pos, state)));
    }

    private void update(ServerLevel level, BlockPos pos, BlockState state) {
        BlockPos inputPos = inputPos(pos, state);
        BlockPos output = outputPos(pos, state);
        InformationRuntime.Snapshot input = InformationRuntime.snapshot(level, "serial", inputPos);
        PortQuality upstream = inputQuality(level, pos, state);
        boolean accepted = (upstream == PortQuality.VALID || upstream == PortQuality.SATURATED)
                && input.valid()
                && input.qualityPercent() >= minimumQuality(state.getValue(THRESHOLD));
        int[] runtime = RuntimeIntStore.get(level, RUNTIME_KEY, pos, RUNTIME_SIZE);
        int current = accepted ? 1 : 0;
        if (current != runtime[LAST_ACCEPTED]) {
            if (accepted) runtime[ACCEPTED_COUNT]++;
            else if (runtime[LAST_ACCEPTED] != 0) runtime[REJECTED_COUNT]++;
            runtime[LAST_ACCEPTED] = current;
        }
        int period = Math.max(1, input.selector());
        InformationRuntime.write(level, "serial", pos, input.value() & 0xFF, period,
                accepted, accepted ? 100 : 0);
        if (level.getBlockState(output).getBlock() instanceof SerialDataLineBlock) {
            SerialNetwork.recompute(level, output);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel serverLevel) {
            update(serverLevel, pos, state);
            serverLevel.scheduleTick(pos, this, WATCHDOG_TICKS);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        update(level, pos, state);
        level.scheduleTick(pos, this, WATCHDOG_TICKS);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel && neighborPos.equals(inputPos(pos, state))) {
            update(serverLevel, pos, state);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            InformationRuntime.clear(level, "serial", pos);
            RuntimeIntStore.remove(level, RUNTIME_KEY, pos);
            BlockPos output = outputPos(pos, state);
            BlockState outputState = level.getBlockState(output);
            if (outputState.getBlock() instanceof SerialDataLineBlock line) {
                serverLevel.scheduleTick(output, line, 1);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                stepThreshold(level, pos, true);
                BlockState next = level.getBlockState(pos);
                int nextThreshold = next.getValue(THRESHOLD);
                InformationRuntime.Snapshot input = InformationRuntime.snapshot(level, "serial", inputPos(pos, next));
                player.displayClientMessage(Component.literal(
                        "Digital regenerator minQuality=" + minimumQuality(nextThreshold) + "%"
                                + " inputQuality=" + input.qualityPercent() + "%"
                                + " state=" + outputQuality(level, pos, next)
                                + " accepted=" + acceptedCount(level, pos)
                                + " rejected=" + rejectedCount(level, pos)), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
