package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.CopperNetworkSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Lightweight regressions for serial-first Copper terminal evidence. */
public final class RseCopperTerminalEvidenceGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseCopperTerminalEvidenceGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 30)
    public static void multipleCopperTerminalFeedsFailClosed(GameTestHelper helper) {
        BlockPos terminal = new BlockPos(2, 1, 2);
        BlockPos westSource = new BlockPos(1, 1, 2);
        BlockPos eastSource = new BlockPos(3, 1, 2);

        helper.setBlock(westSource, RedstoneEngineering.COPPER_VOLTAGE_SOURCE.get().defaultBlockState());
        helper.setBlock(eastSource, RedstoneEngineering.COPPER_VOLTAGE_SOURCE.get().defaultBlockState());

        CopperNetworkSupport.TerminalInput input = CopperNetworkSupport.terminalInput(
                helper.getLevel(), helper.absolutePos(terminal));
        if (input.connectedFeeds() != 2
                || input.voltage() != 0
                || input.quality() != PortQuality.TOPOLOGY_ERROR) {
            helper.fail("Multiple direct Copper terminal feeds must fail closed as TOPOLOGY_ERROR", terminal);
            return;
        }
        helper.succeed();
    }
}
