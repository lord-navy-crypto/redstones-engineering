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
import dev.redstoneengineering.diagnostics.redstone.VanillaRedstoneDiagnosticsReport;
import dev.redstoneengineering.diagnostics.redstone.VanillaRedstoneEngineeringProfile;
import dev.redstoneengineering.diagnostics.topology.EngineeringTopologyView;
import dev.redstoneengineering.diagnostics.topology.TopologyDiagnosticsReport;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/**
 * Observer-oriented topology debugger. RSE devices use formal EngineeringPort topology;
 * vanilla redstone targets use the bounded Vanilla Redstone Engineering profiler.
 * The diagnostic layer never rewrites vanilla redstone state or runs a replacement solver.
 */
public class TopologyDebuggerBlock extends PassiveDirectionalSignalBlock {
    private static final String KEY = "topology_debugger";
    // Engineering mode: [ports, connected, open, mismatch, unloaded, faults, scans, previousIssue]
    // Vanilla mode:     [nodes, dust, poweredDust, timing, observers, qcRisk, scans, previousIssue]
    private static final int RUNTIME_SIZE = 8;

    public TopologyDebuggerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<TopologyDebuggerBlock> codec() {
        return EngineeringSystemsModule.TOPOLOGY_DEBUGGER_CODEC.value();
    }

    @Override
    protected boolean isEngineeringPort(BlockState state, Direction side) {
        return side == outputSide(state);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(new EngineeringPort("TOPOLOGY ALARM OUT", outputSide(state), EngineeringDomain.REDSTONE,
                PortKind.MEASUREMENT, PortDirection.OUTPUT, true, "topology_alarm"));
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        int value = state.getValue(OUTPUT);
        return Optional.of(EngineeringPortSnapshot.redstone(port.get(), value,
                value > 0 ? PortQuality.FAULT : PortQuality.VALID));
    }

    private static BlockPos targetPos(BlockPos pos, BlockState debuggerState) {
        Direction back = debuggerState.getValue(FACING).getOpposite();
        return pos.relative(back);
    }

    public static boolean targetsVanillaRedstone(Level level, BlockPos pos, BlockState debuggerState) {
        return VanillaRedstoneEngineeringProfile.isVanillaRedstoneTarget(level.getBlockState(targetPos(pos, debuggerState)));
    }

    public static TopologyDiagnosticsReport inspectTarget(Level level, BlockPos pos, BlockState debuggerState) {
        BlockPos targetPos = targetPos(pos, debuggerState);
        BlockState targetState = level.getBlockState(targetPos);
        return TopologyDiagnosticsReport.from(EngineeringTopologyView.inspect(level, targetPos, targetState));
    }

    public static VanillaRedstoneDiagnosticsReport inspectVanillaTarget(Level level, BlockPos pos, BlockState debuggerState) {
        return VanillaRedstoneEngineeringProfile.inspect(level, targetPos(pos, debuggerState));
    }

    @Override
    protected int computeOutput(Level level, BlockPos pos, BlockState state) {
        if (targetsVanillaRedstone(level, pos, state)) return computeVanillaOutput(level, pos, state);

        TopologyDiagnosticsReport report = inspectTarget(level, pos, state);
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        boolean issue = report.hasIssue();
        boolean previousIssue = runtime[7] != 0;
        runtime[0] = report.portCount();
        runtime[1] = report.connectedCount();
        runtime[2] = report.openCount();
        runtime[3] = report.mismatchCount();
        runtime[4] = report.unloadedCount();
        runtime[5] = report.faultSampleCount();
        runtime[6]++;
        runtime[7] = issue ? 1 : 0;
        recordTopologyTransition(level, pos, issue, previousIssue, report.summary());
        return issue ? 15 : 0;
    }

    private static int computeVanillaOutput(Level level, BlockPos pos, BlockState state) {
        VanillaRedstoneDiagnosticsReport report = inspectVanillaTarget(level, pos, state);
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        boolean issue = report.hasTopologyIssue();
        boolean previousIssue = runtime[7] != 0;
        runtime[0] = report.nodeCount();
        runtime[1] = report.dustCount();
        runtime[2] = report.poweredDustCount();
        runtime[3] = report.timingComponentCount();
        runtime[4] = report.observerCount();
        runtime[5] = report.possibleQcDependencyCount();
        runtime[6]++;
        runtime[7] = issue ? 1 : 0;
        recordTopologyTransition(level, pos, issue, previousIssue, report.summary());
        // Phase one treats QC/fan-out/density as advisories, not faults. Only an incomplete
        // observation (unloaded boundary or traversal cap) raises the existing topology alarm.
        return issue ? 15 : 0;
    }

    private static void recordTopologyTransition(Level level, BlockPos pos, boolean issue, boolean previousIssue, String summary) {
        if (issue == previousIssue) return;
        if (issue) {
            SystemEventTimeline.record(level, pos, SystemEventKind.TOPOLOGY_ISSUE, 2,
                    "TOPOLOGY_ISSUE", summary);
        } else {
            SystemEventTimeline.record(level, pos, SystemEventKind.TOPOLOGY_CLEAR, 0,
                    "TOPOLOGY_CLEAR", summary);
        }
    }

    public static int scanCount(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length < RUNTIME_SIZE ? 0 : runtime[6];
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
        if (!level.isClientSide) {
            String summary = targetsVanillaRedstone(level, pos, state)
                    ? inspectVanillaTarget(level, pos, state).summary()
                    : inspectTarget(level, pos, state).summary();
            player.displayClientMessage(Component.literal(summary), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
