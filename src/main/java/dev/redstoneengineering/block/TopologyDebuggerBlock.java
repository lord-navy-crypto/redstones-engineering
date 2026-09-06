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
 * Observer-only topology debugger. It inspects the block behind itself and emits a redstone alarm
 * on FRONT when the target has dangling/mismatched/unloaded/faulted engineering interfaces.
 */
public class TopologyDebuggerBlock extends PassiveDirectionalSignalBlock {
    private static final String KEY = "topology_debugger";
    // [ports, connected, open, mismatch, unloaded, faults, scans, previousIssue]
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

    public static TopologyDiagnosticsReport inspectTarget(Level level, BlockPos pos, BlockState debuggerState) {
        Direction back = debuggerState.getValue(FACING).getOpposite();
        BlockPos targetPos = pos.relative(back);
        BlockState targetState = level.getBlockState(targetPos);
        return TopologyDiagnosticsReport.from(EngineeringTopologyView.inspect(level, targetPos, targetState));
    }

    @Override
    protected int computeOutput(Level level, BlockPos pos, BlockState state) {
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
        if (issue != previousIssue) {
            if (issue) {
                SystemEventTimeline.record(level, pos, SystemEventKind.TOPOLOGY_ISSUE, 2,
                        "TOPOLOGY_ISSUE", report.summary());
            } else {
                SystemEventTimeline.record(level, pos, SystemEventKind.TOPOLOGY_CLEAR, 0,
                        "TOPOLOGY_CLEAR", report.summary());
            }
        }
        return issue ? 15 : 0;
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
        if (!level.isClientSide) player.displayClientMessage(Component.literal(inspectTarget(level, pos, state).summary()), true);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
