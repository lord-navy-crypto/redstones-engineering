package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.EdgeDetectorBlock;
import dev.redstoneengineering.block.LapisPrecisionSourceBlock;
import dev.redstoneengineering.block.LapisSignalLineBlock;
import dev.redstoneengineering.block.PulseShaperBlock;
import dev.redstoneengineering.block.QuartzOscillatorBlock;
import dev.redstoneengineering.block.QuartzTimingLineBlock;
import dev.redstoneengineering.block.RangeSensorBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.SurfaceTraceBlock;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.physics.DomainNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Tenth block-by-block acceptance campaign: signal formation and foundational domain integrity. */
public final class RseTenthEightAcceptanceGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseTenthEightAcceptanceGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void edgeDetectorPulsesOnlyOnConfiguredTransition(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 2);
        BlockPos detector = new BlockPos(2, 1, 2);
        helper.setBlock(source, reference(Direction.EAST, 0));
        helper.setBlock(detector, RedstoneEngineering.EDGE_DETECTOR.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(EdgeDetectorBlock.MODE, 0));

        helper.runAfterDelay(3, () -> {
            BlockPos world = helper.absolutePos(detector);
            if (!EdgeDetectorBlock.initialized(helper.getLevel(), world)
                    || helper.getBlockState(detector).getValue(DirectionalSignalBlock.OUTPUT) != 0) {
                helper.fail("Edge detector did not initialize LOW without fabricating an edge", detector);
                return;
            }
            assertDirectionalContract(helper, detector, RedstoneEngineering.EDGE_DETECTOR.get());
            helper.setBlock(source, reference(Direction.EAST, 15));
            schedule(helper, detector, RedstoneEngineering.EDGE_DETECTOR.get());
            helper.runAfterDelay(2, () -> {
                if (helper.getBlockState(detector).getValue(DirectionalSignalBlock.OUTPUT) != 15
                        || EdgeDetectorBlock.lastInput(helper.getLevel(), world) != 1) {
                    helper.fail("RISING edge did not produce the bounded detector pulse", detector);
                    return;
                }
                helper.runAfterDelay(3, () -> {
                    if (helper.getBlockState(detector).getValue(DirectionalSignalBlock.OUTPUT) != 0
                            || EdgeDetectorBlock.pulseRemaining(helper.getLevel(), world) != 0) {
                        helper.fail("Edge detector pulse did not clear after its scheduled lifetime", detector);
                        return;
                    }
                    helper.succeed();
                });
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 70)
    public static void pulseShaperHonorsConfiguredWidthAndClears(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 2);
        BlockPos shaper = new BlockPos(2, 1, 2);
        helper.setBlock(source, reference(Direction.EAST, 0));
        helper.setBlock(shaper, RedstoneEngineering.PULSE_SHAPER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(PulseShaperBlock.WIDTH, 3));

        helper.runAfterDelay(3, () -> {
            assertDirectionalContract(helper, shaper, RedstoneEngineering.PULSE_SHAPER.get());
            helper.setBlock(source, reference(Direction.EAST, 15));
            schedule(helper, shaper, RedstoneEngineering.PULSE_SHAPER.get());
            helper.runAfterDelay(2, () -> {
                BlockPos world = helper.absolutePos(shaper);
                if (helper.getBlockState(shaper).getValue(DirectionalSignalBlock.OUTPUT) != 15
                        || !PulseShaperBlock.initialized(helper.getLevel(), world)) {
                    helper.fail("Pulse shaper did not start its configured three-tick pulse", shaper);
                    return;
                }
                helper.runAfterDelay(4, () -> {
                    if (helper.getBlockState(shaper).getValue(DirectionalSignalBlock.OUTPUT) != 0
                            || PulseShaperBlock.pulseRemaining(helper.getLevel(), world) != 0) {
                        helper.fail("Pulse shaper output outlived its configured width", shaper);
                        return;
                    }
                    helper.succeed();
                });
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void signalTapDeclaresThroughAndNonInvasiveTapOutputs(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 2);
        BlockPos tap = new BlockPos(2, 1, 2);
        helper.setBlock(source, reference(Direction.EAST, 9));
        helper.setBlock(tap, RedstoneEngineering.SIGNAL_TAP.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));

        helper.runAfterDelay(5, () -> {
            BlockState state = helper.getBlockState(tap);
            EngineeringPortProvider provider = RedstoneEngineering.SIGNAL_TAP.get();
            var back = provider.engineeringPort(state, Direction.WEST).orElse(null);
            var through = provider.engineeringPort(state, Direction.EAST).orElse(null);
            var branch = provider.engineeringPort(state, Direction.NORTH).orElse(null);
            if (provider.engineeringPorts(state).size() != 3
                    || back == null || back.direction() != PortDirection.INPUT
                    || through == null || through.direction() != PortDirection.OUTPUT
                    || branch == null || branch.kind() != PortKind.TAP || branch.direction() != PortDirection.OUTPUT
                    || provider.engineeringPort(state, Direction.SOUTH).isPresent()
                    || state.getValue(DirectionalSignalBlock.OUTPUT) != 9) {
                helper.fail("Signal Tap physical BACK/FRONT/LEFT topology disagreed with its engineering contract", tap);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void rangeSensorSeparatesSensingApertureFromSignalOutput(GameTestHelper helper) {
        BlockPos sensor = new BlockPos(1, 1, 2);
        BlockPos target = new BlockPos(3, 1, 2);
        helper.setBlock(sensor, RedstoneEngineering.RANGE_SENSOR.get().defaultBlockState()
                .setValue(RangeSensorBlock.FACING, Direction.EAST)
                .setValue(RangeSensorBlock.MODE, 0)
                .setValue(RangeSensorBlock.RANGE_MODE, 0)
                .setValue(RangeSensorBlock.RESPONSE, 0));
        helper.setBlock(target, Blocks.STONE.defaultBlockState());

        helper.runAfterDelay(7, () -> {
            BlockState state = helper.getBlockState(sensor);
            EngineeringPortProvider provider = RedstoneEngineering.RANGE_SENSOR.get();
            var output = provider.engineeringPort(state, Direction.WEST).orElse(null);
            BlockPos world = helper.absolutePos(sensor);
            if (RangeSensorBlock.sensingSide(state) != Direction.EAST
                    || RangeSensorBlock.outputSide(state) != Direction.WEST
                    || RangeSensorBlock.detectedDistance(helper.getLevel(), world, state) != 2
                    || state.getValue(RangeSensorBlock.OUTPUT) != 11
                    || provider.engineeringPorts(state).size() != 1
                    || output == null || output.domain() != EngineeringDomain.REDSTONE
                    || output.kind() != PortKind.SENSOR || output.direction() != PortDirection.OUTPUT
                    || provider.engineeringPort(state, Direction.EAST).isPresent()) {
                helper.fail("Range sensor aperture/output separation or proximity transfer was false", sensor);
                return;
            }
            if (!RedstoneEngineering.RANGE_SENSOR.get().canConnectRedstone(
                    state, helper.getLevel(), world, Direction.EAST)
                    || RedstoneEngineering.RANGE_SENSOR.get().canConnectRedstone(
                    state, helper.getLevel(), world, Direction.WEST)) {
                helper.fail("Range sensor vanilla-redstone connectivity disagreed with its WEST output", sensor);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void lapisSignalLineBreakClearsSeparatedIsland(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 2);
        BlockPos lineA = new BlockPos(2, 1, 2);
        BlockPos lineB = new BlockPos(3, 1, 2);
        helper.setBlock(source, RedstoneEngineering.LAPIS_PRECISION_SOURCE.get().defaultBlockState()
                .setValue(LapisPrecisionSourceBlock.VALUE, 60));
        helper.setBlock(lineA, RedstoneEngineering.LAPIS_SIGNAL_LINE.get().defaultBlockState()
                .setValue(SurfaceTraceBlock.WEST, true)
                .setValue(SurfaceTraceBlock.EAST, true));
        helper.setBlock(lineB, RedstoneEngineering.LAPIS_SIGNAL_LINE.get().defaultBlockState()
                .setValue(SurfaceTraceBlock.WEST, true));
        DomainNetwork.recomputeLapis(helper.getLevel(), helper.absolutePos(source));

        helper.runAfterDelay(3, () -> {
            assertFourHorizontalPorts(helper, lineA, RedstoneEngineering.LAPIS_SIGNAL_LINE.get(),
                    EngineeringDomain.LAPIS, PortDirection.BIDIRECTIONAL);
            if (!LapisSignalLineBlock.valid(helper.getLevel(), helper.absolutePos(lineB))
                    || LapisSignalLineBlock.value(helper.getLevel(), helper.absolutePos(lineB)) != 60) {
                helper.fail("Lapis line did not propagate the unique precision source", lineB);
                return;
            }
            helper.setBlock(lineA, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(3, () -> {
                if (LapisSignalLineBlock.valid(helper.getLevel(), helper.absolutePos(lineB))
                        || LapisSignalLineBlock.value(helper.getLevel(), helper.absolutePos(lineB)) != 0) {
                    helper.fail("Breaking a Lapis line left a stale precision value on the separated island", lineB);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void lapisPrecisionSourceDrivesOnlyLapisDomain(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 2);
        BlockPos line = new BlockPos(2, 1, 2);
        helper.setBlock(source, RedstoneEngineering.LAPIS_PRECISION_SOURCE.get().defaultBlockState()
                .setValue(LapisPrecisionSourceBlock.VALUE, 75));
        helper.setBlock(line, RedstoneEngineering.LAPIS_SIGNAL_LINE.get().defaultBlockState());
        DomainNetwork.recomputeLapis(helper.getLevel(), helper.absolutePos(source));

        helper.runAfterDelay(3, () -> {
            assertFourHorizontalPorts(helper, source, RedstoneEngineering.LAPIS_PRECISION_SOURCE.get(),
                    EngineeringDomain.LAPIS, PortDirection.OUTPUT);
            if (!LapisSignalLineBlock.valid(helper.getLevel(), helper.absolutePos(line))
                    || LapisSignalLineBlock.value(helper.getLevel(), helper.absolutePos(line)) != 75
                    || RedstoneEngineering.LAPIS_PRECISION_SOURCE.get().canConnectRedstone(
                    helper.getBlockState(source), helper.getLevel(), helper.absolutePos(source), Direction.WEST)) {
                helper.fail("Lapis precision source leaked domains or failed to drive value 75", source);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 70)
    public static void quartzTimingLineBreakClearsSeparatedClockIsland(GameTestHelper helper) {
        BlockPos oscillator = new BlockPos(1, 1, 2);
        BlockPos lineA = new BlockPos(2, 1, 2);
        BlockPos lineB = new BlockPos(3, 1, 2);
        helper.setBlock(oscillator, RedstoneEngineering.QUARTZ_OSCILLATOR.get().defaultBlockState()
                .setValue(QuartzOscillatorBlock.PERIOD_INDEX, 2));
        helper.setBlock(lineA, RedstoneEngineering.QUARTZ_TIMING_LINE.get().defaultBlockState()
                .setValue(SurfaceTraceBlock.WEST, true)
                .setValue(SurfaceTraceBlock.EAST, true));
        helper.setBlock(lineB, RedstoneEngineering.QUARTZ_TIMING_LINE.get().defaultBlockState()
                .setValue(SurfaceTraceBlock.WEST, true));
        DomainNetwork.recomputeQuartz(helper.getLevel(), helper.absolutePos(oscillator));

        helper.runAfterDelay(4, () -> {
            assertFourHorizontalPorts(helper, lineA, RedstoneEngineering.QUARTZ_TIMING_LINE.get(),
                    EngineeringDomain.QUARTZ, PortDirection.BIDIRECTIONAL);
            if (!QuartzTimingLineBlock.valid(helper.getLevel(), helper.absolutePos(lineB))
                    || QuartzTimingLineBlock.period(helper.getLevel(), helper.absolutePos(lineB)) != 8) {
                helper.fail("Quartz line did not propagate the oscillator's eight-tick clock contract", lineB);
                return;
            }
            helper.setBlock(lineA, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(3, () -> {
                if (QuartzTimingLineBlock.valid(helper.getLevel(), helper.absolutePos(lineB))
                        || QuartzTimingLineBlock.period(helper.getLevel(), helper.absolutePos(lineB)) != 0
                        || QuartzTimingLineBlock.active(helper.getLevel(), helper.absolutePos(lineB))) {
                    helper.fail("Breaking a Quartz line left a ghost clock on the separated island", lineB);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void quartzOscillatorPublishesFourWayClockContract(GameTestHelper helper) {
        BlockPos oscillator = new BlockPos(1, 1, 2);
        BlockPos line = new BlockPos(2, 1, 2);
        helper.setBlock(oscillator, RedstoneEngineering.QUARTZ_OSCILLATOR.get().defaultBlockState()
                .setValue(QuartzOscillatorBlock.PERIOD_INDEX, 1));
        helper.setBlock(line, RedstoneEngineering.QUARTZ_TIMING_LINE.get().defaultBlockState());

        helper.runAfterDelay(5, () -> {
            assertFourHorizontalPorts(helper, oscillator, RedstoneEngineering.QUARTZ_OSCILLATOR.get(),
                    EngineeringDomain.QUARTZ, PortDirection.OUTPUT);
            if (!QuartzTimingLineBlock.valid(helper.getLevel(), helper.absolutePos(line))
                    || QuartzTimingLineBlock.period(helper.getLevel(), helper.absolutePos(line)) != 4
                    || RedstoneEngineering.QUARTZ_OSCILLATOR.get().canConnectRedstone(
                    helper.getBlockState(oscillator), helper.getLevel(), helper.absolutePos(oscillator), Direction.WEST)) {
                helper.fail("Quartz oscillator failed its four-way, non-redstone clock-source contract", oscillator);
                return;
            }
            helper.succeed();
        });
    }

    private static BlockState reference(Direction facing, int power) {
        return RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, facing)
                .setValue(RedstoneReferenceSourceBlock.POWER, power);
    }

    private static void schedule(GameTestHelper helper, BlockPos pos, Block block) {
        helper.getLevel().scheduleTick(helper.absolutePos(pos), block, 1);
    }

    private static void assertDirectionalContract(
            GameTestHelper helper, BlockPos pos, EngineeringPortProvider provider
    ) {
        BlockState state = helper.getBlockState(pos);
        var back = provider.engineeringPort(state, Direction.WEST).orElse(null);
        var front = provider.engineeringPort(state, Direction.EAST).orElse(null);
        if (provider.engineeringPorts(state).size() != 2
                || back == null || back.domain() != EngineeringDomain.REDSTONE
                || back.direction() != PortDirection.INPUT
                || front == null || front.domain() != EngineeringDomain.REDSTONE
                || front.direction() != PortDirection.OUTPUT) {
            helper.fail("Directional signal processor port contract is not BACK input -> FRONT output", pos);
        }
    }

    /**
     * Sources legitimately publish four horizontal ports. Surface traces instead
     * publish only the directions represented by their real connection arms.
     */
    private static void assertFourHorizontalPorts(
            GameTestHelper helper,
            BlockPos pos,
            EngineeringPortProvider provider,
            EngineeringDomain domain,
            PortDirection direction
    ) {
        BlockState state = helper.getBlockState(pos);
        boolean topologyTrace = state.getBlock() instanceof LapisSignalLineBlock
                || state.getBlock() instanceof QuartzTimingLineBlock;

        if (topologyTrace) {
            int expected = 0;
            for (Direction side : Direction.Plane.HORIZONTAL) {
                boolean connected = SurfaceTraceBlock.connected(state, side);
                var port = provider.engineeringPort(state, side).orElse(null);
                if (connected) {
                    expected++;
                    if (port == null || port.domain() != domain || port.direction() != direction) {
                        helper.fail("Surface trace engineering port disagreed with its physical connection arm", pos);
                        return;
                    }
                } else if (port != null) {
                    helper.fail("Surface trace exposed an engineering port with no physical connection arm", pos);
                    return;
                }
            }
            if (provider.engineeringPorts(state).size() != expected) {
                helper.fail("Surface trace engineering-port count disagreed with physical topology", pos);
                return;
            }
        } else if (provider.engineeringPorts(state).size() != 4
                || provider.engineeringPorts(state).stream().anyMatch(port ->
                port.side().getAxis() == Direction.Axis.Y
                        || port.domain() != domain
                        || port.direction() != direction)) {
            helper.fail("Foundational domain source did not expose exactly four horizontal ports", pos);
            return;
        }

        if (provider.engineeringPort(state, Direction.UP).isPresent()
                || provider.engineeringPort(state, Direction.DOWN).isPresent()) {
            helper.fail("Foundational horizontal domain exposed a vertical engineering port", pos);
        }
    }
}
