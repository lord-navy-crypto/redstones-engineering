package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.diagnostics.OperationsDashboardSnapshot;
import dev.redstoneengineering.diagnostics.OperationsIncidentSummary;
import dev.redstoneengineering.diagnostics.events.RootCauseEvidenceTrace;
import dev.redstoneengineering.diagnostics.events.SystemEventKind;
import dev.redstoneengineering.diagnostics.events.SystemEventScope;
import dev.redstoneengineering.diagnostics.events.SystemEventTimeline;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Runtime guards for operator-facing first-out localization and bounded incident evidence. */
public final class RseOperationsIncidentGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseOperationsIncidentGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void operationsIncidentSummaryLocalizesFirstOutAndCountsFollowUpEvidence(GameTestHelper helper) {
        BlockPos monitor = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos firstOut = monitor.offset(1, 0, -1);
        SystemEventScope scope = new SystemEventScope(monitor, 2);

        SystemEventTimeline.record(helper.getLevel(), firstOut, SystemEventKind.INTERLOCK_TRIPPED, 3,
                "LOCALIZED_FIRST_OUT", "first abnormal evidence for localization");

        helper.runAfterDelay(4, () -> {
            SystemEventTimeline.record(helper.getLevel(), monitor.offset(-1, 0, 1), SystemEventKind.ALARM_RAISED, 2,
                    "FOLLOW_UP_ALARM", "later abnormal observation");
            SystemEventTimeline.record(helper.getLevel(), monitor, SystemEventKind.SEQUENCE_STEP, 0,
                    "FOLLOW_UP_CONTEXT", "later normal observation");

            OperationsDashboardSnapshot dashboard = OperationsDashboardSnapshot.inspect(helper.getLevel(), monitor, scope);
            OperationsIncidentSummary summary = OperationsIncidentSummary.inspect(helper.getLevel(), monitor, dashboard);
            if (!summary.present()) {
                helper.fail("Operations incident summary did not expose latest plant incident", new BlockPos(2, 1, 2));
                return;
            }
            if (summary.firstOutDx() != 1 || summary.firstOutDy() != 0 || summary.firstOutDz() != -1) {
                helper.fail("First-out localization did not preserve monitor-relative source coordinates", new BlockPos(2, 1, 2));
                return;
            }
            if (summary.incidentDurationTicks() != 4L) {
                helper.fail("Incident span did not use authoritative first/last abnormal event ticks", new BlockPos(2, 1, 2));
                return;
            }
            if (summary.downstreamObservations() != 2 || summary.abnormalDownstreamObservations() != 1) {
                helper.fail("Incident follow-up counts did not match chronological server evidence", new BlockPos(2, 1, 2));
                return;
            }
            if (summary.evidenceTraceEntries() != 3) {
                helper.fail("Root-cause evidence trace was not projected into incident summary", new BlockPos(2, 1, 2));
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 30)
    public static void operationsIncidentSummaryPreservesBoundedEvidenceTrace(GameTestHelper helper) {
        BlockPos monitor = helper.absolutePos(new BlockPos(2, 1, 2));
        SystemEventScope scope = new SystemEventScope(monitor, 2);

        SystemEventTimeline.record(helper.getLevel(), monitor, SystemEventKind.ALARM_RAISED, 3,
                "BOUNDED_FIRST_OUT", "incident first-out");
        for (int i = 0; i < 20; i++) {
            SystemEventTimeline.record(helper.getLevel(), monitor, SystemEventKind.SEQUENCE_STEP, 0,
                    "FOLLOW_UP_" + i, "bounded downstream evidence");
        }

        OperationsDashboardSnapshot dashboard = OperationsDashboardSnapshot.inspect(helper.getLevel(), monitor, scope);
        OperationsIncidentSummary summary = OperationsIncidentSummary.inspect(helper.getLevel(), monitor, dashboard);
        if (summary.downstreamObservations() != 16) {
            helper.fail("First-out downstream evidence exceeded or missed its bounded 16-observation contract", new BlockPos(2, 1, 2));
            return;
        }
        if (summary.evidenceTraceEntries() != RootCauseEvidenceTrace.MAX_TRACE_ENTRIES
                || summary.evidenceTraceEntries() != 12) {
            helper.fail("Operator root-cause evidence trace did not retain the bounded 12-entry contract", new BlockPos(2, 1, 2));
            return;
        }
        if (summary.abnormalDownstreamObservations() != 0) {
            helper.fail("Normal follow-up context was incorrectly classified as abnormal evidence", new BlockPos(2, 1, 2));
            return;
        }
        helper.succeed();
    }
}
