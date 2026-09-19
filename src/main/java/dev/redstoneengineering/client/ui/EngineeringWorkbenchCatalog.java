package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.ui.menu.*;

import java.util.List;

/**
 * Client-only presentation catalog for the equations and processing ideas already represented by
 * server-authoritative RSE devices. It never evaluates physics or acceptance logic.
 */
public final class EngineeringWorkbenchCatalog {
    public record ModelCard(String family, String equation, String parameters, String process, String boundary) {}
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
            String detail
    ) {
        public ParameterSpec {
            unit = unit == null ? "" : unit;
            detail = detail == null ? "" : detail;
        }
    }

    private EngineeringWorkbenchCatalog() {}

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
        // UniversalFieldDevice has its own exact target editor in Configure because it may expose
        // two independent IntegerProperties and action/toggle controls at the same time.
        if (menu instanceof UniversalFieldDeviceMenu) return List.of();

        if (menu instanceof FieldDeviceMenu field) {
            return switch (field.kind()) {
                case FieldDeviceMenu.KIND_PROBE -> List.of(
                        spec("Probe channel", field.secondary(), 0, 3,
                                FieldDeviceMenu.BUTTON_PRIMARY_DECREASE, FieldDeviceMenu.BUTTON_PRIMARY_INCREASE,
                                "channel", "Legacy/fallback field HMI channel selection.")
                );
                case FieldDeviceMenu.KIND_FILTER -> List.of(
                        spec("Filter rate", field.tertiary(), 1, 4,
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
                        spec("Coil turns index", field.tertiary(), 1, 4,
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
                        spec("Attenuation", field.tertiary(), 0, 8,
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
            return List.of(spec("Logic threshold", analyzer.threshold(), 1, 15,
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
                    spec("Mode parameter", conditioner.parameter(), min, max,
                            SignalConditionerMenu.BUTTON_PARAM_DECREASE, SignalConditionerMenu.BUTTON_PARAM_INCREASE,
                            "raw", "Bounded parameter interpreted by the currently selected transfer mode.")
            );
        }
        if (menu instanceof SignalProcessorMenu processor) {
            return switch (processor.kind()) {
                case SignalProcessorMenu.KIND_FILTER -> List.of(
                        spec("Rise rate", processor.parameter(), 1, 4,
                                SignalProcessorMenu.BUTTON_PARAMETER_PREVIOUS, SignalProcessorMenu.BUTTON_PARAMETER_NEXT,
                                "level/tick", "Maximum upward output slew."),
                        spec("Fall rate", processor.secondaryParameter(), 1, 4,
                                SignalProcessorMenu.BUTTON_FILTER_FALL_PREVIOUS, SignalProcessorMenu.BUTTON_FILTER_FALL_NEXT,
                                "level/tick", "Maximum downward output slew.")
                );
                case SignalProcessorMenu.KIND_EDGE -> List.of(
                        spec("Edge mode", processor.parameter(), 0, 2,
                                SignalProcessorMenu.BUTTON_PARAMETER_PREVIOUS, SignalProcessorMenu.BUTTON_PARAMETER_NEXT,
                                "mode", "Rising, falling, or both-edge detection.")
                );
                case SignalProcessorMenu.KIND_PULSE -> List.of(
                        spec("Pulse width", processor.parameter(), 1, 8,
                                SignalProcessorMenu.BUTTON_PARAMETER_PREVIOUS, SignalProcessorMenu.BUTTON_PARAMETER_NEXT,
                                "ticks", "Accepted trigger pulse duration."),
                        spec("Trigger threshold", processor.secondaryParameter(), 1, 15,
                                SignalProcessorMenu.BUTTON_THRESHOLD_PREVIOUS, SignalProcessorMenu.BUTTON_THRESHOLD_NEXT,
                                "/15", "Input level required for a trigger."),
                        spec("Hysteresis", processor.tertiaryParameter(), 1, 4,
                                SignalProcessorMenu.BUTTON_PULSE_HYSTERESIS_PREVIOUS, SignalProcessorMenu.BUTTON_PULSE_HYSTERESIS_NEXT,
                                "levels", "Re-arm separation below the trigger threshold.")
                );
                default -> List.of();
            };
        }
        if (menu instanceof QuartzTimingMenu quartz) {
            if (quartz.kind() == QuartzTimingMenu.KIND_OSCILLATOR) {
                return List.of(spec("Period index", quartz.tertiary(), 0, 4,
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
                        spec("Pressure setpoint", Math.max(1, pneumatic.secondary() / 10), 1, 10,
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
                        spec("Relief setpoint", Math.max(1, pneumatic.tertiary() / 25), 1, 4,
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
                        spec("Target frequency", amethyst.tertiary(), 1, 15,
                                AmethystSystemMenu.BUTTON_PRIMARY_PREVIOUS, AmethystSystemMenu.BUTTON_PRIMARY_NEXT,
                                "index", "Pass frequency selected by the frequency filter.")
                );
                case AmethystSystemMenu.KIND_TUNED -> List.of(
                        spec("Natural frequency", amethyst.tertiary(), 1, 15,
                                AmethystSystemMenu.BUTTON_PRIMARY_PREVIOUS, AmethystSystemMenu.BUTTON_PRIMARY_NEXT,
                                "index", "Natural resonance frequency."),
                        spec("Q index", amethyst.auxiliary(), 1, 4,
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
                        spec("Attenuation", optical.secondary(), 0, 8,
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
                        spec("Coil turns index", magnetic.tertiary(), 1, 4,
                                MagneticSystemMenu.BUTTON_PRIMARY_PREVIOUS, MagneticSystemMenu.BUTTON_PRIMARY_NEXT,
                                "turns index", "Induced response scales with the configured turns index.")
                );
                default -> List.of();
            };
        }
        return List.of();
    }

    private static ParameterSpec spec(
            String label, int current, int minimum, int maximum,
            int decrementButton, int incrementButton, String unit, String detail
    ) {
        return new ParameterSpec(label, current, minimum, maximum,
                decrementButton, incrementButton, unit, detail);
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
