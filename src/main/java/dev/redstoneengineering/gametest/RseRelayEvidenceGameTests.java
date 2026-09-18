package dev.redstoneengineering.gametest;

import dev.redstoneengineering.EngineeringSystemsModule;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.FaultInjectorBlock;
import dev.redstoneengineering.block.SingleRelayBlock;
import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Evidence-retention acceptance tests for the redstone engineering relay. */
public final class RseRelayEvidenceGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseRelayEvidenceGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 90)
    public static void relayHoldsLastPayloadAcrossFaultQuality(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 2);
        BlockPos injector = new BlockPos(2, 1, 2);
        BlockPos arm = new BlockPos(2, 1, 3);
        BlockPos relay = new BlockPos(3, 1, 2);
        BlockPos coil = new BlockPos(3, 1, 1);

        helper.setBlock(source, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(injector, EngineeringSystemsModule.FAULT_INJECTOR.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(DirectionalSignalBlock.INPUT_FACING, Direction.WEST)
                .setValue(FaultInjectorBlock.MODE, 0));
        helper.setBlock(coil, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(relay, RedstoneEngineering.SINGLE_RELAY.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(DirectionalSignalBlock.INPUT_FACING, Direction.WEST)
                .setValue(SingleRelayBlock.NORMALLY_CLOSED, false)
                .setValue(SingleRelayBlock.PICKUP_MODE, 0));

        helper.runAfterDelay(5, () -> {
            BlockPos relayWorld = helper.absolutePos(relay);
            BlockState relayState = helper.getBlockState(relay);
            var initialOut = RedstoneEngineering.SINGLE_RELAY.get().engineeringSnapshot(
                    helper.getLevel(), relayWorld, relayState, Direction.EAST).orElseThrow();
            if (relayState.getValue(DirectionalSignalBlock.OUTPUT) != 15
                    || initialOut.value() != 15.0
                    || initialOut.quality() != PortQuality.VALID
                    || SingleRelayBlock.payloadHoldActive(helper.getLevel(), relayWorld)
                    || SingleRelayBlock.payloadBadEpisodes(helper.getLevel(), relayWorld) != 0) {
                helper.fail("Relay did not establish a valid closed-contact payload baseline", relay);
                return;
            }

            helper.setBlock(arm, Blocks.REDSTONE_BLOCK.defaultBlockState());
            helper.runAfterDelay(4, () -> {
                BlockState faultState = helper.getBlockState(relay);
                var faultOut = RedstoneEngineering.SINGLE_RELAY.get().engineeringSnapshot(
                        helper.getLevel(), relayWorld, faultState, Direction.EAST).orElseThrow();
                if (faultState.getValue(DirectionalSignalBlock.OUTPUT) != 15
                        || faultOut.value() != 15.0
                        || faultOut.quality() != PortQuality.FAULT
                        || !SingleRelayBlock.payloadHoldActive(helper.getLevel(), relayWorld)
                        || SingleRelayBlock.payloadBadEpisodes(helper.getLevel(), relayWorld) != 1) {
                    helper.fail("Relay treated FAULT-quality payload evidence as numerical zero instead of holding last good output", relay);
                    return;
                }

                helper.runAfterDelay(5, () -> {
                    if (SingleRelayBlock.payloadBadEpisodes(helper.getLevel(), relayWorld) != 1
                            || helper.getBlockState(relay).getValue(DirectionalSignalBlock.OUTPUT) != 15) {
                        helper.fail("Stable bad payload evidence inflated relay fault episodes or lost retained output", relay);
                        return;
                    }

                    helper.setBlock(arm, Blocks.AIR.defaultBlockState());
                    helper.runAfterDelay(4, () -> {
                        BlockState recoveredState = helper.getBlockState(relay);
                        var recoveredOut = RedstoneEngineering.SINGLE_RELAY.get().engineeringSnapshot(
                                helper.getLevel(), relayWorld, recoveredState, Direction.EAST).orElseThrow();
                        if (SingleRelayBlock.payloadHoldActive(helper.getLevel(), relayWorld)
                                || SingleRelayBlock.payloadBadEpisodes(helper.getLevel(), relayWorld) != 1
                                || recoveredState.getValue(DirectionalSignalBlock.OUTPUT) != 15
                                || recoveredOut.quality() != PortQuality.VALID) {
                            helper.fail("Relay did not recover cleanly from held payload evidence", relay);
                            return;
                        }
                        helper.succeed();
                    });
                });
            });
        });
    }
}
