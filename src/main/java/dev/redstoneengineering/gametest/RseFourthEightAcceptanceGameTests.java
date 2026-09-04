package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.ByteToRedstoneDecoderBlock;
import dev.redstoneengineering.block.DifferentialDataPairBlock;
import dev.redstoneengineering.block.DigitalRegeneratorBlock;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.EightBitDataBusBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.SerialDataLineBlock;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.DataBusNetwork;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.SerialNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Fourth block-by-block acceptance campaign: digital communication media and explicit
 * Redstone/byte/serial conversion boundaries. These tests prioritize invalidation,
 * conflict handling and removal cleanup so stale runtime payloads cannot become ghost signals.
 */
public final class RseFourthEightAcceptanceGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseFourthEightAcceptanceGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void eightBitBusWithoutDriverIsExplicitlyInvalid(GameTestHelper helper) {
        BlockPos busPos = new BlockPos(2, 1, 2);
        helper.setBlock(busPos, RedstoneEngineering.EIGHT_BIT_DATA_BUS.get().defaultBlockState());

        helper.runAfterDelay(3, () -> {
            BlockPos worldPos = helper.absolutePos(busPos);
            DataBusNetwork.Diagnostics diagnostics = DataBusNetwork.getDiagnostics(helper.getLevel(), worldPos);
            if (DataBusNetwork.valid(helper.getLevel(), worldPos)
                    || diagnostics.driverCount() != 0
                    || diagnostics.distinctValues() != 0) {
                helper.fail("Floating 8-bit bus must be NO-SIGNAL, not a valid zero-valued frame", busPos);
                return;
            }

            EightBitDataBusBlock bus = RedstoneEngineering.EIGHT_BIT_DATA_BUS.get();
            BlockState state = helper.getBlockState(busPos);
            var port = bus.engineeringPort(state, Direction.NORTH).orElse(null);
            var snapshot = bus.engineeringSnapshot(helper.getLevel(), worldPos, state, Direction.NORTH).orElse(null);
            if (port == null
                    || port.domain() != EngineeringDomain.DATA_BUS_8
                    || port.direction() != PortDirection.BIDIRECTIONAL
                    || snapshot == null
                    || snapshot.quality() != PortQuality.NO_SIGNAL) {
                helper.fail("8-bit bus did not expose a bidirectional DATA_BUS_8 no-signal port contract", busPos);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 70)
    public static void redstoneByteEncoderDrivesBusAndRemovalReleasesDriver(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(0, 1, 2);
        BlockPos encoderPos = new BlockPos(1, 1, 2);
        BlockPos busPos = new BlockPos(2, 1, 2);

        helper.setBlock(sourcePos, reference(Direction.EAST, 9));
        helper.setBlock(encoderPos, RedstoneEngineering.REDSTONE_BYTE_ENCODER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(busPos, RedstoneEngineering.EIGHT_BIT_DATA_BUS.get().defaultBlockState());

        helper.runAfterDelay(5, () -> {
            BlockPos busWorld = helper.absolutePos(busPos);
            if (!DataBusNetwork.valid(helper.getLevel(), busWorld)
                    || DataBusNetwork.sample(helper.getLevel(), busWorld) != 9
                    || DataBusNetwork.getDiagnostics(helper.getLevel(), busWorld).driverCount() != 1) {
                helper.fail("Redstone byte encoder failed to publish one authoritative byte=9 driver", encoderPos);
                return;
            }

            helper.setBlock(encoderPos, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(4, () -> {
                DataBusNetwork.Diagnostics diagnostics = DataBusNetwork.getDiagnostics(helper.getLevel(), busWorld);
                if (DataBusNetwork.valid(helper.getLevel(), busWorld) || diagnostics.driverCount() != 0) {
                    helper.fail("Removed encoder remained as a ghost 8-bit bus driver", busPos);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void byteDecoderOwnsBusInputAndSaturatesRedstoneOutput(GameTestHelper helper) {
        BlockPos driverPos = new BlockPos(0, 1, 2);
        BlockPos busPos = new BlockPos(1, 1, 2);
        BlockPos decoderPos = new BlockPos(2, 1, 2);

        helper.setBlock(driverPos, RedstoneEngineering.DESERIALIZER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(busPos, RedstoneEngineering.EIGHT_BIT_DATA_BUS.get().defaultBlockState());
        helper.setBlock(decoderPos, RedstoneEngineering.BYTE_TO_REDSTONE_DECODER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));

        BlockPos driverWorld = helper.absolutePos(driverPos);
        BlockPos busWorld = helper.absolutePos(busPos);
        InformationRuntime.write(helper.getLevel(), "bus8_out", driverWorld, 200, 0, true, 100);
        DataBusNetwork.resolve(helper.getLevel(), DataBusNetwork.collect(helper.getLevel(), busWorld));

        helper.runAfterDelay(4, () -> {
            BlockState decoderState = helper.getBlockState(decoderPos);
            ByteToRedstoneDecoderBlock decoder = RedstoneEngineering.BYTE_TO_REDSTONE_DECODER.get();
            var back = decoder.engineeringPort(decoderState, Direction.WEST).orElse(null);
            var front = decoder.engineeringPort(decoderState, Direction.EAST).orElse(null);
            var frontSnapshot = decoder.engineeringSnapshot(
                    helper.getLevel(), helper.absolutePos(decoderPos), decoderState, Direction.EAST).orElse(null);

            if (back == null
                    || back.domain() != EngineeringDomain.DATA_BUS_8
                    || back.redstoneConnectable()
                    || front == null
                    || front.domain() != EngineeringDomain.REDSTONE
                    || !front.redstoneConnectable()) {
                helper.fail("Byte decoder did not preserve DATA_BUS_8 BACK -> REDSTONE FRONT boundary", decoderPos);
                return;
            }
            if (decoderState.getValue(DirectionalSignalBlock.OUTPUT) != 15
                    || frontSnapshot == null
                    || frontSnapshot.quality() != PortQuality.SATURATED) {
                helper.fail("Byte decoder must saturate byte 200 to redstone 15 and report saturation", decoderPos);
                return;
            }
            if (decoder.canConnectRedstone(decoderState, helper.getLevel(), helper.absolutePos(decoderPos), Direction.EAST)) {
                helper.fail("Byte decoder incorrectly exposed its physical BACK data-bus face as vanilla redstone", decoderPos);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void serialLineBreakInvalidatesDisconnectedSegment(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(0, 1, 1);
        BlockPos encoderPos = new BlockPos(1, 1, 1);
        BlockPos busPos = new BlockPos(2, 1, 1);
        BlockPos serializerPos = new BlockPos(3, 1, 1);
        BlockPos lineA = new BlockPos(4, 1, 1);
        BlockPos lineB = new BlockPos(4, 1, 2);

        helper.setBlock(sourcePos, reference(Direction.EAST, 7));
        helper.setBlock(encoderPos, RedstoneEngineering.REDSTONE_BYTE_ENCODER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(busPos, RedstoneEngineering.EIGHT_BIT_DATA_BUS.get().defaultBlockState());
        helper.setBlock(serializerPos, RedstoneEngineering.SERIALIZER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(lineA, RedstoneEngineering.SERIAL_DATA_LINE.get().defaultBlockState());
        helper.setBlock(lineB, RedstoneEngineering.SERIAL_DATA_LINE.get().defaultBlockState());

        helper.runAfterDelay(7, () -> {
            BlockPos lineAWorld = helper.absolutePos(lineA);
            BlockPos lineBWorld = helper.absolutePos(lineB);
            if (!InformationRuntime.valid(helper.getLevel(), "serial", lineAWorld)
                    || !InformationRuntime.valid(helper.getLevel(), "serial", lineBWorld)
                    || (InformationRuntime.value(helper.getLevel(), "serial", lineBWorld) & 0xFF) != 7) {
                helper.fail("Serializer frame did not propagate through the connected serial component", lineB);
                return;
            }

            helper.setBlock(lineA, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(4, () -> {
                if (InformationRuntime.valid(helper.getLevel(), "serial", lineBWorld)) {
                    helper.fail("Disconnected serial segment retained a stale valid frame after the link was broken", lineB);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void serializerFramesOnlyValidDrivenBusWords(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(0, 1, 2);
        BlockPos encoderPos = new BlockPos(1, 1, 2);
        BlockPos busPos = new BlockPos(2, 1, 2);
        BlockPos serializerPos = new BlockPos(3, 1, 2);
        BlockPos linePos = new BlockPos(4, 1, 2);

        helper.setBlock(sourcePos, reference(Direction.EAST, 12));
        helper.setBlock(encoderPos, RedstoneEngineering.REDSTONE_BYTE_ENCODER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(busPos, RedstoneEngineering.EIGHT_BIT_DATA_BUS.get().defaultBlockState());
        helper.setBlock(serializerPos, RedstoneEngineering.SERIALIZER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(linePos, RedstoneEngineering.SERIAL_DATA_LINE.get().defaultBlockState());

        BlockPos serializerWorld = helper.absolutePos(serializerPos);
        BlockPos lineWorld = helper.absolutePos(linePos);
        helper.runAfterDelay(6, () -> {
            if (!InformationRuntime.valid(helper.getLevel(), "serial", serializerWorld)
                    || !InformationRuntime.valid(helper.getLevel(), "serial", lineWorld)
                    || (InformationRuntime.value(helper.getLevel(), "serial", lineWorld) & 0xFF) != 12
                    || InformationRuntime.aux(helper.getLevel(), "serial", lineWorld) != 8) {
                helper.fail("Serializer must frame driven byte 12 with the fixed 8-tick word period", serializerPos);
                return;
            }

            helper.setBlock(encoderPos, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(5, () -> {
                if (InformationRuntime.valid(helper.getLevel(), "serial", serializerWorld)
                        || InformationRuntime.valid(helper.getLevel(), "serial", lineWorld)) {
                    helper.fail("Serializer emitted a valid frame after its byte bus lost every driver", serializerPos);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 70)
    public static void deserializerRecoversFullByteAndRemovalReleasesBus(GameTestHelper helper) {
        BlockPos serializerPos = new BlockPos(0, 1, 2);
        BlockPos linePos = new BlockPos(1, 1, 2);
        BlockPos deserializerPos = new BlockPos(2, 1, 2);
        BlockPos busPos = new BlockPos(3, 1, 2);

        helper.setBlock(serializerPos, RedstoneEngineering.SERIALIZER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(linePos, RedstoneEngineering.SERIAL_DATA_LINE.get().defaultBlockState());
        helper.setBlock(deserializerPos, RedstoneEngineering.DESERIALIZER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(busPos, RedstoneEngineering.EIGHT_BIT_DATA_BUS.get().defaultBlockState());

        BlockPos serializerWorld = helper.absolutePos(serializerPos);
        BlockPos lineWorld = helper.absolutePos(linePos);
        BlockPos busWorld = helper.absolutePos(busPos);
        InformationRuntime.write(helper.getLevel(), "serial", serializerWorld, 200, 8, true, 100);
        SerialNetwork.recompute(helper.getLevel(), lineWorld);

        helper.runAfterDelay(5, () -> {
            if (!DataBusNetwork.valid(helper.getLevel(), busWorld)
                    || DataBusNetwork.sample(helper.getLevel(), busWorld) != 200) {
                helper.fail("Deserializer failed to recover full byte 200 onto DATA_BUS_8", deserializerPos);
                return;
            }

            helper.setBlock(deserializerPos, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(4, () -> {
                if (DataBusNetwork.valid(helper.getLevel(), busWorld)
                        || DataBusNetwork.getDiagnostics(helper.getLevel(), busWorld).driverCount() != 0) {
                    helper.fail("Removed deserializer remained as a ghost byte-bus driver", busPos);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 70)
    public static void differentialPairBreakInvalidatesRemoteBit(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(0, 1, 2);
        BlockPos driverPos = new BlockPos(1, 1, 2);
        BlockPos pairA = new BlockPos(2, 1, 2);
        BlockPos pairB = new BlockPos(3, 1, 2);

        helper.setBlock(sourcePos, reference(Direction.EAST, 15));
        helper.setBlock(driverPos, RedstoneEngineering.DIFFERENTIAL_DRIVER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(pairA, RedstoneEngineering.DIFFERENTIAL_DATA_PAIR.get().defaultBlockState());
        helper.setBlock(pairB, RedstoneEngineering.DIFFERENTIAL_DATA_PAIR.get().defaultBlockState());

        helper.runAfterDelay(5, () -> {
            BlockPos pairBWorld = helper.absolutePos(pairB);
            if (!InformationRuntime.valid(helper.getLevel(), "diff", pairBWorld)
                    || (InformationRuntime.value(helper.getLevel(), "diff", pairBWorld) & 1) != 1) {
                helper.fail("Differential pair did not carry the driven HIGH bit", pairB);
                return;
            }
            DifferentialDataPairBlock pair = RedstoneEngineering.DIFFERENTIAL_DATA_PAIR.get();
            var port = pair.engineeringPort(helper.getBlockState(pairB), Direction.EAST).orElse(null);
            if (port == null || port.domain() != EngineeringDomain.DIFFERENTIAL_DATA) {
                helper.fail("Differential pair did not expose DIFFERENTIAL_DATA engineering ports", pairB);
                return;
            }

            helper.setBlock(pairA, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(4, () -> {
                if (InformationRuntime.valid(helper.getLevel(), "diff", pairBWorld)) {
                    helper.fail("Remote differential segment retained a stale valid bit after the pair was broken", pairB);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void digitalRegeneratorEnforcesQualityThreshold(GameTestHelper helper) {
        BlockPos serializerPos = new BlockPos(0, 1, 2);
        BlockPos inputLine = new BlockPos(1, 1, 2);
        BlockPos regeneratorPos = new BlockPos(2, 1, 2);
        BlockPos outputLine = new BlockPos(3, 1, 2);

        helper.setBlock(serializerPos, RedstoneEngineering.SERIALIZER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(inputLine, RedstoneEngineering.SERIAL_DATA_LINE.get().defaultBlockState());
        helper.setBlock(regeneratorPos, RedstoneEngineering.DIGITAL_REGENERATOR.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                .setValue(DigitalRegeneratorBlock.THRESHOLD, 2));
        helper.setBlock(outputLine, RedstoneEngineering.SERIAL_DATA_LINE.get().defaultBlockState());

        BlockPos serializerWorld = helper.absolutePos(serializerPos);
        BlockPos inputWorld = helper.absolutePos(inputLine);
        BlockPos regeneratorWorld = helper.absolutePos(regeneratorPos);
        BlockPos outputWorld = helper.absolutePos(outputLine);
        InformationRuntime.write(helper.getLevel(), "serial", serializerWorld, 77, 8, true, 50);
        SerialNetwork.recompute(helper.getLevel(), inputWorld);

        helper.runAfterDelay(5, () -> {
            if (InformationRuntime.quality(helper.getLevel(), "serial", inputWorld) != 50) {
                helper.fail("Precondition failed: serial source quality should arrive as 50%", inputLine);
                return;
            }
            if (InformationRuntime.valid(helper.getLevel(), "serial", regeneratorWorld)
                    || InformationRuntime.valid(helper.getLevel(), "serial", outputWorld)) {
                helper.fail("60% regenerator threshold incorrectly accepted a 50% input frame", regeneratorPos);
                return;
            }

            BlockState lowerThreshold = helper.getBlockState(regeneratorPos)
                    .setValue(DigitalRegeneratorBlock.THRESHOLD, 1);
            helper.setBlock(regeneratorPos, lowerThreshold);
            helper.getLevel().scheduleTick(
                    helper.absolutePos(regeneratorPos),
                    RedstoneEngineering.DIGITAL_REGENERATOR.get(),
                    1
            );

            helper.runAfterDelay(5, () -> {
                if (!InformationRuntime.valid(helper.getLevel(), "serial", regeneratorWorld)
                        || !InformationRuntime.valid(helper.getLevel(), "serial", outputWorld)
                        || (InformationRuntime.value(helper.getLevel(), "serial", outputWorld) & 0xFF) != 77
                        || InformationRuntime.quality(helper.getLevel(), "serial", outputWorld) != 100) {
                    helper.fail("40% regenerator threshold failed to accept/re-shape the 50% input frame", regeneratorPos);
                    return;
                }
                helper.succeed();
            });
        });
    }

    private static BlockState reference(Direction facing, int power) {
        return RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, facing)
                .setValue(RedstoneReferenceSourceBlock.POWER, power);
    }

    private static void schedule(GameTestHelper helper, BlockPos pos, Block block, int delay) {
        helper.getLevel().scheduleTick(helper.absolutePos(pos), block, delay);
    }
}
