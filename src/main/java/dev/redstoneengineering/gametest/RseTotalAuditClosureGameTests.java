package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.LogicAnalyzerBlock;
import dev.redstoneengineering.block.MagneticFieldSensorBlock;
import dev.redstoneengineering.block.OscilloscopeBlock;
import dev.redstoneengineering.block.PermanentMagnetBlock;
import dev.redstoneengineering.block.SignalAnalyzerBlock;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Closure tests added by the repository-wide 122-block audit. */
public final class RseTotalAuditClosureGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseTotalAuditClosureGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 30)
    public static void signalAnalyzerPublishesModeTruthAndClearsRuntime(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        BlockState tap = RedstoneEngineering.SIGNAL_ANALYZER.get().defaultBlockState()
                .setValue(SignalAnalyzerBlock.FACING, Direction.WEST)
                .setValue(SignalAnalyzerBlock.MODE, SignalAnalyzerBlock.TAP);
        helper.setBlock(pos, tap);

        List<EngineeringPort> tapPorts = ((EngineeringPortProvider) tap.getBlock()).engineeringPorts(tap);
        if (tapPorts.size() != 1
                || tapPorts.getFirst().side() != Direction.WEST
                || tapPorts.getFirst().domain() != EngineeringDomain.REDSTONE
                || tapPorts.getFirst().kind() != PortKind.MEASUREMENT
                || tapPorts.getFirst().redstoneConnectable()) {
            helper.fail("Signal Analyzer TAP must expose one non-invasive REDSTONE measurement aperture", pos);
            return;
        }

        BlockState inline = tap.setValue(SignalAnalyzerBlock.MODE, SignalAnalyzerBlock.INLINE);
        helper.setBlock(pos, inline);
        List<EngineeringPort> inlinePorts = ((EngineeringPortProvider) inline.getBlock()).engineeringPorts(inline);
        if (inlinePorts.size() != 2
                || inlinePorts.stream().noneMatch(p -> p.side() == Direction.WEST && p.direction() == PortDirection.INPUT && p.redstoneConnectable())
                || inlinePorts.stream().noneMatch(p -> p.side() == Direction.EAST && p.direction() == PortDirection.OUTPUT && p.redstoneConnectable())) {
            helper.fail("Signal Analyzer INLINE must publish physical TEST input and opposite OUT output", pos);
            return;
        }

        BlockPos absolute = helper.absolutePos(pos);
        RuntimeIntStore.get(helper.getLevel(), "signal_analyzer", absolute, 33)[0] = 7;
        helper.setBlock(pos, Blocks.AIR.defaultBlockState());
        if (RuntimeIntStore.peek(helper.getLevel(), "signal_analyzer", absolute) != null) {
            helper.fail("Signal Analyzer removal leaked rolling runtime history", pos);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 20)
    public static void oscilloscopePublishesSixFaceObserverBus(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        BlockState state = RedstoneEngineering.OSCILLOSCOPE.get().defaultBlockState();
        helper.setBlock(pos, state);
        List<EngineeringPort> ports = ((OscilloscopeBlock) state.getBlock()).engineeringPorts(state);
        if (ports.size() != 6
                || ports.stream().anyMatch(EngineeringPort::redstoneConnectable)
                || ports.stream().anyMatch(p -> p.domain() != EngineeringDomain.INSTRUMENT_BUS || p.direction() != PortDirection.INPUT)) {
            helper.fail("Oscilloscope must be a six-face non-redstone Instrument Bus observer", pos);
            return;
        }
        for (Direction side : Direction.values()) {
            if (((OscilloscopeBlock) state.getBlock()).engineeringSnapshot(helper.getLevel(), helper.absolutePos(pos), state, side).isEmpty()) {
                helper.fail("Oscilloscope missing server-backed engineering snapshot on " + side, pos);
                return;
            }
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 20)
    public static void logicAnalyzerPublishesSixFaceObserverBus(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        BlockState state = RedstoneEngineering.LOGIC_ANALYZER.get().defaultBlockState();
        helper.setBlock(pos, state);
        List<EngineeringPort> ports = ((LogicAnalyzerBlock) state.getBlock()).engineeringPorts(state);
        if (ports.size() != 6
                || ports.stream().anyMatch(EngineeringPort::redstoneConnectable)
                || ports.stream().anyMatch(p -> p.domain() != EngineeringDomain.INSTRUMENT_BUS || p.direction() != PortDirection.INPUT)) {
            helper.fail("Logic Analyzer must be a six-face non-redstone Instrument Bus observer", pos);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 30)
    public static void magneticFreeSpaceInterfacesRemainNonWired(GameTestHelper helper) {
        BlockPos magnetPos = new BlockPos(1, 1, 2);
        BlockPos sensorPos = new BlockPos(3, 1, 2);
        BlockState magnet = RedstoneEngineering.PERMANENT_MAGNET.get().defaultBlockState()
                .setValue(PermanentMagnetBlock.STRENGTH, 12);
        BlockState sensor = RedstoneEngineering.MAGNETIC_FIELD_SENSOR.get().defaultBlockState();
        helper.setBlock(magnetPos, magnet);
        helper.setBlock(sensorPos, sensor);

        List<EngineeringPort> magnetPorts = ((PermanentMagnetBlock) magnet.getBlock()).engineeringPorts(magnet);
        if (magnetPorts.size() != 6
                || magnetPorts.stream().anyMatch(EngineeringPort::redstoneConnectable)
                || magnetPorts.stream().anyMatch(p -> p.domain() != EngineeringDomain.IRON_MAGNETIC || p.direction() != PortDirection.OUTPUT)) {
            helper.fail("Permanent magnet free-space field descriptors are inconsistent", magnetPos);
            return;
        }

        helper.runAfterDelay(8, () -> {
            BlockState updated = helper.getBlockState(sensorPos);
            if (updated.getValue(MagneticFieldSensorBlock.FIELD) <= 0) {
                helper.fail("Magnetic field sensor failed to observe nearby permanent magnet", sensorPos);
                return;
            }
            List<EngineeringPort> sensorPorts = ((MagneticFieldSensorBlock) updated.getBlock()).engineeringPorts(updated);
            if (sensorPorts.size() != 6
                    || sensorPorts.stream().anyMatch(EngineeringPort::redstoneConnectable)
                    || sensorPorts.stream().anyMatch(p -> p.domain() != EngineeringDomain.IRON_MAGNETIC || p.kind() != PortKind.MEASUREMENT)) {
                helper.fail("Magnetic sensor must expose non-wired IRON_MAGNETIC measurement apertures", sensorPos);
                return;
            }
            helper.succeed();
        });
    }
}
