package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.AbstractLapisTransducerBlock;
import dev.redstoneengineering.block.CalibrationModuleBlock;
import dev.redstoneengineering.block.PwmControllerBlock;
import dev.redstoneengineering.block.SampleHoldBlock;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.physics.SensorModel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Behavioral contracts for the seven-block Pioneer Wave 14 signal/transduction rollout. */
public final class RsePioneerWave14GameTests {
    private static final String TEMPLATE = "empty5x4x5";
    private static final BlockPos MARKER = new BlockPos(2, 1, 2);

    private RsePioneerWave14GameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void wave14PortRolesRemainDeviceSpecific(GameTestHelper helper) {
        CalibrationModuleBlock calibration = RedstoneEngineering.CALIBRATION_MODULE.get();
        BlockState calibrationState = calibration.defaultBlockState();
        if (calibration.engineeringPorts(calibrationState).size() != 3
                || calibration.engineeringPorts(calibrationState).stream().noneMatch(p -> p.label().equals("OBSERVED") && p.direction() == PortDirection.INPUT)
                || calibration.engineeringPorts(calibrationState).stream().noneMatch(p -> p.label().equals("REFERENCE") && p.direction() == PortDirection.INPUT)
                || calibration.engineeringPorts(calibrationState).stream().noneMatch(p -> p.label().equals("CALIBRATED") && p.direction() == PortDirection.OUTPUT)) {
            helper.fail("Calibration Module must retain distinct OBSERVED / REFERENCE / CALIBRATED ports", MARKER);
            return;
        }

        SampleHoldBlock sampleHold = RedstoneEngineering.SAMPLE_HOLD.get();
        if (sampleHold.engineeringPorts(sampleHold.defaultBlockState()).size() != 4) {
            helper.fail("Sample & Hold must retain VALUE / HELD / TRIGGER / RESET as four physical roles", MARKER);
            return;
        }

        PwmControllerBlock pwm = RedstoneEngineering.PWM_CONTROLLER.get();
        if (pwm.engineeringPorts(pwm.defaultBlockState()).size() != 3) {
            helper.fail("PWM Controller must retain COMMAND / PWM OUT / INHIBIT as three physical roles", MARKER);
            return;
        }

        assertTransducer(helper, RedstoneEngineering.LAPIS_TEMPERATURE_TRANSDUCER.get(), EngineeringDomain.THERMAL);
        assertTransducer(helper, RedstoneEngineering.LAPIS_MAGNETIC_TRANSDUCER.get(), EngineeringDomain.IRON_MAGNETIC);
        assertTransducer(helper, RedstoneEngineering.LAPIS_OPTICAL_TRANSDUCER.get(), EngineeringDomain.OPTICAL);
        assertTransducer(helper, RedstoneEngineering.LAPIS_VOLTAGE_TRANSDUCER.get(), EngineeringDomain.COPPER);
        if (helper.getLevel() == null) return;
        helper.succeed();
    }

    private static void assertTransducer(GameTestHelper helper, Block block, EngineeringDomain inputDomain) {
        if (!(block instanceof AbstractLapisTransducerBlock transducer)) {
            helper.fail("Wave 14 transducer registry entry lost AbstractLapisTransducerBlock contract", MARKER);
            return;
        }
        var ports = transducer.engineeringPorts(transducer.defaultBlockState());
        boolean input = ports.stream().anyMatch(p -> p.direction() == PortDirection.INPUT && p.domain() == inputDomain);
        boolean output = ports.stream().anyMatch(p -> p.direction() == PortDirection.OUTPUT && p.domain() == EngineeringDomain.LAPIS);
        if (!input || !output) {
            helper.fail("Wave 14 transducer must preserve physical input domain and isolated Lapis output", MARKER);
        }
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void pwmQuantizationSweepIsBoundedAndEndpointExact(GameTestHelper helper) {
        for (int mode = 0; mode < 4; mode++) {
            int period = PwmControllerBlock.periodFor(mode);
            int previous = -1;
            for (int command = 0; command <= 15; command++) {
                int onTicks = PwmControllerBlock.quantizedOnTicks(command, period);
                int effective = PwmControllerBlock.effectiveDutyPermille(command, period);
                if (onTicks < 0 || onTicks > period || onTicks < previous || effective < 0 || effective > 1000) {
                    helper.fail("PWM quantization must remain bounded and monotonic for every supported period", MARKER);
                    return;
                }
                previous = onTicks;
            }
            if (PwmControllerBlock.quantizedOnTicks(0, period) != 0
                    || PwmControllerBlock.quantizedOnTicks(15, period) != period) {
                helper.fail("PWM endpoints must remain exact at command 0 and 15", MARKER);
                return;
            }
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void transducerProfilesExposeRealSamplingQuantities(GameTestHelper helper) {
        for (int profile = 0; profile < 4; profile++) {
            if (SensorModel.samplePeriod(profile) <= 0
                    || SensorModel.resolutionStep(profile) <= 0
                    || SensorModel.noiseAmplitude(profile) < 0
                    || SensorModel.latencySamples(profile) < 0) {
                helper.fail("Every transducer profile must expose bounded sample/resolution/noise/latency quantities", MARKER);
                return;
            }
        }
        helper.succeed();
    }
}
