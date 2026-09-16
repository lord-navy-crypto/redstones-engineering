package dev.redstoneengineering.operations.world;

import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.operations.OperationBufferReceiptEvidence;
import dev.redstoneengineering.operations.OperationBufferRuntime;
import dev.redstoneengineering.operations.OperationBufferSnapshot;
import dev.redstoneengineering.operations.OperationInputRequirement;
import dev.redstoneengineering.operations.OperationJob;
import dev.redstoneengineering.operations.OperationMaterialReleaseRuntime;
import dev.redstoneengineering.operations.OperationOutputSnapshot;
import dev.redstoneengineering.operations.OperationQueueSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * Server-owned world facade for finite Industrial Operations WIP buffers.
 *
 * <p>The persisted payload is the same logical {@link OperationBufferSnapshot} consumed by the
 * pure Operations runtimes: buffer identity, world location, finite capacity, and explicit
 * {@code OperationBufferLot} output/job/unit identities. No ItemStack container is substituted
 * for lot traceability and no world mutation is used as a second inventory authority.</p>
 */
public final class OperationIndustrialBufferState {
    private OperationIndustrialBufferState() {}

    public enum Verdict {
        CREATED,
        REMOVED,
        RECEIVED,
        ALLOCATED,
        RELEASED,
        WAIT,
        SAFE_STOP,
        FAULT
    }

    public record Decision(
            Verdict verdict,
            String reason,
            OperationBufferSnapshot snapshot
    ) {
        public Decision {
            if (verdict == null) verdict = Verdict.SAFE_STOP;
            if (reason == null || reason.isBlank()) reason = "UNSPECIFIED";
        }

        public boolean changed() {
            return verdict == Verdict.CREATED
                    || verdict == Verdict.REMOVED
                    || verdict == Verdict.RECEIVED
                    || verdict == Verdict.ALLOCATED
                    || verdict == Verdict.RELEASED;
        }
    }

    /** Explicit creation only; location and capacity are never inferred from nearby blocks. */
    public static Decision create(
            ServerLevel level,
            String bufferId,
            BlockPos location,
            int capacityUnits
    ) {
        if (level == null) return safeStop("SERVER_LEVEL_MISSING", null);
        if (bufferId == null || bufferId.isBlank()) return safeStop("BUFFER_ID_MISSING", null);
        if (location == null) return safeStop("BUFFER_LOCATION_MISSING", null);
        OperationPlantSavedData data = OperationPlantSavedData.get(level);
        String normalized = bufferId.trim();
        if (data.buffer(normalized) != null) return safeStop("BUFFER_ID_ALREADY_EXISTS", data.buffer(normalized));
        for (OperationBufferSnapshot existing : data.buffers()) {
            if (existing.location().equals(location)) {
                return safeStop("BUFFER_LOCATION_ALREADY_BOUND", existing);
            }
        }
        final OperationBufferSnapshot created;
        try {
            created = new OperationBufferSnapshot(normalized, location, capacityUnits, List.of());
        } catch (IllegalArgumentException ex) {
            return safeStop("BUFFER_CONFIGURATION_INVALID", null);
        }
        if (!data.putBuffer(created)) return safeStop("BUFFER_PERSISTENCE_REJECTED", null);
        // OperationPlantSavedData.putBuffer performs setDirty() after the authoritative replacement.
        return new Decision(Verdict.CREATED, "BUFFER_CREATED", created);
    }

    public static Decision remove(ServerLevel level, String bufferId) {
        if (level == null) return safeStop("SERVER_LEVEL_MISSING", null);
        if (bufferId == null || bufferId.isBlank()) return safeStop("BUFFER_ID_MISSING", null);
        OperationPlantSavedData data = OperationPlantSavedData.get(level);
        OperationBufferSnapshot current = data.buffer(bufferId.trim());
        if (current == null) return waitFor("BUFFER_NOT_FOUND", null);
        if (!current.lots().isEmpty()) return waitFor("BUFFER_NOT_EMPTY", current);
        if (!data.removeBuffer(current.bufferId())) return safeStop("BUFFER_REMOVE_REJECTED", current);
        return new Decision(Verdict.REMOVED, "BUFFER_REMOVED", null);
    }

    public static OperationBufferSnapshot snapshot(ServerLevel level, String bufferId) {
        if (level == null || bufferId == null || bufferId.isBlank()) return null;
        return OperationPlantSavedData.get(level).buffer(bufferId.trim());
    }

    /**
     * Admit one transported, quality-cleared output only after identity and evidence match.
     * The pure OperationBufferRuntime owns duplicate/capacity/unload decisions.
     *
     * <p>A successful receipt is also the authoritative world boundary for completed logistics:
     * mission/output/job identity and destination buffer are all known here, so the plant ledger
     * can record delivery without polling the AMR entity or duplicating transport authority.</p>
     */
    public static Decision receiveQualityCleared(
            ServerLevel level,
            OperationOutputSnapshot acceptedOutput,
            OperationBufferReceiptEvidence receipt
    ) {
        if (level == null) return safeStop("SERVER_LEVEL_MISSING", null);
        if (acceptedOutput == null) return safeStop("ACCEPTED_OUTPUT_MISSING", null);
        if (receipt == null) return safeStop("BUFFER_RECEIPT_MISSING", null);
        if (acceptedOutput.faultActive()) return fault("ACCEPTED_OUTPUT_FAULT_ACTIVE", null);
        if (acceptedOutput.evidenceQuality() != PortQuality.VALID) {
            return safeStop("ACCEPTED_OUTPUT_EVIDENCE_INVALID", null);
        }
        if (!acceptedOutput.completionConfirmed()) return waitFor("ACCEPTED_OUTPUT_COMPLETION_UNCONFIRMED", null);
        if (!acceptedOutput.materialReady()) return waitFor("ACCEPTED_OUTPUT_NOT_MATERIAL_READY", null);
        if (acceptedOutput.outputId() != receipt.outputId()) return safeStop("ACCEPTED_OUTPUT_ID_MISMATCH", null);
        if (acceptedOutput.jobId() != receipt.jobId()) return safeStop("ACCEPTED_OUTPUT_JOB_MISMATCH", null);
        if (acceptedOutput.units() != receipt.units()) return safeStop("ACCEPTED_OUTPUT_QUANTITY_MISMATCH", null);

        OperationPlantSavedData data = OperationPlantSavedData.get(level);
        OperationBufferSnapshot current = data.buffer(receipt.bufferId());
        if (current == null) return safeStop("BUFFER_NOT_FOUND", null);
        OperationBufferRuntime.Decision decision = OperationBufferRuntime.receive(current, receipt);
        Decision applied = applyReceipt(data, decision);
        if (applied.verdict() == Verdict.RECEIVED) {
            OperationTransportRuntimeRecord tracked = data.transportRecord(receipt.missionId());
            if (tracked != null) {
                OperationRobotTransportWorldState.Decision delivered = OperationRobotTransportWorldState.markDelivered(
                        level,
                        receipt.missionId(),
                        receipt.outputId(),
                        receipt.jobId(),
                        level.getGameTime()
                );
                if (delivered.verdict() != OperationRobotTransportWorldState.Verdict.DELIVERED) {
                    if (!data.putBuffer(current)) {
                        throw new IllegalStateException("TRANSPORT_DELIVERY_REJECTED_AND_BUFFER_ROLLBACK_FAILED");
                    }
                    return switch (delivered.verdict()) {
                        case WAIT -> waitFor(delivered.reason(), current);
                        case FAULT -> fault(delivered.reason(), current);
                        default -> safeStop(delivered.reason(), current);
                    };
                }
            } else {
                // Compatibility path for receipts created before persistent transport tracking.
                OperationPlantRuntimeRecorder.recordLogisticsEvent(
                        level,
                        receipt.missionId(),
                        receipt.outputId(),
                        receipt.jobId(),
                        level.getGameTime(),
                        "DELIVERED",
                        "buffer=" + receipt.bufferId()
                                + " units=" + receipt.units()
                                + " unloadConfirmed=" + receipt.unloadConfirmed()
                );
            }
            data.recordPlantEvent(
                    OperationPlantEvent.Type.QUALITY,
                    level.getGameTime(),
                    "output:" + acceptedOutput.outputId(),
                    acceptedOutput.jobId(),
                    "QUALITY_CLEARED_RECEIPT units=" + acceptedOutput.units()
                            + " evidence=" + acceptedOutput.evidenceQuality().name()
                            + " completionConfirmed=" + acceptedOutput.completionConfirmed()
                            + " materialReady=" + acceptedOutput.materialReady()
            );
        }
        return applied;
    }

    /** Direct allocation remains owned by OperationBufferRuntime. */
    public static Decision allocate(
            ServerLevel level,
            String bufferId,
            OperationInputRequirement requirement
    ) {
        if (level == null) return safeStop("SERVER_LEVEL_MISSING", null);
        if (bufferId == null || bufferId.isBlank()) return safeStop("BUFFER_ID_MISSING", null);
        OperationPlantSavedData data = OperationPlantSavedData.get(level);
        OperationBufferSnapshot current = data.buffer(bufferId.trim());
        if (current == null) return safeStop("BUFFER_NOT_FOUND", null);
        OperationBufferRuntime.AllocationDecision decision = OperationBufferRuntime.allocate(current, requirement);
        if (!decision.allocated()) {
            return switch (decision.verdict()) {
                case FAULT -> fault(decision.reason(), current);
                case SAFE_STOP -> safeStop(decision.reason(), current);
                default -> waitFor(decision.reason(), current);
            };
        }
        if (!data.putBuffer(decision.nextState())) {
            return safeStop("BUFFER_PERSISTENCE_REJECTED", current);
        }
        return new Decision(Verdict.ALLOCATED, decision.reason(), decision.nextState());
    }

    /**
     * Atomic downstream material release delegates to OperationMaterialReleaseRuntime using the
     * world-owned queue snapshot. A full queue returns WAIT without consuming persisted WIP.
     * On RELEASED, buffer state, queue state and durable lifecycle/history are one commit boundary.
     */
    public static OperationMaterialReleaseRuntime.Decision releaseMaterial(
            ServerLevel level,
            String bufferId,
            String queueId,
            OperationJob downstreamJob,
            OperationInputRequirement requirement
    ) {
        if (level == null || bufferId == null || bufferId.isBlank()
                || queueId == null || queueId.isBlank()) {
            throw new IllegalArgumentException("server level, bufferId and queueId are required");
        }
        OperationPlantSavedData data = OperationPlantSavedData.get(level);
        String normalizedBufferId = bufferId.trim();
        queueId = queueId.trim();
        OperationBufferSnapshot current = data.buffer(normalizedBufferId);
        if (current == null) {
            throw new IllegalArgumentException("buffer is not registered");
        }
        OperationQueueSnapshot queue = data.queue(queueId);
        if (queue == null) {
            throw new IllegalArgumentException("queue is not registered");
        }

        OperationMaterialReleaseRuntime.Decision decision = OperationMaterialReleaseRuntime.release(
                current, queue, downstreamJob, requirement);
        if (!decision.released()) return decision;

        if (!data.putBuffer(decision.nextBuffer())) {
            throw new IllegalStateException("BUFFER_PERSISTENCE_REJECTED");
        }
        if (!data.putQueue(queueId, decision.nextQueue())) {
            if (!data.putBuffer(current)) {
                throw new IllegalStateException("QUEUE_PERSISTENCE_REJECTED_AND_BUFFER_ROLLBACK_FAILED");
            }
            throw new IllegalStateException("QUEUE_PERSISTENCE_REJECTED");
        }
        if (!OperationPlantRuntimeRecorder.recordMaterialRelease(
                level,
                queueId,
                downstreamJob,
                decision,
                level.getGameTime()
        )) {
            boolean bufferRolledBack = data.putBuffer(current);
            boolean queueRolledBack = data.putQueue(queueId, queue);
            if (!bufferRolledBack || !queueRolledBack) {
                throw new IllegalStateException("PLANT_RUNTIME_HISTORY_REJECTED_AND_RELEASE_ROLLBACK_FAILED");
            }
            throw new IllegalStateException("PLANT_RUNTIME_HISTORY_REJECTED");
        }
        return decision;
    }

    private static Decision applyReceipt(
            OperationPlantSavedData data,
            OperationBufferRuntime.Decision decision
    ) {
        OperationBufferSnapshot current = decision.nextState();
        return switch (decision.verdict()) {
            case RECEIVED -> {
                if (!data.putBuffer(decision.nextState())) {
                    yield safeStop("BUFFER_PERSISTENCE_REJECTED", current);
                }
                yield new Decision(Verdict.RECEIVED, decision.reason(), decision.nextState());
            }
            case FAULT -> fault(decision.reason(), current);
            case SAFE_STOP -> safeStop(decision.reason(), current);
            case WAIT -> waitFor(decision.reason(), current);
            case ALLOCATED -> safeStop("UNEXPECTED_BUFFER_RECEIPT_VERDICT", current);
        };
    }

    // Reasons retained verbatim from the pure authority for diagnostics/verifier traceability:
    // BUFFER_CAPACITY_REACHED and DUPLICATE_OUTPUT_RECEIPT.
    private static Decision waitFor(String reason, OperationBufferSnapshot snapshot) {
        return new Decision(Verdict.WAIT, reason, snapshot);
    }

    private static Decision safeStop(String reason, OperationBufferSnapshot snapshot) {
        return new Decision(Verdict.SAFE_STOP, reason, snapshot);
    }

    private static Decision fault(String reason, OperationBufferSnapshot snapshot) {
        return new Decision(Verdict.FAULT, reason, snapshot);
    }
}
