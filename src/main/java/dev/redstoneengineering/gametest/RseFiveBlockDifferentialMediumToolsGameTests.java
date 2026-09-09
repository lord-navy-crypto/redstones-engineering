package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.AnalogIndicatorBlock;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.RedstoneCableJunctionBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.TransmissionTopology;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.DifferentialNetwork;
import dev.redstoneengineering.physics.InformationRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Five-block differential-data subsystem validation.
 *
 * <p>Main path: Differential Driver -> Differential Pair -> Signal Junction Point ->
 * Differential Pair -> Differential Receiver. A reference source is a fixture for the
 * driver's redstone command; a temporary second driver is used only to inject contention.</p>
 */
public final class RseFiveBlockDifferentialMediumToolsGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private static final BlockPos SOURCE = new BlockPos(0, 1, 2);
    private static final BlockPos DRIVER = new BlockPos(1, 1, 2);
    private static final BlockPos PAIR_A = new BlockPos(2, 1, 2);
    private static final BlockPos JUNCTION = new BlockPos(3, 1, 2);
    private static final BlockPos PAIR_B = new BlockPos(3, 1, 3);
    private static final BlockPos RECEIVER = new BlockPos(3, 1, 4);

    private static final BlockPos FAULT_SOURCE = new BlockPos(2, 1, 0);
    private static final BlockPos FAULT_DRIVER = new BlockPos(2, 1, 1);

    private RseFiveBlockDifferentialMediumToolsGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 90)
    public static void differentialPathPreservesValidLowHighLowAcrossJunction(GameTestHelper helper) {
        buildPath(helper, 0);

        helper.runAfterDelay(5, () -> {
            if (!assertPath(helper, 0, "initial valid LOW")) return;

            helper.setBlock(SOURCE, reference(Direction.EAST, 15));
            helper.runAfterDelay(4, () -> {
                if (!assertPath(helper, 1, "HIGH transition")) return;

                helper.setBlock(SOURCE, reference(Direction.EAST, 0));
                helper.runAfterDelay(4, () -> {
                    if (!assertPath(helper, 0, "LOW recovery")) return;
                    helper.succeed();
                });
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 130)
    public static void differentialBreakConflictAndRecoveryPreserveEvidence(GameTestHelper helper) {
        buildPath(helper, 15);

        helper.runAfterDelay(5, () -> {
            if (!assertPath(helper, 1, "pre-fault baseline")) return;

            helper.setBlock(PAIR_A, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(4, () -> {
                InformationRuntime.Snapshot driverOutput = InformationRuntime.snapshot(
                        helper.getLevel(), "diff_out", helper.absolutePos(DRIVER));
                if (!driverOutput.valid() || (driverOutput.value() & 1) != 1) {
                    helper.fail("Upstream differential driver was back-driven by a medium break", DRIVER);
                    return;
                }
                if (!assertReceiverCleared(helper, "upstream pair break")) return;

                helper.setBlock(PAIR_A, RedstoneEngineering.DIFFERENTIAL_DATA_PAIR.get().defaultBlockState());
                helper.runAfterDelay(5, () -> {
                    if (!assertPath(helper, 1, "upstream pair reconnect")) return;

                    helper.setBlock(PAIR_B, Blocks.AIR.defaultBlockState());
                    helper.runAfterDelay(4, () -> {
                        if (DifferentialNetwork.quality(helper.getLevel(), helper.absolutePos(PAIR_A)) != PortQuality.VALID
                                || DifferentialNetwork.driverCount(helper.getLevel(), helper.absolutePos(PAIR_A)) != 1) {
                            helper.fail("Downstream pair break corrupted the still-driven upstream differential island", PAIR_A);
                            return;
                        }
                        if (!assertReceiverCleared(helper, "receiver-side pair break")) return;

                        helper.setBlock(PAIR_B, RedstoneEngineering.DIFFERENTIAL_DATA_PAIR.get().defaultBlockState());
                        helper.runAfterDelay(5, () -> {
                            if (!assertPath(helper, 1, "receiver-side pair reconnect")) return;

                            helper.setBlock(FAULT_SOURCE, reference(Direction.SOUTH, 0));
                            helper.setBlock(FAULT_DRIVER, RedstoneEngineering.DIFFERENTIAL_DRIVER.get().defaultBlockState()
                                    .setValue(DirectionalDomainBlock.FACING, Direction.SOUTH));
                            helper.runAfterDelay(5, () -> {
                                BlockPos pairWorld = helper.absolutePos(PAIR_A);
                                if (DifferentialNetwork.driverCount(helper.getLevel(), pairWorld) != 2
                                        || DifferentialNetwork.quality(helper.getLevel(), pairWorld) != PortQuality.TOPOLOGY_ERROR) {
                                    helper.fail("Two opposed differential drivers did not produce explicit contention evidence", PAIR_A);
                                    return;
                                }
                                var receiverInput = RedstoneEngineering.DIFFERENTIAL_RECEIVER.get().engineeringSnapshot(
                                        helper.getLevel(), helper.absolutePos(RECEIVER), helper.getBlockState(RECEIVER), Direction.NORTH).orElse(null);
                                var receiverOutput = RedstoneEngineering.DIFFERENTIAL_RECEIVER.get().engineeringSnapshot(
                                        helper.getLevel(), helper.absolutePos(RECEIVER), helper.getBlockState(RECEIVER), Direction.SOUTH).orElse(null);
                                if (receiverInput == null || receiverInput.quality() != PortQuality.TOPOLOGY_ERROR
                                        || receiverOutput == null || receiverOutput.quality() != PortQuality.TOPOLOGY_ERROR
                                        || helper.getBlockState(RECEIVER).getValue(DirectionalSignalBlock.OUTPUT) != 0) {
                                    helper.fail("Differential receiver hid contention or emitted a false redstone HIGH", RECEIVER);
                                    return;
                                }

                                helper.setBlock(FAULT_DRIVER, Blocks.AIR.defaultBlockState());
                                helper.setBlock(FAULT_SOURCE, Blocks.AIR.defaultBlockState());
                                helper.runAfterDelay(5, () -> {
                                    if (!assertPath(helper, 1, "contention recovery")) return;
                                    helper.succeed();
                                });
                            });
                        });
                    });
                });
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void differentialReceiverQualityPropagatesIntoAnalogIndicator(GameTestHelper helper) {
        BlockPos source = new BlockPos(0, 1, 2);
        BlockPos driver = new BlockPos(1, 1, 2);
        BlockPos pair = new BlockPos(2, 1, 2);
        BlockPos receiver = new BlockPos(3, 1, 2);
        BlockPos indicator = new BlockPos(4, 1, 2);
        BlockPos faultSource = new BlockPos(2, 1, 0);
        BlockPos faultDriver = new BlockPos(2, 1, 1);

        helper.setBlock(source, reference(Direction.EAST, 15));
        helper.setBlock(driver, RedstoneEngineering.DIFFERENTIAL_DRIVER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(pair, RedstoneEngineering.DIFFERENTIAL_DATA_PAIR.get().defaultBlockState());
        helper.setBlock(receiver, RedstoneEngineering.DIFFERENTIAL_RECEIVER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));
        helper.setBlock(indicator, RedstoneEngineering.ANALOG_INDICATOR.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.EAST));

        helper.runAfterDelay(5, () -> {
            BlockPos pairWorld = helper.absolutePos(pair);
            BlockPos receiverWorld = helper.absolutePos(receiver);
            BlockPos indicatorWorld = helper.absolutePos(indicator);
            var baseline = RedstoneEngineering.ANALOG_INDICATOR.get().engineeringSnapshot(
                    helper.getLevel(), indicatorWorld, helper.getBlockState(indicator), Direction.WEST).orElse(null);
            if (DifferentialNetwork.quality(helper.getLevel(), pairWorld) != PortQuality.VALID
                    || baseline == null || baseline.quality() != PortQuality.VALID
                    || Math.round(baseline.value()) != 15
                    || helper.getBlockState(indicator).getValue(AnalogIndicatorBlock.LEVEL) != 15) {
                helper.fail("Analog indicator integration baseline did not settle to a valid differential HIGH", indicator);
                return;
            }

            helper.setBlock(faultSource, reference(Direction.SOUTH, 0));
            helper.setBlock(faultDriver, RedstoneEngineering.DIFFERENTIAL_DRIVER.get().defaultBlockState()
                    .setValue(DirectionalDomainBlock.FACING, Direction.SOUTH));
            helper.runAfterDelay(5, () -> {
                if (DifferentialNetwork.driverCount(helper.getLevel(), pairWorld) != 2
                        || DifferentialNetwork.quality(helper.getLevel(), pairWorld) != PortQuality.TOPOLOGY_ERROR) {
                    helper.fail("Differential fault fixture did not create real two-driver contention", pair);
                    return;
                }

                var receiverOut = RedstoneEngineering.DIFFERENTIAL_RECEIVER.get().engineeringSnapshot(
                        helper.getLevel(), receiverWorld, helper.getBlockState(receiver), Direction.EAST).orElse(null);
                var indicatorIn = RedstoneEngineering.ANALOG_INDICATOR.get().engineeringSnapshot(
                        helper.getLevel(), indicatorWorld, helper.getBlockState(indicator), Direction.WEST).orElse(null);
                if (receiverOut == null || receiverOut.quality() != PortQuality.TOPOLOGY_ERROR
                        || Math.round(receiverOut.value()) != 0
                        || indicatorIn == null || indicatorIn.quality() != PortQuality.TOPOLOGY_ERROR
                        || Math.round(indicatorIn.value()) != 0
                        || helper.getBlockState(indicator).getValue(AnalogIndicatorBlock.LEVEL) != 0) {
                    helper.fail("Analog indicator promoted an engineering-output topology error to an ordinary valid zero", indicator);
                    return;
                }

                helper.setBlock(faultDriver, Blocks.AIR.defaultBlockState());
                helper.setBlock(faultSource, Blocks.AIR.defaultBlockState());
                helper.runAfterDelay(5, () -> {
                    var recovered = RedstoneEngineering.ANALOG_INDICATOR.get().engineeringSnapshot(
                            helper.getLevel(), indicatorWorld, helper.getBlockState(indicator), Direction.WEST).orElse(null);
                    if (DifferentialNetwork.driverCount(helper.getLevel(), pairWorld) != 1
                            || DifferentialNetwork.quality(helper.getLevel(), pairWorld) != PortQuality.VALID
                            || recovered == null || recovered.quality() != PortQuality.VALID
                            || Math.round(recovered.value()) != 15
                            || helper.getBlockState(indicator).getValue(AnalogIndicatorBlock.LEVEL) != 15) {
                        helper.fail("Analog indicator did not recover after upstream engineering contention cleared", indicator);
                        return;
                    }
                    helper.succeed();
                });
            });
        });
    }

    private static void buildPath(GameTestHelper helper, int sourcePower) {
        helper.setBlock(SOURCE, reference(Direction.EAST, sourcePower));
        helper.setBlock(DRIVER, RedstoneEngineering.DIFFERENTIAL_DRIVER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(PAIR_A, RedstoneEngineering.DIFFERENTIAL_DATA_PAIR.get().defaultBlockState());
        helper.setBlock(PAIR_B, RedstoneEngineering.DIFFERENTIAL_DATA_PAIR.get().defaultBlockState());
        helper.setBlock(RECEIVER, RedstoneEngineering.DIFFERENTIAL_RECEIVER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.SOUTH));
        helper.setBlock(JUNCTION, RedstoneEngineering.REDSTONE_CABLE_JUNCTION.get().defaultBlockState());
    }

    private static boolean assertPath(GameTestHelper helper, int expectedBit, String phase) {
        BlockState junctionState = helper.getBlockState(JUNCTION);
        if (junctionState.getValue(RedstoneCableJunctionBlock.MEDIUM)
                != TransmissionTopology.SignalMedium.DIFFERENTIAL) {
            helper.fail("Signal Junction did not resolve DIFFERENTIAL during " + phase, JUNCTION);
            return false;
        }

        BlockPos pairAWorld = helper.absolutePos(PAIR_A);
        BlockPos pairBWorld = helper.absolutePos(PAIR_B);
        if (DifferentialNetwork.collect(helper.getLevel(), pairAWorld).size() != 3) {
            helper.fail("Differential visible route and graph disagreed during " + phase, JUNCTION);
            return false;
        }
        for (BlockPos world : new BlockPos[]{pairAWorld, pairBWorld}) {
            InformationRuntime.Snapshot snapshot = InformationRuntime.snapshot(helper.getLevel(), "diff", world);
            if (DifferentialNetwork.quality(helper.getLevel(), world) != PortQuality.VALID
                    || DifferentialNetwork.driverCount(helper.getLevel(), world) != 1
                    || !snapshot.valid() || (snapshot.value() & 1) != expectedBit) {
                helper.fail("Differential medium mismatch during " + phase, PAIR_A);
                return false;
            }
        }

        var receiverInput = RedstoneEngineering.DIFFERENTIAL_RECEIVER.get().engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(RECEIVER), helper.getBlockState(RECEIVER), Direction.NORTH).orElse(null);
        var receiverOutput = RedstoneEngineering.DIFFERENTIAL_RECEIVER.get().engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(RECEIVER), helper.getBlockState(RECEIVER), Direction.SOUTH).orElse(null);
        int expectedRedstone = expectedBit == 0 ? 0 : 15;
        if (receiverInput == null || receiverInput.quality() != PortQuality.VALID
                || Math.round(receiverInput.value()) != expectedBit
                || receiverOutput == null || receiverOutput.quality() != PortQuality.VALID
                || Math.round(receiverOutput.value()) != expectedRedstone
                || helper.getBlockState(RECEIVER).getValue(DirectionalSignalBlock.OUTPUT) != expectedRedstone) {
            helper.fail("Differential receiver conversion mismatch during " + phase, RECEIVER);
            return false;
        }
        return true;
    }

    private static boolean assertReceiverCleared(GameTestHelper helper, String phase) {
        BlockState receiverState = helper.getBlockState(RECEIVER);
        var input = RedstoneEngineering.DIFFERENTIAL_RECEIVER.get().engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(RECEIVER), receiverState, Direction.NORTH).orElse(null);
        var output = RedstoneEngineering.DIFFERENTIAL_RECEIVER.get().engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(RECEIVER), receiverState, Direction.SOUTH).orElse(null);
        if (input == null || input.quality() != PortQuality.NO_SIGNAL
                || output == null || output.quality() != PortQuality.NO_SIGNAL
                || receiverState.getValue(DirectionalSignalBlock.OUTPUT) != 0) {
            helper.fail("Differential receiver retained or fabricated signal during " + phase, RECEIVER);
            return false;
        }
        return true;
    }

    private static BlockState reference(Direction facing, int power) {
        return RedstoneEngineering.REDSTONE_REFERENCE_SOURCE.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, facing)
                .setValue(RedstoneReferenceSourceBlock.POWER, power);
    }
}
