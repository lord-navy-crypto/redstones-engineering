package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.AnalogIndicatorBlock;
import dev.redstoneengineering.block.ConnectedCableBlock;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.PrecisionFilterBlock;
import dev.redstoneengineering.block.RedstoneCableJunctionBlock;
import dev.redstoneengineering.block.RedstoneCableTerminalBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.RedstoneSignalCableBlock;
import dev.redstoneengineering.block.SignalProbeBlock;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.instrument.InstrumentNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Second block-by-block acceptance campaign: laboratory wiring, signal distribution,
 * basic measurement endpoints and the precision slew filter.
 */
public final class RseSecondEightAcceptanceGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseSecondEightAcceptanceGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void instrumentCableCarriesRemoteProbeChannel(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(0, 1, 2);
        BlockPos probePos = new BlockPos(1, 1, 2);
        BlockPos cableA = new BlockPos(2, 1, 2);
        BlockPos cableB = new BlockPos(3, 1, 2);
        BlockPos scopePos = new BlockPos(4, 1, 2);

        helper.setBlock(sourcePos, reference(Direction.EAST, 11));
        helper.setBlock(probePos, probe(Direction.WEST, 0));
        helper.setBlock(cableA, RedstoneEngineering.INSTRUMENT_CABLE.get().defaultBlockState());
        helper.setBlock(cableB, RedstoneEngineering.INSTRUMENT_CABLE.get().defaultBlockState());
        helper.setBlock(scopePos, RedstoneEngineering.OSCILLOSCOPE.get().defaultBlockState());

        helper.runAfterDelay(3, () -> {
            InstrumentNetwork.ProbeSnapshot snapshot = InstrumentNetwork.scan(
                    helper.getLevel(), helper.absolutePos(scopePos));
            if (!snapshot.valid(0) || snapshot.values()[0] != 11) {
                helper.fail("Remote channel A did not reach the instrument through the cable bus", scopePos);
                return;
            }
            if (snapshot.cableNodes() != 2 || snapshot.probeNodes() != 1 || !snapshot.bounded()) {
                helper.fail("Instrument bus topology snapshot did not match the two-cable/one-probe network", cableA);
                return;
            }
            if (!ConnectedCableBlock.connected(helper.getBlockState(cableA), Direction.WEST)
                    || !ConnectedCableBlock.connected(helper.getBlockState(cableA), Direction.EAST)
                    || !ConnectedCableBlock.connected(helper.getBlockState(cableB), Direction.WEST)
                    || !ConnectedCableBlock.connected(helper.getBlockState(cableB), Direction.EAST)) {
                helper.fail("Instrument cable connection state is not symmetric across the remote probe path", cableA);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void instrumentBusFlagsDuplicateProbeChannel(GameTestHelper helper) {
        BlockPos scopePos = new BlockPos(2, 1, 2);
        BlockPos northCable = new BlockPos(2, 1, 1);
        BlockPos northProbe = new BlockPos(2, 1, 0);
        BlockPos southCable = new BlockPos(2, 1, 3);
        BlockPos southProbe = new BlockPos(2, 1, 4);

        helper.setBlock(scopePos, RedstoneEngineering.LOGIC_ANALYZER.get().defaultBlockState());
        helper.setBlock(northCable, RedstoneEngineering.INSTRUMENT_CABLE.get().defaultBlockState());
        helper.setBlock(northProbe, probe(Direction.NORTH, 0));
        helper.setBlock(southCable, RedstoneEngineering.INSTRUMENT_CABLE.get().defaultBlockState());
        helper.setBlock(southProbe, probe(Direction.SOUTH, 0));

        helper.runAfterDelay(3, () -> {
            InstrumentNetwork.ProbeSnapshot snapshot = InstrumentNetwork.scan(
                    helper.getLevel(), helper.absolutePos(scopePos));
            if (snapshot.counts()[0] != 2
                    || snapshot.valid(0)
                    || snapshot.duplicateChannels() != 1
                    || !"AMBIGUOUS".equals(snapshot.status(0))
                    || !"AMBIGUOUS".equals(snapshot.integrity())) {
                helper.fail("Duplicate instrument channel A must be explicit rather than silently choosing one probe", scopePos);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void signalProbePortsExposeMeasurementAndBusBoundary(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(1, 1, 2);
        BlockPos probePos = new BlockPos(2, 1, 2);
        helper.setBlock(sourcePos, reference(Direction.EAST, 9));
        helper.setBlock(probePos, probe(Direction.WEST, 2));

        SignalProbeBlock probe = RedstoneEngineering.SIGNAL_PROBE.get();
        BlockState state = helper.getBlockState(probePos);
        EngineeringPortProvider provider = probe;
        var test = provider.engineeringPort(state, Direction.WEST).orElse(null);
        var bus = provider.engineeringPort(state, Direction.EAST).orElse(null);

        if (test == null
                || test.domain() != EngineeringDomain.REDSTONE
                || test.kind() != PortKind.MEASUREMENT
                || test.direction() != PortDirection.INPUT
                || test.redstoneConnectable()) {
            helper.fail("Signal Probe TEST face must be a non-invasive REDSTONE measurement input", probePos);
            return;
        }
        if (bus == null
                || bus.domain() != EngineeringDomain.INSTRUMENT_BUS
                || bus.kind() != PortKind.BUS
                || bus.direction() != PortDirection.OUTPUT
                || !bus.label().contains("C")) {
            helper.fail("Signal Probe opposite face must expose its selected instrument-bus channel", probePos);
            return;
        }
        if (probe.canConnectRedstone(state, helper.getLevel(), helper.absolutePos(probePos), Direction.EAST)
                || probe.canConnectRedstone(state, helper.getLevel(), helper.absolutePos(probePos), Direction.WEST)) {
            helper.fail("Signal Probe became an invasive vanilla-redstone electrical connection", probePos);
            return;
        }

        var testSnapshot = provider.engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(probePos), state, Direction.WEST).orElse(null);
        var busSnapshot = provider.engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(probePos), state, Direction.EAST).orElse(null);
        if (testSnapshot == null || busSnapshot == null
                || Math.round(testSnapshot.value()) != 9
                || Math.round(busSnapshot.value()) != 9) {
            helper.fail("Signal Probe engineering snapshots must expose the same direction-aware sampled value", probePos);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 70)
    public static void redstoneCableAttenuatesAcrossSegmentsAndClears(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(0, 1, 2);
        BlockPos inputTerminal = new BlockPos(1, 1, 2);
        BlockPos cableA = new BlockPos(2, 1, 2);
        BlockPos cableB = new BlockPos(3, 1, 2);
        BlockPos outputTerminal = new BlockPos(4, 1, 2);

        helper.setBlock(sourcePos, reference(Direction.EAST, 15));
        helper.setBlock(inputTerminal, terminal(Direction.WEST, false));
        helper.setBlock(cableA, RedstoneEngineering.REDSTONE_SIGNAL_CABLE.get().defaultBlockState());
        helper.setBlock(cableB, RedstoneEngineering.REDSTONE_SIGNAL_CABLE.get().defaultBlockState());
        helper.setBlock(outputTerminal, terminal(Direction.EAST, true));

        helper.runAfterDelay(4, () -> {
            int a = RedstoneSignalCableBlock.power(helper.getLevel(), helper.absolutePos(cableA));
            int b = RedstoneSignalCableBlock.power(helper.getLevel(), helper.absolutePos(cableB));
            int terminalPower = helper.getBlockState(outputTerminal).getValue(RedstoneCableTerminalBlock.POWER);
            if (a != 15 || b != 14 || terminalPower != 14) {
                helper.fail("Insulated cable must preserve the boundary injection then lose one level per additional cable segment", cableB);
                return;
            }

            helper.setBlock(sourcePos, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(4, () -> {
                if (RedstoneSignalCableBlock.power(helper.getLevel(), helper.absolutePos(cableA)) != 0
                        || RedstoneSignalCableBlock.power(helper.getLevel(), helper.absolutePos(cableB)) != 0
                        || helper.getBlockState(outputTerminal).getValue(RedstoneCableTerminalBlock.POWER) != 0) {
                    helper.fail("Removing the vanilla source left stale power in the insulated cable network", cableB);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void junctionProvidesExplicitThreeWayBranch(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(0, 1, 2);
        BlockPos inputTerminal = new BlockPos(1, 1, 2);
        BlockPos cable = new BlockPos(2, 1, 2);
        BlockPos junction = new BlockPos(3, 1, 2);
        BlockPos northOutput = new BlockPos(3, 1, 1);
        BlockPos southOutput = new BlockPos(3, 1, 3);

        helper.setBlock(sourcePos, reference(Direction.EAST, 13));
        helper.setBlock(inputTerminal, terminal(Direction.WEST, false));
        helper.setBlock(cable, RedstoneEngineering.REDSTONE_SIGNAL_CABLE.get().defaultBlockState());
        helper.setBlock(junction, RedstoneEngineering.REDSTONE_CABLE_JUNCTION.get().defaultBlockState());
        helper.setBlock(northOutput, terminal(Direction.NORTH, true));
        helper.setBlock(southOutput, terminal(Direction.SOUTH, true));

        helper.runAfterDelay(4, () -> {
            BlockState junctionState = helper.getBlockState(junction);
            if (ConnectedCableBlock.connectionCount(junctionState) != 3
                    || !RedstoneEngineering.REDSTONE_CABLE_JUNCTION.get().topologyValid(junctionState)
                    || !ConnectedCableBlock.connected(junctionState, Direction.WEST)
                    || !ConnectedCableBlock.connected(junctionState, Direction.NORTH)
                    || !ConnectedCableBlock.connected(junctionState, Direction.SOUTH)) {
                helper.fail("Redstone Cable Junction must be the explicit valid three-way branch primitive", junction);
                return;
            }
            int junctionPower = RedstoneCableJunctionBlock.power(helper.getLevel(), helper.absolutePos(junction));
            int north = helper.getBlockState(northOutput).getValue(RedstoneCableTerminalBlock.POWER);
            int south = helper.getBlockState(southOutput).getValue(RedstoneCableTerminalBlock.POWER);
            if (junctionPower != 13 || north != 13 || south != 13) {
                helper.fail("Junction branch did not distribute one authoritative cable signal to both output terminals", junction);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void plainSignalCableRejectsImplicitThreeWayBranch(GameTestHelper helper) {
        BlockPos center = new BlockPos(2, 1, 2);
        helper.setBlock(center, RedstoneEngineering.REDSTONE_SIGNAL_CABLE.get().defaultBlockState());
        helper.setBlock(center.north(), RedstoneEngineering.REDSTONE_SIGNAL_CABLE.get().defaultBlockState());
        helper.setBlock(center.south(), RedstoneEngineering.REDSTONE_SIGNAL_CABLE.get().defaultBlockState());
        helper.setBlock(center.east(), RedstoneEngineering.REDSTONE_SIGNAL_CABLE.get().defaultBlockState());

        helper.runAfterDelay(3, () -> {
            BlockState state = helper.getBlockState(center);
            if (ConnectedCableBlock.connectionCount(state) != 3) {
                helper.fail("Precondition failed: plain cable did not observe its three neighboring cable arms", center);
                return;
            }
            if (RedstoneEngineering.REDSTONE_SIGNAL_CABLE.get().topologyValid(state)) {
                helper.fail("Plain insulated cable silently accepted a branch that must use a Cable Junction", center);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 70)
    public static void referenceSourceDrivesTerminalCableAndAnalogIndicator(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(0, 1, 2);
        BlockPos inputTerminal = new BlockPos(1, 1, 2);
        BlockPos cable = new BlockPos(2, 1, 2);
        BlockPos outputTerminal = new BlockPos(3, 1, 2);
        BlockPos indicator = new BlockPos(4, 1, 2);

        helper.setBlock(sourcePos, reference(Direction.EAST, 12));
        helper.setBlock(inputTerminal, terminal(Direction.WEST, false));
        helper.setBlock(cable, RedstoneEngineering.REDSTONE_SIGNAL_CABLE.get().defaultBlockState());
        helper.setBlock(outputTerminal, terminal(Direction.EAST, true));
        helper.setBlock(indicator, indicator(Direction.EAST));

        helper.runAfterDelay(4, () -> {
            BlockState indicatorState = helper.getBlockState(indicator);
            if (indicatorState.getValue(AnalogIndicatorBlock.LEVEL) != 12) {
                helper.fail("Analog Indicator did not reproduce the reference signal delivered through the terminal/cable boundary", indicator);
                return;
            }
            EngineeringPortProvider provider = RedstoneEngineering.ANALOG_INDICATOR.get();
            var port = provider.engineeringPort(indicatorState, Direction.WEST).orElse(null);
            if (port == null || port.kind() != PortKind.MEASUREMENT || port.direction() != PortDirection.INPUT) {
                helper.fail("Analog Indicator must identify as a read-only measurement endpoint", indicator);
                return;
            }

            helper.setBlock(sourcePos, reference(Direction.EAST, 7));
            helper.runAfterDelay(4, () -> {
                if (helper.getBlockState(indicator).getValue(AnalogIndicatorBlock.LEVEL) != 7) {
                    helper.fail("Analog Indicator did not track a live reference-source change through the cable boundary", indicator);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 90)
    public static void precisionFilterSlewRateIsBoundedAndSymmetric(GameTestHelper helper) {
        BlockPos slowSource = new BlockPos(0, 1, 1);
        BlockPos slowFilter = new BlockPos(1, 1, 1);
        BlockPos slowIndicator = new BlockPos(2, 1, 1);
        BlockPos fastSource = new BlockPos(0, 1, 3);
        BlockPos fastFilter = new BlockPos(1, 1, 3);
        BlockPos fastIndicator = new BlockPos(2, 1, 3);

        helper.setBlock(slowSource, reference(Direction.EAST, 15));
        helper.setBlock(slowFilter, filter(Direction.EAST, 1));
        helper.setBlock(slowIndicator, indicator(Direction.EAST));
        helper.setBlock(fastSource, reference(Direction.EAST, 15));
        helper.setBlock(fastFilter, filter(Direction.EAST, 4));
        helper.setBlock(fastIndicator, indicator(Direction.EAST));

        helper.runAfterDelay(3, () -> {
            int slow = helper.getBlockState(slowFilter).getValue(DirectionalSignalBlock.OUTPUT);
            int fast = helper.getBlockState(fastFilter).getValue(DirectionalSignalBlock.OUTPUT);
            if (slow <= 0 || fast <= slow || fast < 8) {
                helper.fail("Precision Filter RATE=4 must approach a positive step materially faster than RATE=1", fastFilter);
                return;
            }

            helper.runAfterDelay(18, () -> {
                if (helper.getBlockState(slowFilter).getValue(DirectionalSignalBlock.OUTPUT) != 15
                        || helper.getBlockState(fastFilter).getValue(DirectionalSignalBlock.OUTPUT) != 15
                        || helper.getBlockState(slowIndicator).getValue(AnalogIndicatorBlock.LEVEL) != 15
                        || helper.getBlockState(fastIndicator).getValue(AnalogIndicatorBlock.LEVEL) != 15) {
                    helper.fail("Both precision filters must eventually settle exactly on the 0..15 commanded value", slowFilter);
                    return;
                }

                helper.setBlock(slowSource, reference(Direction.EAST, 0));
                helper.setBlock(fastSource, reference(Direction.EAST, 0));
                helper.runAfterDelay(3, () -> {
                    int fallingSlow = helper.getBlockState(slowFilter).getValue(DirectionalSignalBlock.OUTPUT);
                    int fallingFast = helper.getBlockState(fastFilter).getValue(DirectionalSignalBlock.OUTPUT);
                    if (fallingFast >= fallingSlow) {
                        helper.fail("Precision Filter slew-rate ordering must remain symmetric on a falling step", fastFilter);
                        return;
                    }
                    helper.runAfterDelay(18, () -> {
                        if (helper.getBlockState(slowFilter).getValue(DirectionalSignalBlock.OUTPUT) != 0
                                || helper.getBlockState(fastFilter).getValue(DirectionalSignalBlock.OUTPUT) != 0) {
                            helper.fail("Precision Filter retained stale output after sufficient falling-step settling time", slowFilter);
                            return;
                        }
                        helper.succeed();
                    });
                });
            });
        });
    }

    private static BlockState reference(Direction facing, int power) {
        return RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, facing)
                .setValue(RedstoneReferenceSourceBlock.POWER, power);
    }

    private static BlockState probe(Direction testSide, int channel) {
        return RedstoneEngineering.SIGNAL_PROBE.get().defaultBlockState()
                .setValue(SignalProbeBlock.FACING, testSide)
                .setValue(SignalProbeBlock.CHANNEL, channel);
    }

    private static BlockState terminal(Direction vanillaSide, boolean cableToVanilla) {
        return RedstoneEngineering.REDSTONE_CABLE_TERMINAL.get().defaultBlockState()
                .setValue(RedstoneCableTerminalBlock.FACING, vanillaSide)
                .setValue(RedstoneCableTerminalBlock.OUTPUT_MODE, cableToVanilla)
                .setValue(RedstoneCableTerminalBlock.POWER, 0);
    }

    private static BlockState indicator(Direction displaySide) {
        return RedstoneEngineering.ANALOG_INDICATOR.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, displaySide)
                .setValue(AnalogIndicatorBlock.LEVEL, 0);
    }

    private static BlockState filter(Direction outputSide, int rate) {
        return RedstoneEngineering.PRECISION_FILTER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, outputSide)
                .setValue(PrecisionFilterBlock.RATE, rate)
                .setValue(DirectionalSignalBlock.OUTPUT, 0);
    }
}
