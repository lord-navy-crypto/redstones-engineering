package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.diagnostics.topology.EngineeringTopologyView;
import dev.redstoneengineering.diagnostics.topology.TopologyFaceSnapshot;
import dev.redstoneengineering.diagnostics.topology.TopologyLinkStatus;
import dev.redstoneengineering.diagnostics.topology.TopologyVisualizationSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** Executable contracts for Alpha 1.0.17 engineering UX/topology projections. */
public final class RseEngineeringUxGameTests {
    private static final String TEMPLATE = "empty5x4x5";
    private static final BlockPos MARKER = new BlockPos(2, 1, 2);

    private RseEngineeringUxGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void compatibilityProjectionDistinguishesTopologyFaults(GameTestHelper helper) {
        EngineeringPort redstoneOut = port("OUT", Direction.EAST, EngineeringDomain.REDSTONE, PortDirection.OUTPUT);
        EngineeringPort redstoneIn = port("IN", Direction.WEST, EngineeringDomain.REDSTONE, PortDirection.INPUT);
        EngineeringPort redstoneOutPeer = port("OUT2", Direction.WEST, EngineeringDomain.REDSTONE, PortDirection.OUTPUT);
        EngineeringPort copperIn = port("COPPER", Direction.WEST, EngineeringDomain.COPPER, PortDirection.INPUT);

        if (EngineeringTopologyView.classify(redstoneOut, redstoneIn) != TopologyLinkStatus.CONNECTED
                || EngineeringTopologyView.classify(redstoneOut, copperIn) != TopologyLinkStatus.DOMAIN_MISMATCH
                || EngineeringTopologyView.classify(redstoneOut, redstoneOutPeer) != TopologyLinkStatus.DIRECTION_MISMATCH) {
            helper.fail("Topology projection must reuse Engineering Port domain/direction compatibility", MARKER);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void visualizationSnapshotIsImmutableAndCountsIssues(GameTestHelper helper) {
        EngineeringPort local = port("CONTROL", Direction.NORTH, EngineeringDomain.REDSTONE, PortDirection.OUTPUT);
        List<TopologyFaceSnapshot> mutable = new ArrayList<>();
        mutable.add(new TopologyFaceSnapshot(
                Direction.NORTH, local, null, TopologyLinkStatus.DOMAIN_MISMATCH,
                "redstoneengineering:copper_cable_junction", "INSULATED_REDSTONE != COPPER"));
        TopologyVisualizationSnapshot snapshot = new TopologyVisualizationSnapshot(mutable, 1, 0, 1);
        mutable.clear();

        if (snapshot.faces().size() != 1
                || snapshot.issueCount() != 1
                || !snapshot.faces().getFirst().topologyIssue()
                || !snapshot.faces().getFirst().compact().contains("DOMAIN_MISMATCH")
                || !snapshot.summary().contains("issues=1")) {
            helper.fail("Topology visualization snapshot must be immutable, compact, and preserve issue counts", MARKER);
            return;
        }
        helper.succeed();
    }

    private static EngineeringPort port(
            String label,
            Direction side,
            EngineeringDomain domain,
            PortDirection direction
    ) {
        return new EngineeringPort(label, side, domain, PortKind.CONTROL, direction, true, "signal");
    }
}
