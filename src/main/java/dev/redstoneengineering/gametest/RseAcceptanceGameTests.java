package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.diagnostics.CommissioningSnapshot;
import dev.redstoneengineering.diagnostics.CommissioningStatus;
import dev.redstoneengineering.diagnostics.acceptance.AcceptanceEvidenceComparison;
import dev.redstoneengineering.diagnostics.acceptance.AcceptanceEvidenceRecord;
import dev.redstoneengineering.diagnostics.acceptance.AcceptanceEvidenceStore;
import dev.redstoneengineering.diagnostics.acceptance.AcceptanceEvidenceTimeline;
import dev.redstoneengineering.diagnostics.acceptance.AcceptanceEvidenceTrend;
import dev.redstoneengineering.diagnostics.acceptance.EngineeringAcceptance;
import dev.redstoneengineering.diagnostics.acceptance.EngineeringAcceptancePresentation;
import dev.redstoneengineering.diagnostics.acceptance.EngineeringAcceptanceSnapshot;
import dev.redstoneengineering.diagnostics.acceptance.EngineeringAcceptanceStatus;
import dev.redstoneengineering.diagnostics.topology.TopologyVisualizationSnapshot;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Executable contracts for acceptance, presentation, and captured commissioning-run evidence. */
public final class RseAcceptanceGameTests {
    private static final String TEMPLATE = "empty5x4x5";
    private static final BlockPos MARKER = new BlockPos(2, 1, 2);

    private RseAcceptanceGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void structuralMismatchFailsEvenWithPassingCommissioning(GameTestHelper helper) {
        TopologyVisualizationSnapshot topology = new TopologyVisualizationSnapshot(List.of(), 2, 1, 1);
        EngineeringAcceptanceSnapshot acceptance = EngineeringAcceptance.evaluate(topology, commissioning(96, CommissioningStatus.PASS));
        if (acceptance.status() != EngineeringAcceptanceStatus.FAIL
                || acceptance.topologyIssues() != 1
                || acceptance.issues().stream().noneMatch(i -> EngineeringAcceptance.TOPOLOGY_MISMATCH.equals(i.code()))
                || !acceptance.traceKey().contains("acceptance=FAIL")) {
            helper.fail("Known topology mismatches must fail acceptance even when loop commissioning passes", MARKER);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void acceptanceSeparatesReadinessMarginalAndPass(GameTestHelper helper) {
        TopologyVisualizationSnapshot healthy = new TopologyVisualizationSnapshot(List.of(), 2, 2, 0);
        EngineeringAcceptanceSnapshot unavailable = EngineeringAcceptance.evaluate(healthy, CommissioningSnapshot.unavailable());
        EngineeringAcceptanceSnapshot marginal = EngineeringAcceptance.evaluate(healthy, commissioning(72, CommissioningStatus.MARGINAL));
        EngineeringAcceptanceSnapshot pass = EngineeringAcceptance.evaluate(healthy, commissioning(94, CommissioningStatus.PASS));
        if (unavailable.status() != EngineeringAcceptanceStatus.NOT_READY
                || marginal.status() != EngineeringAcceptanceStatus.MARGINAL
                || pass.status() != EngineeringAcceptanceStatus.PASS
                || !pass.accepted()
                || !pass.issues().isEmpty()) {
            helper.fail("Acceptance must distinguish missing evidence, marginal commissioning, and pass", MARKER);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void acceptancePresentationIsConciseAndTraceable(GameTestHelper helper) {
        TopologyVisualizationSnapshot mismatch = new TopologyVisualizationSnapshot(List.of(), 6, 5, 1);
        EngineeringAcceptanceSnapshot acceptance = EngineeringAcceptance.evaluate(mismatch, commissioning(96, CommissioningStatus.PASS));
        String headline = EngineeringAcceptancePresentation.headline(acceptance);
        String issueLine = EngineeringAcceptancePresentation.firstIssueLine(acceptance);
        if (!headline.equals("Acceptance: FAIL | commissioning=PASS 96/100")
                || !issueLine.contains(EngineeringAcceptance.TOPOLOGY_MISMATCH)
                || !acceptance.traceKey().equals("topology=5/6:issues=1|commissioning=PASS:score=96|acceptance=FAIL")) {
            helper.fail("Acceptance UX must preserve verdict, score, issue code, and deterministic trace evidence", MARKER);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void capturedRunTimelineIsBoundedImmutableAndComparable(GameTestHelper helper) {
        TopologyVisualizationSnapshot healthy = new TopologyVisualizationSnapshot(List.of(), 6, 6, 0);
        AcceptanceEvidenceTimeline timeline = new AcceptanceEvidenceTimeline(2);
        timeline.append(100, 1, EngineeringAcceptance.evaluate(healthy, commissioning(60, CommissioningStatus.MARGINAL)));
        timeline.append(200, 2, EngineeringAcceptance.evaluate(healthy, commissioning(72, CommissioningStatus.MARGINAL)));
        timeline.append(300, 2, EngineeringAcceptance.evaluate(healthy, commissioning(94, CommissioningStatus.PASS)));

        List<AcceptanceEvidenceRecord> records = timeline.records();
        AcceptanceEvidenceComparison comparison = timeline.compareLatestToPrevious().orElse(null);
        boolean immutable = false;
        try {
            records.clear();
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }

        if (records.size() != 2
                || timeline.latest().orElseThrow().sequence() != 3
                || timeline.previous().orElseThrow().sequence() != 2
                || comparison == null
                || comparison.trend() != AcceptanceEvidenceTrend.IMPROVED
                || comparison.scoreDelta() != 22
                || !immutable) {
            helper.fail("Captured acceptance history must remain bounded, immutable to callers, and comparable", MARKER);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void removingPidClearsTransientRuntimeAndEvidence(GameTestHelper helper) {
        BlockPos absolute = helper.absolutePos(MARKER);
        helper.setBlock(MARKER, RedstoneEngineering.PID_CONTROLLER.get().defaultBlockState());

        int[] runtime = RuntimeIntStore.get(helper.getLevel(), "pid", absolute, 22);
        runtime[0] = 73;
        TopologyVisualizationSnapshot healthy = new TopologyVisualizationSnapshot(List.of(), 6, 6, 0);
        AcceptanceEvidenceStore.capture(
                helper.getLevel(), absolute, helper.getLevel().getGameTime(), 2,
                EngineeringAcceptance.evaluate(healthy, commissioning(94, CommissioningStatus.PASS)));

        if (RuntimeIntStore.peek(helper.getLevel(), "pid", absolute) == null
                || AcceptanceEvidenceStore.history(helper.getLevel(), absolute).isEmpty()) {
            helper.fail("GameTest failed to seed PID transient runtime/evidence before removal", MARKER);
            return;
        }

        helper.setBlock(MARKER, Blocks.AIR.defaultBlockState());
        if (RuntimeIntStore.peek(helper.getLevel(), "pid", absolute) != null) {
            helper.fail("Removed PID left stale controller runtime at its former position", MARKER);
            return;
        }
        if (!AcceptanceEvidenceStore.history(helper.getLevel(), absolute).isEmpty()) {
            helper.fail("Removed PID left stale acceptance history for a future controller at the same position", MARKER);
            return;
        }
        helper.succeed();
    }

    private static CommissioningSnapshot commissioning(int score, CommissioningStatus status) {
        return new CommissioningSnapshot(
                true, 12, 12, 8, 0, 5, 9, 0, 0, 24,
                false, false, false, 0, score, status);
    }
}