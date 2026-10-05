package dev.redstoneengineering.entity;

import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.item.DiagnosticTabletItem;
import dev.redstoneengineering.robotics.RobotDockAssessment;
import dev.redstoneengineering.robotics.RobotDockSnapshot;
import dev.redstoneengineering.robotics.RobotLocalizationQuality;
import dev.redstoneengineering.robotics.RobotMaterialFlowRuntime;
import dev.redstoneengineering.robotics.RobotMaterialTransferSnapshot;
import dev.redstoneengineering.robotics.RobotMaterialUnloadRuntime;
import dev.redstoneengineering.robotics.RobotMission;
import dev.redstoneengineering.robotics.RobotMissionTelemetrySnapshot;
import dev.redstoneengineering.robotics.RobotNavigationGraph;
import dev.redstoneengineering.robotics.RobotOperatingState;
import dev.redstoneengineering.robotics.RobotPayloadSnapshot;
import dev.redstoneengineering.robotics.RobotRoutePlanner;
import dev.redstoneengineering.robotics.RobotSafetyAssessment;
import dev.redstoneengineering.robotics.RobotStateMachine;
import dev.redstoneengineering.robotics.RobotTransportRouteRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
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
    private static final double DOCK_ENTRY_DISTANCE = 1.75D;
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
    private static final EntityDataAccessor<String> MATERIAL_REASON = SynchedEntityData.defineId(EngineeringMobileRobotEntity.class, EntityDataSerializers.STRING);

    private RobotLocalizationQuality localization = RobotLocalizationQuality.VALID;
    private boolean driveReady = true;
    private boolean emergencyStopClear = true;
    private List<RobotNavigationGraph.Node> routeWaypoints = List.of();
    private int routeIndex;

    // Observer-only mission commissioning telemetry. These fields never participate in motion,
    // planning, docking, material-flow, or safety decisions.
    private long missionStartTick = -1L;
    private long missionEndTick = -1L;
    private BlockPos missionStartPos;
    private BlockPos missionFinalTarget;
    private boolean missionFinished;
    private boolean missionCompleted;
    private int missionObstacleWaitEvents;
    private int missionDegradedEntries;
    private int missionSafeStopEvents;
    private int missionFaultEvents;
    private int missionRouteRejectEvents;
    private int missionDockHoldEvents;
    private int missionMaterialHoldEvents;
    private int missionMaxRouteWaypoints;
    private int missionMotionTicks;
    private RobotLocalizationQuality missionWorstLocalization = RobotLocalizationQuality.VALID;

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
        builder.define(MATERIAL_REASON, "NO_TRANSFER_EVIDENCE");
    }

    public RobotOperatingState robotState() {
        RobotOperatingState[] values = RobotOperatingState.values();
        return values[Math.max(0, Math.min(values.length - 1, entityData.get(STATE)))];
    }

    private void setRobotState(RobotOperatingState state) { entityData.set(STATE, state.ordinal()); }

    private void transition(RobotStateMachine.Event event) {
        RobotOperatingState before = robotState();
        RobotOperatingState after = RobotStateMachine.next(before, event);
        setRobotState(after);
        if (after == before || !missionTelemetryActive()) return;

        switch (event) {
            case OBSTACLE_DETECTED -> missionObstacleWaitEvents++;
            case SENSOR_DEGRADED -> missionDegradedEntries++;
            case LOCALIZATION_LOST, SAFETY_STOP_REQUESTED -> missionSafeStopEvents++;
            case CRITICAL_FAULT -> {
                missionFaultEvents++;
                finishMissionTelemetry(false);
            }
            default -> { }
        }
    }
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
    public String materialReason() { return entityData.get(MATERIAL_REASON); }
    public String robotIdentity() { return getUUID().toString(); }
    public RobotLocalizationQuality localizationQuality() { return localization; }

    public RobotMissionTelemetrySnapshot missionTelemetrySnapshot() {
        boolean started = missionStartTick >= 0L;
        long end = missionEndTick >= 0L ? missionEndTick : (started ? level().getGameTime() : -1L);
        long duration = started ? Math.max(0L, end - missionStartTick) : 0L;
        return new RobotMissionTelemetrySnapshot(
                started,
                missionFinished,
                missionCompleted,
                missionStartTick,
                missionEndTick,
                duration,
                missionStartPos,
                missionFinalTarget,
                robotState(),
                missionWorstLocalization,
                missionObstacleWaitEvents,
                missionDegradedEntries,
                missionSafeStopEvents,
                missionFaultEvents,
                missionRouteRejectEvents,
                missionDockHoldEvents,
                missionMaterialHoldEvents,
                missionMaxRouteWaypoints,
                missionMotionTicks
        );
    }

    public void assignTarget(BlockPos target) {
        if (level().isClientSide || target == null) return;
        routeWaypoints = List.of();
        routeIndex = 0;
        entityData.set(ROUTE_REASON, "DIRECT_TARGET");
        beginMissionTarget(target, target);
    }

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
        beginMissionTarget(routeWaypoints.getFirst().position(), routeWaypoints.getLast().position());
        return true;
    }

    public boolean assignTransportRoute(
            RobotMission mission,
            RobotPayloadSnapshot payload,
            RobotNavigationGraph graph,
            String sourceId,
            String targetId
    ) {
        if (level().isClientSide) return false;
        RobotTransportRouteRuntime.Decision decision = RobotTransportRouteRuntime.evaluate(
                robotState(), mission, payload, graph, sourceId, targetId, robotIdentity());
        entityData.set(ROUTE_REASON, decision.reason());
        if (!decision.permitted() || graph == null) {
            recordRouteReject();
            applyTransportRouteHold(decision);
            return false;
        }

        RobotNavigationGraph.Node source = graph.node(sourceId).orElse(null);
        if (source == null || position().distanceTo(Vec3.atCenterOf(source.position())) > ROUTE_ENTRY_DISTANCE) {
            recordRouteReject();
            setDeltaMovement(Vec3.ZERO);
            entityData.set(ROUTE_REASON, "TRANSPORT_SOURCE_NOT_LOCALIZED_TO_ROBOT");
            transition(RobotStateMachine.Event.SAFETY_STOP_REQUESTED);
            return false;
        }
        if (decision.waypoints().size() > MAX_PERSISTED_ROUTE_WAYPOINTS) {
            recordRouteReject();
            setDeltaMovement(Vec3.ZERO);
            entityData.set(ROUTE_REASON, "TRANSPORT_ROUTE_TOO_LONG");
            transition(RobotStateMachine.Event.SAFETY_STOP_REQUESTED);
            return false;
        }

        routeWaypoints = decision.waypoints();
        routeIndex = 0;
        missionMaxRouteWaypoints = Math.max(missionMaxRouteWaypoints, routeWaypoints.size());
        if (!routeWaypoints.isEmpty()) missionFinalTarget = routeWaypoints.getLast().position().immutable();
        setCurrentTarget(routeWaypoints.getFirst().position());
        entityData.set(HAS_TARGET, true);
        entityData.set(ROUTE_REASON, "FOLLOWING_TRANSPORT_ROUTE");
        return robotState() == RobotOperatingState.TRANSPORTING;
    }

    public boolean beginDocking(RobotDockSnapshot dock) {
        if (level().isClientSide || robotState() != RobotOperatingState.NAVIGATING) return false;
        RobotDockAssessment.Snapshot assessment = assessDock(dock, RobotDockAssessment.Phase.APPROACH);
        if (!assessment.permitted()) {
            applyDockHold(assessment);
            return false;
        }
        if (!isAtDock(dock)) {
            setDeltaMovement(Vec3.ZERO);
            entityData.set(DOCK_REASON, "DOCK_POSITION_MISMATCH");
            transition(RobotStateMachine.Event.SAFETY_STOP_REQUESTED);
            return false;
        }

        setDeltaMovement(Vec3.ZERO);
        entityData.set(HAS_TARGET, false);
        routeWaypoints = List.of();
        routeIndex = 0;
        entityData.set(ROUTE_REASON, "DOCK_APPROACH_COMPLETE");
        transition(RobotStateMachine.Event.ARRIVE_DOCK);
        return robotState() == RobotOperatingState.DOCKING;
    }

    public boolean confirmDocked(RobotDockSnapshot dock) {
        if (level().isClientSide || robotState() != RobotOperatingState.DOCKING) return false;
        RobotDockAssessment.Snapshot assessment = assessDock(dock, RobotDockAssessment.Phase.DOCK);
        if (!assessment.permitted()) {
            applyDockHold(assessment);
            return false;
        }
        if (!isAtDock(dock)) {
            setDeltaMovement(Vec3.ZERO);
            entityData.set(DOCK_REASON, "DOCK_POSITION_MISMATCH");
            transition(RobotStateMachine.Event.SAFETY_STOP_REQUESTED);
            return false;
        }
        if (!dock.occupiedBy(robotIdentity())) {
            setDeltaMovement(Vec3.ZERO);
            entityData.set(DOCK_REASON, "ROBOT_NOT_CONFIRMED_DOCKED");
            return false;
        }
        transition(RobotStateMachine.Event.DOCKED);
        return robotState() == RobotOperatingState.LOADING;
    }

    public boolean completeLoading(RobotDockSnapshot dock, RobotMaterialTransferSnapshot transfer) {
        if (level().isClientSide) return false;
        entityData.set(DOCK_PHASE, RobotDockAssessment.Phase.TRANSFER.ordinal());
        RobotMaterialFlowRuntime.Decision decision = RobotMaterialFlowRuntime.evaluate(
                robotState(), dock, transfer, robotIdentity());
        entityData.set(DOCK_REASON, decision.dockReason());
        entityData.set(MATERIAL_REASON, decision.materialReason());
        setDeltaMovement(Vec3.ZERO);
        setRobotState(decision.nextState());
        if (decision.advancesToTransport()) {
            entityData.set(ROUTE_REASON, "TRANSPORT_ROUTE_REQUIRED");
            return true;
        }
        if (missionTelemetryActive()) missionMaterialHoldEvents++;
        return false;
    }

    public boolean completeUnloading(
            RobotMission mission,
            RobotPayloadSnapshot payload,
            RobotDockSnapshot dock,
            RobotMaterialTransferSnapshot transfer
    ) {
        if (level().isClientSide) return false;
        entityData.set(DOCK_PHASE, RobotDockAssessment.Phase.TRANSFER.ordinal());
        RobotMaterialUnloadRuntime.Decision decision = RobotMaterialUnloadRuntime.evaluate(
                robotState(), mission, payload, dock, transfer, robotIdentity());
        entityData.set(DOCK_REASON, decision.dockReason());
        entityData.set(MATERIAL_REASON, decision.materialReason());
        setDeltaMovement(Vec3.ZERO);
        setRobotState(decision.nextState());
        if (decision.unloadComplete()) {
            entityData.set(ROUTE_REASON, "DELIVERY_COMPLETE");
            entityData.set(SAFETY, RobotSafetyAssessment.Verdict.PERMIT.ordinal());
            entityData.set(SAFETY_REASON, "MISSION_COMPLETE");
            finishMissionTelemetry(true);
            return true;
        }
        if (missionTelemetryActive()) missionMaterialHoldEvents++;
        return false;
    }

    private boolean isAtDock(RobotDockSnapshot dock) {
        return dock != null && position().distanceTo(Vec3.atCenterOf(dock.position())) <= DOCK_ENTRY_DISTANCE;
    }

    private RobotDockAssessment.Snapshot assessDock(RobotDockSnapshot dock, RobotDockAssessment.Phase phase) {
        RobotDockAssessment.Snapshot assessment = RobotDockAssessment.inspect(dock, robotIdentity(), phase);
        entityData.set(DOCK_PHASE, assessment.phase().ordinal());
        entityData.set(DOCK_REASON, assessment.reason());
        return assessment;
    }

    private void applyDockHold(RobotDockAssessment.Snapshot assessment) {
        if (missionTelemetryActive() && assessment != null && !assessment.permitted()) missionDockHoldEvents++;
        setDeltaMovement(Vec3.ZERO);
        switch (assessment.verdict()) {
            case FAULT -> transition(RobotStateMachine.Event.CRITICAL_FAULT);
            case SAFE_STOP -> transition(RobotStateMachine.Event.SAFETY_STOP_REQUESTED);
            case WAIT, PERMIT -> { }
        }
    }

    private void applyTransportRouteHold(RobotTransportRouteRuntime.Decision decision) {
        setDeltaMovement(Vec3.ZERO);
        switch (decision.verdict()) {
            case FAULT -> transition(RobotStateMachine.Event.CRITICAL_FAULT);
            case SAFE_STOP -> transition(RobotStateMachine.Event.SAFETY_STOP_REQUESTED);
            case WAIT, PERMIT -> { }
        }
    }

    private void beginMissionTarget(BlockPos target, BlockPos finalTarget) {
        startMissionTelemetry(finalTarget);
        setCurrentTarget(target);
        entityData.set(HAS_TARGET, true);
        setRobotState(RobotOperatingState.IDLE);
        transition(RobotStateMachine.Event.ASSIGN_MISSION);
        transition(RobotStateMachine.Event.BEGIN_PLANNING);
        transition(RobotStateMachine.Event.ROUTE_READY);
    }

    private void startMissionTelemetry(BlockPos finalTarget) {
        missionStartTick = level().getGameTime();
        missionEndTick = -1L;
        missionStartPos = blockPosition().immutable();
        missionFinalTarget = finalTarget == null ? null : finalTarget.immutable();
        missionFinished = false;
        missionCompleted = false;
        missionObstacleWaitEvents = 0;
        missionDegradedEntries = 0;
        missionSafeStopEvents = 0;
        missionFaultEvents = 0;
        missionRouteRejectEvents = 0;
        missionDockHoldEvents = 0;
        missionMaterialHoldEvents = 0;
        missionMaxRouteWaypoints = routeWaypoints.size();
        missionMotionTicks = 0;
        missionWorstLocalization = localization == null ? RobotLocalizationQuality.LOST : localization;
    }

    private boolean missionTelemetryActive() {
        return missionStartTick >= 0L && !missionFinished;
    }

    private void finishMissionTelemetry(boolean completed) {
        if (!missionTelemetryActive()) return;
        missionEndTick = level().getGameTime();
        missionFinished = true;
        missionCompleted = completed;
    }

    private void recordRouteReject() {
        if (missionTelemetryActive()) missionRouteRejectEvents++;
    }

    private void updateWorstLocalization() {
        if (!missionTelemetryActive()) return;
        RobotLocalizationQuality current = localization == null ? RobotLocalizationQuality.LOST : localization;
        if (localizationSeverity(current) > localizationSeverity(missionWorstLocalization)) {
            missionWorstLocalization = current;
        }
    }

    private static int localizationSeverity(RobotLocalizationQuality quality) {
        if (quality == null) return 3;
        return switch (quality) {
            case VALID -> 0;
            case DEGRADED -> 1;
            case STALE -> 2;
            case LOST -> 3;
        };
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
        updateWorstLocalization();

        if (!hasMissionTarget()) {
            if (robotState() == RobotOperatingState.DOCKING || robotState() == RobotOperatingState.LOADING) {
                stopMotion(RobotSafetyAssessment.Verdict.PERMIT, "DOCK_HANDSHAKE");
                return;
            }
            if (robotState() == RobotOperatingState.UNLOADING) {
                stopMotion(RobotSafetyAssessment.Verdict.PERMIT, "UNLOAD_HANDSHAKE");
                return;
            }
            if (robotState() == RobotOperatingState.TRANSPORTING
                    || robotState() == RobotOperatingState.TRANSPORT_WAITING
                    || robotState() == RobotOperatingState.TRANSPORT_REPLANNING) {
                stopMotion(RobotSafetyAssessment.Verdict.SAFE_STOP, "TRANSPORT_ROUTE_REQUIRED");
                return;
            }
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
            completeRouteArrival();
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
        if (robotState() == RobotOperatingState.TRANSPORT_WAITING) transition(RobotStateMachine.Event.OBSTACLE_CLEARED);
        if (robotState() == RobotOperatingState.DEGRADED) transition(RobotStateMachine.Event.EVIDENCE_RECOVERED);
        if (robotState() == RobotOperatingState.SAFE_STOP) transition(RobotStateMachine.Event.SAFE_CONDITION_RESTORED);
        if (robotState() == RobotOperatingState.REPLANNING) transition(RobotStateMachine.Event.REPLAN_READY);
        if (robotState() == RobotOperatingState.TRANSPORT_REPLANNING) transition(RobotStateMachine.Event.REPLAN_READY);
        if (robotState() != RobotOperatingState.NAVIGATING && robotState() != RobotOperatingState.TRANSPORTING) {
            setRobotState(RobotOperatingState.NAVIGATING);
        }

        setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
        Vec3 command = direction.scale(CRUISE_SPEED);
        setDeltaMovement(command);
        move(MoverType.SELF, command);
        if (missionTelemetryActive()) missionMotionTicks++;
    }

    private boolean advanceRouteWaypoint() {
        if (routeWaypoints.isEmpty() || routeIndex + 1 >= routeWaypoints.size()) return false;
        routeIndex++;
        setCurrentTarget(routeWaypoints.get(routeIndex).position());
        entityData.set(ROUTE_REASON,
                robotState() == RobotOperatingState.TRANSPORTING
                        ? "FOLLOWING_TRANSPORT_ROUTE"
                        : "FOLLOWING_EXPLICIT_ROUTE");
        return true;
    }

    private void completeRouteArrival() {
        if (robotState() == RobotOperatingState.TRANSPORT_WAITING
                || robotState() == RobotOperatingState.TRANSPORT_REPLANNING) {
            stopMotion(RobotSafetyAssessment.Verdict.SAFE_STOP, "TRANSPORT_HOLD_AT_TARGET");
            return;
        }
        if (robotState() == RobotOperatingState.TRANSPORTING) {
            entityData.set(HAS_TARGET, false);
            transition(RobotStateMachine.Event.ARRIVE_TARGET);
            entityData.set(SAFETY, RobotSafetyAssessment.Verdict.PERMIT.ordinal());
            entityData.set(SAFETY_REASON, "TRANSPORT_TARGET_ARRIVED");
            entityData.set(ROUTE_REASON, "TRANSPORT_ROUTE_COMPLETE");
            routeWaypoints = List.of();
            routeIndex = 0;
            return;
        }
        completeMission();
    }

    private void completeMission() {
        entityData.set(HAS_TARGET, false);
        setRobotState(RobotOperatingState.COMPLETE);
        finishMissionTelemetry(true);
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
            if (robotState() == RobotOperatingState.NAVIGATING
                    || robotState() == RobotOperatingState.TRANSPORTING) {
                transition(RobotStateMachine.Event.OBSTACLE_DETECTED);
            } else if (robotState() != RobotOperatingState.WAITING
                    && robotState() != RobotOperatingState.TRANSPORT_WAITING) {
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
            if (player instanceof ServerPlayer serverPlayer
                    && player.getItemInHand(hand).getItem() instanceof DiagnosticTabletItem) {
                DiagnosticTabletItem.captureRobot(
                        serverPlayer,
                        player.getItemInHand(hand),
                        this,
                        player.isShiftKeyDown()
                );
                return InteractionResult.SUCCESS;
            }
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
        tag.putString("MaterialReason", materialReason());
        tag.putInt("RouteCount", routeWaypoints.size());
        tag.putInt("RouteIndex", routeIndex);

        tag.putLong("MissionTelemetryStartTick", missionStartTick);
        tag.putLong("MissionTelemetryEndTick", missionEndTick);
        tag.putBoolean("MissionTelemetryFinished", missionFinished);
        tag.putBoolean("MissionTelemetryCompleted", missionCompleted);
        if (missionStartPos != null) {
            tag.putBoolean("MissionTelemetryHasStart", true);
            tag.putInt("MissionTelemetryStartX", missionStartPos.getX());
            tag.putInt("MissionTelemetryStartY", missionStartPos.getY());
            tag.putInt("MissionTelemetryStartZ", missionStartPos.getZ());
        }
        if (missionFinalTarget != null) {
            tag.putBoolean("MissionTelemetryHasTarget", true);
            tag.putInt("MissionTelemetryTargetX", missionFinalTarget.getX());
            tag.putInt("MissionTelemetryTargetY", missionFinalTarget.getY());
            tag.putInt("MissionTelemetryTargetZ", missionFinalTarget.getZ());
        }
        tag.putInt("MissionTelemetryObstacleWait", missionObstacleWaitEvents);
        tag.putInt("MissionTelemetryDegraded", missionDegradedEntries);
        tag.putInt("MissionTelemetrySafeStop", missionSafeStopEvents);
        tag.putInt("MissionTelemetryFault", missionFaultEvents);
        tag.putInt("MissionTelemetryRouteReject", missionRouteRejectEvents);
        tag.putInt("MissionTelemetryDockHold", missionDockHoldEvents);
        tag.putInt("MissionTelemetryMaterialHold", missionMaterialHoldEvents);
        tag.putInt("MissionTelemetryMaxWaypoints", missionMaxRouteWaypoints);
        tag.putInt("MissionTelemetryMotionTicks", missionMotionTicks);
        tag.putString("MissionTelemetryWorstLocalization", missionWorstLocalization.name());
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
        String persistedMaterialReason = tag.getString("MaterialReason");
        entityData.set(MATERIAL_REASON, persistedMaterialReason.isBlank() ? "NO_TRANSFER_EVIDENCE" : persistedMaterialReason);

        missionStartTick = tag.contains("MissionTelemetryStartTick") ? tag.getLong("MissionTelemetryStartTick") : -1L;
        missionEndTick = tag.contains("MissionTelemetryEndTick") ? tag.getLong("MissionTelemetryEndTick") : -1L;
        missionFinished = tag.getBoolean("MissionTelemetryFinished");
        missionCompleted = tag.getBoolean("MissionTelemetryCompleted");
        missionStartPos = tag.getBoolean("MissionTelemetryHasStart")
                ? new BlockPos(tag.getInt("MissionTelemetryStartX"), tag.getInt("MissionTelemetryStartY"), tag.getInt("MissionTelemetryStartZ"))
                : null;
        missionFinalTarget = tag.getBoolean("MissionTelemetryHasTarget")
                ? new BlockPos(tag.getInt("MissionTelemetryTargetX"), tag.getInt("MissionTelemetryTargetY"), tag.getInt("MissionTelemetryTargetZ"))
                : null;
        missionObstacleWaitEvents = Math.max(0, tag.getInt("MissionTelemetryObstacleWait"));
        missionDegradedEntries = Math.max(0, tag.getInt("MissionTelemetryDegraded"));
        missionSafeStopEvents = Math.max(0, tag.getInt("MissionTelemetrySafeStop"));
        missionFaultEvents = Math.max(0, tag.getInt("MissionTelemetryFault"));
        missionRouteRejectEvents = Math.max(0, tag.getInt("MissionTelemetryRouteReject"));
        missionDockHoldEvents = Math.max(0, tag.getInt("MissionTelemetryDockHold"));
        missionMaterialHoldEvents = Math.max(0, tag.getInt("MissionTelemetryMaterialHold"));
        missionMaxRouteWaypoints = Math.max(0, tag.getInt("MissionTelemetryMaxWaypoints"));
        missionMotionTicks = Math.max(0, tag.getInt("MissionTelemetryMotionTicks"));
        try {
            missionWorstLocalization = RobotLocalizationQuality.valueOf(tag.getString("MissionTelemetryWorstLocalization"));
        } catch (IllegalArgumentException ignored) {
            missionWorstLocalization = localization;
        }

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