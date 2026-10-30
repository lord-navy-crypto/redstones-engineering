package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.CopperCableJunctionBlock;
import dev.redstoneengineering.block.GuidedOpticalEvidenceSupport;
import dev.redstoneengineering.block.MeterEvidenceSupport;
import dev.redstoneengineering.block.OpticalEmitterBlock;
import dev.redstoneengineering.block.OpticalFiberJunctionBlock;
import dev.redstoneengineering.block.OpticalReceiverBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Final ten: actual block snapshots must not promote untrusted cached values. */
public final class RseFinalTenIntegrityGameTests {
    private static final String TEMPLATE = "empty5x4x5";
    private RseFinalTenIntegrityGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void guidedOpticalReceiverConflictCannotExposeRetainedIntensity(GameTestHelper helper) {
        BlockPos local = new BlockPos(2, 1, 2);
        var receiver = RedstoneEngineering.OPTICAL_RECEIVER.get();
        helper.setBlock(local, receiver.defaultBlockState());
        BlockPos world = helper.absolutePos(local);
        int[] state = RuntimeIntStore.get(helper.getLevel(), "optical_receiver", world, 6);
        state[0] = 14; // Previously valid carrier value.
        state[2] = 1;
        state[3] = 2; // Two competing drivers cannot establish one optical source.
        state[4] = 1;
        state[5] = PortQuality.VALID.ordinal();
        var port = receiver.engineeringSnapshot(helper.getLevel(), world,
                helper.getBlockState(local), Direction.NORTH).orElseThrow();
        if (port.quality() != PortQuality.TOPOLOGY_ERROR || port.value() != 0.0) {
            helper.fail("Guided optical receiver leaked intensity through competing-driver fault", local);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void copperJunctionInvalidDriverSuppressesOldVoltage(GameTestHelper helper) {
        BlockPos local = new BlockPos(2, 1, 2);
        var junction = RedstoneEngineering.COPPER_CABLE_JUNCTION.get();
        helper.setBlock(local, junction.defaultBlockState());
        BlockPos world = helper.absolutePos(local);
        int[] payload = RuntimeIntStore.get(helper.getLevel(), "copper_junction", world, 3);
        payload[0] = 15;
        payload[1] = 0;
        payload[2] = PortQuality.NO_SIGNAL.ordinal();
        if (MeterEvidenceSupport.readable(CopperCableJunctionBlock.voltage(helper.getLevel(), world),
                15, CopperCableJunctionBlock.quality(helper.getLevel(), world,
                        helper.getBlockState(local))) != 0) {
            helper.fail("Copper junction treated disconnected stored voltage as an active carrier", local);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void guidedOpticalZeroSettingIsValidButNotALiveCarrier(GameTestHelper helper) {
        BlockPos local = new BlockPos(2, 1, 2);
        var source = RedstoneEngineering.OPTICAL_EMITTER.get();
        helper.setBlock(local, source.defaultBlockState().setValue(OpticalEmitterBlock.INTENSITY, 0));
        var snapshot = source.engineeringSnapshot(helper.getLevel(), helper.absolutePos(local),
                helper.getBlockState(local), Direction.EAST).orElseThrow();
        if (snapshot.quality() != PortQuality.VALID || snapshot.value() != 0.0
                || GuidedOpticalEvidenceSupport.sinkQuality(1, 2, PortQuality.VALID, true)
                   != PortQuality.TOPOLOGY_ERROR) {
            helper.fail("Optical source setting and active-carrier quality were mixed", local);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void serviceOpenSpliceExportsNoOpticalPorts(GameTestHelper helper) {
        BlockPos local = new BlockPos(2, 1, 2);
        var junction = RedstoneEngineering.OPTICAL_FIBER_JUNCTION.get();
        helper.setBlock(local, junction.defaultBlockState()
                .setValue(OpticalFiberJunctionBlock.SERVICE_OPEN, true));
        if (!junction.engineeringPorts(helper.getBlockState(local)).isEmpty()) {
            helper.fail("Service-open optical splice exposed a conductive port", local);
            return;
        }
        helper.succeed();
    }
}
