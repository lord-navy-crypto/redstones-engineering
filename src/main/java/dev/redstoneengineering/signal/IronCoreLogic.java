package dev.redstoneengineering.signal;

/**
 * Pure bounded soft-iron magnetization model.
 *
 * Strong applied field drives magnetization quickly. When the field is removed, soft iron
 * retains only a small remanent component and then relaxes toward zero. Incomplete field
 * evidence freezes the magnetic state instead of fabricating a change from unknown surroundings.
 */
public final class IronCoreLogic {
    private IronCoreLogic() {}

    public static int approach(int value, int target, int step) {
        int boundedValue = Math.max(0, Math.min(15, value));
        int boundedTarget = Math.max(0, Math.min(15, target));
        int boundedStep = Math.max(1, step);
        if (boundedTarget > boundedValue) return Math.min(boundedTarget, boundedValue + boundedStep);
        if (boundedTarget < boundedValue) return Math.max(boundedTarget, boundedValue - boundedStep);
        return boundedValue;
    }

    /** Small short-lived residual field after prior strong excitation. */
    public static int remanentFloor(int currentMagnetization) {
        return currentMagnetization >= 8 ? 2 : currentMagnetization >= 4 ? 1 : 0;
    }

    public static int nextMagnetization(int current, int appliedField, boolean completeEvidence) {
        int magnetization = Math.max(0, Math.min(15, current));
        int applied = Math.max(0, Math.min(15, appliedField));
        if (!completeEvidence) return magnetization;

        if (applied > 0) {
            return approach(magnetization, applied, 3);
        }

        int floor = remanentFloor(magnetization);
        if (magnetization > floor) return Math.max(floor, magnetization - 2);
        if (magnetization > 0) return magnetization - 1;
        return 0;
    }
}
