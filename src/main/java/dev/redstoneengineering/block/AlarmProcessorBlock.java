package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.EngineeringSystemsModule;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.diagnostics.events.SystemEventKind;
import dev.redstoneengineering.diagnostics.events.SystemEventTimeline;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/**
 * Industrial alarm annunciator with latching, acknowledge and guarded reset semantics.
 * BACK=alarm condition, LEFT=ACK, RIGHT=RESET, FRONT=severity-coded alarm output.
 */
public class AlarmProcessorBlock extends PassiveDirectionalSignalBlock {
    public static final IntegerProperty SEVERITY = IntegerProperty.create("severity", 1, 3);
    private static final String KEY = "alarm_processor";
    // [latched, unacked, activations, acknowledgements, clears, prevCondition, prevAck, prevReset, firstOutSeverity, activeTicks]
    private static final int RUNTIME_SIZE = 10;

    public AlarmProcessorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(SEVERITY, 2));
    }

    @Override
    public MapCodec<AlarmProcessorBlock> codec() {
        return EngineeringSystemsModule.ALARM_PROCESSOR_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SEVERITY);
    }

    @Override
    protected boolean isEngineeringPort(BlockState state, Direction side) {
        Direction front = outputSide(state);
        return side == inputSide(state) || side == leftOf(front) || side == rightOf(front) || side == front;
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        Direction front = outputSide(state);
        return List.of(
                new EngineeringPort("ALARM CONDITION", inputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.SAFETY, PortDirection.INPUT, true, "condition"),
                new EngineeringPort("ACKNOWLEDGE", leftOf(front), EngineeringDomain.REDSTONE,
                        PortKind.CONTROL, PortDirection.INPUT, true, "ack"),
                new EngineeringPort("RESET / CLEAR", rightOf(front), EngineeringDomain.REDSTONE,
                        PortKind.RESET, PortDirection.INPUT, true, "reset"),
                new EngineeringPort("ALARM OUT", front, EngineeringDomain.REDSTONE,
                        PortKind.SAFETY, PortDirection.OUTPUT, true, "alarm")
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        int value = side == outputSide(state) ? state.getValue(OUTPUT) : readInputFrom(level, pos, side);
        PortQuality quality = side == outputSide(state) && latched(level, pos) ? PortQuality.FAULT
                : value > 0 ? PortQuality.VALID : PortQuality.NO_SIGNAL;
        return Optional.of(EngineeringPortSnapshot.redstone(port.get(), value, quality));
    }

    @Override
    protected int computeOutput(Level level, BlockPos pos, BlockState state) {
        Direction front = outputSide(state);
        int condition = readBackInput(level, pos, state);
        int ack = readInputFrom(level, pos, leftOf(front));
        int reset = readInputFrom(level, pos, rightOf(front));
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);

        boolean conditionRising = condition > 0 && runtime[5] == 0;
        boolean ackRising = ack > 0 && runtime[6] == 0;
        boolean resetRising = reset > 0 && runtime[7] == 0;

        if (conditionRising) {
            int severity = state.getValue(SEVERITY);
            runtime[0] = 1;
            runtime[1] = 1;
            runtime[2]++;
            runtime[8] = severity;
            SystemEventTimeline.record(level, pos, SystemEventKind.ALARM_RAISED, severity,
                    "ALARM_RAISED", "Alarm condition rose; severity=" + severity);
        }
        if (runtime[0] != 0) runtime[9]++;
        if (ackRising && runtime[0] != 0 && runtime[1] != 0) {
            runtime[1] = 0;
            runtime[3]++;
            SystemEventTimeline.record(level, pos, SystemEventKind.ALARM_ACKNOWLEDGED, 1,
                    "ALARM_ACK", "Latched alarm acknowledged by control input");
        }
        // A reset is fail-safe: it can only clear a latched alarm after the process condition is healthy.
        if (resetRising && condition <= 0 && runtime[0] != 0) {
            int clearedSeverity = runtime[8];
            runtime[0] = 0;
            runtime[1] = 0;
            runtime[4]++;
            runtime[8] = 0;
            SystemEventTimeline.record(level, pos, SystemEventKind.ALARM_CLEARED, 0,
                    "ALARM_CLEARED", "Healthy reset cleared severity=" + clearedSeverity + " alarm");
        }

        runtime[5] = condition > 0 ? 1 : 0;
        runtime[6] = ack > 0 ? 1 : 0;
        runtime[7] = reset > 0 ? 1 : 0;
        if (runtime[0] == 0) return 0;
        int severity = runtime[8] == 0 ? state.getValue(SEVERITY) : runtime[8];
        return severity == 1 ? 5 : severity == 2 ? 10 : 15;
    }

    public static boolean latched(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime != null && runtime.length > 0 && runtime[0] != 0;
    }

    public static boolean unacknowledged(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime != null && runtime.length > 1 && runtime[1] != 0;
    }

    public static int activationCount(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length < 3 ? 0 : runtime[2];
    }

    public static String compactDiagnostics(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        if (runtime == null || runtime.length < RUNTIME_SIZE) return "ALARM idle | no event history";
        if (runtime[0] == 0) return "ALARM CLEAR | activations=" + runtime[2] + " | clears=" + runtime[4];
        return "ALARM LATCHED | severity=" + runtime[8] + " | "
                + (runtime[1] != 0 ? "UNACKNOWLEDGED" : "ACKNOWLEDGED")
                + " | activeTicks=" + runtime[9];
    }

    public void cycleSeverity(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(this)) return;
        int next = state.getValue(SEVERITY) % 3 + 1;
        level.setBlock(pos, state.setValue(SEVERITY, next), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server) server.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        updateOutput(level, pos, state, outputValue(level, pos, state));
        level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, KEY, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
                if (runtime[0] != 0 && runtime[1] != 0) {
                    runtime[1] = 0;
                    runtime[3]++;
                    SystemEventTimeline.record(level, pos, SystemEventKind.ALARM_ACKNOWLEDGED, 1,
                            "ALARM_ACK", "Latched alarm acknowledged by operator");
                }
                player.displayClientMessage(Component.literal(compactDiagnostics(level, pos)), true);
            } else {
                cycleSeverity(level, pos);
                player.displayClientMessage(Component.literal("Alarm severity set to " + level.getBlockState(pos).getValue(SEVERITY)), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
