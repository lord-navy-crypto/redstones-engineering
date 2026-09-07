package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.diagnostics.ElectricalReliabilityAssessment;
import dev.redstoneengineering.diagnostics.events.SystemEventKind;
import dev.redstoneengineering.diagnostics.events.SystemEventScope;
import dev.redstoneengineering.diagnostics.events.SystemEventTimeline;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Runtime guards for evidence-based electrical reliability metrics. */
public final class RseElectricalReliabilityGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseElectricalReliabilityGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 30)
    public static void electricalReliabilityPairsTripRecoveryAndDowntime(GameTestHelper helper) {
        BlockPos asset = helper.absolutePos(new BlockPos(2, 1, 2));
        SystemEventScope scope = new SystemEventScope(asset, 1);
        SystemEventTimeline.record(helper.getLevel(), asset, SystemEventKind.ELECTRICAL_TRIP, 2,
                "RELIABILITY_TRIP", "trip evidence");

        helper.runAfterDelay(6, () -> {
            SystemEventTimeline.record(helper.getLevel(), asset, SystemEventKind.ELECTRICAL_READY, 0,
                    "RELIABILITY_READY", "verified recovery evidence");
            ElectricalReliabilityAssessment.Snapshot snapshot =
                    ElectricalReliabilityAssessment.inspect(helper.getLevel(), scope);
            if (snapshot.tripCount() != 1 || snapshot.recoveryCount() != 1 || snapshot.activeTripCount() != 0) {
                helper.fail("Electrical reliability did not pair one trip with one recovery", new BlockPos(2, 1, 2));
                return;
            }
            if (snapshot.electricalDowntimeTicks() != 6L || snapshot.lastRecoveryDurationTicks() != 6L) {
                helper.fail("Electrical downtime/recovery duration did not preserve authoritative event ticks", new BlockPos(2, 1, 2));
                return;
            }
            if (snapshot.repeatTripCount() != 0) {
                helper.fail("First trip was incorrectly classified as repeat-trip evidence", new BlockPos(2, 1, 2));
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void electricalReliabilityTracksRepeatAndOpenProtection(GameTestHelper helper) {
        BlockPos asset = helper.absolutePos(new BlockPos(2, 1, 2));
        SystemEventScope scope = new SystemEventScope(asset, 1);
        SystemEventTimeline.record(helper.getLevel(), asset, SystemEventKind.ELECTRICAL_TRIP, 2,
                "FIRST_TRIP", "first protection incident");
        helper.runAfterDelay(4, () -> SystemEventTimeline.record(helper.getLevel(), asset,
                SystemEventKind.ELECTRICAL_READY, 0, "FIRST_READY", "first verified recovery"));
        helper.runAfterDelay(7, () -> SystemEventTimeline.record(helper.getLevel(), asset,
                SystemEventKind.ELECTRICAL_TRIP, 2, "REPEAT_TRIP", "repeat protection incident"));

        helper.runAfterDelay(12, () -> {
            ElectricalReliabilityAssessment.Snapshot snapshot =
                    ElectricalReliabilityAssessment.inspect(helper.getLevel(), scope);
            if (snapshot.tripCount() != 2 || snapshot.recoveryCount() != 1 || snapshot.repeatTripCount() != 1) {
                helper.fail("Repeat-trip evidence counts were not preserved", new BlockPos(2, 1, 2));
                return;
            }
            if (snapshot.activeTripCount() != 1 || !snapshot.protectionActive()) {
                helper.fail("Open electrical protection incident was not retained as active", new BlockPos(2, 1, 2));
                return;
            }
            if (snapshot.electricalDowntimeTicks() != 9L) {
                helper.fail("Electrical downtime must include completed 4t plus active 5t incident", new BlockPos(2, 1, 2));
                return;
            }
            if (snapshot.lastTripAgeTicks() != 5L || snapshot.lastRecoveryDurationTicks() != 4L) {
                helper.fail("Last-trip age or last recovery duration did not use event chronology", new BlockPos(2, 1, 2));
                return;
            }
            helper.succeed();
        });
    }
}
