package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.FreeSpaceOpticalReceiverBlock;
import dev.redstoneengineering.block.QuartzClockDividerBlock;
import dev.redstoneengineering.block.QuartzOscillatorBlock;
import dev.redstoneengineering.block.QuartzStabilityMonitorBlock;
import dev.redstoneengineering.block.RadioReceiverBlock;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.RadioKernel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Fifth block-by-block acceptance campaign: communication endpoints and timing integrity. */
public final class RseFifthEightAcceptanceGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseFifthEightAcceptanceGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void differentialDriverOwnsRedstoneBackAndDifferentialFront(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(0, 1, 2);
        BlockPos driverPos = new BlockPos(1, 1, 2);
        BlockPos pairPos = new BlockPos(2, 1, 2);

        helper.setBlock(sourcePos, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(driverPos, RedstoneEngineering.DIFFERENTIAL_DRIVER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(pairPos, RedstoneEngineering.DIFFERENTIAL_DATA_PAIR.get().defaultBlockState());

        helper.runAfterDelay(4, () -> {
            EngineeringPortProvider driver = RedstoneEngineering.DIFFERENTIAL_DRIVER.get();
            BlockState state = helper.getBlockState(driverPos);
            var back = driver.engineeringPort(state, Direction.WEST).orElse(null);
            var front = driver.engineeringPort(state, Direction.EAST).orElse(null);
            BlockPos pairWorld = helper.absolutePos(pairPos);
            if (back == null || back.domain() != EngineeringDomain.REDSTONE || back.direction() != PortDirection.INPUT
                    || front == null || front.domain() != EngineeringDomain.DIFFERENTIAL_DATA
                    || front.direction() != PortDirection.OUTPUT) {
                helper.fail("Differential driver did not expose REDSTONE BACK -> DIFFERENTIAL_DATA FRONT", driverPos);
                return;
            }
            if (!InformationRuntime.valid(helper.getLevel(), "diff", pairWorld)
                    || (InformationRuntime.value(helper.getLevel(), "diff", pairWorld) & 1) != 1) {
                helper.fail("Differential driver did not publish the HIGH input bit onto its pair", pairPos);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 70)
    public static void differentialReceiverConvertsPairAndDropsOnDisconnect(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(0, 1, 2);
        BlockPos driverPos = new BlockPos(1, 1, 2);
        BlockPos pairPos = new BlockPos(2, 1, 2);
        BlockPos receiverPos = new BlockPos(3, 1, 2);

        helper.setBlock(sourcePos, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(driverPos, RedstoneEngineering.DIFFERENTIAL_DRIVER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(pairPos, RedstoneEngineering.DIFFERENTIAL_DATA_PAIR.get().defaultBlockState());
        helper.setBlock(receiverPos, RedstoneEngineering.DIFFERENTIAL_RECEIVER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));

        helper.runAfterDelay(5, () -> {
            BlockState state = helper.getBlockState(receiverPos);
            EngineeringPortProvider receiver = RedstoneEngineering.DIFFERENTIAL_RECEIVER.get();
            var back = receiver.engineeringPort(state, Direction.WEST).orElse(null);
            var front = receiver.engineeringPort(state, Direction.EAST).orElse(null);
            if (back == null || back.domain() != EngineeringDomain.DIFFERENTIAL_DATA
                    || front == null || front.domain() != EngineeringDomain.REDSTONE
                    || state.getValue(DirectionalSignalBlock.OUTPUT) != 15) {
                helper.fail("Differential receiver did not reconstruct HIGH as redstone 15", receiverPos);
                return;
            }
            helper.setBlock(pairPos, Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(5, () -> {
                if (helper.getBlockState(receiverPos).getValue(DirectionalSignalBlock.OUTPUT) != 0) {
                    helper.fail("Differential receiver retained output after its pair was disconnected", receiverPos);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void radioTransmitterSeparatesAntennaFromPayloadInputs(GameTestHelper helper) {
        BlockPos txPos = new BlockPos(2, 1, 2);
        BlockPos antennaPower = new BlockPos(2, 2, 2);
        BlockPos sidePower = new BlockPos(1, 1, 2);
        BlockPos probePos = new BlockPos(4, 1, 2);

        helper.setBlock(antennaPower, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(txPos, RedstoneEngineering.RADIO_TRANSMITTER.get().defaultBlockState());

        helper.runAfterDelay(3, () -> {
            BlockState state = helper.getBlockState(txPos);
            EngineeringPortProvider tx = RedstoneEngineering.RADIO_TRANSMITTER.get();
            var antenna = tx.engineeringPort(state, Direction.UP).orElse(null);
            if (antenna == null || antenna.domain() != EngineeringDomain.RADIO_DATA
                    || antenna.direction() != PortDirection.OUTPUT) {
                helper.fail("Radio TX did not expose its UP RADIO_DATA antenna", txPos);
                return;
            }
            if (RadioKernel.receivePacket(helper.getLevel(), helper.absolutePos(probePos), 0).valid()) {
                helper.fail("Redstone above the antenna face incorrectly became a radio payload input", txPos);
                return;
            }
            helper.setBlock(sidePower, Blocks.REDSTONE_BLOCK.defaultBlockState());
            helper.runAfterDelay(3, () -> {
                var reception = RadioKernel.receivePacket(helper.getLevel(), helper.absolutePos(probePos), 0);
                if (!reception.valid() || reception.value() != 15 || reception.drivers() != 1) {
                    helper.fail("Radio TX did not transmit its strongest non-antenna redstone payload", txPos);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void radioReceiverReportsAntennaAndRejectsSameChannelCollision(GameTestHelper helper) {
        BlockPos txAPos = new BlockPos(0, 1, 1);
        BlockPos txBPos = new BlockPos(0, 1, 3);
        BlockPos powerAPos = new BlockPos(0, 0, 1);
        BlockPos powerBPos = new BlockPos(0, 0, 3);
        BlockPos rxPos = new BlockPos(3, 1, 2);

        helper.setBlock(powerAPos, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(txAPos, RedstoneEngineering.RADIO_TRANSMITTER.get().defaultBlockState());
        helper.setBlock(rxPos, RedstoneEngineering.RADIO_RECEIVER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));

        helper.runAfterDelay(8, () -> {
            BlockState state = helper.getBlockState(rxPos);
            RadioReceiverBlock receiver = RedstoneEngineering.RADIO_RECEIVER.get();
            var antenna = receiver.engineeringPort(state, Direction.UP).orElse(null);
            if (antenna == null || antenna.domain() != EngineeringDomain.RADIO_DATA
                    || state.getValue(DirectionalSignalBlock.OUTPUT) != 15) {
                helper.fail("Radio RX did not decode one valid same-channel transmitter", rxPos);
                return;
            }
            helper.setBlock(powerBPos, Blocks.REDSTONE_BLOCK.defaultBlockState());
            helper.setBlock(txBPos, RedstoneEngineering.RADIO_TRANSMITTER.get().defaultBlockState());
            helper.runAfterDelay(8, () -> {
                BlockState collisionState = helper.getBlockState(rxPos);
                var snapshot = receiver.engineeringSnapshot(
                        helper.getLevel(), helper.absolutePos(rxPos), collisionState, Direction.UP).orElse(null);
                if (collisionState.getValue(DirectionalSignalBlock.OUTPUT) != 0
                        || snapshot == null || snapshot.quality() != PortQuality.TOPOLOGY_ERROR) {
                    helper.fail("Radio RX did not reject and diagnose a same-channel collision", rxPos);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 70)
    public static void freeSpaceOpticalTransmitterUsesOnlyDirectionalBackInput(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(0, 1, 2);
        BlockPos txPos = new BlockPos(1, 1, 2);
        BlockPos rxPos = new BlockPos(4, 1, 2);

        helper.setBlock(sourcePos, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(txPos, RedstoneEngineering.FREE_SPACE_OPTICAL_TRANSMITTER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(rxPos, RedstoneEngineering.FREE_SPACE_OPTICAL_RECEIVER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));

        helper.runAfterDelay(7, () -> {
            BlockState state = helper.getBlockState(txPos);
            EngineeringPortProvider tx = RedstoneEngineering.FREE_SPACE_OPTICAL_TRANSMITTER.get();
            var back = tx.engineeringPort(state, Direction.WEST).orElse(null);
            var front = tx.engineeringPort(state, Direction.EAST).orElse(null);
            BlockPos rxWorld = helper.absolutePos(rxPos);
            if (back == null || back.domain() != EngineeringDomain.REDSTONE
                    || front == null || front.domain() != EngineeringDomain.OPTICAL) {
                helper.fail("Free-space optical TX did not expose REDSTONE BACK -> OPTICAL FRONT", txPos);
                return;
            }
            if (!InformationRuntime.valid(helper.getLevel(), "free_optical", rxWorld)
                    || InformationRuntime.value(helper.getLevel(), "free_optical", rxWorld) <= 0) {
                helper.fail("Directional free-space optical transmitter failed to illuminate aligned receiver", rxPos);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void freeSpaceOpticalReceiverRejectsChannelMismatch(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(0, 1, 2);
        BlockPos txPos = new BlockPos(1, 1, 2);
        BlockPos rxPos = new BlockPos(4, 1, 2);

        helper.setBlock(sourcePos, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(txPos, RedstoneEngineering.FREE_SPACE_OPTICAL_TRANSMITTER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(rxPos, RedstoneEngineering.FREE_SPACE_OPTICAL_RECEIVER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST));

        helper.runAfterDelay(7, () -> {
            BlockState state = helper.getBlockState(rxPos);
            FreeSpaceOpticalReceiverBlock receiver = RedstoneEngineering.FREE_SPACE_OPTICAL_RECEIVER.get();
            if (state.getValue(DirectionalSignalBlock.OUTPUT) <= 0) {
                helper.fail("Aligned channel-0 optical receiver did not reconstruct the beam", rxPos);
                return;
            }
            helper.setBlock(rxPos, state.setValue(FreeSpaceOpticalReceiverBlock.CHANNEL, 1));
            helper.runAfterDelay(7, () -> {
                BlockState mismatch = helper.getBlockState(rxPos);
                if (mismatch.getValue(DirectionalSignalBlock.OUTPUT) != 0) {
                    helper.fail("Optical RX continued driving redstone after channel mismatch", rxPos);
                    return;
                }
                var back = receiver.engineeringPort(mismatch, Direction.WEST).orElse(null);
                if (back == null || back.domain() != EngineeringDomain.OPTICAL) {
                    helper.fail("Optical RX did not retain its physical OPTICAL BACK input contract", rxPos);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void quartzClockDividerPublishesExpectedPeriod(GameTestHelper helper) {
        BlockPos oscillatorPos = new BlockPos(1, 1, 2);
        BlockPos dividerPos = new BlockPos(2, 1, 2);
        BlockPos linePos = new BlockPos(3, 1, 2);

        helper.setBlock(oscillatorPos, RedstoneEngineering.QUARTZ_OSCILLATOR.get().defaultBlockState()
                .setValue(QuartzOscillatorBlock.PERIOD_INDEX, 0));
        helper.setBlock(dividerPos, RedstoneEngineering.QUARTZ_CLOCK_DIVIDER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                .setValue(QuartzClockDividerBlock.DIV_INDEX, 0));
        helper.setBlock(linePos, RedstoneEngineering.QUARTZ_TIMING_LINE.get().defaultBlockState());

        helper.runAfterDelay(12, () -> {
            BlockState state = helper.getBlockState(dividerPos);
            QuartzClockDividerBlock divider = RedstoneEngineering.QUARTZ_CLOCK_DIVIDER.get();
            var input = divider.engineeringPort(state, Direction.WEST).orElse(null);
            var output = divider.engineeringPort(state, Direction.EAST).orElse(null);
            var sample = DomainNetwork.sampleQuartz(helper.getLevel(), helper.absolutePos(linePos));
            if (input == null || input.domain() != EngineeringDomain.QUARTZ
                    || output == null || output.domain() != EngineeringDomain.QUARTZ) {
                helper.fail("Quartz divider did not expose QUARTZ input/output timing ports", dividerPos);
                return;
            }
            if (!sample.valid() || sample.periodTicks() != 4) {
                helper.fail("Quartz ÷2 divider did not convert a 2t input period into a 4t output period", linePos);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 80)
    public static void quartzStabilityMonitorMeasuresPeriodWithoutDriving(GameTestHelper helper) {
        BlockPos oscillatorPos = new BlockPos(1, 1, 2);
        BlockPos monitorPos = new BlockPos(2, 1, 2);
        BlockPos frontPos = new BlockPos(3, 1, 2);

        helper.setBlock(oscillatorPos, RedstoneEngineering.QUARTZ_OSCILLATOR.get().defaultBlockState()
                .setValue(QuartzOscillatorBlock.PERIOD_INDEX, 0));
        helper.setBlock(monitorPos, RedstoneEngineering.QUARTZ_STABILITY_MONITOR.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));

        helper.runAfterDelay(12, () -> {
            QuartzStabilityMonitorBlock monitor = RedstoneEngineering.QUARTZ_STABILITY_MONITOR.get();
            BlockState state = helper.getBlockState(monitorPos);
            var port = monitor.engineeringPort(state, Direction.WEST).orElse(null);
            if (port == null || port.domain() != EngineeringDomain.QUARTZ
                    || port.direction() != PortDirection.INPUT) {
                helper.fail("Quartz stability monitor did not expose its QUARTZ measurement input", monitorPos);
                return;
            }
            int measured = monitor.measuredPeriod(helper.getLevel(), helper.absolutePos(monitorPos));
            int error = monitor.nominalError(helper.getLevel(), helper.absolutePos(monitorPos));
            if (measured != 2 || error != 0) {
                helper.fail("Quartz stability monitor did not converge to measured=2t error=0t", monitorPos);
                return;
            }
            if (!helper.getBlockState(frontPos).isAir()) {
                helper.fail("Quartz stability monitor unexpectedly created or drove a FRONT timing node", frontPos);
                return;
            }
            helper.succeed();
        });
    }
}
