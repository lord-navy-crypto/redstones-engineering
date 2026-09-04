package dev.redstoneengineering.diagnostics;

/** Baseline-versus-disturbed result used for repeatable fault-injection commissioning. */
public record CommissioningComparison(
        CommissioningSnapshot baseline,
        CommissioningSnapshot disturbed,
        int scoreLoss,
        int settlingPenaltyTicks,
        int overshootIncrease,
        int saturationIncrease,
        boolean robust
) {}
