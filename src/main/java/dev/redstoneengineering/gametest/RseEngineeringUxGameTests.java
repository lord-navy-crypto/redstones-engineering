package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.diagnostics.topology.EngineeringTopologyView;
import dev.redstoneengineering.diagnostics.topology.TopologyFaceSnapshot;
import dev.redstoneengineering.diagnostics.topology.TopologyLinkStatus;
import dev.redstoneengineering.diagnostics.topology.TopologyVisualizationSnapshot;
import dev.redstoneengineering.ui.menu.EngineeringDeviceMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
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

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void topologyRoleProjectionUsesFormalPortContract(GameTestHelper helper) {
        Block source = new TestPortBlock(List.of(
                port("OUT", Direction.EAST, EngineeringDomain.REDSTONE, PortDirection.OUTPUT)
        ));
        Block sink = new TestPortBlock(List.of(
                port("IN", Direction.WEST, EngineeringDomain.REDSTONE, PortDirection.INPUT)
        ));
        Block series = new TestPortBlock(List.of(
                port("IN", Direction.WEST, EngineeringDomain.REDSTONE, PortDirection.INPUT),
                port("OUT", Direction.EAST, EngineeringDomain.REDSTONE, PortDirection.OUTPUT)
        ));
        Block observer = new TestPortBlock(List.of(
                measurementPort("TAP", Direction.NORTH)
        ));
        Block passive = new TestPortBlock(List.of(
                port("A", Direction.WEST, EngineeringDomain.REDSTONE, PortDirection.BIDIRECTIONAL),
                port("B", Direction.EAST, EngineeringDomain.REDSTONE, PortDirection.BIDIRECTIONAL)
        ));
        Block junction = new TestJunctionBlock(List.of(
                port("A", Direction.WEST, EngineeringDomain.REDSTONE, PortDirection.BIDIRECTIONAL),
                port("B", Direction.EAST, EngineeringDomain.REDSTONE, PortDirection.BIDIRECTIONAL),
                port("C", Direction.NORTH, EngineeringDomain.REDSTONE, PortDirection.BIDIRECTIONAL)
        ));

        if (EngineeringDeviceMenu.classifyTopologyRole(source.defaultBlockState()) != EngineeringDeviceMenu.TOPOLOGY_SOURCE
                || EngineeringDeviceMenu.classifyTopologyRole(sink.defaultBlockState()) != EngineeringDeviceMenu.TOPOLOGY_SINK
                || EngineeringDeviceMenu.classifyTopologyRole(series.defaultBlockState()) != EngineeringDeviceMenu.TOPOLOGY_SERIES
                || EngineeringDeviceMenu.classifyTopologyRole(observer.defaultBlockState()) != EngineeringDeviceMenu.TOPOLOGY_OBSERVER
                || EngineeringDeviceMenu.classifyTopologyRole(passive.defaultBlockState()) != EngineeringDeviceMenu.TOPOLOGY_PASSIVE
                || EngineeringDeviceMenu.classifyTopologyRole(junction.defaultBlockState()) != EngineeringDeviceMenu.TOPOLOGY_EXPLICIT_JUNCTION) {
            helper.fail("Engineering topology role projection drifted from the formal port contract", MARKER);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void directionalDomainRotationMovesTheWholeSeriesContract(GameTestHelper helper) {
        BlockPos relative = MARKER;
        BlockPos absolute = helper.absolutePos(relative);
        BlockState north = RedstoneEngineering.PNEUMATIC_CHECK_VALVE.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.NORTH);
        helper.getLevel().setBlock(absolute, north, Block.UPDATE_ALL);

        if (!DirectionalDomainBlock.rotateSeriesAxis(helper.getLevel(), absolute, true)) {
            helper.fail("Directional domain series-axis rotation was rejected", relative);
            return;
        }

        BlockState rotated = helper.getLevel().getBlockState(absolute);
        if (rotated.getValue(DirectionalDomainBlock.FACING) != Direction.EAST) {
            helper.fail("Series-axis rotation did not move FRONT from NORTH to EAST", relative);
            return;
        }

        if (!(rotated.getBlock() instanceof EngineeringPortProvider provider)) {
            helper.fail("Directional domain device lost EngineeringPortProvider contract", relative);
            return;
        }

        EngineeringPort input = provider.engineeringPort(rotated, Direction.WEST).orElse(null);
        EngineeringPort output = provider.engineeringPort(rotated, Direction.EAST).orElse(null);
        if (input == null || output == null
                || input.direction() != PortDirection.INPUT
                || output.direction() != PortDirection.OUTPUT
                || provider.engineeringPort(rotated, Direction.NORTH).isPresent()
                || provider.engineeringPort(rotated, Direction.SOUTH).isPresent()) {
            helper.fail("Rotation must preserve exactly one BACK input and one FRONT output on the new axis", relative);
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

    private static EngineeringPort measurementPort(String label, Direction side) {
        return new EngineeringPort(
                label, side, EngineeringDomain.REDSTONE, PortKind.MEASUREMENT, PortDirection.INPUT, false, "signal"
        );
    }

    private static class TestPortBlock extends Block implements EngineeringPortProvider {
        private final List<EngineeringPort> ports;

        TestPortBlock(List<EngineeringPort> ports) {
            super(BlockBehaviour.Properties.of());
            this.ports = List.copyOf(ports);
        }

        @Override
        public List<EngineeringPort> engineeringPorts(BlockState state) {
            return ports;
        }
    }

    private static final class TestJunctionBlock extends TestPortBlock {
        TestJunctionBlock(List<EngineeringPort> ports) {
            super(ports);
        }
    }
}
