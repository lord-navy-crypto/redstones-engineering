package dev.redstoneengineering.validation;

import dev.redstoneengineering.block.AlarmProcessorBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.FaultInjectorBlock;
import dev.redstoneengineering.block.QuartzLabOscillatorBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.SafetyInterlockBlock;
import dev.redstoneengineering.block.ServoActuatorBlock;
import dev.redstoneengineering.block.ServoPositionSensorBlock;
import dev.redstoneengineering.block.SignalAnalyzerBlock;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Phase-aware station evaluator for Mega Validation Factory v2.1.
 *
 * <p>Device health is intentionally separate from scenario verdict. Deliberate scenario faults
 * live in physical validation fixtures; a healthy DUT must not be reclassified as broken merely
 * because the surrounding test channel is being fault-injected.</p>
 */
public final class RseMegaStationEvaluator {
    public static final long PROPAGATION_TIMEOUT_TICKS = 600L;
    private static final BlockPos H_SENSOR_FAULT_INJECTOR = new BlockPos(7, 1, 10);

    public enum Health {
        HEALTHY,
        DEGRADED,
        FAILED,
        PENDING
    }

    public record StationEvaluation(
            int station,
            String cell,
            Health health,
            RseValidationSelfTestService.Verdict scenarioVerdict,
            String reasonCode,
            PortQuality quality,
            String detail,
            BlockPos worldPos
    ) {
        public RseValidationSelfTestService.Evaluation asLegacyEvaluation() {
            return new RseValidationSelfTestService.Evaluation(scenarioVerdict, detail);
        }
    }

    private record QualityDecision(
            Health health,
            RseValidationSelfTestService.Verdict verdict,
            String reason
    ) {}

    private RseMegaStationEvaluator() {}

    public static StationEvaluation evaluate(
            ServerLevel level,
            BlockPos plantOrigin,
            RseMegaValidationTopology.Station station,
            RseMegaValidationService.Phase phase,
            long phaseAge
    ) {
        BlockPos pos = RseMegaValidationTopology.stationWorldPos(plantOrigin, station);
        if (!level.hasChunkAt(pos)) {
            return pending(station, pos, "CHUNK_UNLOADED", PortQuality.STALE,
                    "chunk not loaded at " + pos.toShortString());
        }

        BlockState state = level.getBlockState(pos);
        String actual = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if (!station.blockId().equals(actual)) {
            return failed(station, pos, "IDENTITY_MISMATCH", PortQuality.TOPOLOGY_ERROR,
                    "identity=" + actual + " expected=" + station.blockId());
        }

        return switch (station.number()) {
            case 1 -> evaluateReference(level, station, pos, state);
            case 5 -> evaluateAnalyzer(level, station, pos, phaseAge);
            case 6 -> evaluateQuartzOscillator(level, station, pos, state, phaseAge);
            case 10 -> evaluateSelectedInputs(level, station, pos, state, phaseAge, "COMMAND IN");
            case 36 -> evaluateSelectedInputs(level, station, pos, state, phaseAge,
                    "SETPOINT IN", "PROCESS VALUE IN");
            case 37 -> evaluateServo(level, station, pos, phase);
            case 38 -> evaluatePositionSensor(level, plantOrigin, station, pos, state, phase, phaseAge);
            case 39 -> evaluateInterlock(level, station, pos, state, phase, phaseAge);
            case 40 -> evaluateAlarm(level, station, pos, state, phase);
            default -> evaluateGenericPorts(level, station, pos, state, phase, phaseAge);
        };
    }

    private static StationEvaluation evaluateReference(
            ServerLevel level,
            RseMegaValidationTopology.Station station,
            BlockPos pos,
            BlockState state
    ) {
        if (!(state.getBlock() instanceof RedstoneReferenceSourceBlock)) {
            return failed(station, pos, "SOURCE_CLASS_MISMATCH", PortQuality.TOPOLOGY_ERROR,
                    "reference source class mismatch");
        }
        int power = state.getValue(RedstoneReferenceSourceBlock.POWER);
        if (power <= 0) {
            return failed(station, pos, "SOURCE_ZERO", PortQuality.NO_SIGNAL,
                    "reference source power=" + power);
        }
        return healthy(station, pos, "SOURCE_VALID", PortQuality.VALID,
                "identity + source power=" + power);
    }

    private static StationEvaluation evaluateAnalyzer(
            ServerLevel level,
            RseMegaValidationTopology.Station station,
            BlockPos pos,
            long phaseAge
    ) {
        SignalAnalyzerBlock.UiSnapshot snapshot = SignalAnalyzerBlock.uiSnapshot(level, pos);
        if (snapshot.totalSamples() <= 0) {
            return phaseAge < PROPAGATION_TIMEOUT_TICKS
                    ? pending(station, pos, "ANALYZER_PENDING", PortQuality.NO_SIGNAL,
                    "analyzer awaiting first sample")
                    : failed(station, pos, "ANALYZER_NO_SAMPLES", PortQuality.NO_SIGNAL,
                    "analyzer produced no samples");
        }
        return healthy(station, pos, "ANALYZER_VALID", PortQuality.VALID,
                "analyzer samples=" + snapshot.totalSamples()
                        + " raw=" + snapshot.raw() + " out=" + snapshot.output());
    }

    private static StationEvaluation evaluateQuartzOscillator(
            ServerLevel level,
            RseMegaValidationTopology.Station station,
            BlockPos pos,
            BlockState state,
            long phaseAge
    ) {
        if (!(state.getBlock() instanceof QuartzLabOscillatorBlock)) {
            return failed(station, pos, "QUARTZ_CLASS_MISMATCH", PortQuality.TOPOLOGY_ERROR,
                    "quartz oscillator class mismatch");
        }
        QuartzLabOscillatorBlock.TimingEvidence evidence = QuartzLabOscillatorBlock.timingEvidence(level, pos, state);
        if (!evidence.available()) {
            return phaseAge < PROPAGATION_TIMEOUT_TICKS
                    ? pending(station, pos, "QUARTZ_TIMING_PENDING", PortQuality.STALE,
                    "quartz timing evidence pending")
                    : failed(station, pos, "QUARTZ_TIMING_TIMEOUT", PortQuality.STALE,
                    "quartz timing evidence unavailable");
        }
        return healthy(station, pos, "QUARTZ_TIMING_VALID", PortQuality.VALID,
                "timing nominal=" + evidence.nominalPeriod() + "t lastHalf="
                        + evidence.lastHalfInterval() + "t jitter=" + evidence.lastJitterOffset() + "t");
    }

    /**
     * Evaluate only the physically required process inputs. Optional operator/safety ports such as
     * PWM inhibit and PID manual/mode inputs may legitimately be left at their inactive state.
     */
    private static StationEvaluation evaluateSelectedInputs(
            ServerLevel level,
            RseMegaValidationTopology.Station station,
            BlockPos pos,
            BlockState state,
            long phaseAge,
            String... requiredLabels
    ) {
        if (!(state.getBlock() instanceof EngineeringPortProvider provider)) {
            return failed(station, pos, "PORT_PROVIDER_MISSING", PortQuality.TOPOLOGY_ERROR,
                    "required-input evaluator has no engineering port provider");
        }
        ArrayList<String> evidence = new ArrayList<>();
        for (String label : requiredLabels) {
            EngineeringPort port = provider.engineeringPorts(state).stream()
                    .filter(candidate -> candidate.label().equals(label))
                    .findFirst().orElse(null);
            if (port == null) {
                return failed(station, pos, "REQUIRED_PORT_MISSING", PortQuality.TOPOLOGY_ERROR,
                        "required port missing: " + label);
            }
            Optional<EngineeringPortSnapshot> optional = provider.engineeringSnapshot(level, pos, state, port.side());
            if (optional.isEmpty()) {
                return phaseAge < PROPAGATION_TIMEOUT_TICKS
                        ? pending(station, pos, "REQUIRED_INPUT_PENDING", PortQuality.STALE,
                        "required input has no snapshot: " + label)
                        : failed(station, pos, "REQUIRED_INPUT_NO_SNAPSHOT", PortQuality.NO_SIGNAL,
                        "required input has no snapshot: " + label);
            }
            EngineeringPortSnapshot snapshot = optional.get();
            QualityDecision decision = decideQuality(snapshot.quality(), false, phaseAge);
            evidence.add(label + "=" + Math.round(snapshot.value()) + "/" + snapshot.quality());
            if (decision.verdict() == RseValidationSelfTestService.Verdict.FAIL) {
                return failed(station, pos, decision.reason(), snapshot.quality(),
                        "required input " + String.join(";", evidence));
            }
            if (decision.verdict() == RseValidationSelfTestService.Verdict.WAIT) {
                return pending(station, pos, decision.reason(), snapshot.quality(),
                        "required input " + String.join(";", evidence));
            }
        }
        return healthy(station, pos, "REQUIRED_INPUTS_VALID", PortQuality.VALID,
                "required inputs: " + String.join(";", evidence));
    }

    private static StationEvaluation evaluateServo(
            ServerLevel level,
            RseMegaValidationTopology.Station station,
            BlockPos pos,
            RseMegaValidationService.Phase phase
    ) {
        int command = ServoActuatorBlock.command(level, pos);
        int position = ServoActuatorBlock.position(level, pos);
        boolean braking = ServoActuatorBlock.braking(level, pos);
        if (command < 0 || command > 15 || position < 0 || position > 15) {
            return failed(station, pos, "SERVO_RUNTIME_BOUNDS", PortQuality.FAULT,
                    "servo command=" + command + " position=" + position + " brake=" + braking);
        }

        boolean brakeExpected = phase == RseMegaValidationService.Phase.ACTUATOR_FAULT
                || phase == RseMegaValidationService.Phase.INTERLOCK_TRIP
                || phase == RseMegaValidationService.Phase.SAFE_STATE;
        if (braking && !brakeExpected) {
            return failed(station, pos, "UNINTENDED_BRAKE", PortQuality.FAULT,
                    "servo command=" + command + " position=" + position + " brake=true");
        }
        if (!braking && brakeExpected) {
            return pending(station, pos, "EXPECTED_BRAKE_PENDING", PortQuality.STALE,
                    "servo command=" + command + " position=" + position + " brake=false");
        }
        if (braking) {
            return degradedPass(station, pos, "EXPECTED_BRAKE", PortQuality.FAULT,
                    "servo command=" + command + " position=" + position + " brake=true");
        }
        return healthy(station, pos, "SERVO_HEALTHY", PortQuality.VALID,
                "servo command=" + command + " position=" + position + " brake=false");
    }

    private static StationEvaluation evaluatePositionSensor(
            ServerLevel level,
            BlockPos plantOrigin,
            RseMegaValidationTopology.Station station,
            BlockPos pos,
            BlockState state,
            RseMegaValidationService.Phase phase,
            long phaseAge
    ) {
        if (!(state.getBlock() instanceof ServoPositionSensorBlock)) {
            return failed(station, pos, "POSITION_SENSOR_CLASS_MISMATCH", PortQuality.TOPOLOGY_ERROR,
                    "position sensor class mismatch");
        }
        PortQuality quality = ServoPositionSensorBlock.sourceQuality(level, pos, state);
        QualityDecision sensorDecision = decideQuality(quality, false, phaseAge);
        if (sensorDecision.verdict() != RseValidationSelfTestService.Verdict.PASS) {
            String reason = quality == PortQuality.NO_SIGNAL ? "POSITION_FEEDBACK_NO_SIGNAL" : sensorDecision.reason();
            return evaluation(station, pos, sensorDecision.health(), sensorDecision.verdict(), reason, quality,
                    "position-sensor sourceQuality=" + quality);
        }

        if (phase == RseMegaValidationService.Phase.SENSOR_FAULT) {
            RseMegaValidationTopology.Module h = RseMegaValidationTopology.cellModules().get("H");
            if (h == null) {
                return failed(station, pos, "H_CELL_TOPOLOGY_MISSING", PortQuality.TOPOLOGY_ERROR,
                        "H-cell module missing while checking feedback fault injector");
            }
            BlockPos injectorPos = plantOrigin.offset(h.offset()).offset(H_SENSOR_FAULT_INJECTOR);
            BlockState injectorState = level.getBlockState(injectorPos);
            if (!(injectorState.getBlock() instanceof FaultInjectorBlock)) {
                return failed(station, pos, "FEEDBACK_FAULT_INJECTOR_MISSING", PortQuality.TOPOLOGY_ERROR,
                        "expected fault injector at " + injectorPos.toShortString());
            }
            if (!FaultInjectorBlock.active(level, injectorPos)) {
                return phaseAge < PROPAGATION_TIMEOUT_TICKS
                        ? pending(station, pos, "FEEDBACK_FAULT_ARM_PENDING", PortQuality.STALE,
                        "position sensor healthy but feedback fault injector is not armed yet")
                        : failed(station, pos, "FEEDBACK_FAULT_NOT_ARMED", PortQuality.FAULT,
                        "position sensor healthy but feedback fault injector never armed");
            }
            int output = FaultInjectorBlock.lastOutput(level, injectorPos);
            if (output != 0) {
                return phaseAge < PROPAGATION_TIMEOUT_TICKS
                        ? pending(station, pos, "FEEDBACK_STUCK_LOW_PENDING", PortQuality.STALE,
                        "fault injector armed but output=" + output)
                        : failed(station, pos, "FEEDBACK_STUCK_LOW_FAILED", PortQuality.FAULT,
                        "fault injector armed but output=" + output);
            }
            return degradedPass(station, pos, "EXPECTED_FEEDBACK_CHANNEL_FAULT", PortQuality.FAULT,
                    "position-sensor sourceQuality=VALID | feedback injector active=true output=0 at "
                            + injectorPos.toShortString());
        }

        return healthy(station, pos, "POSITION_FEEDBACK_VALID", PortQuality.VALID,
                "position-sensor sourceQuality=VALID");
    }

    private static StationEvaluation evaluateInterlock(
            ServerLevel level,
            RseMegaValidationTopology.Station station,
            BlockPos pos,
            BlockState state,
            RseMegaValidationService.Phase phase,
            long phaseAge
    ) {
        int mask = SafetyInterlockBlock.failedMask(level, pos);
        if (mask < 0) {
            return phaseAge < PROPAGATION_TIMEOUT_TICKS
                    ? pending(station, pos, "INTERLOCK_PENDING", PortQuality.STALE,
                    "interlock runtime pending")
                    : failed(station, pos, "INTERLOCK_RUNTIME_UNAVAILABLE", PortQuality.FAULT,
                    "interlock runtime unavailable");
        }
        boolean tripExpected = phase == RseMegaValidationService.Phase.INTERLOCK_TRIP
                || phase == RseMegaValidationService.Phase.SAFE_STATE;
        int output = outputOrMinusOne(state);
        if (!tripExpected && mask != 0) {
            return failed(station, pos, "INTERLOCK_FAILED_MASK", PortQuality.FAULT,
                    "interlock failedMask=" + mask + " output=" + output);
        }
        if (tripExpected && mask == 0) {
            return pending(station, pos, "INTERLOCK_TRIP_PENDING", PortQuality.STALE,
                    "interlock failedMask=0 output=" + output);
        }
        if (tripExpected) {
            return degradedPass(station, pos, "EXPECTED_INTERLOCK_TRIP", PortQuality.FAULT,
                    "interlock failedMask=" + mask + " output=" + output);
        }
        return healthy(station, pos, "INTERLOCK_HEALTHY", PortQuality.VALID,
                "interlock failedMask=0 output=" + output);
    }

    private static StationEvaluation evaluateAlarm(
            ServerLevel level,
            RseMegaValidationTopology.Station station,
            BlockPos pos,
            BlockState state,
            RseMegaValidationService.Phase phase
    ) {
        boolean latched = AlarmProcessorBlock.latched(level, pos);
        int output = outputOrMinusOne(state);
        boolean latchExpected = phase == RseMegaValidationService.Phase.INTERLOCK_TRIP
                || phase == RseMegaValidationService.Phase.SAFE_STATE;
        if (!latchExpected && latched) {
            return failed(station, pos, "UNEXPECTED_ALARM_LATCH", PortQuality.FAULT,
                    "alarm latched=true output=" + output);
        }
        if (latchExpected && !latched) {
            return pending(station, pos, "ALARM_LATCH_PENDING", PortQuality.STALE,
                    "alarm latched=false output=" + output);
        }
        if (latched) {
            return degradedPass(station, pos, "EXPECTED_ALARM_LATCH", PortQuality.FAULT,
                    "alarm latched=true output=" + output);
        }
        return healthy(station, pos, "ALARM_CLEAR", PortQuality.VALID,
                "alarm latched=false output=" + output);
    }

    private static StationEvaluation evaluateGenericPorts(
            ServerLevel level,
            RseMegaValidationTopology.Station station,
            BlockPos pos,
            BlockState state,
            RseMegaValidationService.Phase phase,
            long phaseAge
    ) {
        boolean saturationExpected = phase == RseMegaValidationService.Phase.SATURATION_TEST
                && station.number() == 3;

        if (state.getBlock() instanceof EngineeringPortProvider provider) {
            List<EngineeringPort> ports = provider.engineeringPorts(state);
            int inputPorts = 0;
            int inputSnapshots = 0;
            int healthyInputs = 0;
            int pendingInputs = 0;
            int outputSnapshots = 0;
            ArrayList<String> evidence = new ArrayList<>();
            PortQuality representative = PortQuality.VALID;

            for (EngineeringPort port : ports) {
                Optional<EngineeringPortSnapshot> optional = provider.engineeringSnapshot(level, pos, state, port.side());
                if (port.canReceive()) inputPorts++;
                if (optional.isEmpty()) {
                    if (port.canReceive()) pendingInputs++;
                    evidence.add(port.label() + "=NO_SNAPSHOT");
                    continue;
                }

                EngineeringPortSnapshot snapshot = optional.get();
                PortQuality quality = snapshot.quality();
                representative = quality;
                evidence.add(port.label() + "=" + Math.round(snapshot.value()) + "/" + quality);
                if (port.canTransmit()) outputSnapshots++;
                if (!port.canReceive()) continue;

                inputSnapshots++;
                QualityDecision decision = decideQuality(quality, saturationExpected, phaseAge);
                if (decision.verdict() == RseValidationSelfTestService.Verdict.FAIL) {
                    return failed(station, pos, decision.reason(), quality,
                            "INPUT " + port.label() + "=" + Math.round(snapshot.value()) + "/" + quality);
                }
                if (decision.verdict() == RseValidationSelfTestService.Verdict.WAIT) pendingInputs++;
                if (decision.verdict() == RseValidationSelfTestService.Verdict.PASS) healthyInputs++;
            }

            if (inputPorts > 0) {
                if (healthyInputs == 0) {
                    if (phaseAge < PROPAGATION_TIMEOUT_TICKS) {
                        return pending(station, pos, "INPUT_NO_HEALTHY_SIGNAL", representative,
                                "ports=" + ports.size() + " inputSnapshots=" + inputSnapshots
                                        + " evidence=" + String.join(";", evidence));
                    }
                    return failed(station, pos, "INPUT_NO_HEALTHY_SIGNAL", PortQuality.NO_SIGNAL,
                            "ports=" + ports.size() + " inputSnapshots=" + inputSnapshots
                                    + " evidence=" + String.join(";", evidence));
                }
                if (pendingInputs > 0) {
                    return pending(station, pos, "INPUT_PARTIALLY_PENDING", representative,
                            "healthyInputs=" + healthyInputs + "/" + inputPorts
                                    + " evidence=" + String.join(";", evidence));
                }
                if (saturationExpected && representative == PortQuality.SATURATED) {
                    return degradedPass(station, pos, "EXPECTED_SATURATION", representative,
                            "healthyInputs=" + healthyInputs + "/" + inputPorts
                                    + " evidence=" + String.join(";", evidence));
                }
                return healthy(station, pos, "INPUTS_VALID", PortQuality.VALID,
                        "inputs=" + healthyInputs + "/" + inputPorts
                                + " outputs=" + outputSnapshots + " evidence=" + String.join(";", evidence));
            }

            if (!ports.isEmpty()) {
                for (String item : evidence) {
                    if (item.endsWith("/FAULT") || item.endsWith("/DOMAIN_MISMATCH")
                            || item.endsWith("/TOPOLOGY_ERROR")) {
                        return failed(station, pos, "SOURCE_PORT_FAULT", representative,
                                "ports=" + ports.size() + " evidence=" + String.join(";", evidence));
                    }
                }
                return healthy(station, pos, "SOURCE_PORTS_AVAILABLE", representative,
                        "ports=" + ports.size() + " outputs=" + outputSnapshots
                                + " evidence=" + String.join(";", evidence));
            }
        }

        int output = outputOrMinusOne(state);
        if (output >= 0) {
            return healthy(station, pos, "DIRECTIONAL_OUTPUT_AVAILABLE", PortQuality.VALID,
                    "identity + directional output=" + output);
        }

        return healthy(station, pos, "IDENTITY_ONLY_SOURCE", PortQuality.VALID,
                "identity verified; no runtime port contract exposed");
    }

    private static QualityDecision decideQuality(PortQuality quality, boolean expectedDegradation, long age) {
        return switch (quality) {
            case VALID -> new QualityDecision(Health.HEALTHY, RseValidationSelfTestService.Verdict.PASS, "VALID");
            case STALE -> age < PROPAGATION_TIMEOUT_TICKS
                    ? new QualityDecision(Health.PENDING, RseValidationSelfTestService.Verdict.WAIT, "STALE_PROPAGATING")
                    : new QualityDecision(Health.FAILED, RseValidationSelfTestService.Verdict.FAIL, "STALE_TIMEOUT");
            case NO_SIGNAL -> expectedDegradation
                    ? new QualityDecision(Health.DEGRADED, RseValidationSelfTestService.Verdict.PASS, "EXPECTED_NO_SIGNAL")
                    : age < PROPAGATION_TIMEOUT_TICKS
                        ? new QualityDecision(Health.PENDING, RseValidationSelfTestService.Verdict.WAIT, "NO_SIGNAL_PROPAGATING")
                        : new QualityDecision(Health.FAILED, RseValidationSelfTestService.Verdict.FAIL, "NO_SIGNAL_TIMEOUT");
            case SATURATED -> expectedDegradation
                    ? new QualityDecision(Health.DEGRADED, RseValidationSelfTestService.Verdict.PASS, "EXPECTED_SATURATION")
                    : new QualityDecision(Health.FAILED, RseValidationSelfTestService.Verdict.FAIL, "UNEXPECTED_SATURATION");
            case FAULT -> new QualityDecision(Health.FAILED, RseValidationSelfTestService.Verdict.FAIL, "FAULT");
            case DOMAIN_MISMATCH -> new QualityDecision(Health.FAILED, RseValidationSelfTestService.Verdict.FAIL, "DOMAIN_MISMATCH");
            case TOPOLOGY_ERROR -> new QualityDecision(Health.FAILED, RseValidationSelfTestService.Verdict.FAIL, "TOPOLOGY_ERROR");
        };
    }

    private static int outputOrMinusOne(BlockState state) {
        return state.hasProperty(DirectionalSignalBlock.OUTPUT) ? state.getValue(DirectionalSignalBlock.OUTPUT) : -1;
    }

    private static StationEvaluation healthy(
            RseMegaValidationTopology.Station station,
            BlockPos pos,
            String reason,
            PortQuality quality,
            String detail
    ) {
        return evaluation(station, pos, Health.HEALTHY, RseValidationSelfTestService.Verdict.PASS,
                reason, quality, detail);
    }

    private static StationEvaluation degradedPass(
            RseMegaValidationTopology.Station station,
            BlockPos pos,
            String reason,
            PortQuality quality,
            String detail
    ) {
        return evaluation(station, pos, Health.DEGRADED, RseValidationSelfTestService.Verdict.PASS,
                reason, quality, detail);
    }

    private static StationEvaluation pending(
            RseMegaValidationTopology.Station station,
            BlockPos pos,
            String reason,
            PortQuality quality,
            String detail
    ) {
        return evaluation(station, pos, Health.PENDING, RseValidationSelfTestService.Verdict.WAIT,
                reason, quality, detail);
    }

    private static StationEvaluation failed(
            RseMegaValidationTopology.Station station,
            BlockPos pos,
            String reason,
            PortQuality quality,
            String detail
    ) {
        return evaluation(station, pos, Health.FAILED, RseValidationSelfTestService.Verdict.FAIL,
                reason, quality, detail);
    }

    private static StationEvaluation evaluation(
            RseMegaValidationTopology.Station station,
            BlockPos pos,
            Health health,
            RseValidationSelfTestService.Verdict verdict,
            String reason,
            PortQuality quality,
            String detail
    ) {
        return new StationEvaluation(
                station.number(), station.cell(), health, verdict,
                reason == null ? "UNKNOWN" : reason.toUpperCase(Locale.ROOT),
                quality, detail, pos
        );
    }
}
