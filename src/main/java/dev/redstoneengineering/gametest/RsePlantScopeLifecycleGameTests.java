package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.diagnostics.OperationsDashboardSnapshot;
import dev.redstoneengineering.diagnostics.events.FirstOutAnalysis;
import dev.redstoneengineering.diagnostics.events.SystemEventKind;
import dev.redstoneengineering.diagnostics.events.SystemEventScope;
import dev.redstoneengineering.diagnostics.events.SystemEventTimeline;
import dev.redstoneengineering.diagnostics.lifecycle.RuntimePersistenceContract;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Cross-system tests for plant scoping and explicit lifecycle/persistence semantics. */
public final class RsePlantScopeLifecycleGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RsePlantScopeLifecycleGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void plantScopeExcludesUnrelatedRemoteIncident(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 1, 2));
        // GameTests share one Level and run concurrently. Use a deliberately tiny explicit scope here
        // so neighboring test structures cannot contribute unrelated event evidence. Production/default
        // operations views still use DEFAULT_PLANT_RADIUS=32, asserted separately below.
        BlockPos local = anchor;
        BlockPos remote = anchor.offset(SystemEventScope.DEFAULT_PLANT_RADIUS + 40, 0, 0);
        SystemEventScope scope = new SystemEventScope(anchor, 2);
        SystemEventScope defaultScope = SystemEventScope.around(anchor);
        if (defaultScope.radiusBlocks() != SystemEventScope.DEFAULT_PLANT_RADIUS
                || defaultScope.radiusBlocks() != 32) {
            helper.fail("Default plant scope radius changed unexpectedly", new BlockPos(2, 1, 2)); return;
        }

        SystemEventTimeline.record(helper.getLevel(), local, SystemEventKind.INTERLOCK_TRIPPED, 3,
                "LOCAL_PRESSURE_LOW", "Local plant permissive lost");
        SystemEventTimeline.record(helper.getLevel(), remote, SystemEventKind.ALARM_RAISED, 3,
                "REMOTE_FIRE_ALARM", "Unrelated remote plant event");

        var scopedEvents = SystemEventTimeline.within(helper.getLevel(), scope);
        if (scopedEvents.stream().noneMatch(event -> "LOCAL_PRESSURE_LOW".equals(event.code()))) {
            helper.fail("Plant scope omitted its local event", new BlockPos(2, 1, 2)); return;
        }
        if (scopedEvents.stream().anyMatch(event -> "REMOTE_FIRE_ALARM".equals(event.code()))) {
            helper.fail("Plant scope leaked an unrelated remote event", new BlockPos(2, 1, 2)); return;
        }
        var firstOut = FirstOutAnalysis.latestWithin(helper.getLevel(), scope);
        if (firstOut.isEmpty() || !"LOCAL_PRESSURE_LOW".equals(firstOut.get().firstOut().code())) {
            helper.fail("Plant-scoped first-out did not select local incident evidence", new BlockPos(2, 1, 2)); return;
        }
        OperationsDashboardSnapshot dashboard = OperationsDashboardSnapshot.inspect(helper.getLevel(), anchor, scope);
        if (!dashboard.eventScope().equals(scope)) {
            helper.fail("Operations dashboard did not retain explicit plant scope", new BlockPos(2, 1, 2)); return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 20)
    public static void persistenceContractKeepsSafetyCriticalBoundariesExplicit(GameTestHelper helper) {
        var sequence = RuntimePersistenceContract.find("SequenceController", "current step");
        var alarm = RuntimePersistenceContract.find("AlarmProcessor", "latched");
        var faultMode = RuntimePersistenceContract.find("FaultInjector", "configured fault mode");
        var events = RuntimePersistenceContract.find("SystemEventTimeline", "bounded event evidence");
        var pidRuntime = RuntimePersistenceContract.find("PidController", "integral/derivative");

        if (sequence.survivesServerRestart()) {
            helper.fail("Sequence runtime must not silently resume after restart", new BlockPos(2, 1, 2)); return;
        }
        if (pidRuntime.survivesServerRestart()) {
            helper.fail("PID dynamic runtime must not survive restart under current contract", new BlockPos(2, 1, 2)); return;
        }
        if (!faultMode.survivesServerRestart()
                || faultMode.currentStorage() != RuntimePersistenceContract.StorageClass.BLOCK_STATE_DURABLE) {
            helper.fail("Fault mode configuration must remain durable BlockState", new BlockPos(2, 1, 2)); return;
        }
        if (events.clearedOnBlockRemoval()) {
            helper.fail("Historical event evidence must not vanish merely because its source block is removed", new BlockPos(2, 1, 2)); return;
        }
        if (alarm.futureDirection() != RuntimePersistenceContract.FutureDirection.CONSIDER_DURABLE_RECORDER) {
            helper.fail("Alarm evidence persistence direction is not explicit", new BlockPos(2, 1, 2)); return;
        }
        helper.succeed();
    }
}
