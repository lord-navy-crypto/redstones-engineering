package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.ui.menu.*;
import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.Direction;

import java.util.List;

/**
 * Client-only presentation catalog for the equations and processing ideas already represented by
 * server-authoritative RSE devices. It never evaluates physics or acceptance logic.
 */
public final class EngineeringWorkbenchCatalog {
    public enum UiTier { BLOCK, DEVICE, LAB }

    /**
     * Minecraft-first UI policy. BLOCK keeps the GUI light, DEVICE adds bounded configuration,
     * and LAB enables controlled experiments only when the underlying device semantics justify it.
     */
    public record UiPolicy(UiTier tier, String pageLabel, String rationale) {
        public boolean experimental() { return tier == UiTier.LAB; }
        public boolean configurable() { return tier != UiTier.BLOCK; }
    }

    public record ModelCard(String family, String equation, String parameters, String process, String boundary) {}

    /**
     * One live server-synchronized response quantity for parameter experiments.
     * This is observation only: it never predicts or drives the device.
     */
    public record ResponseSpec(
            String label,
            int value,
            int minimum,
            int maximum,
            String unit,
            boolean usable,
            String detail
    ) {
        public ResponseSpec {
            unit = unit == null ? "" : unit;
            detail = detail == null ? "" : detail;
        }
    }
    /**
     * One bounded server-owned parameter exposed to the shared Model workbench.
     * decrement/increment are existing menu-button actions; the client never writes world state.
     */
    public record ParameterSpec(
            String label,
            int current,
            int minimum,
            int maximum,
            int decrementButton,
            int incrementButton,
            String unit,
            String detail,
            boolean fractionPresets,
            boolean sweepMeaningful
    ) {
        public ParameterSpec(
                String label,
                int current,
                int minimum,
                int maximum,
                int decrementButton,
                int incrementButton,
                String unit,
                String detail
        ) {
            this(label, current, minimum, maximum, decrementButton, incrementButton,
                    unit, detail, false, false);
        }

        public ParameterSpec {
            unit = unit == null ? "" : unit;
            detail = detail == null ? "" : detail;
        }
    }

    private EngineeringWorkbenchCatalog() {}

    public static UiPolicy uiPolicy(EngineeringDeviceMenu menu) {
        if (menu instanceof PidControllerMenu
                || menu instanceof SignalConditionerMenu
                || menu instanceof SignalProcessorMenu
                || menu instanceof SignalAnalyzerMenu
                || menu instanceof OscilloscopeMenu
                || menu instanceof LogicAnalyzerMenu) {
            return lab("LAB", "Dynamic signal/control behavior benefits from measured experiments.");
        }
        if (menu instanceof QuartzTimingMenu) {
            return lab("LAB", "Timing devices expose real edge/period behavior that benefits from measured experiments.");
        }
        if (menu instanceof AmethystSystemMenu amethyst) {
            return switch (amethyst.kind()) {
                case AmethystSystemMenu.KIND_SOURCE,
                     AmethystSystemMenu.KIND_FILTER,
                     AmethystSystemMenu.KIND_TUNED,
                     AmethystSystemMenu.KIND_SPECTRUM ->
                        lab("LAB", "Resonance/frequency behavior is meaningful as a measured experiment.");
                default -> device("MODEL", "Active resonance device with bounded configuration/readback.");
            };
        }
        if (menu instanceof PneumaticSystemMenu pneumatic) {
            return switch (pneumatic.kind()) {
                case PneumaticSystemMenu.KIND_COMPRESSOR,
                     PneumaticSystemMenu.KIND_REGULATOR,
                     PneumaticSystemMenu.KIND_PROPORTIONAL,
                     PneumaticSystemMenu.KIND_CYLINDER,
                     PneumaticSystemMenu.KIND_FLOW_METER ->
                        lab("LAB", "Finite pressure/actuator dynamics justify response-oriented tooling.");
                default -> device("MODEL", "Pneumatic component configuration belongs to the block, not a desktop simulator.");
            };
        }
        if (menu instanceof ReliabilitySystemMenu reliability) {
            return reliability.kind() == ReliabilitySystemMenu.KIND_SERVO
                    ? lab("LAB", "Servo motion has real finite response and feedback behavior.")
                    : device("MODEL", "Protection/voting devices need clear settings and state, not a full simulator.");
        }
        if (menu instanceof MagneticSystemMenu magnetic) {
            return switch (magnetic.kind()) {
                case MagneticSystemMenu.KIND_ELECTROMAGNET,
                     MagneticSystemMenu.KIND_COIL ->
                        lab("LAB", "Field/induction dynamics can produce meaningful measured response studies.");
                default -> device("MODEL", "Magnetic source/sensor needs bounded configuration and evidence.");
            };
        }
        if (menu instanceof RangeSensorMenu
                || menu instanceof RadioLinkMenu
                || menu instanceof DigitalCommunicationMenu
                || menu instanceof OpticalSystemMenu
                || menu instanceof MediaConversionMenu
                || menu instanceof CopperCircuitMeterMenu) {
            return device("MODEL", "Keep configuration and evidence close to the in-world block.");
        }
        if (menu instanceof UniversalFieldDeviceMenu universal) {
            return universalPolicy(universal.configKind());
        }
        if (menu instanceof FieldDeviceMenu field) {
            return fieldPolicy(field.kind());
        }
        return device("MODEL", "Use the smallest engineering UI that still explains the block.");
    }

    private static UiPolicy universalPolicy(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.CONFIG_NONE,
                 UniversalFieldDeviceMenu.CONFIG_REDSTONE_CABLE,
                 UniversalFieldDeviceMenu.CONFIG_JUNCTION,
                 UniversalFieldDeviceMenu.CONFIG_SERIAL_LINE,
                 UniversalFieldDeviceMenu.CONFIG_DIFF_PAIR,
                 UniversalFieldDeviceMenu.CONFIG_DATA_BUS,
                 UniversalFieldDeviceMenu.CONFIG_INSTRUMENT_BUS,
                 UniversalFieldDeviceMenu.CONFIG_QUARTZ_TRACE,
                 UniversalFieldDeviceMenu.CONFIG_SIGNAL_TAP ->
                    block("INFO", "Passive transport/topology block: world wiring and port evidence are the main interface.");
            case UniversalFieldDeviceMenu.CONFIG_SAMPLE_HOLD,
                 UniversalFieldDeviceMenu.CONFIG_PWM,
                 UniversalFieldDeviceMenu.CONFIG_QUARTZ_OSCILLATOR,
                 UniversalFieldDeviceMenu.CONFIG_QUARTZ_LAPIS_SAMPLER ->
                    lab("LAB", "Discrete-time behavior is meaningful to observe experimentally.");
            default -> device("MODEL", "Configurable field device: exact settings and evidence, without desktop-style workflow overhead.");
        };
    }

    private static UiPolicy fieldPolicy(int kind) {
        return switch (kind) {
            case FieldDeviceMenu.KIND_UNKNOWN,
                 FieldDeviceMenu.KIND_TERMINAL,
                 FieldDeviceMenu.KIND_REDSTONE_CABLE,
                 FieldDeviceMenu.KIND_REDSTONE_JUNCTION,
                 FieldDeviceMenu.KIND_INSTRUMENT_CABLE,
                 FieldDeviceMenu.KIND_DATA_BUS_8,
                 FieldDeviceMenu.KIND_SERIAL_LINE,
                 FieldDeviceMenu.KIND_DIFFERENTIAL_PAIR,
                 FieldDeviceMenu.KIND_AMETHYST_DUST,
                 FieldDeviceMenu.KIND_SLIME_VIBRATION,
                 FieldDeviceMenu.KIND_HYDRO_TUBE,
                 FieldDeviceMenu.KIND_PHONON_CONDUIT,
                 FieldDeviceMenu.KIND_SHIELDED_INSTRUMENT_CABLE,
                 FieldDeviceMenu.KIND_PNEUMATIC_PIPE,
                 FieldDeviceMenu.KIND_LAPIS_LINE,
                 FieldDeviceMenu.KIND_QUARTZ_LINE,
                 FieldDeviceMenu.KIND_OPTICAL_FIBER,
                 FieldDeviceMenu.KIND_OPTICAL_FIBER_JUNCTION ->
                    block("INFO", "Passive medium/topology block: keep interaction lightweight and world-centric.");

            case FieldDeviceMenu.KIND_FILTER,
                 FieldDeviceMenu.KIND_EDGE_DETECTOR,
                 FieldDeviceMenu.KIND_PULSE_SHAPER,
                 FieldDeviceMenu.KIND_QUARTZ_OSCILLATOR,
                 FieldDeviceMenu.KIND_QUARTZ_DIVIDER,
                 FieldDeviceMenu.KIND_QUARTZ_STABILITY,
                 FieldDeviceMenu.KIND_AMETHYST_RESONATOR,
                 FieldDeviceMenu.KIND_AMETHYST_FILTER,
                 FieldDeviceMenu.KIND_AMETHYST_TUNED,
                 FieldDeviceMenu.KIND_AMETHYST_SPECTRUM,
                 FieldDeviceMenu.KIND_AIR_COMPRESSOR,
                 FieldDeviceMenu.KIND_PRESSURE_REGULATOR,
                 FieldDeviceMenu.KIND_PNEUMATIC_PROPORTIONAL_VALVE,
                 FieldDeviceMenu.KIND_PNEUMATIC_CYLINDER,
                 FieldDeviceMenu.KIND_INDUCTION_COIL,
                 FieldDeviceMenu.KIND_SERVO_ACTUATOR ->
                    lab("LAB", "This block has time/frequency/dynamic behavior worth measuring, not merely configuring.");

            default -> device("MODEL", "Active or configurable device: expose settings, result and evidence without overbuilding the GUI.");
        };
    }

    private static UiPolicy block(String label, String rationale) {
        return new UiPolicy(UiTier.BLOCK, label, rationale);
    }

    private static UiPolicy device(String label, String rationale) {
        return new UiPolicy(UiTier.DEVICE, label, rationale);
    }

    private static UiPolicy lab(String label, String rationale) {
        return new UiPolicy(UiTier.LAB, label, rationale);
    }

    public static ModelCard describe(EngineeringDeviceMenu menu) {
        if (menu instanceof UniversalFieldDeviceMenu universal) return universal(universal);
        if (menu instanceof PidControllerMenu) return card(
                "FEEDBACK CONTROL",
                "e = SP - PV;  u = P(e) + I(sum e) + D(delta e)",
                "setpoint • process value • tuning preset • mode",
                "compare target to measurement -> compute control effort -> observe response",
                "Displayed equation explains the implemented control structure; the server remains authoritative.");
        if (menu instanceof SignalConditionerMenu) return card(
                "SIGNAL CONDITIONING",
                "y = bounded transform(x; gain, offset, clamp, threshold, deadband)",
                "mode • bounded parameter • input/output route",
                "measure -> transform -> clamp/threshold -> publish output",
                "The UI visualizes synchronized state and does not recalculate the signal path.");
        if (menu instanceof SignalProcessorMenu) return card(
                "DISCRETE SIGNAL PROCESSING",
                "y[k] = F(x[k], x[k-1], internal state)",
                "edge mode / pulse width / hysteresis / rise-fall rates",
                "sample -> detect/update state -> emit bounded output",
                "History shown by the UI is observation of synchronized state, not a second simulation.");
        if (menu instanceof QuartzTimingMenu quartz) {
            String eq = quartz.kind() == QuartzTimingMenu.KIND_DIVIDER
                    ? "T_out = N * T_in"
                    : quartz.kind() == QuartzTimingMenu.KIND_STABILITY
                    ? "T_measured = t(edge n) - t(edge n-1)"
                    : "q toggles every T/2; rising edges repeat every T";
            return card("TIMING / CLOCK", eq,
                    "period • division ratio • measured timing evidence",
                    "generate/observe edges -> retain baseline -> measure or divide genuine transitions",
                    "Opening the UI never creates a timing edge.");
        }
        if (menu instanceof RangeSensorMenu) return card(
                "SENSOR TRANSFER",
                "distance evidence -> configured response profile -> bounded output",
                "detection mode • range mode • response mode",
                "scan aperture -> classify evidence -> map distance into output",
                "Range and response are server-side sensor settings, not client-side estimates.");
        if (menu instanceof PneumaticSystemMenu) return card(
                "PNEUMATIC DYNAMICS",
                "deltaP = P_in - P_out; state[k+1] = F(state[k], command, losses)",
                "pressure setpoint • response profile • valve state • physical route",
                "solve pressure network -> apply restriction/loss -> update actuator/metrology state",
                "This is an RSE-scale engineering abstraction, not a dimensional CFD solver.");
        if (menu instanceof MagneticSystemMenu) return card(
                "MAGNETIC / INDUCTION",
                "induction uses change in flux: |emf| proportional to N |deltaPhi/delta t|",
                "field command • geometry/profile • measurement aperture",
                "field evidence -> magnetic state -> sensor/induction response",
                "Constant valid field can produce a valid zero derivative; missing evidence is different.");
        if (menu instanceof AmethystSystemMenu) return card(
                "RESONANCE / FREQUENCY",
                "response = F(drive frequency, natural frequency, Q, amplitude)",
                "drive frequency • natural frequency • Q/profile • amplitude",
                "excite -> resonate/ring down -> frequency-select -> measure",
                "The client presents synchronized resonant state; server runtime owns the dynamics.");
        if (menu instanceof OpticalSystemMenu) return card(
                "OPTICAL TRANSPORT",
                "P_out = F(P_in, channel, attenuation, topology)",
                "channel • attenuation/filter setting • route",
                "emit -> transport/split/filter -> receive/measure",
                "No optical power is invented by the UI; it only presents network evidence.");
        if (menu instanceof DigitalCommunicationMenu) return card(
                "DIGITAL COMMUNICATION",
                "decoded symbol = F(encoded symbol, threshold, medium evidence)",
                "encoding mode • threshold • route",
                "encode -> transport -> validate evidence -> regenerate/decode",
                "Transport quality and payload value remain separate evidence layers.");
        if (menu instanceof RadioLinkMenu) return card(
                "RADIO LINK",
                "decoded output = F(channel, payload, coverage, interference)",
                "channel • receiver route",
                "transmit -> propagation/link evidence -> decode -> output",
                "Coverage, collision and payload are displayed separately; UI does not synthesize reception.");
        if (menu instanceof ReliabilitySystemMenu) return card(
                "RELIABILITY / PROTECTION",
                "decision = F(inputs, tolerance, timeout, retained fault state)",
                "tolerance • timeout • reset/acknowledge actions",
                "observe redundant evidence -> classify health -> hold/trip/reset according to state machine",
                "Protective state is not the same thing as invalid measurement evidence.");
        if (menu instanceof MediaConversionMenu) return card(
                "DOMAIN CONVERSION",
                "y = Q(F(x)) across an explicit engineering-domain boundary",
                "source domain • destination domain • conversion profile",
                "observe source -> convert/scale/quantize -> publish destination evidence",
                "Conversion may change representation without creating new source precision.");
        if (menu instanceof IndustrialBufferMenu buffer) return card(
                "WIP / BUFFER FLOW",
                "WIP pressure = used capacity / total capacity; flow is constrained by available buffer space",
                "capacity=" + buffer.capacityUnits() + " • used=" + buffer.usedUnits() + " • available=" + buffer.availableUnits(),
                "accept lots -> accumulate WIP -> release downstream when capacity and route permit",
                "The buffer reports real retained lot/capacity state; the Model page does not invent throughput.");
        if (menu instanceof OperationsMonitorMenu monitor) return card(
                "OPERATIONS / PLANT OBSERVATION",
                "queue pressure = queued work / observed capacity; throughput and downtime come from retained plant evidence",
                "queue=" + monitor.queue() + " • throughput=" + monitor.throughput() + " • downtime=" + monitor.downtimeTicks() + "t",
                "observe RUN/queue/cycle evidence -> retain events -> derive WIP, yield, availability and bottleneck indicators",
                monitor.telemetryReady()
                        ? "Plant telemetry is authoritative for the current retained observation scope."
                        : "Telemetry is not ready; missing evidence must not be interpreted as idle production.");
        if (menu instanceof CopperCircuitMeterMenu) return card(
                "ELECTRICAL METROLOGY",
                "I = V/R;  P = V I",
                "measurement face • observed circuit state",
                "observe solved circuit -> derive electrical quantities -> report quality",
                "Readout follows the server circuit solution and never drives the circuit.");
        if (menu instanceof SignalAnalyzerMenu || menu instanceof OscilloscopeMenu || menu instanceof LogicAnalyzerMenu) return card(
                "INSTRUMENTATION",
                "measurement = sampled server evidence; derived metrics use captured samples",
                "trigger • threshold • channel • cursor/capture settings",
                "capture -> retain samples -> derive metrics -> visualize",
                "Instrument UI is downstream of captured evidence.");
        return card(
                "ENGINEERING DEVICE MODEL",
                "state[k+1] = F(state[k], inputs[k], configuration)",
                "device-specific synchronized configuration",
                "observe inputs -> update authoritative server state -> publish outputs/evidence",
                "This page explains the server-owned model; it never moves simulation authority into the client.");
    }

    public static List<ParameterSpec> parameters(EngineeringDeviceMenu menu) {
        if (menu instanceof UniversalFieldDeviceMenu universal) {
            java.util.ArrayList<ParameterSpec> specs = new java.util.ArrayList<>(2);
            if (universal.editPrimaryAvailable()) {
                boolean sweep = switch (universal.configKind()) {
                    case UniversalFieldDeviceMenu.CONFIG_SIGNAL_AMPLIFIER,
                         UniversalFieldDeviceMenu.CONFIG_DIFF_DRIVER,
                         UniversalFieldDeviceMenu.CONFIG_REGENERATOR,
                         UniversalFieldDeviceMenu.CONFIG_ANALOG_COMPARATOR,
                         UniversalFieldDeviceMenu.CONFIG_MAGNETIC_FIELD -> true;
                    default -> false;
                };
                boolean fractions = switch (universal.configKind()) {
                    case UniversalFieldDeviceMenu.CONFIG_SIGNAL_AMPLIFIER,
                         UniversalFieldDeviceMenu.CONFIG_DIFF_DRIVER,
                         UniversalFieldDeviceMenu.CONFIG_REGENERATOR,
                         UniversalFieldDeviceMenu.CONFIG_ANALOG_COMPARATOR -> true;
                    default -> false;
                };
                specs.add(new ParameterSpec(
                        universalPrimaryLabel(universal.configKind()), universal.editPrimaryValue(),
                        universal.editPrimaryMin(), universal.editPrimaryMax(),
                        UniversalFieldDeviceMenu.BUTTON_CONFIG_PRIMARY_PREVIOUS,
                        UniversalFieldDeviceMenu.BUTTON_CONFIG_PRIMARY_NEXT,
                        "raw", "Bounded server-owned field-device parameter A.", fractions, sweep));
            }
            if (universal.editSecondaryAvailable()) {
                specs.add(spec(universalSecondaryLabel(universal.configKind()), universal.editSecondaryValue(),
                        universal.editSecondaryMin(), universal.editSecondaryMax(),
                        UniversalFieldDeviceMenu.BUTTON_CONFIG_SECONDARY_PREVIOUS,
                        UniversalFieldDeviceMenu.BUTTON_CONFIG_SECONDARY_NEXT,
                        "raw", "Bounded server-owned field-device parameter B."));
            }
            return List.copyOf(specs);
        }

        if (menu instanceof FieldDeviceMenu field) {
            return switch (field.kind()) {
                case FieldDeviceMenu.KIND_PROBE -> List.of(
                        spec("Probe channel", field.secondary(), 0, 3,
                                FieldDeviceMenu.BUTTON_PRIMARY_DECREASE, FieldDeviceMenu.BUTTON_PRIMARY_INCREASE,
                                "channel", "Legacy/fallback field HMI channel selection.")
                );
                case FieldDeviceMenu.KIND_FILTER -> List.of(
                        experimentSpec("Filter rate", field.tertiary(), 1, 4,
                                FieldDeviceMenu.BUTTON_PRIMARY_DECREASE, FieldDeviceMenu.BUTTON_PRIMARY_INCREASE,
                                "level/tick", "Precision-filter rise rate.")
                );
                case FieldDeviceMenu.KIND_REFERENCE -> List.of(
                        spec("Reference level", field.primary(), 0, 15,
                                FieldDeviceMenu.BUTTON_PRIMARY_DECREASE, FieldDeviceMenu.BUTTON_PRIMARY_INCREASE,
                                "/15", "Configured Redstone reference level.")
                );
                case FieldDeviceMenu.KIND_DIGITAL_REGENERATOR -> List.of(
                        spec("Quality threshold", field.tertiary(), 0, 2,
                                FieldDeviceMenu.BUTTON_PRIMARY_DECREASE, FieldDeviceMenu.BUTTON_PRIMARY_INCREASE,
                                "mode", "Digital regeneration threshold profile.")
                );
                case FieldDeviceMenu.KIND_PERMANENT_MAGNET -> List.of(
                        spec("Magnet strength", field.primary(), 1, 15,
                                FieldDeviceMenu.BUTTON_PRIMARY_DECREASE, FieldDeviceMenu.BUTTON_PRIMARY_INCREASE,
                                "/15", "Permanent magnetic source strength.")
                );
                case FieldDeviceMenu.KIND_INDUCTION_COIL -> List.of(
                        sweepSpec("Coil turns index", field.tertiary(), 1, 4,
                                FieldDeviceMenu.BUTTON_PRIMARY_DECREASE, FieldDeviceMenu.BUTTON_PRIMARY_INCREASE,
                                "index", "Discrete turns multiplier used by the induction model.")
                );
                case FieldDeviceMenu.KIND_OPTICAL_EMITTER -> List.of(
                        spec("Optical intensity", field.primary(), 0, 15,
                                FieldDeviceMenu.BUTTON_PRIMARY_DECREASE, FieldDeviceMenu.BUTTON_PRIMARY_INCREASE,
                                "/15", "Optical source intensity.")
                );
                case FieldDeviceMenu.KIND_OPTICAL_CHANNEL_FILTER -> List.of(
                        spec("Target channel", field.tertiary(), 0, 15,
                                FieldDeviceMenu.BUTTON_PRIMARY_DECREASE, FieldDeviceMenu.BUTTON_PRIMARY_INCREASE,
                                "channel", "Channel selected by the optical filter.")
                );
                case FieldDeviceMenu.KIND_OPTICAL_ATTENUATOR -> List.of(
                        experimentSpec("Attenuation", field.tertiary(), 0, 8,
                                FieldDeviceMenu.BUTTON_PRIMARY_DECREASE, FieldDeviceMenu.BUTTON_PRIMARY_INCREASE,
                                "levels", "Passive optical attenuation.")
                );
                default -> List.of();
            };
        }
        if (menu instanceof SignalAnalyzerMenu analyzer) {
            return List.of(spec("Calibration offset", analyzer.calibrationOffset(), -2, 2,
                    SignalAnalyzerMenu.BUTTON_CALIBRATION_DECREASE, SignalAnalyzerMenu.BUTTON_CALIBRATION_INCREASE,
                    "levels", "Display/output calibration offset; raw captured evidence remains distinct."));
        }
        if (menu instanceof LogicAnalyzerMenu analyzer) {
            return List.of(experimentSpec("Logic threshold", analyzer.threshold(), 1, 15,
                    LogicAnalyzerMenu.BUTTON_THRESHOLD_DECREASE, LogicAnalyzerMenu.BUTTON_THRESHOLD_INCREASE,
                    "/15", "Threshold used to classify captured channel samples as LOW/HIGH."));
        }
        if (menu instanceof PidControllerMenu pid) {
            return List.of(spec("Tuning preset", pid.tuning(), 0, 3,
                    PidControllerMenu.BUTTON_TUNING_PREVIOUS, PidControllerMenu.BUTTON_TUNING_NEXT,
                    "preset", "Selects one of the four server-owned PID tuning profiles."));
        }
        if (menu instanceof SignalConditionerMenu conditioner) {
            int mode = conditioner.mode();
            int min = switch (mode) { case 1 -> 0; case 5 -> 2; default -> 1; };
            int max = switch (mode) { case 0, 4, 5 -> 4; case 1 -> 10; case 2, 3 -> 15; default -> 15; };
            return List.of(
                    spec("Transfer mode", mode, 0, 5,
                            SignalConditionerMenu.BUTTON_MODE_PREVIOUS, SignalConditionerMenu.BUTTON_MODE_NEXT,
                            "mode", "Changing mode selects a different transfer equation and restores its default parameter."),
                    experimentSpec("Mode parameter", conditioner.parameter(), min, max,
                            SignalConditionerMenu.BUTTON_PARAM_DECREASE, SignalConditionerMenu.BUTTON_PARAM_INCREASE,
                            "raw", "Bounded parameter interpreted by the currently selected transfer mode.")
            );
        }
        if (menu instanceof SignalProcessorMenu processor) {
            return switch (processor.kind()) {
                case SignalProcessorMenu.KIND_FILTER -> List.of(
                        experimentSpec("Rise rate", processor.parameter(), 1, 4,
                                SignalProcessorMenu.BUTTON_PARAMETER_PREVIOUS, SignalProcessorMenu.BUTTON_PARAMETER_NEXT,
                                "level/tick", "Maximum upward output slew."),
                        experimentSpec("Fall rate", processor.secondaryParameter(), 1, 4,
                                SignalProcessorMenu.BUTTON_FILTER_FALL_PREVIOUS, SignalProcessorMenu.BUTTON_FILTER_FALL_NEXT,
                                "level/tick", "Maximum downward output slew.")
                );
                case SignalProcessorMenu.KIND_EDGE -> List.of(
                        spec("Edge mode", processor.parameter(), 0, 2,
                                SignalProcessorMenu.BUTTON_PARAMETER_PREVIOUS, SignalProcessorMenu.BUTTON_PARAMETER_NEXT,
                                "mode", "Rising, falling, or both-edge detection.")
                );
                case SignalProcessorMenu.KIND_PULSE -> List.of(
                        experimentSpec("Pulse width", processor.parameter(), 1, 8,
                                SignalProcessorMenu.BUTTON_PARAMETER_PREVIOUS, SignalProcessorMenu.BUTTON_PARAMETER_NEXT,
                                "ticks", "Accepted trigger pulse duration."),
                        experimentSpec("Trigger threshold", processor.secondaryParameter(), 1, 15,
                                SignalProcessorMenu.BUTTON_THRESHOLD_PREVIOUS, SignalProcessorMenu.BUTTON_THRESHOLD_NEXT,
                                "/15", "Input level required for a trigger."),
                        experimentSpec("Hysteresis", processor.tertiaryParameter(), 1, 4,
                                SignalProcessorMenu.BUTTON_PULSE_HYSTERESIS_PREVIOUS, SignalProcessorMenu.BUTTON_PULSE_HYSTERESIS_NEXT,
                                "levels", "Re-arm separation below the trigger threshold.")
                );
                default -> List.of();
            };
        }
        if (menu instanceof QuartzTimingMenu quartz) {
            if (quartz.kind() == QuartzTimingMenu.KIND_OSCILLATOR) {
                return List.of(sweepSpec("Period index", quartz.tertiary(), 0, 4,
                        QuartzTimingMenu.BUTTON_PARAMETER_PREVIOUS, QuartzTimingMenu.BUTTON_PARAMETER_NEXT,
                        "index", "Current realized nominal period = " + quartz.secondary() + " ticks."));
            }
            return List.of();
        }
        if (menu instanceof RangeSensorMenu range) {
            return List.of(
                    spec("Detection mode", range.detectMode(), 0, 2,
                            RangeSensorMenu.BUTTON_MODE_PREVIOUS, RangeSensorMenu.BUTTON_MODE_NEXT,
                            "mode", "Selects the detection interpretation."),
                    spec("Range mode", range.rangeMode(), 0, 2,
                            RangeSensorMenu.BUTTON_RANGE_PREVIOUS, RangeSensorMenu.BUTTON_RANGE_NEXT,
                            "mode", "Changes the bounded sensing range."),
                    spec("Response mode", range.responseMode(), 0, 3,
                            RangeSensorMenu.BUTTON_RESPONSE_PREVIOUS, RangeSensorMenu.BUTTON_RESPONSE_NEXT,
                            "mode", "Changes the distance-to-output response.")
            );
        }
        if (menu instanceof PneumaticSystemMenu pneumatic) {
            return switch (pneumatic.kind()) {
                case PneumaticSystemMenu.KIND_COMPRESSOR -> List.of(
                        spec("Response profile", pneumatic.stateFlag(), 0, 2,
                                PneumaticSystemMenu.BUTTON_PARAMETER_PREVIOUS, PneumaticSystemMenu.BUTTON_PARAMETER_NEXT,
                                "mode", "Controls finite compressor pressure response.")
                );
                case PneumaticSystemMenu.KIND_REGULATOR -> List.of(
                        experimentSpec("Pressure setpoint", Math.max(1, pneumatic.secondary() / 10), 1, 10,
                                PneumaticSystemMenu.BUTTON_PARAMETER_PREVIOUS, PneumaticSystemMenu.BUTTON_PARAMETER_NEXT,
                                "x10 pressure", "Raw setting 1..10 corresponds to 10..100 pressure.")
                );
                case PneumaticSystemMenu.KIND_RECEIVER -> List.of(
                        spec("Pressure range", pneumatic.stateFlag(), 0, 2,
                                PneumaticSystemMenu.BUTTON_PARAMETER_PREVIOUS, PneumaticSystemMenu.BUTTON_PARAMETER_NEXT,
                                "mode", "Selects the pressure full-scale used for transduction.")
                );
                case PneumaticSystemMenu.KIND_PROPORTIONAL -> List.of(
                        spec("Response profile", pneumatic.stateFlag(), 0, 2,
                                PneumaticSystemMenu.BUTTON_PARAMETER_PREVIOUS, PneumaticSystemMenu.BUTTON_PARAMETER_NEXT,
                                "mode", "Controls finite valve-opening response.")
                );
                case PneumaticSystemMenu.KIND_RELIEF -> List.of(
                        experimentSpec("Relief setpoint", Math.max(1, pneumatic.tertiary() / 25), 1, 4,
                                PneumaticSystemMenu.BUTTON_PARAMETER_PREVIOUS, PneumaticSystemMenu.BUTTON_PARAMETER_NEXT,
                                "x25 pressure", "Raw setting 1..4 corresponds to 25..100 pressure.")
                );
                default -> List.of();
            };
        }
        if (menu instanceof AmethystSystemMenu amethyst) {
            return switch (amethyst.kind()) {
                case AmethystSystemMenu.KIND_SOURCE -> List.of(
                        spec("Drive frequency", amethyst.primary(), 1, 15,
                                AmethystSystemMenu.BUTTON_PRIMARY_PREVIOUS, AmethystSystemMenu.BUTTON_PRIMARY_NEXT,
                                "index", "Frequency index of the impulse resonator."),
                        spec("Peak amplitude", amethyst.secondary(), 1, 15,
                                AmethystSystemMenu.BUTTON_SECONDARY_PREVIOUS, AmethystSystemMenu.BUTTON_SECONDARY_NEXT,
                                "/15", "Initial excitation amplitude before ring-down.")
                );
                case AmethystSystemMenu.KIND_FILTER -> List.of(
                        sweepSpec("Target frequency", amethyst.tertiary(), 1, 15,
                                AmethystSystemMenu.BUTTON_PRIMARY_PREVIOUS, AmethystSystemMenu.BUTTON_PRIMARY_NEXT,
                                "index", "Pass frequency selected by the frequency filter.")
                );
                case AmethystSystemMenu.KIND_TUNED -> List.of(
                        sweepSpec("Natural frequency", amethyst.tertiary(), 1, 15,
                                AmethystSystemMenu.BUTTON_PRIMARY_PREVIOUS, AmethystSystemMenu.BUTTON_PRIMARY_NEXT,
                                "index", "Natural resonance frequency."),
                        sweepSpec("Q index", amethyst.auxiliary(), 1, 4,
                                AmethystSystemMenu.BUTTON_SECONDARY_PREVIOUS, AmethystSystemMenu.BUTTON_SECONDARY_NEXT,
                                "index", "Controls resonance selectivity/bandwidth.")
                );
                default -> List.of();
            };
        }
        if (menu instanceof RadioLinkMenu radio) {
            return List.of(spec("Radio channel", radio.channel(), 0, 3,
                    RadioLinkMenu.BUTTON_CHANNEL_PREVIOUS, RadioLinkMenu.BUTTON_CHANNEL_NEXT,
                    "channel", "Transmitter/receiver channel selection."));
        }
        if (menu instanceof DigitalCommunicationMenu digital
                && digital.kind() == DigitalCommunicationMenu.KIND_REGENERATOR) {
            return List.of(spec("Quality threshold", digital.parameter(), 0, 2,
                    DigitalCommunicationMenu.BUTTON_PARAMETER_PREVIOUS, DigitalCommunicationMenu.BUTTON_PARAMETER_NEXT,
                    "mode", "Minimum medium quality required before regeneration."));
        }
        if (menu instanceof ReliabilitySystemMenu reliability) {
            return switch (reliability.kind()) {
                case ReliabilitySystemMenu.KIND_WATCHDOG -> List.of(
                        spec("Timeout profile", reliability.parameterIndex(), 0, 3,
                                ReliabilitySystemMenu.BUTTON_PARAMETER_PREVIOUS, ReliabilitySystemMenu.BUTTON_PARAMETER_NEXT,
                                "index", "Selects the server-owned heartbeat timeout profile.")
                );
                case ReliabilitySystemMenu.KIND_SERVO -> List.of(
                        spec("Servo slew profile", reliability.parameterIndex(), 0, 2,
                                ReliabilitySystemMenu.BUTTON_PARAMETER_PREVIOUS, ReliabilitySystemMenu.BUTTON_PARAMETER_NEXT,
                                "index", "Controls bounded actuator slew response.")
                );
                case ReliabilitySystemMenu.KIND_VOTER -> List.of(
                        spec("Voting tolerance", reliability.parameterIndex(), 0, 3,
                                ReliabilitySystemMenu.BUTTON_PARAMETER_PREVIOUS, ReliabilitySystemMenu.BUTTON_PARAMETER_NEXT,
                                "index", "Selects the allowed disagreement tolerance.")
                );
                case ReliabilitySystemMenu.KIND_FAULT_LATCH -> List.of(
                        spec("Trip threshold", reliability.parameterIndex(), 0, 3,
                                ReliabilitySystemMenu.BUTTON_PARAMETER_PREVIOUS, ReliabilitySystemMenu.BUTTON_PARAMETER_NEXT,
                                "index", "Selects the latched-fault trip threshold profile.")
                );
                default -> List.of();
            };
        }
        if (menu instanceof OpticalSystemMenu optical) {
            return switch (optical.kind()) {
                case OpticalSystemMenu.KIND_EMITTER -> List.of(
                        spec("Optical intensity", optical.primary(), 0, 15,
                                OpticalSystemMenu.BUTTON_PRIMARY_PREVIOUS, OpticalSystemMenu.BUTTON_PRIMARY_NEXT,
                                "/15", "Source intensity."),
                        spec("Optical channel", optical.secondary(), 0, 15,
                                OpticalSystemMenu.BUTTON_SECONDARY_PREVIOUS, OpticalSystemMenu.BUTTON_SECONDARY_NEXT,
                                "channel", "Source wavelength/channel identity.")
                );
                case OpticalSystemMenu.KIND_FILTER -> List.of(
                        spec("Target channel", optical.secondary(), 0, 15,
                                OpticalSystemMenu.BUTTON_PRIMARY_PREVIOUS, OpticalSystemMenu.BUTTON_PRIMARY_NEXT,
                                "channel", "Only matching channel evidence is passed.")
                );
                case OpticalSystemMenu.KIND_ATTENUATOR -> List.of(
                        experimentSpec("Attenuation", optical.secondary(), 0, 8,
                                OpticalSystemMenu.BUTTON_PRIMARY_PREVIOUS, OpticalSystemMenu.BUTTON_PRIMARY_NEXT,
                                "levels", "Configured passive signal loss.")
                );
                case OpticalSystemMenu.KIND_FREE_SPACE_TX, OpticalSystemMenu.KIND_FREE_SPACE_RX -> List.of(
                        spec("Optical channel", optical.secondary(), 0, 3,
                                OpticalSystemMenu.BUTTON_SECONDARY_PREVIOUS, OpticalSystemMenu.BUTTON_SECONDARY_NEXT,
                                "channel", "Free-space link channel.")
                );
                default -> List.of();
            };
        }
        if (menu instanceof MagneticSystemMenu magnetic) {
            return switch (magnetic.kind()) {
                case MagneticSystemMenu.KIND_PERMANENT -> List.of(
                        spec("Magnet strength", magnetic.primary(), 1, 15,
                                MagneticSystemMenu.BUTTON_PRIMARY_PREVIOUS, MagneticSystemMenu.BUTTON_PRIMARY_NEXT,
                                "/15", "Permanent source strength.")
                );
                case MagneticSystemMenu.KIND_COIL -> List.of(
                        sweepSpec("Coil turns index", magnetic.tertiary(), 1, 4,
                                MagneticSystemMenu.BUTTON_PRIMARY_PREVIOUS, MagneticSystemMenu.BUTTON_PRIMARY_NEXT,
                                "turns index", "Induced response scales with the configured turns index.")
                );
                default -> List.of();
            };
        }
        return List.of();
    }

    public static ResponseSpec response(EngineeringDeviceMenu menu) {
        if (menu instanceof UniversalFieldDeviceMenu universal) {
            for (Direction side : Direction.values()) {
                if (!universal.hasPort(side) || (!universal.isOutput(side) && !universal.isBidirectional(side))) continue;
                PortQuality quality = universal.quality(side);
                boolean usable = quality == PortQuality.VALID || quality == PortQuality.SATURATED;
                return response("Output " + side.getName().toUpperCase(), universal.value(side),
                        universal.minimum(side), universal.maximum(side), "port", usable,
                        "First declared transmit-capable engineering port; quality=" + quality.name() + ".");
            }
            return null;
        }
        if (menu instanceof PidControllerMenu pid) {
            return response("Control output", pid.controlOutput(), 0, 15, "/15",
                    pid.available(), "Live PID command after the currently selected tuning profile.");
        }
        if (menu instanceof SignalConditionerMenu conditioner) {
            return response("Conditioned output", conditioner.output(), 0, 15, "/15",
                    menu.evidenceState() == EngineeringDeviceMenu.EVIDENCE_VALID
                            || menu.evidenceState() == EngineeringDeviceMenu.EVIDENCE_SATURATED,
                    "Live server-computed transfer-function output.");
        }
        if (menu instanceof SignalProcessorMenu processor) {
            return response("Processor output", processor.output(), 0, 15, "/15",
                    menu.evidenceState() == EngineeringDeviceMenu.EVIDENCE_VALID
                            || menu.evidenceState() == EngineeringDeviceMenu.EVIDENCE_SATURATED,
                    "Live output after the selected discrete-time processing stage.");
        }
        if (menu instanceof QuartzTimingMenu quartz && quartz.kind() == QuartzTimingMenu.KIND_OSCILLATOR) {
            return response("Realized period", quartz.secondary(), 1, 64, "ticks",
                    quartz.quality() == PortQuality.VALID,
                    "Server timing evidence; not a client-predicted waveform.");
        }
        if (menu instanceof RangeSensorMenu range) {
            return response("Sensor output", range.output(), 0, 15, "/15",
                    range.evidenceValid(), "Live distance-to-output response from the configured sensing profile.");
        }
        if (menu instanceof PneumaticSystemMenu pneumatic) {
            return switch (pneumatic.kind()) {
                case PneumaticSystemMenu.KIND_COMPRESSOR ->
                        response("Actual pressure", pneumatic.tertiary(), 0, 100, "/100",
                                pneumatic.outputQuality() == PortQuality.VALID,
                                "Finite-response compressor pressure.");
                case PneumaticSystemMenu.KIND_REGULATOR ->
                        response("Regulated pressure", pneumatic.tertiary(), 0, 100, "/100",
                                pneumatic.outputQuality() == PortQuality.VALID,
                                "Live regulated downstream pressure.");
                case PneumaticSystemMenu.KIND_RECEIVER ->
                        response("Redstone output", pneumatic.secondary(), 0, 15, "/15",
                                pneumatic.outputQuality() == PortQuality.VALID,
                                "Pressure-to-Redstone transducer output.");
                case PneumaticSystemMenu.KIND_PROPORTIONAL ->
                        response("Actual opening", pneumatic.tertiary(), 0, 15, "/15",
                                pneumatic.outputQuality() == PortQuality.VALID,
                                "Finite-response proportional-valve opening.");
                case PneumaticSystemMenu.KIND_CYLINDER ->
                        response("Cylinder position", pneumatic.secondary(), 0, 15, "/15",
                                pneumatic.outputQuality() == PortQuality.VALID,
                                "Live actuator position, not the requested target.");
                default -> null;
            };
        }
        if (menu instanceof AmethystSystemMenu amethyst) {
            return switch (amethyst.kind()) {
                case AmethystSystemMenu.KIND_FILTER ->
                        response("Filtered amplitude", amethyst.auxiliary(), 0, 15, "/15",
                                amethyst.quality() == PortQuality.VALID,
                                "Expected output amplitude from the live filter evidence.");
                case AmethystSystemMenu.KIND_TUNED ->
                        response("Resonant amplitude", amethyst.extraB(), 0, 15, "/15",
                                amethyst.quality() == PortQuality.VALID,
                                "Live tuned-resonator output amplitude.");
                default -> null;
            };
        }
        if (menu instanceof ReliabilitySystemMenu reliability) {
            return switch (reliability.kind()) {
                case ReliabilitySystemMenu.KIND_SERVO ->
                        response("Servo position", reliability.primary(), 0, 15, "/15",
                                reliability.quality() == PortQuality.VALID,
                                "Measured actuator position while the slew profile changes.");
                case ReliabilitySystemMenu.KIND_VOTER ->
                        response("Voted output", reliability.primary(), 0, 15, "/15",
                                reliability.quality() == PortQuality.VALID
                                        || reliability.quality() == PortQuality.SATURATED,
                                "Current 2oo3 voter output.");
                case ReliabilitySystemMenu.KIND_WATCHDOG ->
                        response("Safety output", reliability.extraA(), 0, 15, "/15",
                                reliability.quality() == PortQuality.VALID,
                                "Current watchdog output while timeout profile changes.");
                case ReliabilitySystemMenu.KIND_FAULT_LATCH ->
                        response("Latch output", reliability.primary(), 0, 15, "/15",
                                reliability.quality() == PortQuality.VALID,
                                "Current latched safety output.");
                default -> null;
            };
        }
        if (menu instanceof MagneticSystemMenu magnetic && magnetic.kind() == MagneticSystemMenu.KIND_COIL) {
            return response("Induced voltage", magnetic.secondary(), 0, 15, "/15",
                    magnetic.quality() == PortQuality.VALID,
                    "Live induction-coil output voltage while turns index changes.");
        }
        if (menu instanceof OpticalSystemMenu optical) {
            return switch (optical.kind()) {
                case OpticalSystemMenu.KIND_FILTER, OpticalSystemMenu.KIND_ATTENUATOR ->
                        response("Optical output", optical.tertiary(), 0, 15, "/15",
                                optical.quality() == PortQuality.VALID,
                                "Live expected output intensity from synchronized optical evidence.");
                default -> null;
            };
        }
        if (menu instanceof SignalAnalyzerMenu analyzer) {
            return response("Calibrated signal", analyzer.calibrated(), 0, 15, "/15",
                    menu.evidenceState() == EngineeringDeviceMenu.EVIDENCE_VALID,
                    "Calibrated display/output reading; raw evidence remains separate.");
        }
        if (menu instanceof LogicAnalyzerMenu analyzer) {
            return response("Active channels", analyzer.activeChannels(), 0, 4, "channels",
                    analyzer.bounded(), "Number of active captured channels at the current logic threshold.");
        }
        return null;
    }

    private static ResponseSpec response(
            String label, int value, int minimum, int maximum, String unit, boolean usable, String detail
    ) {
        return new ResponseSpec(label, value, minimum, maximum, unit, usable, detail);
    }

    private static String universalPrimaryLabel(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.CONFIG_ANALOG_COMPARATOR -> "Hysteresis";
            case UniversalFieldDeviceMenu.CONFIG_SIGNAL_AMPLIFIER -> "Gain mode";
            case UniversalFieldDeviceMenu.CONFIG_REDUNDANT_VOTER -> "Tolerance";
            case UniversalFieldDeviceMenu.CONFIG_WATCHDOG -> "Timeout profile";
            case UniversalFieldDeviceMenu.CONFIG_DIFF_DRIVER,
                 UniversalFieldDeviceMenu.CONFIG_REGENERATOR,
                 UniversalFieldDeviceMenu.CONFIG_FAULT_LATCH -> "Threshold";
            case UniversalFieldDeviceMenu.CONFIG_SERIALIZER,
                 UniversalFieldDeviceMenu.CONFIG_QUARTZ_OSCILLATOR,
                 UniversalFieldDeviceMenu.CONFIG_PWM -> "Period";
            case UniversalFieldDeviceMenu.CONFIG_REFERENCE_SOURCE -> "Reference level";
            case UniversalFieldDeviceMenu.CONFIG_SIGNAL_PROBE -> "Channel";
            case UniversalFieldDeviceMenu.CONFIG_MAGNETIC_FIELD -> "Radius";
            case UniversalFieldDeviceMenu.CONFIG_LIGHT_SENSOR,
                 UniversalFieldDeviceMenu.CONFIG_ENTITY_DENSITY,
                 UniversalFieldDeviceMenu.CONFIG_LAPIS_RANGE,
                 UniversalFieldDeviceMenu.CONFIG_LAPIS_TRANSDUCER,
                 UniversalFieldDeviceMenu.CONFIG_CALIBRATION -> "Profile";
            case UniversalFieldDeviceMenu.CONFIG_TANK_LEVEL -> "Range";
            case UniversalFieldDeviceMenu.CONFIG_MOLECULAR_RECEIVER -> "Sensitivity";
            case UniversalFieldDeviceMenu.CONFIG_ALARM -> "Severity";
            case UniversalFieldDeviceMenu.CONFIG_SAMPLE_HOLD -> "Trigger mode";
            case UniversalFieldDeviceMenu.CONFIG_FAULT_INJECTOR -> "Fault mode";
            default -> "Parameter A";
        };
    }

    private static String universalSecondaryLabel(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.CONFIG_MAGNETIC_FIELD -> "Sampling mode";
            case UniversalFieldDeviceMenu.CONFIG_ENTITY_DENSITY -> "Aperture";
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_RANGE -> "Range index";
            case UniversalFieldDeviceMenu.CONFIG_SINGLE_RELAY -> "Pickup profile";
            default -> "Parameter B";
        };
    }

    private static ParameterSpec spec(
            String label, int current, int minimum, int maximum,
            int decrementButton, int incrementButton, String unit, String detail
    ) {
        return new ParameterSpec(label, current, minimum, maximum,
                decrementButton, incrementButton, unit, detail);
    }

    private static ParameterSpec scaledSpec(
            String label, int current, int minimum, int maximum,
            int decrementButton, int incrementButton, String unit, String detail
    ) {
        return new ParameterSpec(label, current, minimum, maximum,
                decrementButton, incrementButton, unit, detail, true, false);
    }

    private static ParameterSpec sweepSpec(
            String label, int current, int minimum, int maximum,
            int decrementButton, int incrementButton, String unit, String detail
    ) {
        return new ParameterSpec(label, current, minimum, maximum,
                decrementButton, incrementButton, unit, detail, false, true);
    }

    private static ParameterSpec experimentSpec(
            String label, int current, int minimum, int maximum,
            int decrementButton, int incrementButton, String unit, String detail
    ) {
        return new ParameterSpec(label, current, minimum, maximum,
                decrementButton, incrementButton, unit, detail, true, true);
    }

    private static ModelCard universal(UniversalFieldDeviceMenu menu) {
        return switch (menu.configKind()) {
            case UniversalFieldDeviceMenu.CONFIG_ANALOG_COMPARATOR -> card(
                    "SCHMITT COMPARATOR", "decision = Schmitt(process - reference, hysteresis)",
                    "comparison mode • hysteresis", "compare two live inputs -> retain decision inside hysteresis band",
                    "Invalid process/reference evidence holds the last trusted decision.");
            case UniversalFieldDeviceMenu.CONFIG_SIGNAL_SELECTOR -> card(
                    "2:1 SIGNAL SELECTOR", "y = select ? x_B : x_A",
                    "select polarity", "validate select evidence -> choose payload -> propagate payload quality",
                    "Missing control evidence cannot masquerade as an explicit LOW command.");
            case UniversalFieldDeviceMenu.CONFIG_SIGNAL_TAP -> card(
                    "BUFFER / TAP", "y = x for trustworthy input evidence",
                    "route only", "observe series input -> copy payload -> preserve evidence quality",
                    "Retained numeric history is not automatically current evidence.");
            case UniversalFieldDeviceMenu.CONFIG_QUARTZ_LAPIS_SAMPLER,
                 UniversalFieldDeviceMenu.CONFIG_SAMPLE_HOLD -> card(
                    "SAMPLE AND HOLD", "y[n] = x(t_n); hold y until the next accepted trigger",
                    "trigger mode • clock/trigger evidence", "detect trigger edge -> capture valid input -> hold sample",
                    "A rejected capture keeps the previous value instead of fabricating zero.");
            case UniversalFieldDeviceMenu.CONFIG_SIGNAL_AMPLIFIER -> card(
                    "SIGNAL AMPLIFIER", "y = clamp(g * x, 0, 15)",
                    "gain mode", "scale input -> detect clipping -> publish bounded output",
                    "Clipping evidence remains visible instead of being hidden by the bounded output.");
            case UniversalFieldDeviceMenu.CONFIG_REDUNDANT_VOTER -> card(
                    "2oo3 REDUNDANT VOTER", "vote = consensus(inputs; configured tolerance)",
                    "tolerance", "compare three channels -> classify disagreement -> vote or degrade",
                    "Degraded operation and evidence quality are reported separately.");
            case UniversalFieldDeviceMenu.CONFIG_WATCHDOG -> card(
                    "WATCHDOG", "trip = (heartbeat age >= configured timeout)",
                    "timeout mode", "observe heartbeat -> measure age -> enter protective state on timeout",
                    "Timeout is a real protective state, not a fake signal-quality error.");
            case UniversalFieldDeviceMenu.CONFIG_DIFF_DRIVER -> card(
                    "DIFFERENTIAL DRIVER", "bit = 1 when input crosses configured threshold",
                    "threshold mode", "classify input -> drive differential pair -> retain evidence quality",
                    "Thresholding does not create information when source evidence is missing.");
            case UniversalFieldDeviceMenu.CONFIG_REGENERATOR -> card(
                    "DIGITAL REGENERATOR", "output symbol = classify(input; threshold)",
                    "decision threshold", "sample medium -> accept/reject -> regenerate a clean symbol",
                    "Accepted and rejected counts are retained independently.");
            case UniversalFieldDeviceMenu.CONFIG_SERIALIZER -> card(
                    "SERIALIZER", "parallel word -> time-ordered serial symbols",
                    "period mode", "capture word -> emit symbols at configured period",
                    "Timing and payload evidence remain explicit.");
            case UniversalFieldDeviceMenu.CONFIG_REFERENCE_SOURCE -> card(
                    "REFERENCE SOURCE", "y = configured reference level",
                    "reference level 0..15", "store bounded setting -> drive output reference",
                    "Configured zero is a valid source, not NO_SIGNAL.");
            case UniversalFieldDeviceMenu.CONFIG_MAGNETIC_FIELD -> card(
                    "MAGNETIC FIELD SENSOR", "measurement = aggregate(B over configured sampling aperture)",
                    "radius mode • sampling mode", "scan field aperture -> aggregate valid evidence -> publish measurement",
                    "Incomplete coverage degrades evidence instead of silently changing the field value.");
            case UniversalFieldDeviceMenu.CONFIG_ELECTROMAGNET -> card(
                    "ELECTROMAGNET", "B[k+1] approaches B_target(command) with finite response",
                    "command-derived target • thermal/runtime state", "read command -> update target -> slew field -> expose tracking error",
                    "Target field and actual field are intentionally distinct.");
            case UniversalFieldDeviceMenu.CONFIG_PWM -> card(
                    "PWM CONTROLLER", "duty approximates command/15 over the configured carrier period",
                    "carrier period mode • invert", "sample command -> schedule duty pattern -> update only at owned carrier state",
                    "PWM representation is time-domain control, not extra analog precision.");
            case UniversalFieldDeviceMenu.CONFIG_CALIBRATION -> card(
                    "CALIBRATION", "corrected measurement = F(raw measurement, estimated bias/profile)",
                    "calibration profile", "observe raw/reference evidence -> estimate correction -> publish corrected result",
                    "Bias estimates never override missing or contradictory source evidence.");
            default -> card(
                    "FIELD DEVICE MODEL", "state[k+1] = F(state[k], input evidence, configuration)",
                    parameterSummary(menu), "server-authoritative bounded processing with explicit port evidence",
                    "Use Configure for owned parameters, Ports for interfaces, and Observe/Log for evidence.");
        };
    }

    private static String parameterSummary(UniversalFieldDeviceMenu menu) {
        if (menu.editPrimaryAvailable() && menu.editSecondaryAvailable()) {
            return "parameter A " + menu.editPrimaryMin() + ".." + menu.editPrimaryMax()
                    + " • parameter B " + menu.editSecondaryMin() + ".." + menu.editSecondaryMax();
        }
        if (menu.editPrimaryAvailable()) {
            return "parameter " + menu.editPrimaryMin() + ".." + menu.editPrimaryMax();
        }
        if (menu.editSecondaryAvailable()) {
            return "parameter " + menu.editSecondaryMin() + ".." + menu.editSecondaryMax();
        }
        return "read-only or action/toggle configuration";
    }

    private static ModelCard card(String family, String equation, String parameters, String process, String boundary) {
        return new ModelCard(family, equation, parameters, process, boundary);
    }
}
