package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.CopperCircuitMeterBlock;
import dev.redstoneengineering.block.CopperResistiveLoadBlock;
import dev.redstoneengineering.block.CopperSeriesResistorBlock;
import dev.redstoneengineering.block.CopperVoltageSourceBlock;
import dev.redstoneengineering.block.CopperWireBlock;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.CopperNetworkSupport;
import dev.redstoneengineering.physics.CopperObservationSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Five-block Copper medium/key-tool validation.
 *
 * <p>The two bounded layouts exercise a real electrical source, Copper wire,
 * a directional series processor, a terminal load or second wire segment, and
 * a non-invasive Circuit Meter. The suite intentionally distinguishes valid 0 V
 * from loss of source evidence and checks retained-meter STALE semantics after
 * a physical medium break.</p>
 */
public final class RseFiveBlockCopperMediumToolsGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private static final BlockPos SOURCE = new BlockPos(0, 1, 2);
    private static final BlockPos INPUT_WIRE = new BlockPos(1, 1, 2);
    private static final BlockPos RESISTOR = new BlockPos(2, 1, 2);
    private static final BlockPos OUTPUT = new BlockPos(3, 1, 2);
    private static final BlockPos METER = new BlockPos(3, 1, 3);

    private RseFiveBlockCopperMediumToolsGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 70)
    public static void loadedCopperCircuitAttenuatesAndMeterDoesNotBecomeAFeed(GameTestHelper helper) {
        helper.setBlock(SOURCE, source(12));
        helper.setBlock(INPUT_WIRE, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());
        helper.setBlock(RESISTOR, resistor(4));
        helper.setBlock(OUTPUT, RedstoneEngineering.COPPER_RESISTIVE_LOAD.get().defaultBlockState()
                .setValue(CopperResistiveLoadBlock.RESISTANCE, 4));
        helper.setBlock(METER, RedstoneEngineering.COPPER_CIRCUIT_METER.get().defaultBlockState()
                .setValue(CopperCircuitMeterBlock.FACING, Direction.NORTH));

        helper.runAfterDelay(22, () -> {
            if (CopperWireBlock.voltage(helper.getLevel(), helper.absolutePos(INPUT_WIRE)) != 12
                    || CopperWireBlock.quality(helper.getLevel(), helper.absolutePos(INPUT_WIRE), helper.getBlockState(INPUT_WIRE)) != PortQuality.VALID) {
                helper.fail("Copper medium did not preserve the live 12 V source evidence", INPUT_WIRE);
                return;
            }

            int resistorOutput = CopperSeriesResistorBlock.outputVoltage(helper.getLevel(), helper.absolutePos(RESISTOR));
            PortQuality resistorQuality = CopperSeriesResistorBlock.outputQuality(helper.getLevel(), helper.absolutePos(RESISTOR));
            if (resistorOutput != 6 || resistorQuality != PortQuality.VALID) {
                helper.fail("Rs=4 with Rload=4 must produce a valid 6 V divider output; actual="
                        + resistorOutput + " quality=" + resistorQuality, RESISTOR);
                return;
            }

            CopperNetworkSupport.TerminalInput loadInput = CopperResistiveLoadBlock.input(
                    helper.getLevel(), helper.absolutePos(OUTPUT));
            if (loadInput.connectedFeeds() != 1 || loadInput.voltage() != 6 || loadInput.quality() != PortQuality.VALID) {
                helper.fail("Terminal load must have exactly one real 6 V feed; the input-only meter must not back-drive it", OUTPUT);
                return;
            }
            if (helper.getBlockState(OUTPUT).getValue(CopperResistiveLoadBlock.VOLTAGE) != 6) {
                helper.fail("Copper load compatibility readback did not settle to the 6 V divider result", OUTPUT);
                return;
            }

            CopperObservationSupport.Observation observed = CopperCircuitMeterBlock.targetObservation(
                    helper.getLevel(), helper.absolutePos(METER), helper.getBlockState(METER));
            var measurement = CopperCircuitMeterBlock.measurement(helper.getLevel(), helper.absolutePos(METER));
            PortQuality meterQuality = CopperCircuitMeterBlock.measurementQuality(
                    helper.getLevel(), helper.absolutePos(METER), helper.getBlockState(METER));
            if (observed.voltage() != 6 || observed.quality() != PortQuality.VALID) {
                helper.fail("Circuit Meter live target observation disagreed with the energized load", METER);
                return;
            }
            if (measurement.sampleCount() <= 0 || meterQuality != PortQuality.VALID
                    || Math.abs(measurement.reading() - 6.0) > 0.35) {
                helper.fail("Circuit Meter failed to record a bounded non-invasive measurement near 6 V", METER);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 120)
    public static void processedCopperMediumPreservesValidZeroThenDisconnectsAndRecovers(GameTestHelper helper) {
        buildProcessedMedium(helper, 12);

        helper.runAfterDelay(18, () -> {
            if (!assertProcessedPath(helper, 12, 9, PortQuality.VALID, "initial energized path")) return;

            // A configured zero-volt source still exists physically and must not collapse into NO_SIGNAL.
            helper.setBlock(SOURCE, source(0));
            helper.runAfterDelay(18, () -> {
                if (!assertProcessedPath(helper, 0, 0, PortQuality.VALID, "connected valid zero")) return;

                var sourceSnapshot = RedstoneEngineering.COPPER_VOLTAGE_SOURCE.get().engineeringSnapshot(
                        helper.getLevel(), helper.absolutePos(SOURCE), helper.getBlockState(SOURCE), Direction.EAST).orElse(null);
                if (sourceSnapshot == null || sourceSnapshot.quality() != PortQuality.VALID
                        || Math.round(sourceSnapshot.value()) != 0) {
                    helper.fail("Configured 0 V Copper source did not remain VALID at its own port", SOURCE);
                    return;
                }

                // Restore energy, then physically break the input medium.
                helper.setBlock(SOURCE, source(12));
                helper.runAfterDelay(18, () -> {
                    if (!assertProcessedPath(helper, 12, 9, PortQuality.VALID, "pre-disconnect recovery")) return;

                    helper.setBlock(INPUT_WIRE, Blocks.AIR.defaultBlockState());
                    helper.runAfterDelay(18, () -> {
                        PortQuality resistorQuality = CopperSeriesResistorBlock.outputQuality(
                                helper.getLevel(), helper.absolutePos(RESISTOR));
                        PortQuality outputQuality = CopperWireBlock.quality(
                                helper.getLevel(), helper.absolutePos(OUTPUT), helper.getBlockState(OUTPUT));
                        PortQuality meterQuality = CopperCircuitMeterBlock.measurementQuality(
                                helper.getLevel(), helper.absolutePos(METER), helper.getBlockState(METER));

                        if (CopperSeriesResistorBlock.outputVoltage(helper.getLevel(), helper.absolutePos(RESISTOR)) != 0
                                || resistorQuality != PortQuality.NO_SIGNAL) {
                            helper.fail("Breaking the input Copper medium did not clear resistor source evidence", RESISTOR);
                            return;
                        }
                        if (CopperWireBlock.voltage(helper.getLevel(), helper.absolutePos(OUTPUT)) != 0
                                || outputQuality != PortQuality.NO_SIGNAL) {
                            helper.fail("Downstream Copper medium retained a ghost driver after the upstream break", OUTPUT);
                            return;
                        }
                        if (meterQuality != PortQuality.STALE) {
                            helper.fail("Circuit Meter must mark retained pre-break evidence STALE after its live target loses source", METER);
                            return;
                        }

                        helper.setBlock(INPUT_WIRE, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());
                        helper.runAfterDelay(18, () -> {
                            if (!assertProcessedPath(helper, 12, 9, PortQuality.VALID, "medium reconnect")) return;
                            helper.succeed();
                        });
                    });
                });
            });
        });
    }

    private static void buildProcessedMedium(GameTestHelper helper, int voltage) {
        helper.setBlock(SOURCE, source(voltage));
        helper.setBlock(INPUT_WIRE, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());
        helper.setBlock(RESISTOR, resistor(4));
        helper.setBlock(OUTPUT, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());
        helper.setBlock(METER, RedstoneEngineering.COPPER_CIRCUIT_METER.get().defaultBlockState()
                .setValue(CopperCircuitMeterBlock.FACING, Direction.NORTH));
    }

    private static BlockState source(int voltage) {
        return RedstoneEngineering.COPPER_VOLTAGE_SOURCE.get().defaultBlockState()
                .setValue(CopperVoltageSourceBlock.VOLTAGE, voltage);
    }

    private static BlockState resistor(int resistance) {
        return RedstoneEngineering.COPPER_SERIES_RESISTOR.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                .setValue(CopperSeriesResistorBlock.RESISTANCE, resistance);
    }

    private static boolean assertProcessedPath(
            GameTestHelper helper,
            int expectedInput,
            int expectedOutput,
            PortQuality expectedQuality,
            String phase
    ) {
        int inputVoltage = CopperWireBlock.voltage(helper.getLevel(), helper.absolutePos(INPUT_WIRE));
        PortQuality inputQuality = CopperWireBlock.quality(
                helper.getLevel(), helper.absolutePos(INPUT_WIRE), helper.getBlockState(INPUT_WIRE));
        int resistorOutput = CopperSeriesResistorBlock.outputVoltage(helper.getLevel(), helper.absolutePos(RESISTOR));
        PortQuality resistorQuality = CopperSeriesResistorBlock.outputQuality(helper.getLevel(), helper.absolutePos(RESISTOR));
        int outputVoltage = CopperWireBlock.voltage(helper.getLevel(), helper.absolutePos(OUTPUT));
        PortQuality outputQuality = CopperWireBlock.quality(
                helper.getLevel(), helper.absolutePos(OUTPUT), helper.getBlockState(OUTPUT));
        CopperObservationSupport.Observation meterTarget = CopperCircuitMeterBlock.targetObservation(
                helper.getLevel(), helper.absolutePos(METER), helper.getBlockState(METER));
        PortQuality meterQuality = CopperCircuitMeterBlock.measurementQuality(
                helper.getLevel(), helper.absolutePos(METER), helper.getBlockState(METER));

        if (inputVoltage != expectedInput || inputQuality != expectedQuality) {
            helper.fail("Copper input medium mismatch during " + phase + ": expected=" + expectedInput
                    + "/" + expectedQuality + " actual=" + inputVoltage + "/" + inputQuality, INPUT_WIRE);
            return false;
        }
        if (resistorOutput != expectedOutput || resistorQuality != expectedQuality) {
            helper.fail("Copper processor mismatch during " + phase + ": expected=" + expectedOutput
                    + "/" + expectedQuality + " actual=" + resistorOutput + "/" + resistorQuality, RESISTOR);
            return false;
        }
        if (outputVoltage != expectedOutput || outputQuality != expectedQuality) {
            helper.fail("Copper output medium mismatch during " + phase + ": expected=" + expectedOutput
                    + "/" + expectedQuality + " actual=" + outputVoltage + "/" + outputQuality, OUTPUT);
            return false;
        }
        if (meterTarget.voltage() != expectedOutput || meterTarget.quality() != expectedQuality
                || meterQuality != expectedQuality) {
            helper.fail("Circuit Meter quality/value mismatch during " + phase + ": target="
                    + meterTarget.voltage() + "/" + meterTarget.quality() + " meter=" + meterQuality, METER);
            return false;
        }
        return true;
    }
}
