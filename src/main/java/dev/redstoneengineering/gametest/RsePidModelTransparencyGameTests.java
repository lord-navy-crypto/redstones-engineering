package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.PidControllerBlock;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Behavioral guard for the PID model values surfaced by the engineering HMI. */
public final class RsePidModelTransparencyGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RsePidModelTransparencyGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void tuningModelsExposeImplementedDiscreteCoefficients(GameTestHelper helper) {
        PidControllerBlock.TuningModel gentle = PidControllerBlock.tuningModel(0);
        PidControllerBlock.TuningModel balanced = PidControllerBlock.tuningModel(2);
        PidControllerBlock.TuningModel aggressive = PidControllerBlock.tuningModel(3);
        if (!gentle.equals(new PidControllerBlock.TuningModel(1, 0, 0, 2, 2))
                || !balanced.equals(new PidControllerBlock.TuningModel(2, 18, 1, 3, 2))
                || !aggressive.equals(new PidControllerBlock.TuningModel(3, 14, 2, 4, 2))) {
            helper.fail("PID HMI coefficient contract drifted from the implemented preset model");
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 40)
    public static void runtimeTermInspectionIsObserverNeutralAndReconstructsOutput(GameTestHelper helper) {
        BlockPos relative = new BlockPos(2, 1, 2);
        BlockPos world = helper.absolutePos(relative);
        helper.setBlock(relative, RedstoneEngineering.PID_CONTROLLER.get().defaultBlockState());

        int before = RuntimeIntStore.entryCount(helper.getLevel());
        PidControllerBlock.RuntimeTerms empty = PidControllerBlock.runtimeTerms(helper.getLevel(), world, 2, 5);
        if (empty.available() || RuntimeIntStore.entryCount(helper.getLevel()) != before) {
            helper.fail("PID model inspection allocated or mutated runtime evidence", relative);
            return;
        }

        int[] rt = RuntimeIntStore.get(helper.getLevel(), "pid", world, 27);
        rt[21] = 1;
        rt[0] = 36;
        rt[2] = 2;
        rt[3] = 15;
        rt[20] = 8;
        rt[22] = 8;
        rt[23] = 2;
        rt[24] = 2;
        rt[25] = 20;
        rt[26] = 2;
        PidControllerBlock.RuntimeTerms terms = PidControllerBlock.runtimeTerms(helper.getLevel(), world, 2, 4);
        if (!terms.available() || terms.pTerm() != 8 || terms.iTerm() != 2 || terms.dTerm() != 2
                || terms.unsaturatedOutput() != 20 || terms.output() != 15
                || !terms.saturated() || !terms.antiWindupHolding()) {
            helper.fail("PID displayed term reconstruction does not match the implemented discrete solve", relative);
            return;
        }
        helper.succeed();
    }
}
