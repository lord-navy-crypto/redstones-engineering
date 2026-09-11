package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.ConnectedCableBlock;
import dev.redstoneengineering.block.RedstoneCableJunctionBlock;
import dev.redstoneengineering.block.TransmissionTopology;
import dev.redstoneengineering.physics.DataBusNetwork;
import dev.redstoneengineering.physics.SerialNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Runtime contracts for the shared planar-line + same-medium vertical-junction routing grammar. */
public final class RseSignalJunctionTopologyGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseSignalJunctionTopologyGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 30)
    public static void stackedBusLinesDoNotConnectWithoutJunction(GameTestHelper helper) {
        BlockPos lower = new BlockPos(2, 1, 2);
        BlockPos upper = new BlockPos(2, 2, 2);
        helper.setBlock(lower, RedstoneEngineering.EIGHT_BIT_DATA_BUS.get().defaultBlockState());
        helper.setBlock(upper, RedstoneEngineering.EIGHT_BIT_DATA_BUS.get().defaultBlockState());

        helper.runAfterDelay(2, () -> {
            BlockState lowerState = helper.getLevel().getBlockState(helper.absolutePos(lower));
            BlockState upperState = helper.getLevel().getBlockState(helper.absolutePos(upper));
            if (ConnectedCableBlock.connected(lowerState, Direction.UP)
                    || ConnectedCableBlock.connected(upperState, Direction.DOWN)) {
                helper.fail("Direct vertical bus-to-bus adjacency created a forbidden cable edge", lower);
                return;
            }
            if (DataBusNetwork.collect(helper.getLevel(), helper.absolutePos(lower)).size() != 1) {
                helper.fail("Directly stacked bus lines leaked into one network component", lower);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 30)
    public static void sameMediumJunctionCreatesVerticalBusRoute(GameTestHelper helper) {
        BlockPos lower = new BlockPos(2, 1, 2);
        BlockPos junction = new BlockPos(2, 2, 2);
        BlockPos upper = new BlockPos(2, 3, 2);
        helper.setBlock(lower, RedstoneEngineering.EIGHT_BIT_DATA_BUS.get().defaultBlockState());
        helper.setBlock(upper, RedstoneEngineering.EIGHT_BIT_DATA_BUS.get().defaultBlockState());
        helper.setBlock(junction, RedstoneEngineering.REDSTONE_CABLE_JUNCTION.get().defaultBlockState());

        helper.runAfterDelay(2, () -> {
            BlockState junctionState = helper.getLevel().getBlockState(helper.absolutePos(junction));
            if (junctionState.getValue(RedstoneCableJunctionBlock.MEDIUM) != TransmissionTopology.SignalMedium.DATA_BUS_8) {
                helper.fail("Signal Junction Point did not resolve the adjacent 8-bit medium", junction);
                return;
            }
            if (!ConnectedCableBlock.connected(junctionState, Direction.DOWN)
                    || !ConnectedCableBlock.connected(junctionState, Direction.UP)) {
                helper.fail("Same-medium junction did not expose both vertical routing arms", junction);
                return;
            }
            if (DataBusNetwork.collect(helper.getLevel(), helper.absolutePos(lower)).size() != 3) {
                helper.fail("Same-medium junction failed to join the lower and upper bus components", junction);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 30)
    public static void mixedMediaJunctionIsHardTopologyMismatch(GameTestHelper helper) {
        BlockPos bus = new BlockPos(2, 1, 2);
        BlockPos junction = new BlockPos(2, 2, 2);
        BlockPos serial = new BlockPos(2, 3, 2);
        helper.setBlock(bus, RedstoneEngineering.EIGHT_BIT_DATA_BUS.get().defaultBlockState());
        helper.setBlock(serial, RedstoneEngineering.SERIAL_DATA_LINE.get().defaultBlockState());
        helper.setBlock(junction, RedstoneEngineering.REDSTONE_CABLE_JUNCTION.get().defaultBlockState());

        helper.runAfterDelay(2, () -> {
            BlockState junctionState = helper.getLevel().getBlockState(helper.absolutePos(junction));
            if (junctionState.getValue(RedstoneCableJunctionBlock.MEDIUM) != TransmissionTopology.SignalMedium.MISMATCH) {
                helper.fail("Mixed cable identities did not force the junction into MISMATCH", junction);
                return;
            }
            if (ConnectedCableBlock.connectionCount(junctionState) != 0) {
                helper.fail("Mixed-media junction retained physical arms and could imply false continuity", junction);
                return;
            }
            if (DataBusNetwork.collect(helper.getLevel(), helper.absolutePos(bus)).size() != 1
                    || SerialNetwork.collect(helper.getLevel(), helper.absolutePos(serial)).size() != 1) {
                helper.fail("Mixed-media junction bridged two different information domains", junction);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 30)
    public static void horizontalBusBranchingNeedsNoJunction(GameTestHelper helper) {
        BlockPos center = new BlockPos(2, 1, 2);
        helper.setBlock(center, RedstoneEngineering.EIGHT_BIT_DATA_BUS.get().defaultBlockState());
        helper.setBlock(center.north(), RedstoneEngineering.EIGHT_BIT_DATA_BUS.get().defaultBlockState());
        helper.setBlock(center.east(), RedstoneEngineering.EIGHT_BIT_DATA_BUS.get().defaultBlockState());
        helper.setBlock(center.south(), RedstoneEngineering.EIGHT_BIT_DATA_BUS.get().defaultBlockState());
        helper.setBlock(center.west(), RedstoneEngineering.EIGHT_BIT_DATA_BUS.get().defaultBlockState());

        helper.runAfterDelay(2, () -> {
            BlockState state = helper.getLevel().getBlockState(helper.absolutePos(center));
            if (ConnectedCableBlock.connectionCount(state) != 4
                    || !ConnectedCableBlock.connected(state, Direction.NORTH)
                    || !ConnectedCableBlock.connected(state, Direction.EAST)
                    || !ConnectedCableBlock.connected(state, Direction.SOUTH)
                    || !ConnectedCableBlock.connected(state, Direction.WEST)) {
                helper.fail("Planar information cable did not form a four-way redstone-like branch", center);
                return;
            }
            if (DataBusNetwork.collect(helper.getLevel(), helper.absolutePos(center)).size() != 5) {
                helper.fail("Planar branch visuals and data-bus graph disagreed", center);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 10)
    public static void legacyHorizontalJunctionArmsAreIgnoredImmediately(GameTestHelper helper) {
        BlockState legacy = RedstoneEngineering.REDSTONE_CABLE_JUNCTION.get().defaultBlockState()
                .setValue(RedstoneCableJunctionBlock.MEDIUM, TransmissionTopology.SignalMedium.DATA_BUS_8)
                .setValue(ConnectedCableBlock.NORTH, true)
                .setValue(ConnectedCableBlock.EAST, true)
                .setValue(ConnectedCableBlock.SOUTH, true)
                .setValue(ConnectedCableBlock.WEST, true)
                .setValue(ConnectedCableBlock.UP, true)
                .setValue(ConnectedCableBlock.DOWN, true);

        if (ConnectedCableBlock.connected(legacy, Direction.NORTH)
                || ConnectedCableBlock.connected(legacy, Direction.EAST)
                || ConnectedCableBlock.connected(legacy, Direction.SOUTH)
                || ConnectedCableBlock.connected(legacy, Direction.WEST)) {
            helper.fail("Legacy horizontal Junction arm bits remained effective after the vertical-only migration", new BlockPos(2, 1, 2));
            return;
        }
        if (!ConnectedCableBlock.connected(legacy, Direction.UP)
                || !ConnectedCableBlock.connected(legacy, Direction.DOWN)
                || ConnectedCableBlock.connectionCount(legacy) != 2) {
            helper.fail("Legacy Junction effective topology did not collapse to the UP/DOWN contract", new BlockPos(2, 1, 2));
            return;
        }
        helper.succeed();
    }
}
