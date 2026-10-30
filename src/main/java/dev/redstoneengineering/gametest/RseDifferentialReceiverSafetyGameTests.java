package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DifferentialReceiverBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.DigitalEndpointEvidenceSupport;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.InformationRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Source-free differential receiver must not retain or report asserted output. */
public final class RseDifferentialReceiverSafetyGameTests {
    private static final String TEMPLATE = "empty5x4x5";
    private RseDifferentialReceiverSafetyGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 45)
    public static void differentialReceiverWithdrawsOrphanedHigh(GameTestHelper helper) {
        BlockPos local = new BlockPos(2, 1, 2);
        var receiver = RedstoneEngineering.DIFFERENTIAL_RECEIVER.get();
        var placed = receiver.defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(DirectionalSignalBlock.INPUT_FACING, Direction.WEST)
                .setValue(DirectionalSignalBlock.OUTPUT, 15);
        helper.setBlock(local, placed);
        var portBefore = receiver.engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(local), helper.getBlockState(local), Direction.EAST)
                .orElseThrow();
        if (portBefore.value() != 0 || portBefore.quality() == PortQuality.VALID) {
            helper.fail("Unconnected differential RX presented a retained HIGH as valid data", local);
            return;
        }
        helper.runAfterDelay(18, () -> {
            var updated = helper.getBlockState(local);
            var snapshot = receiver.engineeringSnapshot(helper.getLevel(),
                    helper.absolutePos(local), updated, Direction.EAST).orElseThrow();
            if (updated.getValue(DirectionalSignalBlock.OUTPUT) != 0
                    || snapshot.value() != 0
                    || snapshot.quality() == PortQuality.VALID) {
                helper.fail("Differential RX failed to de-energize stale HIGH on scheduled recheck", local);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void byteAndBitQualityNeverPromoteInvalidEnvelope(GameTestHelper helper) {
        var invalid = new InformationRuntime.Snapshot(255, 0, false, 0, 1);
        var validZero = new InformationRuntime.Snapshot(0, 0, true, 100, 0);
        if (DigitalEndpointEvidenceSupport.outputQuality(PortQuality.VALID, invalid) == PortQuality.VALID
                || DigitalEndpointEvidenceSupport.valueWhenValid(PortQuality.STALE, invalid, 0xFF) != 0
                || DigitalEndpointEvidenceSupport.outputQuality(PortQuality.VALID, validZero) != PortQuality.VALID
                || DigitalEndpointEvidenceSupport.valueWhenValid(PortQuality.VALID, validZero, 1) != 0
                || DigitalEndpointEvidenceSupport.outputQuality(PortQuality.TOPOLOGY_ERROR, validZero)
                        != PortQuality.TOPOLOGY_ERROR) {
            helper.fail("Digital endpoint misclassified invalid byte, valid zero, or topology conflict");
            return;
        }
        helper.succeed();
    }
}
