package dev.redstoneengineering.entity;

import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.robotics.RobotDockAssessment;
import dev.redstoneengineering.robotics.RobotDockSnapshot;
import dev.redstoneengineering.robotics.RobotLocalizationQuality;
import dev.redstoneengineering.robotics.RobotNavigationGraph;
import dev.redstoneengineering.robotics.RobotOperatingState;
import dev.redstoneengineering.robotics.RobotRoutePlanner;
import dev.redstoneengineering.robotics.RobotSafetyAssessment;
import dev.redstoneengineering.robotics.RobotStateMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** First world-running RSE autonomous mobile robot entity. */
public final class EngineeringMobileRobotEntity extends Entity {
    private static final double CRUISE_SPEED = 0.12D;
    private static final double ARRIVAL_DISTANCE = 0.45D;
    private static final double OBSTACLE_LOOKAHEAD = 0.80D;
    private static final double ROUTE_ENTRY_DISTANCE = 1.75D;
    private static final int MAX_PERSISTED_ROUTE_WAYPOINTS = 256;

    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(EngineeringMobileRobotEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> HAS_TARGET = SynchedEntityData.defineId(EngineeringMobileRobotEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> TARGET_X = SynchedEntityData.defineId(EngineeringMobileRobotEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TARGET_Y = SynchedEntityData.defineId(EngineeringMobileRobotEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TARGET_Z = SynchedEntityData.defineId(EngineeringMobileRobotEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SAFETY = SynchedEntityData.defineId(EngineeringMobileRobotEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> SAFETY_REASON = SynchedEntityData.defineId(EngineeringMobileRobotEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> ROUTE_REASON = SynchedEntityData.defineId(EngineeringMobileRobotEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DOCK_PHASE = SynchedEntityData.defineId(EngineeringMobileRobotEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DOCK_REASON = SynchedEntityData.defineId(EngineeringMobileRobotEntity.class, EntityDataSerializers.STRING);

    private RobotLocalizationQuality localization = RobotLocalizationQuality.VALID;
    private boolean driveReady = true;
    private boolean emergencyStopClear = true;
    private List<RobotNavigationGraph.Node> routeWaypoints = List.of();
    private int routeIndex;

    public EngineeringMobileRobotEntity(EntityType<? extends EngineeringMobileRobotEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STATE, RobotOperatingState.IDLE.ordinal());
        builder.define(HAS_TARGET, false);
        builder.define(TARGET_X, 0);
        builder.define(TARGET_Y, 0);
        builder.define(TARGET_Z, 0);
        builder.define(SAFETY, RobotSafetyAssessment.Verdict.SAFE_STOP.ordinal());
        builder.define(SAFETY_REASON, "NO_MISSION");
        builder.define(ROUTE_REASON, "NO_ROUTE");
        builder.define(DOCK_PHASE, RobotDockAssessment.Phase.APPROACH.ordinal());
        builder.define(DOCK_REASON, "NO_DOCK_EVIDENCE");
    }

    public RobotOperatingState robotState() {
        RobotOperatingState[] values = RobotOperatingState.values();
        return values[Math.max(0, Math.min(values.length - 1, entityData.get(STATE)))];
    }

    private void setRobotState(RobotOperatingState state) { entityData.set(STATE, state.ordinal()); }
    private void transition(RobotStateMachine.Event event) { setRobotState(RobotStateMachine.next(robotState(), event)); }
    public boolean hasMissionTarget() { return entityData.get(HAS_TARGET); }
    public BlockPos missionTarget() { return new BlockPos(entityData.get(TARGET_X), entityData.get(TARGET_Y), entityData.get(TARGET_Z)); }

    public RobotSafetyAssessment.Verdict safetyVerdict() {
        RobotSafetyAssessment.Verdict[] values = RobotSafetyAssessment.Verdict.values();
        return values[Math.max(0, Math.min(values.length - 1, entityData.get(SAFETY)))];
    }

    public String safetyReason() { return entityData.get(SAFETY_REASON); }
    public String routeReason() { return entityData.get(ROUTE_REASON); }
    public boolean hasExplicitRoute() { return !routeWaypoints.isEmpty(); }
    public int routeWaypointIndex() { return routeIndex; }
    public int routeWaypointCount() { return routeWaypoints.size(); }

    public RobotDockAssessment.Phase dockPhase() {
        RobotDockAssessment.Phase[] values = RobotDockAssessment.Phase.values();
        return values[Math.max(0, Math.min(values.length - 1, entityData.get(DOCK_PHASE)))];
    }

    public String dockReason() { return entityData.get(DOCK_REASON); }
    public String robotIdentity() { return getUUID().toString(); }

    public void assignTarget(BlockPos target) {
        if (level().isClientSide || target == null) return;
        routeWaypoints = List.of();
        routeIndex = 0;
        entityData.set(ROUTE_REASON, "DIRECT_TARGET");
        beginMissionTarget(target);
    }

    /**
     * Accepts only a route produced from explicit navigation topology. The
     * robot must already be localized near the declared source node; otherwise
     * accepting the route would fabricate an unmodeled segment to the graph.
     */
    public boolean assignNavigationRoute(RobotNavigationGraph graph, String sourceId, String targetId) {
        if (level().isClientSide) return false;
        RobotRoutePlanner.Route route = RobotRoutePlanner.plan(graph, sourceId, targetId);
        entityData.set(ROUTE_REASON, route.reason());
        if (!route.available() || graph == null) return false;

        RobotNavigationGraph.Node source = graph.node(sourceId).orElse(null);
        if (source == null || position().distanceTo(Vec3.atCenterOf(source.position())) > ROUTE_ENTRY_DISTANCE) {
            entityData.set(ROUTE_REASON, "SOURCE_NOT_LOCALIZED_TO_ROBOT");
            return false;
        }

        ArrayList<RobotNavigationGraph.Node> resolved = new ArrayList<>(route.nodeIds().size());
        for (String nodeId : route.nodeIds()) {
            RobotNavigationGraph.Node node = graph.node(nodeId).orElse(null);
            if (node == null) {
                entityData.set(ROUTE_REASON, "ROUTE_NODE_EVIDENCE_MISSING");
                return false;
            }
            resolved.add(node);
        }
        if (resolved.isEmpty() || resolved.size() > MAX_PERSISTED_ROUTE_WAYPOINTS) {
            entityData.set(ROUTE_REASON, resolved.isEmpty() ? "EMPTY_ROUTE" : "ROUTE_TOO_LONG");
            return false;
        }

        routeWaypoints = List.copyOf(resolved);
        routeIndex = 0;
        entityData.set(ROUTE_REASON, "FOLLOWING_EXPLICIT_ROUTE");
        beginMissionTarget(routeWaypoints.getFirst().position());
        return true;
    }

    /**
     * Begins the docking lifecycle only from explicit, valid dock evidence.
     * A permit means the robot may enter DOCKING; it never fabricates a
     * physical docked/occupied fact.
     */
    public boolean beginDocking(RobotDockSnapshot dock) {
        if (level().isClientSide || robotState() != RobotOperatingState.NAVIGATING) return false;
        RobotDockAssessment.Snapshot assessment = assessDock(dock, RobotDockAssessment.Phase.APPROACH);
        if (!assessment.permitted()) {
            applyDockHold(assessment);
            return false;
        }
        setDeltaMovement(Vec3.ZERO);
        transition(RobotStateMachine.Event.ARRIVE_DOCK);
        return robotState() == RobotOperatingState.DOCKING;
    }

    /**
     * Confirms the physical docking fact before entering LOADING. Alignment
     * permission alone is insufficient: occupancy must name this exact AMR.
     */
    public boolean confirmDocked(RobotDockSnapshot dock) {
        if (level().isClientSide || robotState() != RobotOperatingState.DOCKING) return false;
        RobotDockAssessment.Snapshot assessment = assessDock(dock, RobotDockAssessment.Phase.DOCK);
        if (!assessment.permitted()) {
            applyDockHold(assessment);
            return false;
        }
        if (dock == null || !dock.occupiedBy(robotIdentity())) {
            setDeltaMovement(Vec3.ZERO);
            entityData.set(DOCK_REASON, "ROBOT_NOT_CONFIRMED_DOCKED");
            return false;
        }
        transition(RobotStateMachine.Event.DOCKED);
        return robotState() == RobotOperatingState.LOADING;
    }

    /**
     * Marks loading complete only after the dock's authoritative transfer
     * evidence confirms this AMR is still occupying the dock and transfer is ready.
     */
    public boolean completeDockTransfer(RobotDockSnapshot dock) {
        if (level().isClientSide || robotState() != RobotOperatingState.LOADING) return false;
        RobotDockAssessment.Snapshot assessment = assessDock(dock, RobotDockAssessment.Phase.TRANSFER);
        if (!assessment.permitted()) {
            applyDockHold(assessment);
            return false;
        }
        transition(RobotStateMachine.Event.LOAD_COMPLETE);
        return robotState() == RobotOperatingState.TRANSPORTING;
    }

    private RobotDockAssessment.Snapshot assessDock(RobotDockSnapshot dock, RobotDockAssessment.Phase phase) {
        RobotDockAssessment.Snapshot assessment = RobotDockAssessment.inspect(dock, robotIdentity(), phase);
        entityData.set(DOCK_PHASE, assessment.phase().ordinal());
        entityData.set(DOCK_REASON, assessment.reason());
        return assessment;
    }

    private void applyDockHold(RobotDockAssessment.Snapshot assessment) {
        setDeltaMovement(Vec3.ZERO);
        switch (assessment.verdict()) {
            case FAULT -> transition(RobotStateMachine.Event.CRITICAL_FAULT);
            case SAFE_STOP -> transition(RobotStateMachine.Event.SAFETY_STOP_REQUESTED);
            case WAIT, PERMIT -> {
                // WAIT deliberately preserves NAVIGATING / DOCKING / LOADING so
                // the caller can retry against fresh dock evidence without
                // inventing an obstacle or a completed phase.
            }
        }
    }

    private void beginMissionTarget(BlockPos target) {
        setCurrentTarget(target);
        entityData.set(HAS_TARGET, true);
        setRobotState(RobotOperatingState.IDLE);
        transition(RobotStateMachine.Event.ASSIGN_MISSION);
        transition(RobotStateMachine.Event.BEGIN_PLANNING);
        transition(RobotStateMachine.Event.ROUTE_READY);
    }

    private void setCurrentTarget(BlockPos target) {
        entityData.set(TARGET_X, target.getX());
        entityData.set(TARGET_Y, target.getY());
        entityData.set(TARGET_Z, target.getZ());
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        if (!hasMissionTarget()) {
            stopMotion(RobotSafetyAssessment.Verdict.SAFE_STOP, "NO_MISSION");
            if (robotState() != RobotOperatingState.IDLE && robotState() != RobotOperatingState.COMPLETE) setRobotState(RobotOperatingState.IDLE);
            return;
        }

        Vec3 target = Vec3.atCenterOf(missionTarget());
        Vec3 horizontal = new Vec3(target.x - getX(), 0.0D, target.z - getZ());
        if (horizontal.length() <= ARRIVAL_DISTANCE) {
            setDeltaMovement(Vec3.ZERO);
            if (advanceRouteWaypoint()) {
                entityData.set(SAFETY, RobotSafetyAssessment.Verdict.PERMIT.ordinal());
                entityData.set(SAFETY_REASON, "MOTION_PERMIT");
                return;
            }
            completeMission();
            return;
        }

        Vec3 direction = horizontal.normalize();
        boolean obstacleClear = level().noCollision(this, getBoundingBox().move(direction.scale(OBSTACLE_LOOKAHEAD)));
        RobotSafetyAssessment.Snapshot safety = RobotSafetyAssessment.inspect(new RobotSafetyAssessment.Input(
                localization, PortQuality.VALID, obstacleClear, driveReady, emergencyStopClear));
        entityData.set(SAFETY, safety.verdict().ordinal());
        entityData.set(SAFETY_REASON, safety.primaryReason());

        if (!safety.motionPermit()) {
            setDeltaMovement(Vec3.ZERO);
            applySafetyHold(safety);
            return;
        }

        if (robotState() == RobotOperatingState.WAITING) transition(RobotStateMachine.Event.OBSTACLE_CLEARED);
        if (robotState() == RobotOperatingState.DEGRADED) transition(RobotStateMachine.Event.EVIDENCE_RECOVERED);
        if (robotState() == RobotOperatingState.SAFE_STOP) transition(RobotStateMachine.Event.SAFE_CONDITION_RESTORED);
        if (robotState() == RobotOperatingState.REPLANNING) transition(RobotStateMachine.Event.REPLAN_READY);
        if (robotState() != RobotOperatingState.NAVIGATING) setRobotState(RobotOperatingState.NAVIGATING);

        setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
        Vec3 command = direction.scale(CRUISE_SPEED);
        setDeltaMovement(command);
        move(MoverType.SELF, command);
    }

    private boolean advanceRouteWaypoint() {
        if (routeWaypoints.isEmpty() || routeIndex + 1 >= routeWaypoints.size()) return false;
        routeIndex++;
        setCurrentTarget(routeWaypoints.get(routeIndex).position());
        entityData.set(ROUTE_REASON, "FOLLOWING_EXPLICIT_ROUTE");
        return true;
    }

    private void completeMission() {
        entityData.set(HAS_TARGET, false);
        setRobotState(RobotOperatingState.COMPLETE);
        entityData.set(SAFETY, RobotSafetyAssessment.Verdict.PERMIT.ordinal());
        entityData.set(SAFETY_REASON, "MISSION_COMPLETE");
        if (!routeWaypoints.isEmpty()) entityData.set(ROUTE_REASON, "ROUTE_COMPLETE");
        routeWaypoints = List.of();
        routeIndex = 0;
    }

    private void applySafetyHold(RobotSafetyAssessment.Snapshot safety) {
        if (safety.verdict() == RobotSafetyAssessment.Verdict.FAULT) {
            transition(RobotStateMachine.Event.CRITICAL_FAULT);
            return;
        }

        if (safety.verdict() == RobotSafetyAssessment.Verdict.DEGRADED_HOLD) {
            transition(RobotStateMachine.Event.SENSOR_DEGRADED);
            return;
        }

        if ("LOCALIZATION_LOST".equals(safety.primaryReason())) {
            transition(RobotStateMachine.Event.LOCALIZATION_LOST);
            return;
        }

        if ("OBSTACLE_UNSAFE".equals(safety.primaryReason())) {
            if (robotState() == RobotOperatingState.NAVIGATING) {
                transition(RobotStateMachine.Event.OBSTACLE_DETECTED);
            } else if (robotState() != RobotOperatingState.WAITING) {
                transition(RobotStateMachine.Event.SAFETY_STOP_REQUESTED);
            }
            return;
        }

        transition(RobotStateMachine.Event.SAFETY_STOP_REQUESTED);
    }

    private void stopMotion(RobotSafetyAssessment.Verdict verdict, String reason) {
        setDeltaMovement(Vec3.ZERO);
        entityData.set(SAFETY, verdict.ordinal());
        entityData.set(SAFETY_REASON, reason);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!level().isClientSide && hand == InteractionHand.MAIN_HAND) {
            BlockPos target = player.blockPosition().relative(player.getDirection(), 8);
            assignTarget(target);
            player.displayClientMessage(Component.literal("AMR mission target: " + target.toShortString()), true);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("RobotState", robotState().ordinal());
        tag.putBoolean("HasTarget", hasMissionTarget());
        if (hasMissionTarget()) {
            BlockPos target = missionTarget();
            tag.putInt("TargetX", target.getX()); tag.putInt("TargetY", target.getY()); tag.putInt("TargetZ", target.getZ());
        }
        tag.putString("Localization", localization.name());
        tag.putBoolean("DriveReady", driveReady);
        tag.putBoolean("EmergencyStopClear", emergencyStopClear);
        tag.putString("RouteReason", routeReason());
        tag.putInt("RouteCount", routeWaypoints.size());
        tag.putInt("RouteIndex", routeIndex);
        for (int i = 0; i < routeWaypoints.size(); i++) {
            RobotNavigationGraph.Node waypoint = routeWaypoints.get(i);
            tag.putString("RouteNode" + i, waypoint.id());
            tag.putInt("RouteX" + i, waypoint.position().getX());
            tag.putInt("RouteY" + i, waypoint.position().getY());
            tag.putInt("RouteZ" + i, waypoint.position().getZ());
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        int ordinal = Math.max(0, Math.min(RobotOperatingState.values().length - 1, tag.getInt("RobotState")));
        entityData.set(STATE, ordinal);
        entityData.set(HAS_TARGET, tag.getBoolean("HasTarget"));
        entityData.set(TARGET_X, tag.getInt("TargetX")); entityData.set(TARGET_Y, tag.getInt("TargetY")); entityData.set(TARGET_Z, tag.getInt("TargetZ"));
        try { localization = RobotLocalizationQuality.valueOf(tag.getString("Localization")); }
        catch (IllegalArgumentException ignored) { localization = RobotLocalizationQuality.LOST; }
        driveReady = tag.getBoolean("DriveReady");
        emergencyStopClear = tag.getBoolean("EmergencyStopClear");
        String persistedRouteReason = tag.getString("RouteReason");
        entityData.set(ROUTE_REASON, persistedRouteReason.isBlank() ? "NO_ROUTE" : persistedRouteReason);
        restoreRoute(tag);
    }

    private void restoreRoute(CompoundTag tag) {
        int count = tag.getInt("RouteCount");
        if (count <= 0) {
            routeWaypoints = List.of();
            routeIndex = 0;
            return;
        }
        if (count > MAX_PERSISTED_ROUTE_WAYPOINTS) {
            invalidatePersistedRoute("PERSISTED_ROUTE_TOO_LONG");
            return;
        }

        ArrayList<RobotNavigationGraph.Node> restored = new ArrayList<>(count);
        try {
            for (int i = 0; i < count; i++) {
                String nodeId = tag.getString("RouteNode" + i);
                BlockPos position = new BlockPos(tag.getInt("RouteX" + i), tag.getInt("RouteY" + i), tag.getInt("RouteZ" + i));
                restored.add(new RobotNavigationGraph.Node(nodeId, position));
            }
        } catch (IllegalArgumentException ignored) {
            invalidatePersistedRoute("PERSISTED_ROUTE_INVALID");
            return;
        }

        routeWaypoints = List.copyOf(restored);
        routeIndex = Math.max(0, Math.min(routeWaypoints.size() - 1, tag.getInt("RouteIndex")));
        setCurrentTarget(routeWaypoints.get(routeIndex).position());
        entityData.set(HAS_TARGET, true);
    }

    private void invalidatePersistedRoute(String reason) {
        routeWaypoints = List.of();
        routeIndex = 0;
        entityData.set(HAS_TARGET, false);
        entityData.set(ROUTE_REASON, reason);
        setRobotState(RobotOperatingState.SAFE_STOP);
    }

    @Override public boolean isPickable() { return true; }
    @Override public boolean isPushable() { return false; }
}
