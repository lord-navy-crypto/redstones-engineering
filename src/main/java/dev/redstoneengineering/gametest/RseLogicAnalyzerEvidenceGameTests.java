package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.blockentity.LogicAnalyzerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Behavioral acceptance coverage for retained Logic Analyzer evidence.
 *
 * <p>The Pioneer rollout UI is only useful if the server-authoritative capture semantics
 * underneath it are reproducible. These tests exercise valid/missing sample handling,
 * edge evidence, duty cycle and capture quality rather than checking UI tokens.</p>
 */
public final class RseLogicAnalyzerEvidenceGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseLogicAnalyzerEvidenceGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 20)
    public static void captureEvidenceUsesOnlyValidSamples(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, RedstoneEngineering.LOGIC_ANALYZER.get().defaultBlockState());

        if (!(helper.getBlockEntity(pos) instanceof LogicAnalyzerBlockEntity analyzer)) {
            helper.fail("Logic Analyzer block entity was not created", pos);
            return;
        }

        // CH1 sequence: valid LOW, missing, valid HIGH, valid HIGH.
        // The missing sample must reduce coverage but must not invent an edge because
        // edge counting requires two consecutive valid observations.
        analyzer.clear();
        analyzer.addSample(0b0000, 0b0001);
        analyzer.addSample(0b0000, 0b0000);
        analyzer.addSample(0b0001, 0b0001);
        analyzer.addSample(0b0001, 0b0001);

        require(helper, analyzer.sampleCount() == 4, "capture must retain all four time slots");
        require(helper, analyzer.validSamples(0) == 3, "CH1 must report three valid samples");
        require(helper, analyzer.highSamples(0) == 2, "CH1 must report two valid HIGH samples");
        require(helper, analyzer.coveragePercent(0) == 75, "CH1 coverage must be 75%");
        require(helper, analyzer.dutyPercent(0) == 66, "CH1 duty must use valid samples only (2/3 -> 66%)");
        require(helper, analyzer.rising(0) == 0, "missing sample must break edge continuity");
        require(helper, "PARTIAL".equals(analyzer.captureQuality(0)), "75% coverage must classify PARTIAL");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 20)
    public static void captureEvidenceCountsConsecutiveValidEdges(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, RedstoneEngineering.LOGIC_ANALYZER.get().defaultBlockState());

        if (!(helper.getBlockEntity(pos) instanceof LogicAnalyzerBlockEntity analyzer)) {
            helper.fail("Logic Analyzer block entity was not created", pos);
            return;
        }

        // CH1: LOW -> HIGH -> HIGH -> LOW, all valid.
        analyzer.clear();
        analyzer.addSample(0b0000, 0b0001);
        analyzer.addSample(0b0001, 0b0001);
        analyzer.addSample(0b0001, 0b0001);
        analyzer.addSample(0b0000, 0b0001);

        require(helper, analyzer.rising(0) == 1, "CH1 must retain one rising edge");
        require(helper, analyzer.falling(0) == 1, "CH1 must retain one falling edge");
        require(helper, analyzer.edgeCount(0) == 2, "CH1 total edge evidence must equal rise + fall");
        require(helper, analyzer.coveragePercent(0) == 100, "fully valid capture must report 100% coverage");
        require(helper, analyzer.dutyPercent(0) == 50, "two of four valid samples HIGH must report 50% duty");
        require(helper, "WARMUP".equals(analyzer.captureQuality(0)), "short complete capture must remain WARMUP");
        helper.succeed();
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
