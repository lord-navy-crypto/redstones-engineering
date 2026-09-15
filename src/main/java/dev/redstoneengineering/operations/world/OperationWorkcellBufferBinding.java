package dev.redstoneengineering.operations.world;

/**
 * Explicit persisted finite-capacity material-flow membership for one Operations workcell.
 *
 * <p>Input and output buffer identity is high-cardinality plant configuration. It remains
 * server-owned and never enters BlockState, analog redstone, or proximity discovery.</p>
 */
public record OperationWorkcellBufferBinding(
        String workcellId,
        String inputBufferId,
        String outputBufferId
) {
    public OperationWorkcellBufferBinding {
        workcellId = normalize(workcellId);
        inputBufferId = normalize(inputBufferId);
        outputBufferId = normalize(outputBufferId);
    }

    public boolean validBinding() {
        return !workcellId.isBlank()
                && !inputBufferId.isBlank()
                && !outputBufferId.isBlank()
                && !inputBufferId.equals(outputBufferId);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
