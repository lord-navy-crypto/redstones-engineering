package dev.redstoneengineering.item;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.diagnostics.RseDiagnosticSeverity;
import dev.redstoneengineering.diagnostics.RseDiagnostics;
import dev.redstoneengineering.diagnostics.topology.EngineeringTopologyView;
import dev.redstoneengineering.diagnostics.topology.TopologyFaceSnapshot;
import dev.redstoneengineering.diagnostics.topology.TopologyVisualizationSnapshot;
import dev.redstoneengineering.entity.EngineeringMobileRobotEntity;
import dev.redstoneengineering.robotics.RobotCommissioningTrialComparison;
import dev.redstoneengineering.robotics.RobotCommissioningTrialRecord;
import dev.redstoneengineering.robotics.RobotCommissioningTrialStore;
import dev.redstoneengineering.robotics.RobotMissionTelemetrySnapshot;
import dev.redstoneengineering.ui.menu.DiagnosticTabletMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Hand-held observer-only diagnostics tablet.
 *
 * <p>Right-click a block to retain a bounded topology/evidence snapshot and review it immediately.
 * Right-click an RSE AMR to retain observer-only robot evidence; Shift+right-click a finished AMR
 * mission run captures an explicit baseline/candidate commissioning trial. Right-click air reopens
 * retained history. The tablet never drives a network or robot, changes simulation state, schedules
 * ticks, or runs a second solver.</p>
 */
public final class DiagnosticTabletItem extends Item {
    public static final int MAX_HISTORY = 8;
    private static final String COUNT = "rse_tablet_count";
    private static final String SNAPSHOT_PREFIX = "rse_tablet_snapshot_";

    public DiagnosticTabletItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal("Engineering Diagnostic Tablet");
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            BlockPos pos = context.getClickedPos();
            String snapshot = capture(level, pos, context.getClickedFace());
            ItemStack tablet = context.getItemInHand();
            pushSnapshot(tablet, snapshot);
            String blockName = level.getBlockState(pos).getBlock().getName().getString();
            serverPlayer.displayClientMessage(Component.literal("Tablet snapshot captured: " + blockName), true);
            RseDiagnostics.record(RseDiagnosticSeverity.INFO, "DiagnosticTablet", "Captured observer snapshot at " + pos.toShortString(), null);
            openTablet(serverPlayer, tablet);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static void captureRobot(
            ServerPlayer player,
            ItemStack tablet,
            EngineeringMobileRobotEntity robot,
            boolean trialMode
    ) {
        if (player == null || tablet == null || robot == null || robot.level().isClientSide) return;

        if (!trialMode) {
            String snapshot = captureRobotObserver(robot);
            pushSnapshot(tablet, snapshot);
            player.displayClientMessage(Component.literal(
                    "Tablet AMR snapshot captured: " + robot.robotIdentity()), true);
            RseDiagnostics.record(
                    RseDiagnosticSeverity.INFO,
                    "DiagnosticTablet",
                    "Captured AMR observer snapshot for " + robot.robotIdentity(),
                    null
            );
            openTablet(player, tablet);
            return;
        }

        RobotMissionTelemetrySnapshot telemetry = robot.missionTelemetrySnapshot();
        if (!telemetry.finished()) {
            player.displayClientMessage(Component.literal(
                    "AMR trial capture rejected: finish the mission run (COMPLETE or FAULT) first."), true);
            return;
        }

        RobotCommissioningTrialRecord record;
        String role;
        RobotCommissioningTrialComparison comparison = null;

        boolean startNewBaseline = RobotCommissioningTrialStore.baseline(robot.level(), robot.getUUID()).isEmpty()
                || RobotCommissioningTrialStore.candidate(robot.level(), robot.getUUID()).isPresent();
        if (startNewBaseline) {
            record = RobotCommissioningTrialStore.captureBaseline(
                    robot.level(), robot.getUUID(), robot.level().getGameTime(), telemetry);
            if (record == null) {
                player.displayClientMessage(Component.literal("AMR baseline capture rejected: evidence unavailable."), true);
                return;
            }
            role = "BASELINE";
        } else {
            record = RobotCommissioningTrialStore.captureCandidate(
                    robot.level(), robot.getUUID(), robot.level().getGameTime(), telemetry).orElse(null);
            if (record == null) {
                player.displayClientMessage(Component.literal("AMR candidate capture rejected: baseline missing."), true);
                return;
            }
            role = "CANDIDATE";
            comparison = RobotCommissioningTrialStore.comparison(robot.level(), robot.getUUID()).orElse(null);
        }

        String snapshot = captureRobotTrial(robot, record, role, comparison);
        pushSnapshot(tablet, snapshot);
        String message = "AMR trial " + role + " captured: #" + record.sequence();
        if (comparison != null) message += " • " + comparison.trend();
        player.displayClientMessage(Component.literal(message), true);
        RseDiagnostics.record(
                RseDiagnosticSeverity.INFO,
                "DiagnosticTablet",
                message + " for " + robot.robotIdentity(),
                null
        );
        openTablet(player, tablet);
    }

    private static String captureRobotObserver(EngineeringMobileRobotEntity robot) {
        RobotMissionTelemetrySnapshot telemetry = robot.missionTelemetrySnapshot();
        StringBuilder out = new StringBuilder(1800);
        out.append("Engineering Mobile Robot\n");
        out.append("TYPE: AMR OBSERVER\n");
        out.append("ENTITY: ").append(robot.robotIdentity()).append('\n');
        out.append("POS: ").append(robot.blockPosition().getX()).append(", ")
                .append(robot.blockPosition().getY()).append(", ")
                .append(robot.blockPosition().getZ()).append('\n');
        out.append("CONTEXT: dimension=").append(robot.level().dimension().location())
                .append(" • tick=").append(robot.level().getGameTime()).append('\n');
        out.append("STATE: ").append(robot.robotState()).append('\n');
        out.append("LOCALIZATION: ").append(robot.localizationQuality()).append('\n');
        out.append("SAFETY: ").append(robot.safetyVerdict()).append(" • ").append(robot.safetyReason()).append('\n');
        out.append("ROUTE: ").append(robot.routeReason())
                .append(" • waypoint=").append(robot.routeWaypointIndex())
                .append('/').append(robot.routeWaypointCount()).append('\n');
        out.append("DOCK: ").append(robot.dockPhase()).append(" • ").append(robot.dockReason()).append('\n');
        out.append("MATERIAL: ").append(robot.materialReason()).append('\n');
        out.append("MISSION TELEMETRY: ").append(telemetry.compact()).append('\n');
        out.append("STATUS: ").append(amrStatus(robot, telemetry)).append('\n');
        out.append("MODE: observer-only; no motion, route, dock, transfer, or safety mutation");
        return out.substring(0, Math.min(4000, out.length()));
    }

    private static String captureRobotTrial(
            EngineeringMobileRobotEntity robot,
            RobotCommissioningTrialRecord record,
            String role,
            RobotCommissioningTrialComparison comparison
    ) {
        RobotMissionTelemetrySnapshot telemetry = record.telemetry();
        StringBuilder out = new StringBuilder(2200);
        out.append("Engineering Mobile Robot Commissioning Trial\n");
        out.append("TYPE: AMR TRIAL\n");
        out.append("ENTITY: ").append(robot.robotIdentity()).append('\n');
        out.append("TRIAL ROLE: ").append(role).append('\n');
        out.append("TRIAL SEQUENCE: #").append(record.sequence()).append('\n');
        out.append("POS: ").append(robot.blockPosition().getX()).append(", ")
                .append(robot.blockPosition().getY()).append(", ")
                .append(robot.blockPosition().getZ()).append('\n');
        out.append("CONTEXT: dimension=").append(robot.level().dimension().location())
                .append(" • tick=").append(record.captureTick()).append('\n');
        out.append("PATH: ").append(posText(telemetry.startPos()))
                .append(" → ").append(posText(telemetry.finalTarget())).append('\n');
        out.append("RESULT: ").append(telemetry.completed() ? "COMPLETE" : "FAILED")
                .append(" • terminal=").append(telemetry.terminalState()).append('\n');
        out.append("DURATION: ").append(telemetry.durationTicks()).append("t")
                .append(" • motion=").append(telemetry.motionTicks()).append("t")
                .append(" • stationary=").append(telemetry.stationaryTicks()).append("t\n");
        out.append("HOLDS: obstacle=").append(telemetry.obstacleWaitEvents())
                .append(" degraded=").append(telemetry.degradedEntries())
                .append(" safeStop=").append(telemetry.safeStopEvents())
                .append(" fault=").append(telemetry.faultEvents()).append('\n');
        out.append("ADMISSION: routeReject=").append(telemetry.routeRejectEvents())
                .append(" dockHold=").append(telemetry.dockHoldEvents())
                .append(" materialHold=").append(telemetry.materialHoldEvents()).append('\n');
        out.append("LOCALIZATION: worst=").append(telemetry.worstLocalization()).append('\n');
        out.append("ROUTE: maxWaypoints=").append(telemetry.maxRouteWaypoints()).append('\n');
        if (comparison != null) {
            out.append("TRIAL COMPARE: ").append(comparison.compact()).append('\n');
        } else {
            out.append("TRIAL COMPARE: BASELINE READY • run the same start→target mission again, then Shift+tablet\n");
        }
        out.append("STATUS: AMR TRIAL ").append(role)
                .append(comparison == null ? "" : " • " + comparison.trend()).append('\n');
        out.append("MODE: frozen evidence only; trial capture never commands the robot");
        return out.substring(0, Math.min(4000, out.length()));
    }

    private static String amrStatus(
            EngineeringMobileRobotEntity robot,
            RobotMissionTelemetrySnapshot telemetry
    ) {
        if (telemetry.finished()) {
            return telemetry.completed() ? "MISSION COMPLETE" : "MISSION FAILED • " + robot.robotState();
        }
        return robot.robotState() + " • SAFETY=" + robot.safetyVerdict();
    }

    private static String posText(BlockPos pos) {
        return pos == null ? "UNKNOWN" : pos.toShortString();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            openTablet(serverPlayer, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static void openTablet(ServerPlayer serverPlayer, ItemStack stack) {
        List<String> history = history(stack);
        serverPlayer.openMenu(
                new SimpleMenuProvider(
                        (containerId, inventory, ignored) -> new DiagnosticTabletMenu(containerId, inventory, history),
                        Component.literal("Engineering Diagnostic Tablet")
                ),
                buffer -> {
                    buffer.writeVarInt(history.size());
                    for (String entry : history) buffer.writeUtf(entry, 4096);
                }
        );
    }

    public static List<String> history(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) return List.of();
        CompoundTag tag = customData.copyTag();
        int count = Math.max(0, Math.min(MAX_HISTORY, tag.getInt(COUNT)));
        List<String> history = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String value = tag.getString(SNAPSHOT_PREFIX + i);
            if (!value.isBlank()) history.add(value);
        }
        return List.copyOf(history);
    }

    private static void pushSnapshot(ItemStack stack, String snapshot) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            int count = Math.max(0, Math.min(MAX_HISTORY, tag.getInt(COUNT)));
            int newCount = Math.min(MAX_HISTORY, count + 1);
            for (int i = newCount - 1; i >= 1; i--) {
                String previous = tag.getString(SNAPSHOT_PREFIX + (i - 1));
                if (!previous.isBlank()) tag.putString(SNAPSHOT_PREFIX + i, previous);
            }
            tag.putString(SNAPSHOT_PREFIX + 0, snapshot);
            tag.putInt(COUNT, newCount);
        });
    }

    private static String capture(Level level, BlockPos pos, Direction clickedFace) {
        BlockState state = level.getBlockState(pos);
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        TopologyVisualizationSnapshot topology = EngineeringTopologyView.inspect(level, pos, state);
        StringBuilder out = new StringBuilder(1536);
        out.append(state.getBlock().getName().getString()).append('\n');
        out.append("ID: ").append(id).append('\n');
        out.append("POS: ").append(pos.getX()).append(", ").append(pos.getY()).append(", ").append(pos.getZ()).append('\n');
        out.append("TARGET FACE: ").append(clickedFace.getName().toUpperCase(Locale.ROOT)).append('\n');
        out.append("CONTEXT: dimension=").append(level.dimension().location())
                .append(" • tick=").append(level.getGameTime()).append('\n');
        out.append("SOURCE: ").append(RedstoneEngineering.MOD_ID.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace()) ? "RSE" : "VANILLA / OTHER").append('\n');
        out.append("REDSTONE IN: best-neighbor=").append(level.getBestNeighborSignal(pos)).append("/15 • powered=").append(level.hasNeighborSignal(pos)).append('\n');
        appendState(out, state);
        out.append("TOPOLOGY: ").append(topology.summary()).append('\n');
        out.append("STATUS: ").append(topology.issueCount() == 0 ? "NOMINAL TOPOLOGY" : "CHECK TOPOLOGY • issues=" + topology.issueCount()).append('\n');
        if (topology.faces().isEmpty()) {
            out.append("PORTS: no EngineeringPort contract; BlockState/redstone evidence only\n");
        } else {
            for (TopologyFaceSnapshot face : topology.faces()) {
                if (!face.hasPort()) continue;
                out.append(face.compact());
                EngineeringPortSnapshot observation = face.observation();
                if (observation != null) {
                    out.append(" value=")
                            .append(String.format(Locale.ROOT, "%.2f", observation.value()))
                            .append("/")
                            .append(String.format(Locale.ROOT, "%.2f", observation.maximum()))
                            .append(" q=").append(observation.quality());
                }
                if (!face.detail().isBlank()) out.append(" • ").append(face.detail());
                out.append('\n');
            }
        }
        out.append("MODE: observer-only; no network recompute or device-state mutation");
        return out.substring(0, Math.min(4000, out.length()));
    }

    private static void appendState(StringBuilder out, BlockState state) {
        if (state.getValues().isEmpty()) {
            out.append("STATE: no BlockState properties\n");
            return;
        }
        out.append("STATE: ");
        boolean first = true;
        List<Property<?>> properties = state.getProperties().stream()
                .sorted(Comparator.comparing(Property::getName))
                .toList();
        for (Property<?> property : properties) {
            if (!first) out.append(" • ");
            first = false;
            out.append(property.getName()).append('=').append(state.getValue(property));
        }
        out.append('\n');
    }
}
