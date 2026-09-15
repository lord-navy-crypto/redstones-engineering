package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.EngineeringSystemsModule;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.operations.OperationBufferSnapshot;
import dev.redstoneengineering.operations.OperationChangeoverRuntime;
import dev.redstoneengineering.operations.OperationDispatchRuntime;
import dev.redstoneengineering.operations.OperationJob;
import dev.redstoneengineering.operations.OperationMaintenanceRuntime;
import dev.redstoneengineering.operations.OperationResourceMaintenanceSnapshot;
import dev.redstoneengineering.operations.OperationResourceSetupSnapshot;
import dev.redstoneengineering.operations.OperationResourceSnapshot;
import dev.redstoneengineering.operations.OperationWorkcellAdmissionAssessment;
import dev.redstoneengineering.operations.OperationWorkcellCapacitySnapshot;
import dev.redstoneengineering.operations.world.OperationPlantSavedData;
import dev.redstoneengineering.operations.world.OperationWorkcellBufferBinding;
import dev.redstoneengineering.operations.world.OperationWorkcellStore;
import dev.redstoneengineering.operations.world.OperationWorldResourceSnapshot;
import dev.redstoneengineering.ui.WorkcellControllerUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * World-facing Industrial Operations workcell boundary.
 *
 * <p>The controller owns no scheduling, setup, maintenance, or capacity algorithm. It projects
 * explicit server-owned resource/buffer bindings into low-cardinality signals and delegates every
 * decision to the existing Operations runtimes.</p>
 */
public class WorkcellControllerBlock extends Block implements EngineeringPortProvider {
    public WorkcellControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<WorkcellControllerBlock> codec() {
        return EngineeringSystemsModule.WORKCELL_CONTROLLER_CODEC.value();
    }

    public record Snapshot(
            String workcellId,
            int boundResources,
            int validResources,
            int runningResources,
            int faultResources,
            boolean capacityEvidenceAvailable,
            boolean permit,
            boolean hold,
            int queuePressure,
            int inputBufferUsedUnits,
            int inputBufferCapacityUnits,
            int outputBufferUsedUnits,
            int outputBufferCapacityUnits,
            int inputWipPressurePercent,
            int outputWipPressurePercent,
            String admissionReason,
            PortQuality evidenceQuality
    ) {
        public Snapshot {
            if (workcellId == null || workcellId.isBlank()) workcellId = "UNKNOWN";
            boundResources = Math.max(0, boundResources);
            validResources = Math.max(0, Math.min(boundResources, validResources));
            runningResources = Math.max(0, Math.min(validResources, runningResources));
            faultResources = Math.max(0, Math.min(boundResources, faultResources));
            queuePressure = Math.max(0, Math.min(15, queuePressure));
            inputBufferUsedUnits = Math.max(0, inputBufferUsedUnits);
            inputBufferCapacityUnits = Math.max(0, inputBufferCapacityUnits);
            outputBufferUsedUnits = Math.max(0, outputBufferUsedUnits);
            outputBufferCapacityUnits = Math.max(0, outputBufferCapacityUnits);
            inputWipPressurePercent = Math.max(0, Math.min(100, inputWipPressurePercent));
            outputWipPressurePercent = Math.max(0, Math.min(100, outputWipPressurePercent));
            if (admissionReason == null || admissionReason.isBlank()) admissionReason = "UNSPECIFIED";
            if (evidenceQuality == null) evidenceQuality = PortQuality.NO_SIGNAL;
        }

        public boolean active() { return runningResources > 0; }
        public boolean faulted() { return faultResources > 0; }
    }

    public static String workcellId(BlockPos controllerPos) {
        return "workcell:" + controllerPos.asLong();
    }

    public static Snapshot inspect(Level level, BlockPos controllerPos) {
        String workcellId = workcellId(controllerPos);
        if (!(level instanceof ServerLevel server)) {
            return unavailable(workcellId, "SERVER_EVIDENCE_UNAVAILABLE");
        }

        List<OperationWorkcellStore.ResolvedResource> resources =
                OperationWorkcellStore.resolveBoundResources(server, workcellId);
        if (resources.isEmpty()) return unavailable(workcellId, "NO_BOUND_RESOURCES");

        int valid = 0;
        int running = 0;
        int faults = 0;
        Set<String> resourceIds = new HashSet<>();
        for (OperationWorkcellStore.ResolvedResource resource : resources) {
            if (resource.snapshot() != null) {
                resourceIds.add(resource.snapshot().resourceId());
                if (resource.snapshot().faultActive()) faults++;
                if (resource.valid()) {
                    valid++;
                    if (resource.snapshot().running()) running++;
                }
            }
        }

        PortQuality quality = valid == resources.size() ? PortQuality.VALID : PortQuality.STALE;
        boolean fault = faults > 0;
        if (resourceIds.isEmpty()) {
            return new Snapshot(workcellId, resources.size(), 0, 0, faults,
                    false, false, true, 0,
                    0, 0, 0, 0, 0, 0,
                    "RESOURCE_EVIDENCE_INVALID", quality);
        }

        OperationPlantSavedData plant = OperationPlantSavedData.get(server);
        OperationWorkcellBufferBinding bufferBinding = plant.workcellBufferBinding(workcellId);
        if (bufferBinding == null || !bufferBinding.validBinding()) {
            return capacityUnavailable(workcellId, resources.size(), valid, running, faults,
                    quality, "WORKCELL_BUFFER_BINDING_MISSING");
        }
        OperationBufferSnapshot inputBuffer = plant.buffer(bufferBinding.inputBufferId());
        if (inputBuffer == null) {
            return capacityUnavailable(workcellId, resources.size(), valid, running, faults,
                    quality, "INPUT_BUFFER_MISSING");
        }
        OperationBufferSnapshot outputBuffer = plant.buffer(bufferBinding.outputBufferId());
        if (outputBuffer == null) {
            return capacityUnavailable(workcellId, resources.size(), valid, running, faults,
                    quality, "OUTPUT_BUFFER_MISSING");
        }

        OperationWorkcellCapacitySnapshot capacity = new OperationWorkcellCapacitySnapshot(
                workcellId,
                resourceIds,
                Math.min(running, resourceIds.size()),
                0,
                inputBuffer.capacityUnits(),
                inputBuffer.usedUnits(),
                outputBuffer.capacityUnits(),
                outputBuffer.usedUnits(),
                quality,
                fault
        );
        int inputWipPressurePercent = capacity.inputWipPressurePercent();
        int outputWipPressurePercent = capacity.outputWipPressurePercent();
        int queuePressure = pressureSignal(Math.max(inputWipPressurePercent, outputWipPressurePercent));
        String representative = resourceIds.stream().sorted().findFirst().orElse("UNKNOWN");
        OperationWorkcellAdmissionAssessment.Snapshot admission =
                OperationWorkcellAdmissionAssessment.inspect(capacity, representative);
        return new Snapshot(
                workcellId,
                resources.size(),
                valid,
                running,
                faults,
                true,
                admission.permitted(),
                !admission.permitted(),
                queuePressure,
                inputBuffer.usedUnits(),
                inputBuffer.capacityUnits(),
                outputBuffer.usedUnits(),
                outputBuffer.capacityUnits(),
                inputWipPressurePercent,
                outputWipPressurePercent,
                admission.reason(),
                quality
        );
    }

    /** Explicit server-side resource binding action; no proximity discovery. */
    public static OperationWorkcellStore.Decision bindResource(
            ServerLevel level, BlockPos controllerPos, BlockPos resourcePos) {
        return OperationWorkcellStore.bindResource(level, workcellId(controllerPos), resourcePos);
    }

    public static OperationWorkcellStore.Decision unbindResource(
            ServerLevel level, BlockPos controllerPos, BlockPos resourcePos) {
        return OperationWorkcellStore.unbindResource(level, workcellId(controllerPos), resourcePos);
    }

    /** Explicit finite-capacity material-flow binding; both buffer identities must already exist. */
    public static OperationWorkcellStore.BufferDecision bindBuffers(
            ServerLevel level,
            BlockPos controllerPos,
            String inputBufferId,
            String outputBufferId
    ) {
        return OperationWorkcellStore.bindBuffers(
                level, workcellId(controllerPos), inputBufferId, outputBufferId);
    }

    public static OperationWorkcellStore.BufferDecision unbindBuffers(
            ServerLevel level,
            BlockPos controllerPos
    ) {
        return OperationWorkcellStore.unbindBuffers(level, workcellId(controllerPos));
    }

    /** Scheduling preview delegates ranking/resource selection to the existing dispatch authority. */
    public static OperationDispatchRuntime.Decision previewDispatch(
            ServerLevel level,
            BlockPos controllerPos,
            Collection<OperationJob> jobs,
            long gameTick,
            OperationDispatchRuntime.Policy policy
    ) {
        ArrayList<OperationResourceSnapshot> resources = new ArrayList<>();
        for (OperationWorkcellStore.ResolvedResource resolved :
                OperationWorkcellStore.resolveBoundResources(level, workcellId(controllerPos))) {
            if (resolved.snapshot() == null) continue;
            resources.add(toDispatchSnapshot(resolved.snapshot()));
        }
        return OperationDispatchRuntime.evaluate(jobs, resources, gameTick, policy);
    }

    public static OperationChangeoverRuntime.Decision requestChangeover(
            OperationResourceSnapshot resource,
            OperationResourceSetupSnapshot setup,
            String targetProcessId
    ) {
        return OperationChangeoverRuntime.request(resource, setup, targetProcessId);
    }

    public static OperationMaintenanceRuntime.Decision startMaintenance(
            OperationResourceMaintenanceSnapshot maintenance
    ) {
        return OperationMaintenanceRuntime.start(maintenance);
    }

    private static OperationResourceSnapshot toDispatchSnapshot(OperationWorldResourceSnapshot resource) {
        OperationResourceSnapshot.State state;
        if (resource.faultActive()) state = OperationResourceSnapshot.State.FAULTED;
        else if (!resource.available()) state = OperationResourceSnapshot.State.UNAVAILABLE;
        else if (resource.running()) state = OperationResourceSnapshot.State.BUSY;
        else state = OperationResourceSnapshot.State.AVAILABLE;
        return new OperationResourceSnapshot(
                resource.resourceId(), resource.processCapabilities(), state, resource.evidenceQuality());
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("ACTIVE", Direction.NORTH, EngineeringDomain.REDSTONE,
                        PortKind.CONTROL, PortDirection.OUTPUT, true, "active"),
                new EngineeringPort("PERMIT", Direction.SOUTH, EngineeringDomain.REDSTONE,
                        PortKind.SAFETY, PortDirection.OUTPUT, true, "permit"),
                new EngineeringPort("HOLD", Direction.EAST, EngineeringDomain.REDSTONE,
                        PortKind.SAFETY, PortDirection.OUTPUT, true, "hold"),
                new EngineeringPort("FAULT", Direction.WEST, EngineeringDomain.REDSTONE,
                        PortKind.SAFETY, PortDirection.OUTPUT, true, "fault"),
                new EngineeringPort("QUEUE PRESSURE", Direction.UP, EngineeringDomain.REDSTONE,
                        PortKind.MEASUREMENT, PortDirection.OUTPUT, true, "queue")
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        Snapshot snapshot = inspect(level, pos);
        int value = valueForPhysicalSide(snapshot, side);
        PortQuality quality = side == Direction.UP && !snapshot.capacityEvidenceAvailable()
                ? PortQuality.STALE
                : snapshot.evidenceQuality();
        return Optional.of(EngineeringPortSnapshot.redstone(port.get(), value, quality));
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return direction != null && engineeringPort(state, direction.getOpposite()).isPresent();
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction queryDirection) {
        if (!(level instanceof Level world)) return 0;
        Direction physicalSide = queryDirection.getOpposite();
        if (engineeringPort(state, physicalSide).isEmpty()) return 0;
        return valueForPhysicalSide(inspect(world, pos), physicalSide);
    }

    private static int valueForPhysicalSide(Snapshot snapshot, Direction physicalSide) {
        return switch (physicalSide) {
            case NORTH -> snapshot.active() ? 15 : 0;
            case SOUTH -> snapshot.permit() ? 15 : 0;
            case EAST -> snapshot.hold() ? 15 : 0;
            case WEST -> snapshot.faulted() ? 15 : 0;
            case UP -> snapshot.capacityEvidenceAvailable() ? snapshot.queuePressure() : 0;
            default -> 0;
        };
    }

    private static int pressureSignal(int pressurePercent) {
        if (pressurePercent <= 0) return 0;
        return Math.max(1, Math.min(15, Math.round(pressurePercent * 15.0F / 100.0F)));
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            WorkcellControllerUi.open(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static Snapshot unavailable(String workcellId, String reason) {
        return new Snapshot(workcellId, 0, 0, 0, 0,
                false, false, true, 0,
                0, 0, 0, 0, 0, 0,
                reason, PortQuality.NO_SIGNAL);
    }

    private static Snapshot capacityUnavailable(
            String workcellId,
            int boundResources,
            int validResources,
            int runningResources,
            int faultResources,
            PortQuality quality,
            String reason
    ) {
        return new Snapshot(workcellId, boundResources, validResources, runningResources, faultResources,
                false, false, true, 0,
                0, 0, 0, 0, 0, 0,
                reason, quality);
    }
}
