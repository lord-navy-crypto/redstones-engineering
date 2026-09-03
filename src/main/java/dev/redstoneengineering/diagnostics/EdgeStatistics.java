package dev.redstoneengineering.diagnostics;

public final class EdgeStatistics {
    private int risingEdges;
    private int fallingEdges;
    private int previous;

    public void accept(int value) {
        int current = value > 0 ? 1 : 0;
        if (previous == 0 && current == 1) risingEdges++;
        if (previous == 1 && current == 0) fallingEdges++;
        previous = current;
    }

    public int risingEdges() {
        return risingEdges;
    }

    public int fallingEdges() {
        return fallingEdges;
    }

    public void reset() {
        risingEdges = 0;
        fallingEdges = 0;
        previous = 0;
    }
}
