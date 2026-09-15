package dev.redstoneengineering.operations;

import dev.redstoneengineering.core.port.PortQuality;

import java.util.ArrayList;

/** Pure immutable receipt and allocation lifecycle for bounded Industrial Operations WIP buffers. */
public final class OperationBufferRuntime {
    private OperationBufferRuntime() {}

    public enum Verdict {
        RECEIVED,
        ALLOCATED,
        WAIT,
        SAFE_STOP,
        FAULT
    }

    public record Decision(
            Verdict verdict,
            String reason,
            OperationBufferSnapshot nextState,
            OperationBufferLot receivedLot
    ) {
        public Decision {
            if (verdict == null) verdict = Verdict.SAFE_STOP;
            if (reason == null || reason.isBlank()) reason = "UNSPECIFIED";
            if (nextState == null) throw new IllegalArgumentException("nextState is required");
            if (verdict != Verdict.RECEIVED) receivedLot = null;
        }

        public boolean changed() {
            return verdict == Verdict.RECEIVED;
        }
    }

    public record AllocationDecision(
            Verdict verdict,
            String reason,
            OperationBufferSnapshot nextState,
            OperationInputAllocation allocation
    ) {
        public AllocationDecision {
            if (verdict == null) verdict = Verdict.SAFE_STOP;
            if (reason == null || reason.isBlank()) reason = "UNSPECIFIED";
            if (nextState == null) throw new IllegalArgumentException("nextState is required");
            if (verdict != Verdict.ALLOCATED) allocation = null;
        }

        public boolean allocated() {
            return verdict == Verdict.ALLOCATED && allocation != null;
        }
    }

    public static Decision receive(OperationBufferSnapshot state, OperationBufferReceiptEvidence receipt) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (receipt == null) return safeStop(state, "BUFFER_RECEIPT_MISSING");
        if (receipt.faultActive()) return new Decision(Verdict.FAULT, "BUFFER_RECEIPT_FAULT_ACTIVE", state, null);
        if (receipt.evidenceQuality() != PortQuality.VALID) return safeStop(state, "BUFFER_RECEIPT_EVIDENCE_INVALID");
        if (!receipt.unloadConfirmed()) return waitFor(state, "BUFFER_UNLOAD_UNCONFIRMED");
        if (!state.bufferId().equals(receipt.bufferId())) return safeStop(state, "BUFFER_ID_MISMATCH");
        if (!state.location().equals(receipt.bufferLocation())) return safeStop(state, "BUFFER_LOCATION_MISMATCH");
        if (state.containsOutput(receipt.outputId())) return safeStop(state, "DUPLICATE_OUTPUT_RECEIPT");
        if (receipt.units() > state.availableUnits()) return waitFor(state, "BUFFER_CAPACITY_REACHED");

        OperationBufferLot lot = new OperationBufferLot(receipt.outputId(), receipt.jobId(), receipt.units());
        ArrayList<OperationBufferLot> lots = new ArrayList<>(state.lots());
        lots.add(lot);
        OperationBufferSnapshot next = new OperationBufferSnapshot(
                state.bufferId(), state.location(), state.capacityUnits(), lots);
        return new Decision(Verdict.RECEIVED, "BUFFER_RECEIPT_ACCEPTED", next, lot);
    }

    public static AllocationDecision allocate(OperationBufferSnapshot state, OperationInputRequirement requirement) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (requirement == null) return allocationSafeStop(state, "INPUT_REQUIREMENT_MISSING");
        if (!state.bufferId().equals(requirement.bufferId())) {
            return allocationSafeStop(state, "INPUT_BUFFER_ID_MISMATCH");
        }

        OperationBufferLot selected = null;
        for (OperationBufferLot lot : state.lots()) {
            if (lot.outputId() == requirement.outputId()) {
                selected = lot;
                break;
            }
        }
        if (selected == null) return allocationWait(state, "INPUT_OUTPUT_NOT_AVAILABLE");
        if (requirement.requiredUnits() > selected.units()) {
            return allocationWait(state, "INPUT_QUANTITY_NOT_AVAILABLE");
        }

        ArrayList<OperationBufferLot> lots = new ArrayList<>();
        for (OperationBufferLot lot : state.lots()) {
            if (lot.outputId() != selected.outputId()) {
                lots.add(lot);
            } else {
                int remaining = lot.units() - requirement.requiredUnits();
                if (remaining > 0) lots.add(new OperationBufferLot(lot.outputId(), lot.jobId(), remaining));
            }
        }
        OperationInputAllocation allocation = new OperationInputAllocation(
                requirement.downstreamJobId(),
                state.bufferId(),
                selected.outputId(),
                selected.jobId(),
                requirement.requiredUnits()
        );
        OperationBufferSnapshot next = new OperationBufferSnapshot(
                state.bufferId(), state.location(), state.capacityUnits(), lots);
        return new AllocationDecision(Verdict.ALLOCATED, "INPUT_ALLOCATED", next, allocation);
    }

    private static Decision waitFor(OperationBufferSnapshot state, String reason) {
        return new Decision(Verdict.WAIT, reason, state, null);
    }

    private static Decision safeStop(OperationBufferSnapshot state, String reason) {
        return new Decision(Verdict.SAFE_STOP, reason, state, null);
    }

    private static AllocationDecision allocationWait(OperationBufferSnapshot state, String reason) {
        return new AllocationDecision(Verdict.WAIT, reason, state, null);
    }

    private static AllocationDecision allocationSafeStop(OperationBufferSnapshot state, String reason) {
        return new AllocationDecision(Verdict.SAFE_STOP, reason, state, null);
    }
}
