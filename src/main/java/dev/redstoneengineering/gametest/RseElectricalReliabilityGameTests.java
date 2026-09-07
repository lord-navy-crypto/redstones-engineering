package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.diagnostics.ElectricalReliabilityAssessment;
import dev.redstoneengineering.diagnostics.events.SystemEventKind;
import dev.redstoneengineering.diagnostics.events.SystemEventRecord;
import dev.redstoneengineering.diagnostics.events.SystemEventScope;
import dev.redstoneengineering.diagnostics.events.SystemEventTimeline;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Runtime guards for evidence-based electrical reliability metrics. */
public final class RseElectricalReliabilityGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseElectricalReliabilityGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 20)
    public static void electricalReliabilityPairsTripRecoveryAndDowntime(GameTestHelper helper) {
        BlockPos asset = helper.absolutePos(new BlockPos(2, 1, 2));
        SystemEventScope scope = new SystemEventScope(asset, 1);

        // Validate the live plant-scoped integration synchronously with event production. The
        // timeline is intentionally a bounded level-wide ring, so a runtime test must not assume
        // that unrelated parallel GameTests leave an old event resident for an arbitrary delay.
        SystemEventTimeline.record(helper.getLevel(), asset, SystemEventKind.ELECTRICAL_TRIP, 2,
                "RELIABILITY_TRIP", "trip evidence");
        ElectricalReliabilityAssessment.Snapshot tripped =
                ElectricalReliabilityAssessment.inspect(helper.getLevel(), scope);
        if (tripped.tripCount() != 1 || tripped.recoveryCount() != 0 || tripped.activeTripCount() != 1) {
            helper.fail("Live reliability projection did not expose the newly recorded trip", new BlockPos(2, 1, 2));
            return;
        }

        SystemEventTimeline.record(helper.getLevel(), asset, SystemEventKind.ELECTRICAL_READY, 0,
                "RELIABILITY_READY", "verified recovery evidence");
        ElectricalReliabilityAssessment.Snapshot recovered =
                ElectricalReliabilityAssessment.inspect(helper.getLevel(), scope);
        if (recovered.tripCount() != 1 || recovered.recoveryCount() != 1 || recovered.activeTripCount() != 0) {
            helper.fail("Electrical reliability did not pair one trip with one recovery", new BlockPos(2, 1, 2));
            return;
        }
        if (recovered.electricalDowntimeTicks() != 0L || recovered.lastRecoveryDurationTicks() != 0L) {
            helper.fail("Same-tick live trip/recovery pair should have zero completed downtime", new BlockPos(2, 1, 2));
            return;
        }
        if (recovered.repeatTripCount() != 0) {
            helper.fail("First trip was incorrectly classified as repeat-trip evidence", new BlockPos(2, 1, 2));
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 20)
    public static void electricalReliabilityTracksRepeatAndOpenProtection(GameTestHelper helper) {
        BlockPos asset = helper.absolutePos(new BlockPos(2, 1, 2));

        // Chronology math is a pure evidence projection. Test it from an explicit ordered record
        // sequence rather than relying on the shared 256-event runtime ring retaining these records
        // while ~200 GameTests emit unrelated evidence in parallel.
        List<SystemEventRecord> chronology = List.of(
                new SystemEventRecord(100L, SystemEventKind.ELECTRICAL_TRIP, asset, 2,
                        "FIRST_TRIP", "first protection incident"),
                new SystemEventRecord(104L, SystemEventKind.ELECTRICAL_READY, asset, 0,
                        "FIRST_READY", "first verified recovery"),
                new SystemEventRecord(107L, SystemEventKind.ELECTRICAL_TRIP, asset, 2,
                        "REPEAT_TRIP", "repeat protection incident")
        );
        ElectricalReliabilityAssessment.Snapshot snapshot =
                ElectricalReliabilityAssessment.project(chronology, 112L);

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
    }
}
