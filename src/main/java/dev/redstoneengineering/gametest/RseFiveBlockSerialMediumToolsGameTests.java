package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DeserializerBlock;
import dev.redstoneengineering.block.DigitalRegeneratorBlock;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.SerialNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Five-block SERIAL_DATA subsystem validation.
 *
 * <p>Main path: Serializer -> Serial Data Line -> Digital Regenerator ->
 * Serial Data Line -> Deserializer. The serializer source runtime is a bounded
 * test fixture so the five-block campaign can focus on serial transport,
 * regeneration, conversion, disconnect evidence and recovery inside the
 * existing 5x4x5 GameTest structure.</p>
 */
public final class RseFiveBlockSerialMediumToolsGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private static final BlockPos SERIALIZER = new BlockPos(0, 1, 2);
    private static final BlockPos LINE_A = new BlockPos(1, 1, 2);
    private static final BlockPos REGENERATOR = new BlockPos(2, 1, 2);
    private static final BlockPos LINE_B = new BlockPos(3, 1, 2);
    private static final BlockPos DESERIALIZER = new BlockPos(4, 1, 2);
    private static final BlockPos FAULT_SERIALIZER = new BlockPos(1, 1, 1);

    private RseFiveBlockSerialMediumToolsGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void serialSubsystemCarriesLiveAndValidZeroThroughRegeneration(GameTestHelper helper) {
        buildPath(helper);
        driveSerializerFixture(helper, 11);

        helper.runAfterDelay(3, () -> {
            if (!assertLivePath(helper, 11, "initial frame")) return;

            driveSerializerFixture(helper, 0);
            helper.runAfterDelay(3, () -> {
                if (!assertLivePath(helper, 0, "valid zero frame")) return;

                driveSerializerFixture(helper, 7);
                helper.runAfterDelay(3, () -> {
                    if (!assertLivePath(helper, 7, "live frame recovery")) return;
                    helper.succeed();
                });
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void serialMediumBreaksClearDownstreamWithoutBackdriveAndRecover(GameTestHelper helper) {
        buildPath(helper);
        driveSerializerFixture(helper, 9);

        helper.runAfterDelay(3, () -> {
            if (!assertLivePath(helper, 9, "pre-break baseline")) return;

            helper.setBlock(LINE_A, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(2, () -> {
                InformationRuntime.Snapshot source = InformationRuntime.snapshot(
                        helper.getLevel(), "serial", helper.absolutePos(SERIALIZER));
                if (!source.valid() || (source.value() & 0xFF) != 9) {
                    helper.fail("Upstream serial source was back-driven/cleared by an input-medium break", SERIALIZER);
                    return;
                }

                DigitalRegeneratorBlock regenerator = RedstoneEngineering.DIGITAL_REGENERATOR.get();
                BlockState regeneratorState = helper.getBlockState(REGENERATOR);
                var regeneratorInput = regenerator.engineeringSnapshot(
                        helper.getLevel(), helper.absolutePos(REGENERATOR), regeneratorState, Direction.WEST).orElse(null);
                var regeneratorOutput = regenerator.engineeringSnapshot(
                        helper.getLevel(), helper.absolutePos(REGENERATOR), regeneratorState, Direction.EAST).orElse(null);
                if (regeneratorInput == null || regeneratorInput.quality() != PortQuality.NO_SIGNAL) {
                    helper.fail("Removing the physical upstream SERIAL_DATA medium must be NO_SIGNAL at regenerator input", REGENERATOR);
                    return;
                }
                if (regeneratorOutput == null || regeneratorOutput.quality() != PortQuality.NO_SIGNAL) {
                    helper.fail("Regenerator did not propagate upstream serial-medium loss as NO_SIGNAL", REGENERATOR);
                    return;
                }

                BlockPos lineBWorld = helper.absolutePos(LINE_B);
                if (SerialNetwork.quality(helper.getLevel(), lineBWorld) != PortQuality.NO_SIGNAL
                        || SerialNetwork.getDiagnostics(helper.getLevel(), lineBWorld).driverCount() != 0) {
                    helper.fail("Downstream serial segment retained a ghost driver after upstream-medium removal", LINE_B);
                    return;
                }

                if (!assertDeserializerCleared(helper, "upstream-medium break")) return;

                helper.setBlock(LINE_A, RedstoneEngineering.SERIAL_DATA_LINE.get().defaultBlockState());
                driveSerializerFixture(helper, 9);
                helper.runAfterDelay(3, () -> {
                    if (!assertLivePath(helper, 9, "upstream-medium reconnect")) return;

                    helper.setBlock(LINE_B, Blocks.AIR.defaultBlockState());
                    helper.runAfterDelay(2, () -> {
                        InformationRuntime.Snapshot regeneratedSource = InformationRuntime.snapshot(
                                helper.getLevel(), "serial", helper.absolutePos(REGENERATOR));
                        if (!regeneratedSource.valid() || (regeneratedSource.value() & 0xFF) != 9) {
                            helper.fail("Downstream serial-medium break back-drove the regenerator output", REGENERATOR);
                            return;
                        }
                        if (!assertDeserializerCleared(helper, "downstream-medium break")) return;

                        helper.setBlock(LINE_B, RedstoneEngineering.SERIAL_DATA_LINE.get().defaultBlockState());
                        helper.runAfterDelay(3, () -> {
                            if (!assertLivePath(helper, 9, "downstream-medium reconnect")) return;
                            helper.succeed();
                        });
                    });
                });
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void serialContentionPropagatesTopologyErrorAndRecovers(GameTestHelper helper) {
        buildPath(helper);
        driveSerializerFixture(helper, 6);

        helper.runAfterDelay(3, () -> {
            if (!assertLivePath(helper, 6, "single-driver baseline")) return;

            helper.setBlock(FAULT_SERIALIZER, RedstoneEngineering.SERIALIZER.get().defaultBlockState()
                    .setValue(DirectionalDomainBlock.FACING, Direction.SOUTH));
            InformationRuntime.write(
                    helper.getLevel(),
                    "serial",
                    helper.absolutePos(FAULT_SERIALIZER),
                    13,
                    8,
                    true,
                    100
            );
            SerialNetwork.recompute(helper.getLevel(), helper.absolutePos(LINE_A));

            helper.runAfterDelay(3, () -> {
                BlockPos lineAWorld = helper.absolutePos(LINE_A);
                if (SerialNetwork.quality(helper.getLevel(), lineAWorld) != PortQuality.TOPOLOGY_ERROR
                        || SerialNetwork.getDiagnostics(helper.getLevel(), lineAWorld).driverCount() != 2) {
                    helper.fail("Two real serializer endpoints did not produce explicit SERIAL_DATA contention", LINE_A);
                    return;
                }

                DigitalRegeneratorBlock regenerator = RedstoneEngineering.DIGITAL_REGENERATOR.get();
                BlockState regeneratorState = helper.getBlockState(REGENERATOR);
                var regeneratorInput = regenerator.engineeringSnapshot(
                        helper.getLevel(), helper.absolutePos(REGENERATOR), regeneratorState, Direction.WEST).orElse(null);
                var regeneratorOutput = regenerator.engineeringSnapshot(
                        helper.getLevel(), helper.absolutePos(REGENERATOR), regeneratorState, Direction.EAST).orElse(null);
                if (regeneratorInput == null || regeneratorInput.quality() != PortQuality.TOPOLOGY_ERROR
                        || regeneratorOutput == null || regeneratorOutput.quality() != PortQuality.TOPOLOGY_ERROR) {
                    helper.fail("Digital Regenerator collapsed upstream serial contention instead of preserving TOPOLOGY_ERROR", REGENERATOR);
                    return;
                }

                BlockPos lineBWorld = helper.absolutePos(LINE_B);
                if (SerialNetwork.quality(helper.getLevel(), lineBWorld) != PortQuality.NO_SIGNAL
                        || SerialNetwork.getDiagnostics(helper.getLevel(), lineBWorld).driverCount() != 0) {
                    helper.fail("Regenerator continued to drive downstream SERIAL_DATA during upstream contention", LINE_B);
                    return;
                }
                if (!assertDeserializerCleared(helper, "upstream serial contention")) return;

                helper.setBlock(FAULT_SERIALIZER, Blocks.AIR.defaultBlockState());
                helper.runAfterDelay(4, () -> {
                    if (!assertLivePath(helper, 6, "contention recovery")) return;
                    helper.succeed();
                });
            });
        });
    }

    private static void buildPath(GameTestHelper helper) {
        helper.setBlock(SERIALIZER, RedstoneEngineering.SERIALIZER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(REGENERATOR, RedstoneEngineering.DIGITAL_REGENERATOR.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                .setValue(DigitalRegeneratorBlock.THRESHOLD, 1));
        helper.setBlock(DESERIALIZER, RedstoneEngineering.DESERIALIZER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(LINE_A, RedstoneEngineering.SERIAL_DATA_LINE.get().defaultBlockState());
        helper.setBlock(LINE_B, RedstoneEngineering.SERIAL_DATA_LINE.get().defaultBlockState());
    }

    private static void driveSerializerFixture(GameTestHelper helper, int value) {
        InformationRuntime.write(
                helper.getLevel(),
                "serial",
                helper.absolutePos(SERIALIZER),
                value & 0xFF,
                8,
                true,
                100
        );
        SerialNetwork.recompute(helper.getLevel(), helper.absolutePos(LINE_A));
    }

    private static boolean assertLivePath(GameTestHelper helper, int expected, String phase) {
        InformationRuntime.Snapshot source = InformationRuntime.snapshot(
                helper.getLevel(), "serial", helper.absolutePos(SERIALIZER));
        if (!source.valid() || (source.value() & 0xFF) != expected) {
            helper.fail("Serializer fixture mismatch during " + phase, SERIALIZER);
            return false;
        }

        BlockPos lineAWorld = helper.absolutePos(LINE_A);
        InformationRuntime.Snapshot lineA = InformationRuntime.snapshot(helper.getLevel(), "serial", lineAWorld);
        if (SerialNetwork.quality(helper.getLevel(), lineAWorld) != PortQuality.VALID
                || !lineA.valid() || (lineA.value() & 0xFF) != expected
                || SerialNetwork.getDiagnostics(helper.getLevel(), lineAWorld).driverCount() != 1) {
            helper.fail("Upstream serial medium mismatch during " + phase, LINE_A);
            return false;
        }

        DigitalRegeneratorBlock regenerator = RedstoneEngineering.DIGITAL_REGENERATOR.get();
        BlockState regeneratorState = helper.getBlockState(REGENERATOR);
        var regeneratorInput = regenerator.engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(REGENERATOR), regeneratorState, Direction.WEST).orElse(null);
        var regeneratorOutput = regenerator.engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(REGENERATOR), regeneratorState, Direction.EAST).orElse(null);
        if (regeneratorInput == null || regeneratorInput.quality() != PortQuality.VALID
                || Math.round(regeneratorInput.value()) != expected
                || regeneratorOutput == null || regeneratorOutput.quality() != PortQuality.VALID
                || Math.round(regeneratorOutput.value()) != expected) {
            helper.fail("Digital regenerator mismatch during " + phase, REGENERATOR);
            return false;
        }

        BlockPos lineBWorld = helper.absolutePos(LINE_B);
        InformationRuntime.Snapshot lineB = InformationRuntime.snapshot(helper.getLevel(), "serial", lineBWorld);
        if (SerialNetwork.quality(helper.getLevel(), lineBWorld) != PortQuality.VALID
                || !lineB.valid() || (lineB.value() & 0xFF) != expected
                || SerialNetwork.getDiagnostics(helper.getLevel(), lineBWorld).driverCount() != 1) {
            helper.fail("Regenerated serial medium mismatch during " + phase, LINE_B);
            return false;
        }

        DeserializerBlock deserializer = RedstoneEngineering.DESERIALIZER.get();
        BlockState deserializerState = helper.getBlockState(DESERIALIZER);
        var deserializerInput = deserializer.engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(DESERIALIZER), deserializerState, Direction.WEST).orElse(null);
        var deserializerOutput = deserializer.engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(DESERIALIZER), deserializerState, Direction.EAST).orElse(null);
        InformationRuntime.Snapshot busSource = InformationRuntime.snapshot(
                helper.getLevel(), "bus8_out", helper.absolutePos(DESERIALIZER));
        if (deserializerInput == null || deserializerInput.quality() != PortQuality.VALID
                || Math.round(deserializerInput.value()) != expected
                || deserializerOutput == null || deserializerOutput.quality() != PortQuality.VALID
                || Math.round(deserializerOutput.value()) != expected
                || !busSource.valid() || (busSource.value() & 0xFF) != expected) {
            helper.fail("Deserializer conversion mismatch during " + phase, DESERIALIZER);
            return false;
        }
        return true;
    }

    private static boolean assertDeserializerCleared(GameTestHelper helper, String phase) {
        DeserializerBlock deserializer = RedstoneEngineering.DESERIALIZER.get();
        BlockState state = helper.getBlockState(DESERIALIZER);
        var input = deserializer.engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(DESERIALIZER), state, Direction.WEST).orElse(null);
        var output = deserializer.engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(DESERIALIZER), state, Direction.EAST).orElse(null);
        InformationRuntime.Snapshot busSource = InformationRuntime.snapshot(
                helper.getLevel(), "bus8_out", helper.absolutePos(DESERIALIZER));
        if (input == null || input.quality() != PortQuality.NO_SIGNAL
                || output == null || output.quality() != PortQuality.NO_SIGNAL
                || busSource.valid() || (busSource.value() & 0xFF) != 0) {
            helper.fail("Deserializer retained/fabricated downstream data during " + phase, DESERIALIZER);
            return false;
        }
        return true;
    }
}
