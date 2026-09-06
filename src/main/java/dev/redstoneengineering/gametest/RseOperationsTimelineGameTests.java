package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.diagnostics.OperationsDashboardSnapshot;
import dev.redstoneengineering.diagnostics.OperationsEventWindow;
import dev.redstoneengineering.diagnostics.events.SystemEventKind;
import dev.redstoneengineering.diagnostics.events.SystemEventScope;
import dev.redstoneengineering.diagnostics.events.SystemEventTimeline;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Runtime guards for the bounded Operations Monitor event strip and first-out mapping. */
public final class RseOperationsTimelineGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseOperationsTimelineGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 30)
    public static void operationsEventWindowKeepsOrderedBoundedPlantTail(GameTestHelper helper) {
        BlockPos local = helper.absolutePos(new BlockPos(2, 1, 2));
        SystemEventScope scope = new SystemEventScope(local, 2);

        for (int i = 0; i < 12; i++) {
            SystemEventTimeline.record(helper.getLevel(), local, SystemEventKind.SEQUENCE_STEP, 0,
                    String.format(java.util.Locale.ROOT, "OPS_EVENT_%02d", i), "ordered tail evidence");
        }

        OperationsDashboardSnapshot dashboard = OperationsDashboardSnapshot.inspect(helper.getLevel(), local, scope);
        OperationsEventWindow window = OperationsEventWindow.inspect(helper.getLevel(), dashboard);
        if (window.events().size() != OperationsEventWindow.MAX_VISIBLE_EVENTS) {
            helper.fail("Operations event strip did not keep exactly the bounded 8-event tail", new BlockPos(2, 1, 2));
            return;
        }
        if (!"OPS_EVENT_04".equals(window.events().get(0).code())
                || !"OPS_EVENT_11".equals(window.events().get(window.events().size() - 1).code())) {
            helper.fail("Operations event strip lost oldest-to-newest tail ordering", new BlockPos(2, 1, 2));
            return;
        }
        if (!window.events().stream().allMatch(event -> scope.contains(event.source()))) {
            helper.fail("Operations event strip leaked evidence outside its plant scope", new BlockPos(2, 1, 2));
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 30)
    public static void operationsEventWindowMapsFirstOutIntoVisibleChronology(GameTestHelper helper) {
        BlockPos local = helper.absolutePos(new BlockPos(2, 1, 2));
        SystemEventScope scope = new SystemEventScope(local, 2);

        SystemEventTimeline.record(helper.getLevel(), local, SystemEventKind.SEQUENCE_STARTED, 0,
                "INCIDENT_CONTEXT", "normal context before incident");
        SystemEventTimeline.record(helper.getLevel(), local, SystemEventKind.INTERLOCK_TRIPPED, 3,
                "FIRST_OUT_TRIP", "first abnormal evidence");
        SystemEventTimeline.record(helper.getLevel(), local, SystemEventKind.SEQUENCE_STEP, 0,
                "DOWNSTREAM_STEP", "downstream observation");
        SystemEventTimeline.record(helper.getLevel(), local, SystemEventKind.ALARM_RAISED, 2,
                "LATER_ALARM", "later abnormal observation");

        OperationsDashboardSnapshot dashboard = OperationsDashboardSnapshot.inspect(helper.getLevel(), local, scope);
        OperationsEventWindow window = OperationsEventWindow.inspect(helper.getLevel(), dashboard);
        if (window.firstOut().isEmpty()
                || !"FIRST_OUT_TRIP".equals(window.firstOut().get().firstOut().code())) {
            helper.fail("Operations first-out did not select the earliest abnormal event in the incident", new BlockPos(2, 1, 2));
            return;
        }
        int index = window.firstOutIndex();
        if (index != 1 || !"FIRST_OUT_TRIP".equals(window.events().get(index).code())) {
            helper.fail("Operations first-out marker did not map into visible chronological evidence", new BlockPos(2, 1, 2));
            return;
        }
        helper.succeed();
    }
}
