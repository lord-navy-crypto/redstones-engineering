package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.AnalogIndicatorBlock;
import dev.redstoneengineering.block.ByteToRedstoneDecoderBlock;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.DataBusNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Five-block end-to-end DATA_BUS_8 validation.
 *
 * <p>Main path: Redstone Reference Source -> Byte Encoder -> 8-bit Data Bus ->
 * Byte Decoder -> Analog Indicator. A second encoder/source is introduced only as
 * a bounded fault injector for contention/recovery.</p>
 */
public final class RseFiveBlockDataBusMediumToolsGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private static final BlockPos SOURCE = new BlockPos(0, 1, 2);
    private static final BlockPos ENCODER = new BlockPos(1, 1, 2);
    private static final BlockPos BUS = new BlockPos(2, 1, 2);
    private static final BlockPos DECODER = new BlockPos(3, 1, 2);
    private static final BlockPos INDICATOR = new BlockPos(4, 1, 2);

    private RseFiveBlockDataBusMediumToolsGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 130)
    public static void conversionPathPreservesValidZeroThenClearsOnMediumBreakAndRecovers(GameTestHelper helper) {
        buildMainPath(helper, 11);

        helper.runAfterDelay(10, () -> {
            if (!assertMainPath(helper, 11, PortQuality.VALID, "initial live frame")) return;

            helper.setBlock(SOURCE, reference(Direction.EAST, 0));
            helper.runAfterDelay(10, () -> {
                if (!assertMainPath(helper, 0, PortQuality.VALID, "connected byte zero")) return;

                helper.setBlock(SOURCE, reference(Direction.EAST, 7));
                helper.runAfterDelay(10, () -> {
                    if (!assertMainPath(helper, 7, PortQuality.VALID, "pre-break recovery")) return;

                    helper.setBlock(BUS, Blocks.AIR.defaultBlockState());
                    helper.runAfterDelay(10, () -> {
                        BlockState decoderState = helper.getBlockState(DECODER);
                        ByteToRedstoneDecoderBlock decoder = RedstoneEngineering.BYTE_TO_REDSTONE_DECODER.get();
                        var decoderInput = decoder.engineeringSnapshot(
                                helper.getLevel(), helper.absolutePos(DECODER), decoderState, Direction.WEST).orElse(null);
                        var decoderOutput = decoder.engineeringSnapshot(
                                helper.getLevel(), helper.absolutePos(DECODER), decoderState, Direction.EAST).orElse(null);
                        AnalogIndicatorBlock indicator = RedstoneEngineering.ANALOG_INDICATOR.get();
                        var indicatorInput = indicator.engineeringSnapshot(
                                helper.getLevel(), helper.absolutePos(INDICATOR), helper.getBlockState(INDICATOR), Direction.WEST).orElse(null);

                        if (decoderInput == null || decoderInput.quality() != PortQuality.NO_SIGNAL) {
                            helper.fail("Removing the physical data-bus medium must be NO_SIGNAL at decoder input, not STALE", DECODER);
                            return;
                        }
                        if (decoderOutput == null || decoderOutput.quality() != PortQuality.NO_SIGNAL
                                || helper.getBlockState(DECODER).getValue(DirectionalSignalBlock.OUTPUT) != 0) {
                            helper.fail("Decoder did not clear its redstone output after the physical bus was removed", DECODER);
                            return;
                        }
                        if (indicatorInput == null || indicatorInput.quality() != PortQuality.NO_SIGNAL
                                || helper.getBlockState(INDICATOR).getValue(AnalogIndicatorBlock.LEVEL) != 0) {
                            helper.fail("Analog Indicator retained/fabricated a signal after DATA_BUS_8 medium removal", INDICATOR);
                            return;
                        }

                        helper.setBlock(BUS, RedstoneEngineering.EIGHT_BIT_DATA_BUS.get().defaultBlockState());
                        helper.runAfterDelay(12, () -> {
                            if (!assertMainPath(helper, 7, PortQuality.VALID, "medium reconnect")) return;
                            helper.succeed();
                        });
                    });
                });
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void realEncoderContentionPropagatesTopologyErrorToDisplayThenRecovers(GameTestHelper helper) {
        buildMainPath(helper, 5);
        BlockPos secondSource = new BlockPos(2, 1, 0);
        BlockPos secondEncoder = new BlockPos(2, 1, 1);

        helper.runAfterDelay(10, () -> {
            if (!assertMainPath(helper, 5, PortQuality.VALID, "single-driver baseline")) return;

            helper.setBlock(secondSource, reference(Direction.SOUTH, 9));
            helper.setBlock(secondEncoder, RedstoneEngineering.REDSTONE_BYTE_ENCODER.get().defaultBlockState()
                    .setValue(DirectionalDomainBlock.FACING, Direction.SOUTH));

            helper.runAfterDelay(12, () -> {
                BlockPos busWorld = helper.absolutePos(BUS);
                DataBusNetwork.Diagnostics diagnostics = DataBusNetwork.getDiagnostics(helper.getLevel(), busWorld);
                if (DataBusNetwork.quality(helper.getLevel(), busWorld) != PortQuality.TOPOLOGY_ERROR
                        || diagnostics.driverCount() != 2
                        || diagnostics.distinctValues() != 2) {
                    helper.fail("Two real encoders driving 5 and 9 did not produce a two-driver DATA_BUS_8 topology conflict", BUS);
                    return;
                }

                ByteToRedstoneDecoderBlock decoder = RedstoneEngineering.BYTE_TO_REDSTONE_DECODER.get();
                BlockState decoderState = helper.getBlockState(DECODER);
                var decoderOutput = decoder.engineeringSnapshot(
                        helper.getLevel(), helper.absolutePos(DECODER), decoderState, Direction.EAST).orElse(null);
                AnalogIndicatorBlock indicator = RedstoneEngineering.ANALOG_INDICATOR.get();
                var indicatorInput = indicator.engineeringSnapshot(
                        helper.getLevel(), helper.absolutePos(INDICATOR), helper.getBlockState(INDICATOR), Direction.WEST).orElse(null);

                if (decoderOutput == null || decoderOutput.quality() != PortQuality.TOPOLOGY_ERROR
                        || helper.getBlockState(DECODER).getValue(DirectionalSignalBlock.OUTPUT) != 0) {
                    helper.fail("Byte decoder collapsed a real bus conflict instead of clearing output with TOPOLOGY_ERROR", DECODER);
                    return;
                }
                if (indicatorInput == null || indicatorInput.quality() != PortQuality.TOPOLOGY_ERROR
                        || helper.getBlockState(INDICATOR).getValue(AnalogIndicatorBlock.LEVEL) != 0) {
                    helper.fail("Analog Indicator failed to preserve decoder TOPOLOGY_ERROR across the redstone boundary", INDICATOR);
                    return;
                }

                helper.setBlock(secondEncoder, Blocks.AIR.defaultBlockState());
                helper.runAfterDelay(12, () -> {
                    if (!assertMainPath(helper, 5, PortQuality.VALID, "contention recovery")) return;
                    helper.succeed();
                });
            });
        });
    }

    private static void buildMainPath(GameTestHelper helper, int power) {
        helper.setBlock(SOURCE, reference(Direction.EAST, power));
        helper.setBlock(ENCODER, RedstoneEngineering.REDSTONE_BYTE_ENCODER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(BUS, RedstoneEngineering.EIGHT_BIT_DATA_BUS.get().defaultBlockState());
        helper.setBlock(DECODER, RedstoneEngineering.BYTE_TO_REDSTONE_DECODER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));
        helper.setBlock(INDICATOR, RedstoneEngineering.ANALOG_INDICATOR.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.EAST));
    }

    private static BlockState reference(Direction facing, int power) {
        return RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, facing)
                .setValue(RedstoneReferenceSourceBlock.POWER, power);
    }

    private static boolean assertMainPath(
            GameTestHelper helper,
            int expected,
            PortQuality expectedQuality,
            String phase
    ) {
        BlockPos busWorld = helper.absolutePos(BUS);
        if (DataBusNetwork.sample(helper.getLevel(), busWorld) != expected
                || DataBusNetwork.quality(helper.getLevel(), busWorld) != expectedQuality
                || DataBusNetwork.getDiagnostics(helper.getLevel(), busWorld).driverCount() != 1) {
            helper.fail("DATA_BUS_8 mismatch during " + phase + ": value="
                    + DataBusNetwork.sample(helper.getLevel(), busWorld)
                    + " quality=" + DataBusNetwork.quality(helper.getLevel(), busWorld)
                    + " drivers=" + DataBusNetwork.getDiagnostics(helper.getLevel(), busWorld).driverCount(), BUS);
            return false;
        }

        ByteToRedstoneDecoderBlock decoder = RedstoneEngineering.BYTE_TO_REDSTONE_DECODER.get();
        BlockState decoderState = helper.getBlockState(DECODER);
        var decoderInput = decoder.engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(DECODER), decoderState, Direction.WEST).orElse(null);
        var decoderOutput = decoder.engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(DECODER), decoderState, Direction.EAST).orElse(null);
        if (decoderInput == null || decoderInput.quality() != expectedQuality
                || Math.round(decoderInput.value()) != expected
                || decoderOutput == null || decoderOutput.quality() != expectedQuality
                || Math.round(decoderOutput.value()) != expected
                || helper.getBlockState(DECODER).getValue(DirectionalSignalBlock.OUTPUT) != expected) {
            helper.fail("Decoder conversion mismatch during " + phase, DECODER);
            return false;
        }

        AnalogIndicatorBlock indicator = RedstoneEngineering.ANALOG_INDICATOR.get();
        var indicatorInput = indicator.engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(INDICATOR), helper.getBlockState(INDICATOR), Direction.WEST).orElse(null);
        if (indicatorInput == null || indicatorInput.quality() != expectedQuality
                || Math.round(indicatorInput.value()) != expected
                || helper.getBlockState(INDICATOR).getValue(AnalogIndicatorBlock.LEVEL) != expected) {
            helper.fail("Indicator conversion/readback mismatch during " + phase, INDICATOR);
            return false;
        }
        return true;
    }
}
