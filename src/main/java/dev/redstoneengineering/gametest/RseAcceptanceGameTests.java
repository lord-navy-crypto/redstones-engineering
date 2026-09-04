package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.diagnostics.CommissioningSnapshot;
import dev.redstoneengineering.diagnostics.CommissioningStatus;
import dev.redstoneengineering.diagnostics.acceptance.EngineeringAcceptance;
import dev.redstoneengineering.diagnostics.acceptance.EngineeringAcceptanceSnapshot;
import dev.redstoneengineering.diagnostics.acceptance.EngineeringAcceptanceStatus;
import dev.redstoneengineering.diagnostics.topology.TopologyVisualizationSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Executable contracts for Alpha 1.0.18 engineering acceptance and traceability. */
public final class RseAcceptanceGameTests {
    private static final String TEMPLATE = "empty5x4x5";
    private static final BlockPos MARKER = new BlockPos(2, 1, 2);

    private RseAcceptanceGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void structuralMismatchFailsEvenWithPassingCommissioning(GameTestHelper helper) {
        TopologyVisualizationSnapshot topology = new TopologyVisualizationSnapshot(List.of(), 2, 1, 1);
        EngineeringAcceptanceSnapshot acceptance = EngineeringAcceptance.evaluate(
                topology,
                commissioning(96, CommissioningStatus.PASS)
        );

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
        EngineeringAcceptanceSnapshot unavailable = EngineeringAcceptance.evaluate(
                healthy, CommissioningSnapshot.unavailable());
        EngineeringAcceptanceSnapshot marginal = EngineeringAcceptance.evaluate(
                healthy, commissioning(72, CommissioningStatus.MARGINAL));
        EngineeringAcceptanceSnapshot pass = EngineeringAcceptance.evaluate(
                healthy, commissioning(94, CommissioningStatus.PASS));

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

    private static CommissioningSnapshot commissioning(int score, CommissioningStatus status) {
        return new CommissioningSnapshot(
                true,
                12,
                12,
                8,
                0,
                5,
                9,
                0,
                0,
                24,
                false,
                false,
                false,
                0,
                score,
                status
        );
    }
}
