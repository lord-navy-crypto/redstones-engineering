package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.ConnectedCableBlock;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.LapisPrecisionSourceBlock;
import dev.redstoneengineering.block.LapisToRedstoneQuantizerBlock;
import dev.redstoneengineering.block.QuartzTimingLineBlock;
import dev.redstoneengineering.block.QuartzTriggeredLapisSamplerBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.RedstoneToLapisScalerBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.DataBusNetwork;
import dev.redstoneengineering.physics.DifferentialNetwork;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.physics.SerialNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Eighth 10-block design/bug campaign: precision conversion and wired information media. */
public final class RseEighthTenDesignBugGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseEighthTenDesignBugGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void lapisQuantizerKeepsUnsampledOutputStaleAndInspectionNeutral(GameTestHelper helper) {
        BlockPos quantizer = new BlockPos(2, 1, 2);
        BlockPos world = helper.absolutePos(quantizer);
        BlockState state = RedstoneEngineering.LAPIS_TO_REDSTONE_QUANTIZER.get().defaultBlockState()
                .setValue(LapisToRedstoneQuantizerBlock.FACING, Direction.EAST);
        RuntimeIntStore.remove(helper.getLevel(), "lapis_to_redstone_quantizer", world);
        int before = RuntimeIntStore.entryCount(helper.getLevel());
        var input = RedstoneEngineering.LAPIS_TO_REDSTONE_QUANTIZER.get()
                .engineeringSnapshot(helper.getLevel(), world, state, Direction.WEST).orElseThrow();
        var output = RedstoneEngineering.LAPIS_TO_REDSTONE_QUANTIZER.get()
                .engineeringSnapshot(helper.getLevel(), world, state, Direction.EAST).orElseThrow();
        if (input.quality() != PortQuality.NO_SIGNAL
                || output.quality() != PortQuality.STALE
                || RuntimeIntStore.entryCount(helper.getLevel()) != before) {
            helper.fail("Lapis quantizer fabricated validity/runtime before its first sample", quantizer);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void redstoneScalerSeparatesEmptyInputFromDrivenZero(GameTestHelper helper) {
        BlockPos emptyScaler = new BlockPos(2, 1, 1);
        BlockPos zeroSource = new BlockPos(1, 1, 3);
        BlockPos zeroScaler = new BlockPos(2, 1, 3);
        BlockState scaler = RedstoneEngineering.REDSTONE_TO_LAPIS_SCALER.get().defaultBlockState()
                .setValue(RedstoneToLapisScalerBlock.FACING, Direction.EAST);
        helper.setBlock(emptyScaler, scaler);
        helper.setBlock(zeroSource, RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.EAST)
                .setValue(RedstoneReferenceSourceBlock.POWER, 0));
        helper.setBlock(zeroScaler, scaler);

        var empty = RedstoneEngineering.REDSTONE_TO_LAPIS_SCALER.get().engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(emptyScaler), helper.getBlockState(emptyScaler), Direction.WEST).orElseThrow();
        var drivenZero = RedstoneEngineering.REDSTONE_TO_LAPIS_SCALER.get().engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(zeroScaler), helper.getBlockState(zeroScaler), Direction.WEST).orElseThrow();
        if (empty.quality() != PortQuality.NO_SIGNAL
                || drivenZero.value() != 0.0 || drivenZero.quality() != PortQuality.VALID) {
            helper.fail("Redstone scaler collapsed empty input and configured zero source", zeroScaler);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void quartzSamplerRequiresObservedLowToHighEdge(GameTestHelper helper) {
        BlockPos lapis = new BlockPos(1, 1, 2);
        BlockPos sampler = new BlockPos(2, 1, 2);
        BlockPos quartz = new BlockPos(2, 1, 3);
        helper.setBlock(lapis, RedstoneEngineering.LAPIS_PRECISION_SOURCE.get().defaultBlockState()
                .setValue(LapisPrecisionSourceBlock.VALUE, 80));
        helper.setBlock(sampler, RedstoneEngineering.QUARTZ_TRIGGERED_LAPIS_SAMPLER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(quartz, RedstoneEngineering.QUARTZ_TIMING_LINE.get().defaultBlockState());
        BlockPos quartzWorld = helper.absolutePos(quartz);
        BlockPos samplerWorld = helper.absolutePos(sampler);
        QuartzTimingLineBlock.setTiming(helper.getLevel(), quartzWorld, true, 8, true, 1);

        helper.runAfterDelay(3, () -> {
            if (QuartzTriggeredLapisSamplerBlock.heldQuality(helper.getLevel(), samplerWorld) != PortQuality.STALE) {
                helper.fail("Initial HIGH Quartz level was misread as a rising edge", sampler);
                return;
            }
            QuartzTimingLineBlock.setTiming(helper.getLevel(), quartzWorld, false, 8, true, 1);
            helper.runAfterDelay(2, () -> {
                QuartzTimingLineBlock.setTiming(helper.getLevel(), quartzWorld, true, 8, true, 1);
                helper.runAfterDelay(2, () -> {
                    if (QuartzTriggeredLapisSamplerBlock.heldQuality(helper.getLevel(), samplerWorld) != PortQuality.VALID
                            || QuartzTriggeredLapisSamplerBlock.heldValue(helper.getLevel(), samplerWorld) != 80) {
                        helper.fail("Observed LOW-to-HIGH Quartz edge did not capture the Lapis input", sampler);
                        return;
                    }
                    helper.succeed();
                });
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void dataBusInspectionIsNeutralBeforeResolutionThenNoSignal(GameTestHelper helper) {
        BlockPos bus = new BlockPos(2, 1, 2);
        helper.setBlock(bus, RedstoneEngineering.EIGHT_BIT_DATA_BUS.get().defaultBlockState());
        BlockPos world = helper.absolutePos(bus);
        int before = RuntimeIntStore.entryCount(helper.getLevel());
        var snapshot = RedstoneEngineering.EIGHT_BIT_DATA_BUS.get().engineeringSnapshot(
                helper.getLevel(), world, helper.getBlockState(bus), Direction.NORTH).orElseThrow();
        if (snapshot.quality() != PortQuality.STALE || RuntimeIntStore.entryCount(helper.getLevel()) != before) {
            helper.fail("Inspecting an unsolved 8-bit bus created runtime or fabricated NO_SIGNAL", bus);
            return;
        }
        helper.runAfterDelay(3, () -> {
            if (DataBusNetwork.quality(helper.getLevel(), world) != PortQuality.NO_SIGNAL) {
                helper.fail("Resolved driverless 8-bit bus did not become NO_SIGNAL", bus);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 70)
    public static void byteEncoderDoesNotDriveFromEmptyInputButAcceptsDrivenZero(GameTestHelper helper) {
        BlockPos input = new BlockPos(1, 1, 2);
        BlockPos encoder = new BlockPos(2, 1, 2);
        BlockPos bus = new BlockPos(3, 1, 2);
        helper.setBlock(encoder, RedstoneEngineering.REDSTONE_BYTE_ENCODER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(bus, RedstoneEngineering.EIGHT_BIT_DATA_BUS.get().defaultBlockState());
        BlockPos busWorld = helper.absolutePos(bus);

        helper.runAfterDelay(4, () -> {
            if (DataBusNetwork.quality(helper.getLevel(), busWorld) != PortQuality.NO_SIGNAL) {
                helper.fail("Encoder with empty redstone input illegally drove byte zero", encoder);
                return;
            }
            helper.setBlock(input, RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get().defaultBlockState()
                    .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.EAST)
                    .setValue(RedstoneReferenceSourceBlock.POWER, 0));
            helper.runAfterDelay(4, () -> {
                if (DataBusNetwork.quality(helper.getLevel(), busWorld) != PortQuality.VALID
                        || DataBusNetwork.sample(helper.getLevel(), busWorld) != 0
                        || DataBusNetwork.getDiagnostics(helper.getLevel(), busWorld).driverCount() != 1) {
                    helper.fail("Configured redstone zero did not become a legitimate byte-zero driver", bus);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void byteDecoderPropagatesBusConflictToOutputQuality(GameTestHelper helper) {
        BlockPos westEncoder = new BlockPos(1, 1, 2);
        BlockPos bus = new BlockPos(2, 1, 2);
        BlockPos decoder = new BlockPos(3, 1, 2);
        BlockPos northEncoder = new BlockPos(2, 1, 1);
        helper.setBlock(westEncoder, RedstoneEngineering.REDSTONE_BYTE_ENCODER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(northEncoder, RedstoneEngineering.REDSTONE_BYTE_ENCODER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.SOUTH));
        helper.setBlock(bus, RedstoneEngineering.EIGHT_BIT_DATA_BUS.get().defaultBlockState());
        helper.setBlock(decoder, RedstoneEngineering.BYTE_TO_REDSTONE_DECODER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        InformationRuntime.write(helper.getLevel(), "bus8_out", helper.absolutePos(westEncoder), 4, 0, true, 100);
        InformationRuntime.write(helper.getLevel(), "bus8_out", helper.absolutePos(northEncoder), 9, 0, true, 100);
        DataBusNetwork.resolve(helper.getLevel(), DataBusNetwork.collect(helper.getLevel(), helper.absolutePos(bus)));

        var inputSnapshot = RedstoneEngineering.BYTE_TO_REDSTONE_DECODER.get().engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(decoder), helper.getBlockState(decoder), Direction.WEST).orElseThrow();
        var outputSnapshot = RedstoneEngineering.BYTE_TO_REDSTONE_DECODER.get().engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(decoder), helper.getBlockState(decoder), Direction.EAST).orElseThrow();
        if (inputSnapshot.quality() != PortQuality.TOPOLOGY_ERROR
                || outputSnapshot.quality() != PortQuality.TOPOLOGY_ERROR) {
            helper.fail("Byte decoder collapsed a bus driver conflict into an ordinary zero", decoder);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void serialLineSeparatesNoDriverFromMultipleDrivers(GameTestHelper helper) {
        BlockPos west = new BlockPos(1, 1, 2);
        BlockPos line = new BlockPos(2, 1, 2);
        BlockPos east = new BlockPos(3, 1, 2);
        helper.setBlock(line, RedstoneEngineering.SERIAL_DATA_LINE.get().defaultBlockState());
        BlockPos lineWorld = helper.absolutePos(line);
        SerialNetwork.recompute(helper.getLevel(), lineWorld);
        if (SerialNetwork.quality(helper.getLevel(), lineWorld) != PortQuality.NO_SIGNAL) {
            helper.fail("Driverless serial line did not report NO_SIGNAL", line);
            return;
        }
        helper.setBlock(west, RedstoneEngineering.SERIALIZER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(east, RedstoneEngineering.SERIALIZER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.WEST));
        InformationRuntime.write(helper.getLevel(), "serial", helper.absolutePos(west), 42, 8, true, 100);
        InformationRuntime.write(helper.getLevel(), "serial", helper.absolutePos(east), 42, 8, true, 100);
        SerialNetwork.recompute(helper.getLevel(), lineWorld);
        if (SerialNetwork.quality(helper.getLevel(), lineWorld) != PortQuality.TOPOLOGY_ERROR
                || SerialNetwork.getDiagnostics(helper.getLevel(), lineWorld).driverCount() != 2) {
            helper.fail("Multiple serial sources did not surface a topology conflict", line);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void serializerRejectsConflictedBusInsteadOfFramingZero(GameTestHelper helper) {
        BlockPos westEncoder = new BlockPos(0, 1, 2);
        BlockPos bus = new BlockPos(1, 1, 2);
        BlockPos serializer = new BlockPos(2, 1, 2);
        BlockPos northEncoder = new BlockPos(1, 1, 1);
        helper.setBlock(westEncoder, RedstoneEngineering.REDSTONE_BYTE_ENCODER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(northEncoder, RedstoneEngineering.REDSTONE_BYTE_ENCODER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.SOUTH));
        helper.setBlock(bus, RedstoneEngineering.EIGHT_BIT_DATA_BUS.get().defaultBlockState());
        helper.setBlock(serializer, RedstoneEngineering.SERIALIZER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        InformationRuntime.write(helper.getLevel(), "bus8_out", helper.absolutePos(westEncoder), 2, 0, true, 100);
        InformationRuntime.write(helper.getLevel(), "bus8_out", helper.absolutePos(northEncoder), 7, 0, true, 100);
        DataBusNetwork.resolve(helper.getLevel(), DataBusNetwork.collect(helper.getLevel(), helper.absolutePos(bus)));

        helper.runAfterDelay(3, () -> {
            var inputSnapshot = RedstoneEngineering.SERIALIZER.get().engineeringSnapshot(
                    helper.getLevel(), helper.absolutePos(serializer), helper.getBlockState(serializer), Direction.WEST).orElseThrow();
            var outputSnapshot = RedstoneEngineering.SERIALIZER.get().engineeringSnapshot(
                    helper.getLevel(), helper.absolutePos(serializer), helper.getBlockState(serializer), Direction.EAST).orElseThrow();
            InformationRuntime.Snapshot source = InformationRuntime.snapshot(helper.getLevel(), "serial", helper.absolutePos(serializer));
            if (inputSnapshot.quality() != PortQuality.TOPOLOGY_ERROR
                    || outputSnapshot.quality() != PortQuality.TOPOLOGY_ERROR
                    || source.valid()) {
                helper.fail("Serializer turned conflicted bus state into a valid serial frame", serializer);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void deserializerRejectsSerialConflictAsBusSource(GameTestHelper helper) {
        BlockPos west = new BlockPos(1, 1, 2);
        BlockPos line = new BlockPos(2, 1, 2);
        BlockPos east = new BlockPos(3, 1, 2);
        BlockPos deserializer = new BlockPos(2, 1, 3);
        helper.setBlock(line, RedstoneEngineering.SERIAL_DATA_LINE.get().defaultBlockState());
        helper.setBlock(west, RedstoneEngineering.SERIALIZER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(east, RedstoneEngineering.SERIALIZER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.WEST));
        helper.setBlock(deserializer, RedstoneEngineering.DESERIALIZER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.SOUTH));
        InformationRuntime.write(helper.getLevel(), "serial", helper.absolutePos(west), 15, 8, true, 100);
        InformationRuntime.write(helper.getLevel(), "serial", helper.absolutePos(east), 31, 8, true, 100);
        SerialNetwork.recompute(helper.getLevel(), helper.absolutePos(line));

        helper.runAfterDelay(3, () -> {
            var inputSnapshot = RedstoneEngineering.DESERIALIZER.get().engineeringSnapshot(
                    helper.getLevel(), helper.absolutePos(deserializer), helper.getBlockState(deserializer), Direction.NORTH).orElseThrow();
            var outputSnapshot = RedstoneEngineering.DESERIALIZER.get().engineeringSnapshot(
                    helper.getLevel(), helper.absolutePos(deserializer), helper.getBlockState(deserializer), Direction.SOUTH).orElseThrow();
            InformationRuntime.Snapshot source = InformationRuntime.snapshot(helper.getLevel(), "bus8_out", helper.absolutePos(deserializer));
            if (inputSnapshot.quality() != PortQuality.TOPOLOGY_ERROR
                    || outputSnapshot.quality() != PortQuality.TOPOLOGY_ERROR
                    || source.valid()) {
                helper.fail("Deserializer turned serial driver conflict into a valid bus source", deserializer);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void differentialPairSeparatesNoDriverFromConflict(GameTestHelper helper) {
        BlockPos west = new BlockPos(1, 1, 2);
        BlockPos pair = new BlockPos(2, 1, 2);
        BlockPos east = new BlockPos(3, 1, 2);
        helper.setBlock(pair, RedstoneEngineering.DIFFERENTIAL_DATA_PAIR.get().defaultBlockState());
        BlockPos pairWorld = helper.absolutePos(pair);
        DifferentialNetwork.recompute(helper.getLevel(), pairWorld);
        if (DifferentialNetwork.quality(helper.getLevel(), pairWorld) != PortQuality.NO_SIGNAL) {
            helper.fail("Driverless differential pair did not report NO_SIGNAL", pair);
            return;
        }
        helper.setBlock(west, RedstoneEngineering.DIFFERENTIAL_DRIVER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(east, RedstoneEngineering.DIFFERENTIAL_DRIVER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.WEST));
        InformationRuntime.write(helper.getLevel(), "diff_out", helper.absolutePos(west), 0, 0, true, 100);
        InformationRuntime.write(helper.getLevel(), "diff_out", helper.absolutePos(east), 1, 0, true, 100);
        DifferentialNetwork.recompute(helper.getLevel(), pairWorld);
        if (DifferentialNetwork.quality(helper.getLevel(), pairWorld) != PortQuality.TOPOLOGY_ERROR
                || DifferentialNetwork.driverCount(helper.getLevel(), pairWorld) != 2) {
            helper.fail("Multiple differential drivers did not surface a topology conflict", pair);
            return;
        }
        helper.succeed();
    }
}
