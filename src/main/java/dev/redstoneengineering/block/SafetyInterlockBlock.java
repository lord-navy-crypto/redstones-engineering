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
import dev.redstoneengineering.operations.world.OperationWorldResourceProvider;
import dev.redstoneengineering.operations.world.OperationWorldResourceSnapshot;
import dev.redstoneengineering.physics.RedstoneObservationSupport;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Three-channel permissive/interlock controller. BACK=A, LEFT=B, RIGHT=C, FRONT=PERMIT. */
public class SafetyInterlockBlock extends PassiveDirectionalSignalBlock implements OperationWorldResourceProvider {
    private static final String KEY = "safety_interlock";
    private static final int INITIALIZED = 5;
    private static final int RUNTIME_SIZE = 6;

    public SafetyInterlockBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<SafetyInterlockBlock> codec() {
        return EngineeringSystemsModule.SAFETY_INTERLOCK_CODEC.value();
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
                new EngineeringPort("PERMISSIVE A", inputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.SAFETY, PortDirection.INPUT, true, "permissive_a"),
                new EngineeringPort("PERMISSIVE B", leftOf(front), EngineeringDomain.REDSTONE,
                        PortKind.SAFETY, PortDirection.INPUT, true, "permissive_b"),
                new EngineeringPort("PERMISSIVE C", rightOf(front), EngineeringDomain.REDSTONE,
                        PortKind.SAFETY, PortDirection.INPUT, true, "permissive_c"),
                new EngineeringPort("PERMIT OUT", front, EngineeringDomain.REDSTONE,
                        PortKind.SAFETY, PortDirection.OUTPUT, true, "permit")
        );
    }

    private record PermissiveAssessment(int failedMask, PortQuality quality) {}

    private PermissiveAssessment assessPermissives(Level level, BlockPos pos, BlockState state) {
        Direction front = outputSide(state);
        var a = RedstoneObservationSupport.observe(level, pos, inputSide(state));
        var b = RedstoneObservationSupport.observe(level, pos, leftOf(front));
        var c = RedstoneObservationSupport.observe(level, pos, rightOf(front));

        int mask = ((!a.valid() || a.value() <= 0) ? 1 : 0)
                | ((!b.valid() || b.value() <= 0) ? 2 : 0)
                | ((!c.valid() || c.value() <= 0) ? 4 : 0);
        PortQuality quality = RedstoneObservationSupport.combineQuality(
                a.quality(), b.quality(), c.quality());
        return new PermissiveAssessment(mask, quality);
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        Direction front = outputSide(state);
        if (side == front) {
            PermissiveAssessment assessment = assessPermissives(level, pos, state);
            return Optional.of(EngineeringPortSnapshot.redstone(
                    port.get(), state.getValue(OUTPUT), assessment.quality()));
        }
        var observed = RedstoneObservationSupport.observe(level, pos, side);
        return Optional.of(EngineeringPortSnapshot.redstone(
                port.get(), observed.value(), observed.quality()));
    }

    @Override
    public OperationWorldResourceSnapshot operationResourceSnapshot(Level level, BlockPos pos, BlockState state) {
        int runtimeMask = failedMask(level, pos);
        PermissiveAssessment assessment = assessPermissives(level, pos, state);
        int mask = runtimeMask < 0 ? assessment.failedMask() : runtimeMask;
        boolean blocked = mask > 0;
        return new OperationWorldResourceSnapshot(
                "safety_interlock:" + pos.asLong(),
                Set.of("safety_permissive"),
                mask == 0,
                false,
                false,
                blocked,
                runtimeMask < 0 ? PortQuality.STALE : assessment.quality(),
                Map.of("failed_mask", (long) mask, "permit", mask == 0 ? 1L : 0L)
        );
    }

    @Override
    protected int computeOutput(Level level, BlockPos pos, BlockState state) {
        PermissiveAssessment assessment = assessPermissives(level, pos, state);
        int mask = assessment.failedMask();
        int permit = mask == 0 ? 1 : 0;

        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        int previousPermit = runtime[4];
        runtime[0] = mask;
        if (runtime[INITIALIZED] == 0) {
            // First evaluation establishes the physical state; it is not a transition event.
            runtime[4] = permit;
            runtime[INITIALIZED] = 1;
        } else if (permit != previousPermit) {
            if (runtime[3] < Integer.MAX_VALUE) runtime[3]++;
            if (permit == 0) {
                SystemEventTimeline.record(level, pos, SystemEventKind.INTERLOCK_TRIPPED, 3,
                        "INTERLOCK_TRIPPED", "Permit removed; failed permissive mask=" + mask);
            } else {
                SystemEventTimeline.record(level, pos, SystemEventKind.INTERLOCK_READY, 0,
                        "INTERLOCK_READY", "All permissives valid; permit restored");
            }
            runtime[4] = permit;
        }
        if (permit != 0) runtime[2]++;
        else runtime[1]++;
        return permit != 0 ? 15 : 0;
    }

    /** Returns -1 while diagnostics have not yet been evaluated after placement/reset. */
    public static int failedMask(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length < RUNTIME_SIZE ? -1 : runtime[0];
    }

    public static int transitionCount(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length < RUNTIME_SIZE ? 0 : Math.max(0, runtime[3]);
    }

    public static int blockedTicks(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length < RUNTIME_SIZE ? 0 : Math.max(0, runtime[1]);
    }

    public static String compactDiagnostics(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        if (runtime == null || runtime.length < RUNTIME_SIZE) return "INTERLOCK not evaluated";
        if (runtime[0] == 0) return "INTERLOCK PERMIT | all permissives valid | transitions=" + runtime[3];
        StringBuilder missing = new StringBuilder();
        if ((runtime[0] & 1) != 0) missing.append("A");
        if ((runtime[0] & 2) != 0) missing.append(missing.isEmpty() ? "B" : ",B");
        if ((runtime[0] & 4) != 0) missing.append(missing.isEmpty() ? "C" : ",C");
        return "INTERLOCK BLOCKED | failed=" + missing + " | blockedTicks=" + runtime[1];
    }

    public boolean resetDiagnostics(Level level, BlockPos pos) {
        if (!level.getBlockState(pos).is(this)) return false;
        RuntimeIntStore.remove(level, KEY, pos);
        return true;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server) server.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        updateOutput(level, pos, state, outputValue(level, pos, state));
        level.scheduleTick(pos, this, 2);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, KEY, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                resetDiagnostics(level, pos);
                player.displayClientMessage(Component.literal("Interlock diagnostics reset"), true);
            } else {
                player.displayClientMessage(Component.literal(compactDiagnostics(level, pos)), true);
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
