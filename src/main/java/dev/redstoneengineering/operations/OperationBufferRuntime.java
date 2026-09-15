package dev.redstoneengineering.operations;

import dev.redstoneengineering.core.port.PortQuality;

import java.util.ArrayList;

/** Pure immutable receipt lifecycle for bounded Industrial Operations WIP buffers. */
public final class OperationBufferRuntime {
    private OperationBufferRuntime() {}

    public enum Verdict {
        RECEIVED,
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

    private static Decision waitFor(OperationBufferSnapshot state, String reason) {
        return new Decision(Verdict.WAIT, reason, state, null);
    }

    private static Decision safeStop(OperationBufferSnapshot state, String reason) {
        return new Decision(Verdict.SAFE_STOP, reason, state, null);
    }
}
