package dev.redstoneengineering.validation;

import dev.redstoneengineering.block.AlarmProcessorBlock;
import dev.redstoneengineering.block.AnalogIndicatorBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.FaultInjectorBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.SafetyInterlockBlock;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Multi-stage L4 system self-tests. Only validation-owned stimulus sources are mutated. */
public final class RseValidationSystemsSelfTestService {
    private RseValidationSystemsSelfTestService() {}

    public static RseValidationSelfTestService.Evaluation evaluate(
            ServerLevel level,
            RseValidationSelfTestSavedData.Placement placement
    ) {
        return switch (placement.testId()) {
            case "03_systems/interlock_trip_restore" -> evaluateInterlock(level, placement);
            case "03_systems/fault_injector_bias" -> evaluateFaultInjector(level, placement);
            case "03_systems/alarm_latch_ack_reset" -> evaluateAlarm(level, placement);
            default -> fail("unknown systems self-test");
        };
    }

    private static RseValidationSelfTestService.Evaluation evaluateInterlock(
            ServerLevel level,
            RseValidationSelfTestSavedData.Placement placement
    ) {
        BlockPos origin = placement.origin();
        BlockPos dut = origin.offset(5, 1, 4);
        BlockPos indicatorPos = origin.offset(6, 1, 4);
        BlockPos permissiveB = origin.offset(5, 1, 3);
        BlockState state = level.getBlockState(dut);
        if (!(state.getBlock() instanceof SafetyInterlockBlock)) return fail("safety interlock missing");

        int mask = SafetyInterlockBlock.failedMask(level, dut);
        if (mask < 0) return waitFor("interlock runtime not evaluated");
        int output = state.getValue(DirectionalSignalBlock.OUTPUT);
        int phase = placement.checkCount();

        if (phase == 0) {
            if (mask != 0 || output != 15) return fail("baseline permit expected mask=0 output=15; got mask=" + mask + " output=" + output);
            RseValidationSelfTestService.Evaluation indicator = indicator(level, indicatorPos, 15);
            if (indicator.verdict() != RseValidationSelfTestService.Verdict.PASS) return indicator;
            if (!setReferencePower(level, permissiveB, 0)) return fail("could not drop permissive B");
            RseValidationSelfTestSavedData.get(level).advanceStep(placement.testId());
            return waitFor("baseline PASS; permissive B dropped to inject trip");
        }

        if (phase == 1) {
            if (mask != 2 || output != 0) return fail("trip expected failedMask=2 output=0; got mask=" + mask + " output=" + output);
            RseValidationSelfTestService.Evaluation indicator = indicator(level, indicatorPos, 0);
            if (indicator.verdict() != RseValidationSelfTestService.Verdict.PASS) return indicator;
            if (!setReferencePower(level, permissiveB, 15)) return fail("could not restore permissive B");
            RseValidationSelfTestSavedData.get(level).advanceStep(placement.testId());
            return waitFor("trip PASS; permissive B restored, waiting for permit recovery");
        }

        if (mask != 0 || output != 15) return fail("restored permit expected mask=0 output=15; got mask=" + mask + " output=" + output);
        RseValidationSelfTestService.Evaluation indicator = indicator(level, indicatorPos, 15);
        return indicator.verdict() == RseValidationSelfTestService.Verdict.PASS
                ? pass("baseline permit → B trip(mask=2) → permit restored")
                : indicator;
    }

    private static RseValidationSelfTestService.Evaluation evaluateFaultInjector(
            ServerLevel level,
            RseValidationSelfTestSavedData.Placement placement
    ) {
        BlockPos origin = placement.origin();
        BlockPos dut = origin.offset(4, 1, 4);
        BlockPos arm = origin.offset(4, 1, 5);
        BlockPos indicatorPos = origin.offset(5, 1, 4);
        BlockState state = level.getBlockState(dut);
        if (!(state.getBlock() instanceof FaultInjectorBlock injector)) return fail("fault injector missing");
        int phase = placement.checkCount();

        if (phase == 0) {
            if (FaultInjectorBlock.active(level, dut)) return fail("fault injector unexpectedly active at baseline");
            int output = state.getValue(DirectionalSignalBlock.OUTPUT);
            if (output != 6) return fail("unarmed passthrough expected 6, got " + output);
            RseValidationSelfTestService.Evaluation indicator = indicator(level, indicatorPos, 6);
            if (indicator.verdict() != RseValidationSelfTestService.Verdict.PASS) return indicator;
            if (!setReferencePower(level, arm, 15)) return fail("could not arm validation fault");
            RseValidationSelfTestSavedData.get(level).advanceStep(placement.testId());
            return waitFor("baseline passthrough PASS; BIAS +4 fault armed");
        }

        if (!FaultInjectorBlock.active(level, dut)) return fail("fault injector did not arm");
        if (FaultInjectorBlock.activationCount(level, dut) <= 0) return fail("fault activation evidence missing");
        if (FaultInjectorBlock.lastInput(level, dut) != 6 || FaultInjectorBlock.lastOutput(level, dut) != 10) {
            return fail("fault runtime expected 6→10, got " + FaultInjectorBlock.lastInput(level, dut) + "→" + FaultInjectorBlock.lastOutput(level, dut));
        }
        BlockState live = level.getBlockState(dut);
        EngineeringPortSnapshot snapshot = injector.engineeringSnapshot(level, dut, live, Direction.EAST).orElse(null);
        if (snapshot == null || snapshot.quality() != PortQuality.FAULT || Math.round(snapshot.value()) != 10) {
            return fail("faulted output evidence expected 10/FAULT");
        }
        RseValidationSelfTestService.Evaluation indicator = indicator(level, indicatorPos, 10);
        return indicator.verdict() == RseValidationSelfTestService.Verdict.PASS
                ? pass("unarmed 6 passthrough → armed BIAS+4 = 10 with FAULT quality")
                : indicator;
    }

    private static RseValidationSelfTestService.Evaluation evaluateAlarm(
            ServerLevel level,
            RseValidationSelfTestSavedData.Placement placement
    ) {
        BlockPos origin = placement.origin();
        BlockPos condition = origin.offset(4, 1, 4);
        BlockPos ack = origin.offset(5, 1, 3);
        BlockPos reset = origin.offset(5, 1, 5);
        BlockPos dut = origin.offset(5, 1, 4);
        BlockPos indicatorPos = origin.offset(6, 1, 4);
        BlockState state = level.getBlockState(dut);
        if (!(state.getBlock() instanceof AlarmProcessorBlock)) return fail("alarm processor missing");
        int phase = placement.checkCount();
        int output = state.getValue(DirectionalSignalBlock.OUTPUT);

        if (phase == 0) {
            if (AlarmProcessorBlock.latched(level, dut) || output != 0) return fail("alarm baseline must be clear");
            if (!setReferencePower(level, condition, 15)) return fail("could not raise alarm condition");
            RseValidationSelfTestSavedData.get(level).advanceStep(placement.testId());
            return waitFor("healthy baseline PASS; alarm condition raised");
        }

        if (phase == 1) {
            if (!AlarmProcessorBlock.latched(level, dut) || !AlarmProcessorBlock.unacknowledged(level, dut) || output != 10) {
                return fail("raised severity-2 alarm must be latched/unacknowledged/output10");
            }
            if (!setReferencePower(level, condition, 0)) return fail("could not clear alarm condition");
            RseValidationSelfTestSavedData.get(level).advanceStep(placement.testId());
            return waitFor("alarm latched PASS; condition cleared to test memory");
        }

        if (phase == 2) {
            if (!AlarmProcessorBlock.latched(level, dut) || !AlarmProcessorBlock.unacknowledged(level, dut) || output != 10) {
                return fail("alarm failed to remain latched after condition cleared");
            }
            if (!setReferencePower(level, ack, 15)) return fail("could not inject ACK edge");
            RseValidationSelfTestSavedData.get(level).advanceStep(placement.testId());
            return waitFor("latch memory PASS; ACK injected");
        }

        if (phase == 3) {
            if (!AlarmProcessorBlock.latched(level, dut) || AlarmProcessorBlock.unacknowledged(level, dut) || output != 10) {
                return fail("ACK must clear unacknowledged state without clearing latch");
            }
            setReferencePower(level, ack, 0);
            if (!setReferencePower(level, reset, 15)) return fail("could not inject healthy RESET edge");
            RseValidationSelfTestSavedData.get(level).advanceStep(placement.testId());
            return waitFor("ACK PASS; healthy RESET injected");
        }

        if (AlarmProcessorBlock.latched(level, dut) || output != 0) return fail("healthy RESET did not clear alarm latch");
        if (AlarmProcessorBlock.activationCount(level, dut) <= 0) return fail("alarm activation evidence missing");
        RseValidationSelfTestService.Evaluation indicator = indicator(level, indicatorPos, 0);
        return indicator.verdict() == RseValidationSelfTestService.Verdict.PASS
                ? pass("raise → latch after healthy → ACK → RESET clear lifecycle complete")
                : indicator;
    }

    private static RseValidationSelfTestService.Evaluation indicator(ServerLevel level, BlockPos pos, int expected) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof AnalogIndicatorBlock indicator)) return fail("analog indicator missing");
        AnalogIndicatorBlock.InputObservation observation = indicator.inputObservation(level, pos, state);
        if (observation.quality() == PortQuality.STALE) return waitFor("indicator evidence STALE");
        if (observation.quality() != PortQuality.VALID) return fail("indicator quality=" + observation.quality());
        if (observation.value() != expected) return fail("indicator=" + observation.value() + " expected=" + expected);
        return pass("indicator=" + expected + "/VALID");
    }

    private static boolean setReferencePower(ServerLevel level, BlockPos pos, int power) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof RedstoneReferenceSourceBlock source)
                || !state.hasProperty(RedstoneReferenceSourceBlock.POWER)) return false;
        int bounded = Math.max(0, Math.min(15, power));
        if (state.getValue(RedstoneReferenceSourceBlock.POWER) == bounded) return false;
        BlockState next = state.setValue(RedstoneReferenceSourceBlock.POWER, bounded);
        level.setBlock(pos, next, 3);
        level.updateNeighborsAt(pos, source);
        level.updateNeighborsAt(pos.relative(next.getValue(RedstoneReferenceSourceBlock.FACING)), source);
        return true;
    }

    private static RseValidationSelfTestService.Evaluation waitFor(String detail) {
        return new RseValidationSelfTestService.Evaluation(RseValidationSelfTestService.Verdict.WAIT, detail);
    }

    private static RseValidationSelfTestService.Evaluation pass(String detail) {
        return new RseValidationSelfTestService.Evaluation(RseValidationSelfTestService.Verdict.PASS, detail);
    }

    private static RseValidationSelfTestService.Evaluation fail(String detail) {
        return new RseValidationSelfTestService.Evaluation(RseValidationSelfTestService.Verdict.FAIL, detail);
    }
}
