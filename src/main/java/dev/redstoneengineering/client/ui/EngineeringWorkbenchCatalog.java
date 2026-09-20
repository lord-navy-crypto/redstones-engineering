package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.ui.menu.*;
import dev.redstoneengineering.block.MagneticFieldSensorBlock;
import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.Direction;

import java.util.List;

/**
 * Client-only presentation catalog for the equations and processing ideas already represented by
 * server-authoritative RSE devices. It never evaluates physics or acceptance logic.
 */
public final class EngineeringWorkbenchCatalog {
    public enum UiTier { BLOCK, DEVICE, LAB }
    public enum ExperimentKind {
        TRANSFER,
        DYNAMIC_RESPONSE,
        TIMING,
        FREQUENCY_RESPONSE,
        ACTUATOR_RESPONSE,
        INSTRUMENTATION
    }

    /**
     * Minecraft-first UI policy. BLOCK keeps the GUI light, DEVICE adds bounded configuration,
     * and LAB enables controlled experiments only when the underlying device semantics justify it.
     */
    public record UiPolicy(UiTier tier, String pageLabel, String rationale) {
        public boolean experimental() { return tier == UiTier.LAB; }
        public boolean configurable() { return tier != UiTier.BLOCK; }
    }

    public record ModelCard(String family, String equation, String parameters, String process, String boundary) {}

    public record LabMetric(String label, String value, String detail) {
        public LabMetric {
            detail = detail == null ? "" : detail;
        }
    }

    /**
     * Device-specific experiment framing for LAB-tier blocks.
     * Every value shown here is derived from already synchronized server state.
     */
    public record LabProfile(
            String question,
            String independentVariable,
            String dependentVariable,
            List<LabMetric> metrics,
            String note
    ) {
        public LabProfile {
            metrics = metrics == null ? List.of() : List.copyOf(metrics);
            note = note == null ? "" : note;
        }
    }

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
    public enum ParameterControl { CHOICE, RANGE, EXPERIMENT }

    public record ParameterSpec(
            String label,
            int current,
            int minimum,
            int maximum,
            int decrementButton,
            int incrementButton,
            String unit,
            String detail,
            ParameterControl control,
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
                    unit, detail, ParameterControl.CHOICE, false, false);
        }

        public ParameterSpec {
            unit = unit == null ? "" : unit;
            detail = detail == null ? "" : detail;
        }
    }

    private EngineeringWorkbenchCatalog() {}

    public static ExperimentKind experimentKind(EngineeringDeviceMenu menu) {
        if (menu instanceof PidControllerMenu) return ExperimentKind.DYNAMIC_RESPONSE;
        if (menu instanceof SignalConditionerMenu) return ExperimentKind.TRANSFER;
        if (menu instanceof SignalProcessorMenu processor) {
            return processor.kind() == SignalProcessorMenu.KIND_FILTER
                    ? ExperimentKind.DYNAMIC_RESPONSE : ExperimentKind.TIMING;
        }
        if (menu instanceof QuartzTimingMenu) return ExperimentKind.TIMING;
        if (menu instanceof AmethystSystemMenu) return ExperimentKind.FREQUENCY_RESPONSE;
        if (menu instanceof PneumaticSystemMenu pneumatic) {
            return switch (pneumatic.kind()) {
                case PneumaticSystemMenu.KIND_FLOW_METER -> ExperimentKind.INSTRUMENTATION;
                case PneumaticSystemMenu.KIND_CYLINDER,
                     PneumaticSystemMenu.KIND_PROPORTIONAL,
                     PneumaticSystemMenu.KIND_COMPRESSOR -> ExperimentKind.ACTUATOR_RESPONSE;
                default -> ExperimentKind.DYNAMIC_RESPONSE;
            };
        }
        if (menu instanceof ReliabilitySystemMenu reliability
                && reliability.kind() == ReliabilitySystemMenu.KIND_SERVO) {
            return ExperimentKind.ACTUATOR_RESPONSE;
        }
        if (menu instanceof MagneticSystemMenu magnetic
                && magnetic.kind() == MagneticSystemMenu.KIND_COIL) {
            return ExperimentKind.DYNAMIC_RESPONSE;
        }
        if (menu instanceof SignalAnalyzerMenu
                || menu instanceof OscilloscopeMenu
                || menu instanceof LogicAnalyzerMenu) {
            return ExperimentKind.INSTRUMENTATION;
        }
        return ExperimentKind.TRANSFER;
    }

    /**
     * Minimum display-side dwell between real server actions during a sweep.
     * Slow physical/dynamic devices intentionally wait longer than static transfer devices.
     */
    public static int recommendedSweepDwellTicks(EngineeringDeviceMenu menu) {
        if (menu instanceof PidControllerMenu) return 30;
        if (menu instanceof SignalConditionerMenu) return 4;
        if (menu instanceof SignalProcessorMenu processor) {
            return processor.kind() == SignalProcessorMenu.KIND_FILTER ? 10 : 6;
        }
        if (menu instanceof QuartzTimingMenu) return 18;
        if (menu instanceof AmethystSystemMenu) return 10;
        if (menu instanceof PneumaticSystemMenu pneumatic) {
            return switch (pneumatic.kind()) {
                case PneumaticSystemMenu.KIND_COMPRESSOR -> 24;
                case PneumaticSystemMenu.KIND_REGULATOR -> 14;
                case PneumaticSystemMenu.KIND_PROPORTIONAL -> 18;
                case PneumaticSystemMenu.KIND_CYLINDER -> 24;
                default -> 10;
            };
        }
        if (menu instanceof ReliabilitySystemMenu reliability
                && reliability.kind() == ReliabilitySystemMenu.KIND_SERVO) return 18;
        if (menu instanceof MagneticSystemMenu magnetic
                && magnetic.kind() == MagneticSystemMenu.KIND_COIL) return 8;
        if (menu instanceof SignalAnalyzerMenu
                || menu instanceof OscilloscopeMenu
                || menu instanceof LogicAnalyzerMenu) return 4;
        return 8;
    }

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
                     PneumaticSystemMenu.KIND_CYLINDER ->
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
        if (menu instanceof IndustrialBufferMenu
                || menu instanceof OperationsMonitorMenu
                || menu instanceof WorkcellControllerMenu) {
            return device("MODEL", "Operations block: show server-owned state, evidence and decisions without turning it into a desktop simulator.");
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
                    block("INFO", "Passive/topology-first block: world wiring and current port evidence are the main interface.");

            case UniversalFieldDeviceMenu.CONFIG_SAMPLE_HOLD,
                 UniversalFieldDeviceMenu.CONFIG_PWM,
                 UniversalFieldDeviceMenu.CONFIG_QUARTZ_OSCILLATOR,
                 UniversalFieldDeviceMenu.CONFIG_QUARTZ_LAPIS_SAMPLER,
                 UniversalFieldDeviceMenu.CONFIG_COPPER_CAPACITOR,
                 UniversalFieldDeviceMenu.CONFIG_LAPIS_LOW_PASS,
                 UniversalFieldDeviceMenu.CONFIG_QUARTZ_PHASE_DELAY,
                 UniversalFieldDeviceMenu.CONFIG_THERMAL_HEATER,
                 UniversalFieldDeviceMenu.CONFIG_THERMAL_MASS,
                 UniversalFieldDeviceMenu.CONFIG_THERMAL_RADIATOR ->
                    lab("LAB", "Discrete-time behavior has meaningful server-owned timing or capture dynamics.");

            case UniversalFieldDeviceMenu.CONFIG_LAPIS_TRANSDUCER,
                 UniversalFieldDeviceMenu.CONFIG_LAPIS_RANGE,
                 UniversalFieldDeviceMenu.CONFIG_MOLECULAR_RECEIVER,
                 UniversalFieldDeviceMenu.CONFIG_ALARM,
                 UniversalFieldDeviceMenu.CONFIG_CALIBRATION,
                 UniversalFieldDeviceMenu.CONFIG_FAULT_INJECTOR,
                 UniversalFieldDeviceMenu.CONFIG_SEQUENCE_CONTROLLER,
                 UniversalFieldDeviceMenu.CONFIG_SAFETY_INTERLOCK,
                 UniversalFieldDeviceMenu.CONFIG_TOPOLOGY_DEBUGGER,
                 UniversalFieldDeviceMenu.CONFIG_LIGHT_SENSOR,
                 UniversalFieldDeviceMenu.CONFIG_TANK_LEVEL,
                 UniversalFieldDeviceMenu.CONFIG_ENTITY_DENSITY,
                 UniversalFieldDeviceMenu.CONFIG_MAGNETIC_FIELD,
                 UniversalFieldDeviceMenu.CONFIG_ELECTROMAGNET,
                 UniversalFieldDeviceMenu.CONFIG_IRON_CORE,
                 UniversalFieldDeviceMenu.CONFIG_SIGNAL_PROBE,
                 UniversalFieldDeviceMenu.CONFIG_REFERENCE_SOURCE,
                 UniversalFieldDeviceMenu.CONFIG_CABLE_TERMINAL,
                 UniversalFieldDeviceMenu.CONFIG_FAULT_LATCH,
                 UniversalFieldDeviceMenu.CONFIG_ANALOG_INDICATOR,
                 UniversalFieldDeviceMenu.CONFIG_BYTE_ENCODER,
                 UniversalFieldDeviceMenu.CONFIG_BYTE_DECODER,
                 UniversalFieldDeviceMenu.CONFIG_SERIALIZER,
                 UniversalFieldDeviceMenu.CONFIG_DESERIALIZER,
                 UniversalFieldDeviceMenu.CONFIG_REGENERATOR,
                 UniversalFieldDeviceMenu.CONFIG_DIFF_DRIVER,
                 UniversalFieldDeviceMenu.CONFIG_DIFF_RECEIVER,
                 UniversalFieldDeviceMenu.CONFIG_WATCHDOG,
                 UniversalFieldDeviceMenu.CONFIG_SINGLE_RELAY,
                 UniversalFieldDeviceMenu.CONFIG_REDUNDANT_VOTER,
                 UniversalFieldDeviceMenu.CONFIG_SIGNAL_AMPLIFIER,
                 UniversalFieldDeviceMenu.CONFIG_SIGNAL_SELECTOR,
                 UniversalFieldDeviceMenu.CONFIG_ANALOG_COMPARATOR,
                 UniversalFieldDeviceMenu.CONFIG_COPPER_SOURCE,
                 UniversalFieldDeviceMenu.CONFIG_COPPER_SERIES_RESISTOR,
                 UniversalFieldDeviceMenu.CONFIG_COPPER_LOAD,
                 UniversalFieldDeviceMenu.CONFIG_COPPER_FUSE ->
                    device("MODEL", "Active/configurable block: expose owned settings, readback and evidence without desktop-workspace overhead.");

            default -> device("MODEL", "UNREVIEWED CONFIG KIND • fail-safe device UI; CI requires every registered kind to be classified explicitly.");
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
                 FieldDeviceMenu.KIND_QUARTZ_DIVIDER,
                 FieldDeviceMenu.KIND_QUARTZ_STABILITY,
                 FieldDeviceMenu.KIND_QUARTZ_OSCILLATOR,
                 FieldDeviceMenu.KIND_AMETHYST_RESONATOR,
                 FieldDeviceMenu.KIND_AMETHYST_FILTER,
                 FieldDeviceMenu.KIND_AMETHYST_TUNED,
                 FieldDeviceMenu.KIND_AMETHYST_SPECTRUM,
                 FieldDeviceMenu.KIND_SERVO_ACTUATOR,
                 FieldDeviceMenu.KIND_AIR_COMPRESSOR,
                 FieldDeviceMenu.KIND_PRESSURE_REGULATOR,
                 FieldDeviceMenu.KIND_PNEUMATIC_PROPORTIONAL_VALVE,
                 FieldDeviceMenu.KIND_PNEUMATIC_CYLINDER,
                 FieldDeviceMenu.KIND_ELECTROMAGNET,
                 FieldDeviceMenu.KIND_INDUCTION_COIL ->
                    lab("LAB", "Time/frequency/feedback/actuator behavior is meaningful to measure, not merely configure.");

            case FieldDeviceMenu.KIND_PROBE,
                 FieldDeviceMenu.KIND_REFERENCE,
                 FieldDeviceMenu.KIND_ENCODER,
                 FieldDeviceMenu.KIND_DECODER,
                 FieldDeviceMenu.KIND_SERIALIZER,
                 FieldDeviceMenu.KIND_DESERIALIZER,
                 FieldDeviceMenu.KIND_DIGITAL_REGENERATOR,
                 FieldDeviceMenu.KIND_DIFFERENTIAL_DRIVER,
                 FieldDeviceMenu.KIND_DIFFERENTIAL_RECEIVER,
                 FieldDeviceMenu.KIND_RADIO_TRANSMITTER,
                 FieldDeviceMenu.KIND_RADIO_RECEIVER,
                 FieldDeviceMenu.KIND_FREE_OPTICAL_TRANSMITTER,
                 FieldDeviceMenu.KIND_FREE_OPTICAL_RECEIVER,
                 FieldDeviceMenu.KIND_MECHANICAL_EXCITER,
                 FieldDeviceMenu.KIND_MECHANICAL_RECEIVER,
                 FieldDeviceMenu.KIND_HONEY_DAMPER,
                 FieldDeviceMenu.KIND_SCULK_INTERFACE,
                 FieldDeviceMenu.KIND_HYDRO_EXCITER,
                 FieldDeviceMenu.KIND_HYDRO_RECEIVER,
                 FieldDeviceMenu.KIND_THERMAL_ENCODER,
                 FieldDeviceMenu.KIND_THERMAL_RECEIVER,
                 FieldDeviceMenu.KIND_WATCHDOG,
                 FieldDeviceMenu.KIND_SERVO_POSITION_SENSOR,
                 FieldDeviceMenu.KIND_REDUNDANT_VOTER,
                 FieldDeviceMenu.KIND_FAULT_LATCH,
                 FieldDeviceMenu.KIND_OPERATIONS_MONITOR,
                 FieldDeviceMenu.KIND_AIR_RESERVOIR,
                 FieldDeviceMenu.KIND_PNEUMATIC_RECEIVER,
                 FieldDeviceMenu.KIND_PNEUMATIC_VALVE,
                 FieldDeviceMenu.KIND_PNEUMATIC_CHECK_VALVE,
                 FieldDeviceMenu.KIND_PNEUMATIC_FLOW_METER,
                 FieldDeviceMenu.KIND_SIGNAL_TAP,
                 FieldDeviceMenu.KIND_RANGE_SENSOR,
                 FieldDeviceMenu.KIND_LAPIS_SOURCE,
                 FieldDeviceMenu.KIND_PNEUMATIC_RELIEF_VALVE,
                 FieldDeviceMenu.KIND_PERMANENT_MAGNET,
                 FieldDeviceMenu.KIND_MAGNETIC_FIELD_SENSOR,
                 FieldDeviceMenu.KIND_MAGNETIC_GRADIENT_METER,
                 FieldDeviceMenu.KIND_OPTICAL_EMITTER,
                 FieldDeviceMenu.KIND_OPTICAL_RECEIVER,
                 FieldDeviceMenu.KIND_OPTICAL_POWER_METER,
                 FieldDeviceMenu.KIND_OPTICAL_SPLITTER,
                 FieldDeviceMenu.KIND_OPTICAL_CHANNEL_FILTER,
                 FieldDeviceMenu.KIND_OPTICAL_ATTENUATOR ->
                    device("MODEL", "Active/configurable/observer block: keep settings and evidence close to the in-world component.");

            default -> device("MODEL", "UNREVIEWED FIELD KIND • fail-safe device UI; CI requires every registered kind to be classified explicitly.");
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
        if (menu instanceof FieldDeviceMenu field) return field(field);
        if (menu instanceof PidControllerMenu pid) return card(
                "FEEDBACK CONTROL",
                "e = SP - PV;  u = bias + Kp·e + integral/KiDiv - Kd·D(PV)",
                "Kp=" + pid.proportionalGain()
                        + " • KiDiv=" + pid.integralDivisor()
                        + " • Kd=" + pid.derivativeGain()
                        + " • Dsmooth=" + pid.derivativeSmoothing()
                        + " • slew=" + pid.riseLimit() + "/" + pid.fallLimit(),
                "compare target to measurement -> deadband -> P/I/D -> clamp -> actuator slew -> observe response",
                "Preset selection changes a transparent six-parameter coefficient bank; the server remains authoritative.");
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
        if (menu instanceof IndustrialBufferMenu) return card(
                "INDUSTRIAL BUFFER",
                "WIP pressure = used units / capacity; lot identity remains server-owned",
                "read-only capacity • used WIP • explicit workcell bindings",
                "retain lots -> expose bounded fullness/permit evidence -> never encode lot identity into analog Redstone",
                "This block is an in-world operations buffer, not an inventory spreadsheet.");
        if (menu instanceof WorkcellControllerMenu) return card(
                "WORKCELL ADMISSION",
                "permit = valid resources AND valid capacity evidence AND no blocking fault",
                "explicit resource/buffer bindings • read-only admission evidence",
                "resolve bindings -> validate resources/capacity -> publish permit/hold reason",
                "Scheduling and lot ownership stay in persistent Operations state, not the client HMI.");
        if (menu instanceof OperationsMonitorMenu) return card(
                "OPERATIONS OBSERVER",
                "KPIs derive from observed RUN / QUEUE / cycle evidence over bounded windows",
                "observer-only • no plant control authority",
                "observe plant evidence -> derive throughput/WIP/downtime -> classify constraints/incidents",
                "The monitor explains the plant; it does not drive or reschedule it.");
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

    public static LabProfile labProfile(EngineeringDeviceMenu menu) {
        if (uiPolicy(menu).tier() != UiTier.LAB) return null;

        if (menu instanceof PidControllerMenu pid) {
            return lab(
                    "How does controller tuning change closed-loop response?",
                    "tuning preset / setpoint disturbance",
                    "PV tracking and control effort",
                    metric("Error", Integer.toString(pid.error()), "SP - PV"),
                    metric("Settling", pid.settlingTicks() < 0 ? "—" : pid.settlingTicks() + "t", "Measured commissioning settling time."),
                    metric("Overshoot", Integer.toString(pid.overshoot()), "Measured peak overshoot above target."),
                    "Saturation events " + pid.saturationEvents() + " • rise90 "
                            + (pid.rise90Ticks() < 0 ? "—" : pid.rise90Ticks() + "t")
            );
        }

        if (menu instanceof SignalConditionerMenu conditioner) {
            return lab(
                    "How does the transfer setting change a live signal?",
                    "mode parameter",
                    "conditioned output",
                    metric("Input", conditioner.input() + "/15", "Server-synchronized input."),
                    metric("Output", conditioner.output() + "/15", "Server-computed transfer output."),
                    metric("Limit events", Integer.toString(conditioner.limitingEpisodes()), "Count of limiting/clipping episodes."),
                    conditioner.limiting() ? "Currently limiting" : "Not currently limiting"
            );
        }

        if (menu instanceof SignalProcessorMenu processor) {
            return switch (processor.kind()) {
                case SignalProcessorMenu.KIND_FILTER -> lab(
                        "How do rise/fall limits change tracking and settling?",
                        "rise/fall rate",
                        "output lag and settling",
                        metric("Output", processor.output() + "/15", "Filtered live output."),
                        metric("Lag", Integer.toString(processor.runtimeA()), "Current input-output lag."),
                        metric("Settle ETA", processor.runtimeC() + "t", "Server-estimated remaining settling time."),
                        processor.runtimeB() == 1 ? "SETTLED" : "SETTLING"
                );
                case SignalProcessorMenu.KIND_EDGE -> lab(
                        "How does edge mode change accepted event evidence?",
                        "edge mode",
                        "detected edge events",
                        metric("Output", processor.output() + "/15", "Current event output."),
                        metric("Edges", Integer.toString(processor.runtimeB()), "Detected genuine edges."),
                        metric("Edge age", processor.runtimeC() < 0 ? "NONE" : processor.runtimeC() + "t", "Age of last accepted edge."),
                        "Edge detection uses retained baseline; UI inspection never creates an edge."
                );
                case SignalProcessorMenu.KIND_PULSE -> lab(
                        "How do pulse width, threshold and hysteresis change trigger behavior?",
                        "pulse / threshold / hysteresis",
                        "accepted versus suppressed triggers",
                        metric("Accepted", Integer.toString(processor.runtimeB()), "Accepted trigger count."),
                        metric("Suppressed", Integer.toString(processor.runtimeC()), "Rejected/suppressed trigger count."),
                        metric("Pulse left", processor.runtimeA() + "t", "Remaining authoritative output pulse time."),
                        "Last trigger age " + (processor.runtimeD() < 0 ? "NONE" : processor.runtimeD() + "t")
                );
                default -> null;
            };
        }

        if (menu instanceof QuartzTimingMenu quartz) {
            return switch (quartz.kind()) {
                case QuartzTimingMenu.KIND_OSCILLATOR -> lab(
                        "How does the configured period change the realized server clock?",
                        "period index",
                        "realized clock period",
                        metric("State", quartz.primary() == 1 ? "HIGH" : "LOW", "Current oscillator state."),
                        metric("Period", quartz.secondary() + "t", "Realized nominal period."),
                        metric("Index", Integer.toString(quartz.tertiary()), "Configured period index."),
                        "Clock source is server-owned; opening the GUI never advances phase."
                );
                case QuartzTimingMenu.KIND_DIVIDER -> lab(
                        "Does the divider preserve the expected timing ratio?",
                        "division ratio",
                        "output period",
                        metric("Period in", quartz.primary() + "t", "Measured upstream period."),
                        metric("Period out", quartz.secondary() + "t", "Measured divided period."),
                        metric("Edges", Integer.toString(quartz.runtimeA()), "Counted genuine input edges."),
                        quartz.runtimeB() == 1 ? "Divider initialized" : "Waiting for genuine edge baseline"
                );
                case QuartzTimingMenu.KIND_STABILITY -> lab(
                        "How stable is the measured clock against its reference?",
                        "incoming edge timing",
                        "measured period error",
                        metric("Measured", quartz.primary() + "t", "Current/retained measured period."),
                        metric("Error", quartz.secondary() + "t", "Deviation from nominal/reference."),
                        metric("Current", quartz.runtimeC() == 1 ? "YES" : "NO", "Whether current timing evidence is fresh."),
                        "Two genuine rising edges are required before timing evidence is accepted."
                );
                default -> null;
            };
        }

        if (menu instanceof AmethystSystemMenu amethyst) {
            return switch (amethyst.kind()) {
                case AmethystSystemMenu.KIND_SOURCE -> lab(
                        "How do drive frequency and peak amplitude shape the emitted resonance state?",
                        "drive frequency / peak amplitude",
                        "active resonant amplitude",
                        metric("Frequency", Integer.toString(amethyst.primary()), "Drive frequency index."),
                        metric("Amplitude", amethyst.secondary() + "/15", "Configured peak amplitude."),
                        metric("Active", amethyst.stateFlag() == 1 ? "YES" : "NO", "Whether the source is currently resonating."),
                        "Source experiment is local excitation; downstream response belongs to connected resonators/filters."
                );
                case AmethystSystemMenu.KIND_FILTER -> lab(
                        "Which frequencies pass the configured filter?",
                        "target frequency",
                        "filtered output amplitude",
                        metric("Input f", Integer.toString(amethyst.primary()), "Observed input frequency."),
                        metric("Target f", Integer.toString(amethyst.tertiary()), "Configured pass frequency."),
                        metric("Aout", amethyst.auxiliary() + "/15", "Server-computed filtered output amplitude."),
                        amethyst.stateFlag() == 1 ? "MATCHED" : "OUT OF BAND"
                );
                case AmethystSystemMenu.KIND_TUNED -> lab(
                        "Where is the resonant peak and how selective is it?",
                        "natural frequency / Q",
                        "resonant output amplitude",
                        metric("Input f", Integer.toString(amethyst.primary()), "Observed drive frequency."),
                        metric("Natural f", Integer.toString(amethyst.tertiary()), "Configured natural frequency."),
                        metric("Aout", amethyst.extraB() + "/15", "Live tuned-resonator response amplitude."),
                        "Modeled bandwidth index ±" + amethyst.extraA()
                );
                case AmethystSystemMenu.KIND_SPECTRUM -> lab(
                        "What frequency evidence exists on this local resonance network?",
                        "observed sources",
                        "frequency coverage/conflicts",
                        metric("Samples", Integer.toString(amethyst.auxiliary()), "Observed spectrum samples."),
                        metric("Conflicts", Integer.toString(amethyst.extraA()), "Overlapping/conflicting evidence."),
                        metric("Coverage", amethyst.extraB() + "/" + amethyst.stateFlag(), "Bounded spectrum coverage evidence."),
                        "Observer-only spectrum view; no source is created by the UI."
                );
                default -> null;
            };
        }

        if (menu instanceof PneumaticSystemMenu pneumatic) {
            return switch (pneumatic.kind()) {
                case PneumaticSystemMenu.KIND_COMPRESSOR -> lab(
                        "How quickly does the compressor track commanded pressure?",
                        "command / response profile",
                        "actual pressure",
                        metric("Target P", pneumatic.secondary() + "/100", "Command-derived pressure target."),
                        metric("Actual P", pneumatic.tertiary() + "/100", "Finite-response compressor pressure."),
                        metric("Track err", Integer.toString(pneumatic.compressorTrackingError()), "Target minus actual pressure."),
                        "Runtime " + pneumatic.compressorRunTicks() + "t"
                );
                case PneumaticSystemMenu.KIND_REGULATOR -> lab(
                        "How does setpoint interact with upstream pressure and downstream loss?",
                        "pressure setpoint",
                        "regulated downstream pressure",
                        metric("Upstream", pneumatic.upstreamPressure() + "/100", "Solved upstream pressure."),
                        metric("Downstream", pneumatic.downstreamPressure() + "/100", "Solved downstream pressure."),
                        metric("Setpoint", pneumatic.secondary() + "/100", "Configured regulator setpoint."),
                        "Pressure is read from the authoritative network solve."
                );
                case PneumaticSystemMenu.KIND_PROPORTIONAL -> lab(
                        "How closely does valve opening follow its command?",
                        "command / response profile",
                        "actual opening",
                        metric("Command", pneumatic.proportionalCommand() + "/15", "Requested valve opening."),
                        metric("Opening", pneumatic.tertiary() + "/15", "Actual finite-response opening."),
                        metric("Track err", Integer.toString(pneumatic.proportionalTrackingError()), "Command minus actual opening."),
                        "Travel " + pneumatic.proportionalTravel() + " • reversals " + pneumatic.proportionalReversals()
                );
                case PneumaticSystemMenu.KIND_CYLINDER -> lab(
                        "How do supply pressure and path losses affect actuator motion?",
                        "supply / target position",
                        "actual cylinder position",
                        metric("Position", pneumatic.secondary() + "/15", "Current actuator position."),
                        metric("Error", Integer.toString(pneumatic.cylinderError()), "Target minus actual position."),
                        metric("Path loss", Integer.toString(pneumatic.cylinderObservedLoss()), "Line + restriction loss on winning path."),
                        "Velocity " + pneumatic.cylinderVelocity() + " • stall " + pneumatic.cylinderStallTicks()
                                + "t • samples " + pneumatic.cylinderSamples()
                );
                case PneumaticSystemMenu.KIND_FLOW_METER -> lab(
                        "What flow proxy follows the measured pressure drop?",
                        "network pressure state",
                        "flow proxy",
                        metric("Flow", Integer.toString(pneumatic.primary()), "Server-computed flow proxy."),
                        metric("dP", Integer.toString(pneumatic.secondary()), "Measured pressure drop."),
                        metric("Pin", pneumatic.tertiary() + "/100", "Upstream pressure."),
                        "Commissioning " + pneumatic.commissioningStatus().name()
                );
                default -> null;
            };
        }

        if (menu instanceof ReliabilitySystemMenu reliability
                && reliability.kind() == ReliabilitySystemMenu.KIND_SERVO) {
            return lab(
                    "How do slew and load profiles affect real actuator tracking?",
                    "slew / load profile / command",
                    "servo position and error",
                    metric("Position", reliability.primary() + "/15", "Current servo position."),
                    metric("Command", reliability.secondary() + "/15", "Requested position."),
                    metric("Error", Integer.toString(reliability.auxiliary()), "Command minus actual position."),
                    "Velocity " + reliability.tertiary() + " • load profile " + reliability.secondaryParameterIndex()
                            + " • soft-limit hits " + reliability.extraB()
            );
        }

        EngineeringWorkbenchCatalog.ResponseSpec response = response(menu);
        List<ParameterSpec> parameters = parameters(menu);
        if (response != null) {
            ParameterSpec p = parameters.stream()
                    .filter(parameter -> parameter.control() == ParameterControl.EXPERIMENT)
                    .filter(ParameterSpec::sweepMeaningful)
                    .findFirst()
                    .orElse(null);
            if (p != null) {
                return lab(
                        "How does this experiment variable affect the synchronized response?",
                        p.label(),
                        response.label(),
                        metric("Parameter", Integer.toString(p.current()), "Current server-owned experiment setting."),
                        metric("Response", response.value() + (response.unit().isBlank() ? "" : " " + response.unit()),
                                response.detail()),
                        metric("Evidence", response.usable() ? "VALID" : "NOT CURRENT", "Response validity gate."),
                        "Generic LAB framing is allowed only for an explicitly declared experiment variable with a real response."
                );
            }
        }
        return null;
    }

    private static LabProfile lab(
            String question, String independent, String dependent,
            LabMetric a, LabMetric b, LabMetric c, String note
    ) {
        return new LabProfile(question, independent, dependent, List.of(a, b, c), note);
    }

    private static LabMetric metric(String label, String value, String detail) {
        return new LabMetric(label, value, detail);
    }

    public static List<ParameterSpec> parameters(EngineeringDeviceMenu menu) {
        if (menu instanceof UniversalFieldDeviceMenu universal) {
            java.util.ArrayList<ParameterSpec> specs = new java.util.ArrayList<>(2);
            if (universal.editPrimaryAvailable()) {
                boolean sweep = switch (universal.configKind()) {
                    case UniversalFieldDeviceMenu.CONFIG_ANALOG_COMPARATOR,
                         UniversalFieldDeviceMenu.CONFIG_MAGNETIC_FIELD,
                         UniversalFieldDeviceMenu.CONFIG_COPPER_CAPACITOR,
                         UniversalFieldDeviceMenu.CONFIG_LAPIS_LOW_PASS,
                         UniversalFieldDeviceMenu.CONFIG_QUARTZ_PHASE_DELAY -> true;
                    default -> false;
                };
                boolean numeric = switch (universal.configKind()) {
                    case UniversalFieldDeviceMenu.CONFIG_ANALOG_COMPARATOR,
                         UniversalFieldDeviceMenu.CONFIG_DIFF_DRIVER,
                         UniversalFieldDeviceMenu.CONFIG_REGENERATOR,
                         UniversalFieldDeviceMenu.CONFIG_FAULT_LATCH,
                         UniversalFieldDeviceMenu.CONFIG_REFERENCE_SOURCE,
                         UniversalFieldDeviceMenu.CONFIG_MAGNETIC_FIELD,
                         UniversalFieldDeviceMenu.CONFIG_MOLECULAR_RECEIVER,
                         UniversalFieldDeviceMenu.CONFIG_PWM,
                         UniversalFieldDeviceMenu.CONFIG_COPPER_SOURCE,
                         UniversalFieldDeviceMenu.CONFIG_COPPER_SERIES_RESISTOR,
                         UniversalFieldDeviceMenu.CONFIG_COPPER_LOAD,
                         UniversalFieldDeviceMenu.CONFIG_COPPER_CAPACITOR,
                         UniversalFieldDeviceMenu.CONFIG_COPPER_FUSE,
                         UniversalFieldDeviceMenu.CONFIG_LAPIS_LOW_PASS,
                         UniversalFieldDeviceMenu.CONFIG_QUARTZ_PHASE_DELAY,
                         UniversalFieldDeviceMenu.CONFIG_THERMAL_HEATER,
                         UniversalFieldDeviceMenu.CONFIG_THERMAL_MASS,
                         UniversalFieldDeviceMenu.CONFIG_THERMAL_RADIATOR -> true;
                    default -> false;
                };
                boolean fractions = switch (universal.configKind()) {
                    case UniversalFieldDeviceMenu.CONFIG_ANALOG_COMPARATOR,
                         UniversalFieldDeviceMenu.CONFIG_DIFF_DRIVER,
                         UniversalFieldDeviceMenu.CONFIG_REGENERATOR,
                         UniversalFieldDeviceMenu.CONFIG_REFERENCE_SOURCE,
                         UniversalFieldDeviceMenu.CONFIG_MOLECULAR_RECEIVER,
                         UniversalFieldDeviceMenu.CONFIG_COPPER_SOURCE,
                         UniversalFieldDeviceMenu.CONFIG_COPPER_SERIES_RESISTOR,
                         UniversalFieldDeviceMenu.CONFIG_COPPER_LOAD -> true;
                    default -> false;
                };
                ParameterControl control = sweep ? ParameterControl.EXPERIMENT
                        : numeric ? ParameterControl.RANGE : ParameterControl.CHOICE;
                specs.add(new ParameterSpec(
                        universalPrimaryLabel(universal.configKind()), universal.editPrimaryValue(),
                        universal.editPrimaryMin(), universal.editPrimaryMax(),
                        UniversalFieldDeviceMenu.BUTTON_CONFIG_PRIMARY_PREVIOUS,
                        UniversalFieldDeviceMenu.BUTTON_CONFIG_PRIMARY_NEXT,
                        universalPrimaryUnit(universal.configKind()),
                        universalPrimaryDetail(universal.configKind()), control, fractions, sweep));
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
                        scaledSpec("Reference level", field.primary(), 0, 15,
                                FieldDeviceMenu.BUTTON_PRIMARY_DECREASE, FieldDeviceMenu.BUTTON_PRIMARY_INCREASE,
                                "/15", "Configured Redstone reference level.")
                );
                case FieldDeviceMenu.KIND_DIGITAL_REGENERATOR -> List.of(
                        spec("Quality threshold", field.tertiary(), 0, 2,
                                FieldDeviceMenu.BUTTON_PRIMARY_DECREASE, FieldDeviceMenu.BUTTON_PRIMARY_INCREASE,
                                "mode", "Digital regeneration threshold profile.")
                );
                case FieldDeviceMenu.KIND_PRESSURE_REGULATOR -> List.of(
                        experimentSpec("Pressure setpoint", field.tertiary(), 1, 4,
                                FieldDeviceMenu.BUTTON_PRIMARY_DECREASE, FieldDeviceMenu.BUTTON_PRIMARY_INCREASE,
                                "index", "Raw 1..4 setpoint index; physical setpoint is derived server-side.")
                );
                case FieldDeviceMenu.KIND_PNEUMATIC_RELIEF_VALVE -> List.of(
                        scaledSpec("Relief setpoint", Math.max(1, field.tertiary() / 25), 1, 4,
                                FieldDeviceMenu.BUTTON_PRIMARY_DECREASE, FieldDeviceMenu.BUTTON_PRIMARY_INCREASE,
                                "x25 pressure", "Protective vent threshold; kept as a configuration control rather than an automated sweep.")
                );
                case FieldDeviceMenu.KIND_PERMANENT_MAGNET -> List.of(
                        scaledSpec("Magnet strength", field.primary(), 1, 15,
                                FieldDeviceMenu.BUTTON_PRIMARY_DECREASE, FieldDeviceMenu.BUTTON_PRIMARY_INCREASE,
                                "/15", "Permanent magnetic source strength.")
                );
                case FieldDeviceMenu.KIND_INDUCTION_COIL -> List.of(
                        sweepSpec("Coil turns index", field.tertiary(), 1, 4,
                                FieldDeviceMenu.BUTTON_PRIMARY_DECREASE, FieldDeviceMenu.BUTTON_PRIMARY_INCREASE,
                                "index", "Discrete turns multiplier used by the induction model.")
                );
                case FieldDeviceMenu.KIND_OPTICAL_EMITTER -> List.of(
                        scaledSpec("Optical intensity", field.primary(), 0, 15,
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
                case FieldDeviceMenu.KIND_MECHANICAL_EXCITER -> List.of(
                        rangeSpec("Drive frequency", field.secondary(), 1, 15,
                                FieldDeviceMenu.BUTTON_PRIMARY_DECREASE, FieldDeviceMenu.BUTTON_PRIMARY_INCREASE,
                                "frequency index", "Mechanical excitation frequency; authoritative source dynamics remain in the block.")
                );
                case FieldDeviceMenu.KIND_HYDRO_EXCITER -> List.of(
                        rangeSpec("Acoustic frequency", field.secondary(), 1, 15,
                                FieldDeviceMenu.BUTTON_PRIMARY_DECREASE, FieldDeviceMenu.BUTTON_PRIMARY_INCREASE,
                                "frequency index", "Hydroacoustic source frequency propagated through the physical medium.")
                );
                case FieldDeviceMenu.KIND_LAPIS_SOURCE -> List.of(
                        scaledSpec("Precision source value", field.primary(), 0, 100,
                                FieldDeviceMenu.BUTTON_PRIMARY_DECREASE, FieldDeviceMenu.BUTTON_PRIMARY_INCREASE,
                                "0.01", "Exact 0.00..1.00 Lapis precision source setting; valid zero remains real evidence.")
                );
                default -> List.of();
            };
        }
        if (menu instanceof SignalAnalyzerMenu analyzer) {
            return List.of(rangeSpec("Calibration offset", analyzer.calibrationOffset(), -2, 2,
                    SignalAnalyzerMenu.BUTTON_CALIBRATION_DECREASE, SignalAnalyzerMenu.BUTTON_CALIBRATION_INCREASE,
                    "levels", "Display/output calibration offset; raw captured evidence remains distinct."));
        }
        if (menu instanceof LogicAnalyzerMenu analyzer) {
            return List.of(experimentSpec("Logic threshold", analyzer.threshold(), 1, 15,
                    LogicAnalyzerMenu.BUTTON_THRESHOLD_DECREASE, LogicAnalyzerMenu.BUTTON_THRESHOLD_INCREASE,
                    "/15", "Threshold used to classify captured channel samples as LOW/HIGH."));
        }
        if (menu instanceof PidControllerMenu pid) {
            return List.of(
                    spec("Tuning preset", pid.tuning(), 0, 3,
                            PidControllerMenu.BUTTON_TUNING_PREVIOUS, PidControllerMenu.BUTTON_TUNING_NEXT,
                            "preset", "Preset is a durable baseline. Selecting another preset clears the custom coefficient overrides."),
                    rangeSpec("Kp", pid.proportionalGain(), 0, 8,
                            PidControllerMenu.BUTTON_KP_DECREASE, PidControllerMenu.BUTTON_KP_INCREASE,
                            "gain", "Proportional gain applied to control error. Individual edits create persistent CUSTOM tuning."),
                    rangeSpec("Ki divisor", pid.integralDivisor(), 0, 64,
                            PidControllerMenu.BUTTON_KI_DECREASE, PidControllerMenu.BUTTON_KI_INCREASE,
                            "divisor", "Integral term = accumulated error / divisor; 0 disables the integral contribution."),
                    rangeSpec("Kd", pid.derivativeGain(), 0, 8,
                            PidControllerMenu.BUTTON_KD_DECREASE, PidControllerMenu.BUTTON_KD_INCREASE,
                            "gain", "Derivative-on-measurement gain; larger values oppose faster measured PV motion."),
                    rangeSpec("D smoothing", pid.derivativeSmoothing(), 1, 8,
                            PidControllerMenu.BUTTON_D_SMOOTH_DECREASE, PidControllerMenu.BUTTON_D_SMOOTH_INCREASE,
                            "samples", "Smoothing strength used by the measured-PV derivative path."),
                    scaledSpec("Rise slew", pid.riseLimit(), 1, 15,
                            PidControllerMenu.BUTTON_RISE_DECREASE, PidControllerMenu.BUTTON_RISE_INCREASE,
                            "levels/2t", "Maximum upward actuator-command change per 2-tick control cycle."),
                    scaledSpec("Fall slew", pid.fallLimit(), 1, 15,
                            PidControllerMenu.BUTTON_FALL_DECREASE, PidControllerMenu.BUTTON_FALL_INCREASE,
                            "levels/2t", "Maximum downward actuator-command change per 2-tick control cycle.")
            );
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
                                "levels", "Re-arm separation below the trigger threshold."),
                        spec("Retrigger mode", processor.modeFlag() ? 1 : 0, 0, 1,
                                SignalProcessorMenu.BUTTON_TOGGLE_RETRIGGER, SignalProcessorMenu.BUTTON_TOGGLE_RETRIGGER,
                                "mode", "0=one-shot holdoff, 1=retriggerable; categorical toggle, never swept.")
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
            if (quartz.kind() == QuartzTimingMenu.KIND_DIVIDER) {
                return List.of(spec("Division ratio", quartz.tertiary(), 1, 16,
                        QuartzTimingMenu.BUTTON_PARAMETER_PREVIOUS, QuartzTimingMenu.BUTTON_PARAMETER_NEXT,
                        "ratio", "Discrete server-owned divider ratio; use Prev/Next because only implemented ratios are valid."));
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
                                "x10 pressure", "Raw setting 1..10 corresponds to 10..100 pressure."),
                        spec("Diaphragm response profile", pneumatic.stateFlag(), 0, 2,
                                PneumaticSystemMenu.BUTTON_SECONDARY_PARAMETER_PREVIOUS,
                                PneumaticSystemMenu.BUTTON_SECONDARY_PARAMETER_NEXT,
                                "mode", "Finite response-rate profile; categorical, so it is selected rather than numerically swept.")
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
                case PneumaticSystemMenu.KIND_VALVE -> List.of(
                        spec("Valve state", pneumatic.stateFlag(), 0, 1,
                                PneumaticSystemMenu.BUTTON_TOGGLE, PneumaticSystemMenu.BUTTON_TOGGLE,
                                "state", "0=CLOSED, 1=OPEN; explicit manual valve state, not a numeric sweep.")
                );
                case PneumaticSystemMenu.KIND_RELIEF -> List.of(
                        scaledSpec("Relief setpoint", Math.max(1, pneumatic.tertiary() / 25), 1, 4,
                                PneumaticSystemMenu.BUTTON_PARAMETER_PREVIOUS, PneumaticSystemMenu.BUTTON_PARAMETER_NEXT,
                                "x25 pressure", "Protective threshold 25..100 pressure; exact/manual configuration only, never an automated sweep.")
                );
                default -> List.of();
            };
        }
        if (menu instanceof AmethystSystemMenu amethyst) {
            return switch (amethyst.kind()) {
                case AmethystSystemMenu.KIND_SOURCE -> List.of(
                        rangeSpec("Drive frequency", amethyst.primary(), 1, 15,
                                AmethystSystemMenu.BUTTON_PRIMARY_PREVIOUS, AmethystSystemMenu.BUTTON_PRIMARY_NEXT,
                                "frequency index", "Exact resonant drive-frequency index for the impulse source."),
                        scaledSpec("Peak amplitude", amethyst.secondary(), 1, 15,
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
        if (menu instanceof DigitalCommunicationMenu digital) {
            return switch (digital.kind()) {
                case DigitalCommunicationMenu.KIND_ENCODER -> List.of(
                        spec("Encoding mode", digital.parameter(), 0, 1,
                                DigitalCommunicationMenu.BUTTON_PARAMETER_PREVIOUS, DigitalCommunicationMenu.BUTTON_PARAMETER_NEXT,
                                "mode", "DIRECT or FULL-SCALE byte encoding; categorical representation choice.")
                );
                case DigitalCommunicationMenu.KIND_DECODER -> List.of(
                        spec("Decoding mode", digital.parameter(), 0, 1,
                                DigitalCommunicationMenu.BUTTON_PARAMETER_PREVIOUS, DigitalCommunicationMenu.BUTTON_PARAMETER_NEXT,
                                "mode", "CLAMP or FULL-SCALE byte decoding; categorical representation choice.")
                );
                case DigitalCommunicationMenu.KIND_SERIALIZER -> List.of(
                        spec("Word period", digital.parameter(), 0, 2,
                                DigitalCommunicationMenu.BUTTON_PARAMETER_PREVIOUS, DigitalCommunicationMenu.BUTTON_PARAMETER_NEXT,
                                "period profile", "Selects one implemented serializer word-period profile; actual serial timing remains server-owned.")
                );
                case DigitalCommunicationMenu.KIND_REGENERATOR -> List.of(
                        spec("Quality threshold", digital.parameter(), 0, 2,
                                DigitalCommunicationMenu.BUTTON_PARAMETER_PREVIOUS, DigitalCommunicationMenu.BUTTON_PARAMETER_NEXT,
                                "profile", "Minimum serial-medium quality profile required before regeneration.")
                );
                case DigitalCommunicationMenu.KIND_DIFF_DRIVER -> List.of(
                        spec("Logic threshold", digital.parameter(), 0, 3,
                                DigitalCommunicationMenu.BUTTON_PARAMETER_PREVIOUS, DigitalCommunicationMenu.BUTTON_PARAMETER_NEXT,
                                "profile", "Selects the implemented differential-driver decision threshold.")
                );
                default -> List.of();
            };
        }
        if (menu instanceof ReliabilitySystemMenu reliability) {
            return switch (reliability.kind()) {
                case ReliabilitySystemMenu.KIND_WATCHDOG -> List.of(
                        spec("Timeout profile", reliability.parameterIndex(), 0, 3,
                                ReliabilitySystemMenu.BUTTON_PARAMETER_PREVIOUS, ReliabilitySystemMenu.BUTTON_PARAMETER_NEXT,
                                "index", "Selects the server-owned heartbeat timeout profile.")
                );
                case ReliabilitySystemMenu.KIND_SERVO -> List.of(
                        sweepSpec("Servo slew profile", reliability.parameterIndex(), 0, 2,
                                ReliabilitySystemMenu.BUTTON_PARAMETER_PREVIOUS, ReliabilitySystemMenu.BUTTON_PARAMETER_NEXT,
                                "index", "Controls bounded actuator slew response."),
                        sweepSpec("Load / inertia profile", reliability.secondaryParameterIndex(), 0, 3,
                                ReliabilitySystemMenu.BUTTON_SECONDARY_PARAMETER_PREVIOUS,
                                ReliabilitySystemMenu.BUTTON_SECONDARY_PARAMETER_NEXT,
                                "index", "0=unloaded, 1=light, 2=medium, 3=heavy; affects finite motion response.")
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
                        scaledSpec("Optical intensity", optical.primary(), 0, 15,
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
                        scaledSpec("Magnet strength", magnetic.primary(), 1, 15,
                                MagneticSystemMenu.BUTTON_PRIMARY_PREVIOUS, MagneticSystemMenu.BUTTON_PRIMARY_NEXT,
                                "/15", "Permanent source strength.")
                );
                case MagneticSystemMenu.KIND_COIL -> List.of(
                        sweepSpec("Coil turns index", magnetic.tertiary(), 1, 4,
                                MagneticSystemMenu.BUTTON_PRIMARY_PREVIOUS, MagneticSystemMenu.BUTTON_PRIMARY_NEXT,
                                "turns index", "Induced response scales with the configured turns index.")
                );
                case MagneticSystemMenu.KIND_FIELD_SENSOR -> List.of(
                        rangeSpec("Scan radius mode", magnetic.parameterIndex(), 0, 3,
                                MagneticSystemMenu.BUTTON_PRIMARY_PREVIOUS, MagneticSystemMenu.BUTTON_PRIMARY_NEXT,
                                "mode", "Exact radius profile; current physical radius = "
                                        + MagneticFieldSensorBlock.radiusForMode(magnetic.parameterIndex()) + " blocks."),
                        rangeSpec("Sampling period mode", magnetic.secondaryParameterIndex(), 0, 2,
                                MagneticSystemMenu.BUTTON_SECONDARY_PREVIOUS, MagneticSystemMenu.BUTTON_SECONDARY_NEXT,
                                "mode", "Exact sampling profile; current physical period = "
                                        + MagneticFieldSensorBlock.samplePeriodForMode(magnetic.secondaryParameterIndex()) + " ticks.")
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
        if (menu instanceof FieldDeviceMenu field) {
            return switch (field.kind()) {
                case FieldDeviceMenu.KIND_FILTER ->
                        response("Filtered output", field.secondary(), 0, 15, "/15",
                                field.dataValid(), "Live slew-limited output.");
                case FieldDeviceMenu.KIND_QUARTZ_OSCILLATOR ->
                        response("Realized period", field.secondary(), 1, 64, "ticks",
                                field.dataValid(), "Synchronized effective period evidence.");
                case FieldDeviceMenu.KIND_PRESSURE_REGULATOR ->
                        response("Regulated pressure", field.primary(), 0, 100, "/100",
                                field.dataValid(), "Live pneumatic pressure at the regulator.");
                case FieldDeviceMenu.KIND_INDUCTION_COIL ->
                        response("Induced voltage", field.secondary(), 0, 15, "/15",
                                field.dataValid(), "Live copper-domain induced output.");
                case FieldDeviceMenu.KIND_OPTICAL_ATTENUATOR ->
                        response("Optical output", field.secondary(), 0, 15, "/15",
                                field.dataValid(), "Live attenuated optical output.");
                default -> null;
            };
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
        if (menu instanceof MagneticSystemMenu magnetic) {
            if (magnetic.kind() == MagneticSystemMenu.KIND_COIL) {
                return response("Induced voltage", magnetic.secondary(), 0, 15, "/15",
                        magnetic.quality() == PortQuality.VALID,
                        "Live induction-coil output voltage while turns index changes.");
            }
            if (magnetic.kind() == MagneticSystemMenu.KIND_FIELD_SENSOR) {
                return response("Measured field", magnetic.primary(), 0, 15, "/15",
                        magnetic.quality() == PortQuality.VALID,
                        "Live server-computed field over the configured radius/sampling profile.");
            }
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
            case UniversalFieldDeviceMenu.CONFIG_SINGLE_RELAY -> "Pickup profile";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_SOURCE -> "Source voltage";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_SERIES_RESISTOR -> "Series resistance";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_LOAD -> "Load resistance";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_CAPACITOR -> "Capacitance index";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_FUSE -> "Fuse rating";
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_LOW_PASS -> "Filter alpha";
            case UniversalFieldDeviceMenu.CONFIG_QUARTZ_PHASE_DELAY -> "Edge delay";
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_HEATER -> "Heater resistance";
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_MASS -> "Heat capacity";
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_RADIATOR -> "Cooling coefficient";
            default -> "Parameter A";
        };
    }

    private static String universalPrimaryUnit(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.CONFIG_ANALOG_COMPARATOR,
                 UniversalFieldDeviceMenu.CONFIG_DIFF_DRIVER,
                 UniversalFieldDeviceMenu.CONFIG_REGENERATOR,
                 UniversalFieldDeviceMenu.CONFIG_FAULT_LATCH,
                 UniversalFieldDeviceMenu.CONFIG_REFERENCE_SOURCE -> "level";
            case UniversalFieldDeviceMenu.CONFIG_MAGNETIC_FIELD -> "blocks";
            case UniversalFieldDeviceMenu.CONFIG_MOLECULAR_RECEIVER -> "sensitivity";
            case UniversalFieldDeviceMenu.CONFIG_PWM -> "ticks/index";
            case UniversalFieldDeviceMenu.CONFIG_SIGNAL_PROBE -> "channel";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_SOURCE -> "V-level";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_SERIES_RESISTOR,
                 UniversalFieldDeviceMenu.CONFIG_COPPER_LOAD -> "R-level";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_CAPACITOR -> "C-index";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_FUSE -> "I-rating";
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_LOW_PASS -> "alpha index";
            case UniversalFieldDeviceMenu.CONFIG_QUARTZ_PHASE_DELAY -> "ticks";
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_HEATER -> "R-index";
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_MASS -> "capacity";
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_RADIATOR -> "cooling";
            default -> "profile";
        };
    }

    private static String universalPrimaryDetail(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.CONFIG_ANALOG_COMPARATOR ->
                    "Comparator hysteresis band; exact bounded numeric configuration with a meaningful measured response.";
            case UniversalFieldDeviceMenu.CONFIG_DIFF_DRIVER ->
                    "Differential-driver logic threshold; exact server-owned numeric configuration.";
            case UniversalFieldDeviceMenu.CONFIG_REGENERATOR ->
                    "Digital regeneration acceptance threshold; exact server-owned numeric configuration.";
            case UniversalFieldDeviceMenu.CONFIG_FAULT_LATCH ->
                    "Protective trip threshold; exact manual configuration, deliberately excluded from automated sweep.";
            case UniversalFieldDeviceMenu.CONFIG_REFERENCE_SOURCE ->
                    "Reference source level; exact bounded engineering setpoint.";
            case UniversalFieldDeviceMenu.CONFIG_MAGNETIC_FIELD ->
                    "Magnetic sampling radius; spatial numeric parameter with measured field response.";
            case UniversalFieldDeviceMenu.CONFIG_MOLECULAR_RECEIVER ->
                    "Receiver sensitivity; exact bounded detection parameter.";
            case UniversalFieldDeviceMenu.CONFIG_PWM ->
                    "PWM period/index; exact discrete numeric timing configuration.";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_SOURCE ->
                    "Exact 0..15 Copper source voltage; network recomputes after every authoritative change.";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_SERIES_RESISTOR ->
                    "Exact series resistance; changing it invalidates stale Vout evidence before the next physical tick.";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_LOAD ->
                    "Exact terminal load resistance used by I=V/R and P=VI calculations.";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_CAPACITOR ->
                    "Capacitance profile controlling RC time response; stored charge is retained when C changes.";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_FUSE ->
                    "Protective current rating; thermal exposure is retained across rating changes and this parameter is never auto-swept.";
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_LOW_PASS ->
                    "Low-pass alpha profile controlling finite smoothing; retained filter history is not rewritten when alpha changes.";
            case UniversalFieldDeviceMenu.CONFIG_QUARTZ_PHASE_DELAY ->
                    "Delay applied to newly accepted rising edges; already queued edges keep their captured delay.";
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_HEATER ->
                    "Electrical heater resistance profile; temperature remains observed physical state and Copper loading is recomputed.";
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_MASS ->
                    "Thermal inertia/capacity; changing it affects future response while current temperature is retained.";
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_RADIATOR ->
                    "Passive cooling coefficient toward the ambient floor; exact bounded thermal configuration.";
            default -> "Bounded server-owned device configuration. Categorical modes remain Prev/Next rather than fake numeric sliders.";
        };
    }

    private static String universalSecondaryLabel(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.CONFIG_MAGNETIC_FIELD -> "Sampling mode";
            case UniversalFieldDeviceMenu.CONFIG_ENTITY_DENSITY -> "Aperture";
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_RANGE -> "Range index";
            case UniversalFieldDeviceMenu.CONFIG_SINGLE_RELAY -> "Timing profile";
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

    private static ParameterSpec rangeSpec(
            String label, int current, int minimum, int maximum,
            int decrementButton, int incrementButton, String unit, String detail
    ) {
        return new ParameterSpec(label, current, minimum, maximum,
                decrementButton, incrementButton, unit, detail,
                ParameterControl.RANGE, false, false);
    }

    private static ParameterSpec scaledSpec(
            String label, int current, int minimum, int maximum,
            int decrementButton, int incrementButton, String unit, String detail
    ) {
        return new ParameterSpec(label, current, minimum, maximum,
                decrementButton, incrementButton, unit, detail,
                ParameterControl.RANGE, true, false);
    }

    private static ParameterSpec sweepSpec(
            String label, int current, int minimum, int maximum,
            int decrementButton, int incrementButton, String unit, String detail
    ) {
        return new ParameterSpec(label, current, minimum, maximum,
                decrementButton, incrementButton, unit, detail,
                ParameterControl.EXPERIMENT, false, true);
    }

    private static ParameterSpec experimentSpec(
            String label, int current, int minimum, int maximum,
            int decrementButton, int incrementButton, String unit, String detail
    ) {
        return new ParameterSpec(label, current, minimum, maximum,
                decrementButton, incrementButton, unit, detail,
                ParameterControl.EXPERIMENT, true, true);
    }

    private static ModelCard field(FieldDeviceMenu menu) {
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_REDSTONE_CABLE,
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
                    card("PASSIVE INTERCONNECT",
                            "output evidence follows the connected medium/topology; no active conversion",
                            "route • medium • quality",
                            "world connection -> continuity/topology check -> propagate existing evidence",
                            "A passive block does not create signal precision, energy, timing edges, or history.");

            case FieldDeviceMenu.KIND_FILTER ->
                    card("SLEW-RATE FILTER",
                            "e = x-y; y[k+1] approaches x by bounded rise/fall rate",
                            "rise rate • fall rate",
                            "observe input -> retain physical output -> move toward input by bounded rate",
                            "Missing input evidence retains the last physical output instead of treating unknown as zero.");

            case FieldDeviceMenu.KIND_EDGE_DETECTOR ->
                    card("EDGE DETECTOR",
                            "edge[k] = transition(x[k-1], x[k], configured edge mode)",
                            "rising / falling / both",
                            "acquire baseline -> compare genuine successive states -> emit finite edge pulse",
                            "Startup or bad-evidence reacquisition establishes a baseline and must not manufacture an edge.");

            case FieldDeviceMenu.KIND_PULSE_SHAPER ->
                    card("MONOSTABLE / PULSE SHAPER",
                            "trigger when x >= threshold; re-arm below threshold-hysteresis; output HIGH for W ticks",
                            "pulse width • threshold • hysteresis • retrigger mode",
                            "classify threshold crossing -> accept trigger -> hold timed pulse -> re-arm",
                            "An accepted pulse may finish on its own timer; uncertain input cannot create a new trigger.");

            case FieldDeviceMenu.KIND_QUARTZ_OSCILLATOR ->
                    card("QUARTZ CLOCK SOURCE",
                            "clock toggles every T/2; rising edges repeat every effective period T",
                            "period index",
                            "run waveform -> latch configured period only at a real transition -> publish timing evidence",
                            "Changing the period never creates an early synthetic edge.");

            case FieldDeviceMenu.KIND_QUARTZ_DIVIDER ->
                    card("CLOCK DIVIDER",
                            "T_out = N * T_in after genuine input-edge counting",
                            "division ratio",
                            "reacquire input baseline -> count real edges -> toggle divided output",
                            "Missing timing continuity invalidates edge history; the divider does not infer missing transitions.");

            case FieldDeviceMenu.KIND_QUARTZ_STABILITY ->
                    card("TIMING STABILITY MONITOR",
                            "T_measured = t(edge_n) - t(edge_n-1)",
                            "nominal timing evidence",
                            "observe successive genuine edges -> compare intervals -> report timing error",
                            "This is a timing observer, not a hidden clock source.");

            case FieldDeviceMenu.KIND_AMETHYST_RESONATOR ->
                    card("RESONANCE SOURCE",
                            "amplitude evolves from configured excitation and finite ring-down",
                            "frequency • peak amplitude",
                            "excite -> oscillate at configured frequency -> decay over time",
                            "Frequency/amplitude are physical-domain state, separate from evidence validity.");

            case FieldDeviceMenu.KIND_AMETHYST_FILTER ->
                    card("FREQUENCY FILTER",
                            "A_out = A_in when f_in matches selected band; otherwise attenuated/rejected",
                            "target frequency",
                            "observe resonance evidence -> compare frequency -> pass/reject amplitude",
                            "A numerical retained amplitude is not accepted when frequency/topology evidence is invalid.");

            case FieldDeviceMenu.KIND_AMETHYST_TUNED ->
                    card("TUNED RESONATOR",
                            "response = F(|f-f0|, Q, input amplitude)",
                            "natural frequency f0 • Q index",
                            "receive drive -> compute bounded resonance response -> retain/ring down according to model",
                            "Sweep is appropriate only because frequency/Q changes have a measurable live amplitude response.");

            case FieldDeviceMenu.KIND_AIR_COMPRESSOR ->
                    card("AIR COMPRESSOR",
                            "P_actual[k+1] approaches P_target(command) with finite spool response",
                            "response profile • Redstone command",
                            "read command -> compute target pressure -> slew actual pressure -> feed pneumatic network",
                            "Commanded pressure and actual pressure are intentionally different states.");

            case FieldDeviceMenu.KIND_PRESSURE_REGULATOR ->
                    card("PRESSURE REGULATOR",
                            "P_out approaches bounded setpoint subject to upstream supply and network loss",
                            "pressure setpoint",
                            "observe upstream pressure -> apply regulation target -> publish downstream pressure",
                            "Setpoint is not proof that downstream pressure actually reached the requested value.");

            case FieldDeviceMenu.KIND_PNEUMATIC_PROPORTIONAL_VALVE ->
                    card("PROPORTIONAL VALVE",
                            "opening[k+1] approaches command with configured finite response",
                            "response profile • command",
                            "read control -> move valve opening -> alter pneumatic restriction -> observe downstream state",
                            "The valve exposes actual opening separately from command.");

            case FieldDeviceMenu.KIND_PNEUMATIC_CYLINDER ->
                    card("PNEUMATIC CYLINDER",
                            "target ~= round(15 P/100); position moves one step per pressure-dependent response period",
                            "supply pressure • path loss",
                            "solve inlet pressure -> derive equilibrium target -> move finite linear position -> output feedback",
                            "This is a lumped Minecraft-scale actuator model, not CFD.");

            case FieldDeviceMenu.KIND_INDUCTION_COIL ->
                    card("INDUCTION COIL",
                            "|emf| proportional to N * |delta Phi / delta t|",
                            "turns index N",
                            "sample magnetic field -> establish derivative baseline -> measure flux change -> drive copper voltage",
                            "A constant valid field produces valid zero EMF; missing magnetic evidence is not zero field.");

            case FieldDeviceMenu.KIND_SERVO_ACTUATOR ->
                    card("SERVO ACTUATOR",
                            "velocity approaches bounded command; position[k+1] = clamp(position[k] + velocity, 0, 15)",
                            "slew profile • load • mode/brake inputs",
                            "read command/mode/brake -> update velocity with load-limited acceleration -> integrate position",
                            "Command, desired velocity, actual velocity, and measured position remain distinct.");

            case FieldDeviceMenu.KIND_WATCHDOG ->
                    card("WATCHDOG",
                            "trip when heartbeat age exceeds configured timeout",
                            "timeout profile",
                            "observe heartbeat -> measure age -> enter protective output on timeout",
                            "A timeout is a protective state and must not be confused with invalid measurement evidence.");

            case FieldDeviceMenu.KIND_REDUNDANT_VOTER ->
                    card("2oo3 VOTER",
                            "output = consensus(inputs, tolerance) when enough valid channels agree",
                            "tolerance profile",
                            "validate three channels -> measure spread -> vote or degrade",
                            "A retained output does not hide disagreement or degraded evidence.");

            case FieldDeviceMenu.KIND_FAULT_LATCH ->
                    card("FAULT LATCH",
                            "latched = latched OR trip(input >= threshold) until permitted reset",
                            "trip threshold",
                            "observe fault evidence -> latch protective state -> require explicit valid reset",
                            "Safety memory is operational state, separate from signal quality.");

            case FieldDeviceMenu.KIND_OPTICAL_ATTENUATOR ->
                    card("OPTICAL ATTENUATOR",
                            "I_out = max(0, I_in - configured loss)",
                            "attenuation loss",
                            "observe optical input -> subtract bounded passive loss -> propagate channel/evidence",
                            "Attenuation cannot create intensity or repair missing channel evidence.");

            default ->
                    card("ENGINEERING BLOCK MODEL",
                            "state[k+1] = F(state[k], input evidence, block-owned configuration)",
                            "device-specific synchronized configuration",
                            "world input -> server-owned state update -> explicit output/evidence",
                            "The HMI explains this Minecraft block; it does not replace the world with a desktop simulator.");
        };
    }

    private static ModelCard universal(UniversalFieldDeviceMenu menu) {
        return switch (menu.configKind()) {
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_LOW_PASS -> card(
                    "LAPIS LOW-PASS FILTER", "y[k+1] = y[k] + alpha * (x[k] - y[k])",
                    "alpha profile", "observe valid Lapis input -> update retained filter state -> publish smoothed precision output",
                    "Changing alpha changes future response; missing evidence does not become a fabricated zero sample.");
            case UniversalFieldDeviceMenu.CONFIG_QUARTZ_PHASE_DELAY -> card(
                    "QUARTZ PHASE DELAY", "t_out(edge_n) = t_in(edge_n) + configured delay",
                    "rising-edge delay 1..8 ticks", "accept genuine edge -> enqueue timestamped event -> release after captured delay",
                    "In-flight events retain the delay captured at acceptance; configuration changes apply only to new edges.");
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_HEATER -> card(
                    "ELECTRO-THERMAL HEATER", "I = V/R; P = V^2/R; T approaches environment + P/3",
                    "heater resistance profile", "observe Copper power -> convert electrical power to thermal target -> evolve finite temperature",
                    "Temperature is physical state, not a configuration knob; uncertain Copper power freezes evolution.");
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_MASS -> card(
                    "THERMAL MASS", "T[k+1] approaches local thermal target with rate set by heat capacity",
                    "heat capacity 1..4", "observe environment/neighbors -> compute target -> evolve temperature at finite rate",
                    "Changing capacity retains current temperature and changes only future thermal inertia.");
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_RADIATOR -> card(
                    "THERMAL RADIATOR", "T_neighbor[k+1] = max(T_ambient, T_neighbor - cooling)",
                    "cooling coefficient 1..4", "observe adjacent thermal mass -> remove bounded heat -> stop at ambient floor",
                    "The radiator cannot cool below the physical ambient floor.");
            case UniversalFieldDeviceMenu.CONFIG_REDSTONE_COPPER_DRIVER -> card(
                    "REDSTONE → COPPER DRIVER", "V_actual[k+1] = moveToward(V_actual, V_target, slew)",
                    "slew profile • target V • actual V", "observe redstone command -> classify evidence -> slew physical Copper output -> publish network source",
                    "A valid zero command is a real 0 V source; missing/uncertain command evidence is source absence, not numeric zero.");
            case UniversalFieldDeviceMenu.CONFIG_COPPER_SOURCE -> card(
                    "COPPER VOLTAGE SOURCE", "V_out = configured V_source",
                    "source voltage 0..15", "set durable source level -> recompute connected Copper network -> expose voltage evidence",
                    "Configured zero is a real source setting; changing voltage recomputes the network.");
            case UniversalFieldDeviceMenu.CONFIG_COPPER_SERIES_RESISTOR -> card(
                    "SERIES RESISTOR", "I = V_in / (R_s + R_load); V_out = I * R_load",
                    "series resistance R_s", "observe input/load -> solve divider -> publish output voltage/current evidence",
                    "Changing R_s invalidates the old derived output until the next authoritative circuit observation.");
            case UniversalFieldDeviceMenu.CONFIG_COPPER_LOAD -> card(
                    "RESISTIVE LOAD", "I = V/R; P = V*I = V^2/R",
                    "load resistance R", "observe terminal voltage -> compute current and power -> contribute load to network",
                    "Observed voltage is not a player parameter; resistance is the owned load configuration.");
            case UniversalFieldDeviceMenu.CONFIG_COPPER_CAPACITOR -> card(
                    "RC CAPACITOR", "V_c[k+1] approaches V_in with tau = R_eq*C",
                    "capacitance index C • observed load R", "observe source/load -> update retained charge -> publish finite-time output response",
                    "Changing C retains physical stored charge; it changes future tau rather than teleporting capacitor energy.");
            case UniversalFieldDeviceMenu.CONFIG_COPPER_FUSE -> card(
                    "THERMAL FUSE", "trip when retained thermal exposure from I/rating exceeds protection threshold",
                    "current rating • retained thermal state", "observe current -> accumulate/cool thermal exposure -> trip or remain armed",
                    "Changing the rating does not cool the fuse or erase prior thermal exposure.");
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
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_TRANSDUCER -> card(
                    "PRECISION TRANSDUCER",
                    "precision output = F(observed physical input, calibration/profile)",
                    "profile • source evidence",
                    "observe physical quantity -> apply bounded transduction profile -> publish 0..100 Lapis evidence",
                    "Changing representation does not create source precision or repair missing evidence.");

            case UniversalFieldDeviceMenu.CONFIG_LAPIS_RANGE -> card(
                    "PRECISION RANGE SENSOR",
                    "precision output = range-map(distance evidence; configured range/profile)",
                    "response profile • range index",
                    "scan physical aperture -> validate distance evidence -> map into Lapis precision output",
                    "No target found is not silently converted into a trustworthy zero-distance measurement.");

            case UniversalFieldDeviceMenu.CONFIG_MOLECULAR_RECEIVER -> card(
                    "MOLECULAR CLOUD RECEIVER",
                    "output = aggregate(observed cloud evidence; sensitivity)",
                    "sensitivity",
                    "sample bounded environment -> aggregate valid evidence -> report signal/quality",
                    "Incomplete environmental evidence degrades quality instead of inventing density.");

            case UniversalFieldDeviceMenu.CONFIG_ALARM -> card(
                    "ALARM PROCESSOR",
                    "alarm state = F(input evidence, severity setting, acknowledgement state)",
                    "severity • acknowledge action",
                    "classify incoming condition -> enter alarm state -> retain/acknowledge according to server state",
                    "Acknowledgement changes operator state; it does not rewrite the measured cause.");

            case UniversalFieldDeviceMenu.CONFIG_FAULT_INJECTOR -> card(
                    "FAULT INJECTOR",
                    "test output = perturb(input evidence; selected fault mode)",
                    "fault mode • reset diagnostics",
                    "accept explicit test configuration -> inject bounded test fault -> expose resulting evidence",
                    "Fault injection is a deliberate engineering-test block, not hidden corruption of unrelated networks.");

            case UniversalFieldDeviceMenu.CONFIG_SEQUENCE_CONTROLLER -> card(
                    "SEQUENCE CONTROLLER",
                    "state[k+1] = transition(state[k], explicit inputs[k])",
                    "sequence state • operator reset",
                    "observe allowed inputs -> advance deterministic state machine -> publish explicit outputs",
                    "The UI may show/reset the state machine but does not run a separate client scheduler.");

            case UniversalFieldDeviceMenu.CONFIG_SAFETY_INTERLOCK -> card(
                    "SAFETY INTERLOCK",
                    "permit = required conditions AND NOT trip; reset only when permitted",
                    "interlock state • reset action",
                    "validate safety evidence -> fail closed on trip/uncertainty -> require explicit permitted reset",
                    "Safety state and signal quality remain separate so a valid trip is not mislabeled as bad data.");

            case UniversalFieldDeviceMenu.CONFIG_TOPOLOGY_DEBUGGER -> card(
                    "TOPOLOGY DEBUGGER",
                    "diagnosis = F(declared ports, connected faces, medium/domain compatibility)",
                    "observer/reset diagnostics",
                    "inspect formal port contract -> classify mismatch/conflict -> report without back-driving",
                    "This block observes topology; it never repairs a network by inventing a connection.");

            case UniversalFieldDeviceMenu.CONFIG_LIGHT_SENSOR -> card(
                    "LIGHT SENSOR",
                    "output = bounded profile(light measurement)",
                    "response profile",
                    "measure local light evidence -> apply selected response profile -> publish bounded signal",
                    "A configured profile changes interpretation, not the underlying world light.");

            case UniversalFieldDeviceMenu.CONFIG_TANK_LEVEL -> card(
                    "TANK LEVEL SENSOR",
                    "output = range-map(level evidence; configured range)",
                    "range mode",
                    "observe tank/level evidence -> scale into bounded output -> preserve quality",
                    "Out-of-range or unavailable evidence is diagnosed separately from a real empty reading.");

            case UniversalFieldDeviceMenu.CONFIG_ENTITY_DENSITY -> card(
                    "ENTITY DENSITY SENSOR",
                    "output = profile(count(valid entities in aperture))",
                    "profile • aperture mode",
                    "scan bounded aperture -> count qualifying entities -> map count to output",
                    "Aperture selection changes what is measured; it does not create entities outside the scan.");

            case UniversalFieldDeviceMenu.CONFIG_IRON_CORE -> card(
                    "IRON CORE",
                    "magnetic state = F(applied field history, core state)",
                    "degauss action • magnetic evidence",
                    "receive magnetic excitation -> retain/condition core state -> expose field evidence",
                    "Degauss is an explicit state-changing action; the UI never fabricates magnetic history.");

            case UniversalFieldDeviceMenu.CONFIG_SIGNAL_PROBE -> card(
                    "SIGNAL PROBE",
                    "reported sample = observed channel value",
                    "channel • measurement axis",
                    "observe selected signal/channel -> publish instrument evidence",
                    "Probe semantics are observer-first: measurement does not become a hidden network driver.");

            case UniversalFieldDeviceMenu.CONFIG_CABLE_TERMINAL -> card(
                    "CABLE TERMINAL",
                    "boundary maps one explicit external interface to one explicit cable interface",
                    "terminal mode • physical orientation",
                    "observe source side -> cross explicit terminal boundary -> drive destination side",
                    "The terminal is a boundary device; it does not merge unrelated cable and vanilla networks.");

            case UniversalFieldDeviceMenu.CONFIG_QUARTZ_OSCILLATOR -> card(
                    "QUARTZ OSCILLATOR",
                    "clock toggles every T/2; configured T becomes effective on a real transition",
                    "period index",
                    "run square-wave state -> latch timing change on genuine edge -> publish Quartz timing evidence",
                    "Opening/configuring the UI never manufactures an edge.");

            case UniversalFieldDeviceMenu.CONFIG_FAULT_LATCH -> card(
                    "FAULT LATCH",
                    "latched = latched OR trip(input >= threshold) until valid reset",
                    "trip threshold • reset action",
                    "observe trip evidence -> retain protective state -> reset only under permitted conditions",
                    "Latched safety memory remains distinct from current evidence quality.");

            case UniversalFieldDeviceMenu.CONFIG_ANALOG_INDICATOR -> card(
                    "ANALOG INDICATOR",
                    "display = observed bounded signal; extrema derive from observed history",
                    "read-only display • reset extrema",
                    "observe input -> update display/extrema -> expose quality",
                    "The indicator is an observer and must not back-drive the measured signal.");

            case UniversalFieldDeviceMenu.CONFIG_BYTE_ENCODER -> card(
                    "BYTE ENCODER",
                    "bus word = encode(input signal/state; selected mode)",
                    "encoding mode",
                    "sample source -> encode explicit 8-bit representation -> drive bus evidence",
                    "Encoding changes representation, not the trustworthiness of source evidence.");

            case UniversalFieldDeviceMenu.CONFIG_BYTE_DECODER -> card(
                    "BYTE DECODER",
                    "output = decode(bus word; selected mode)",
                    "decoding mode",
                    "validate bus evidence -> decode selected representation -> publish bounded output",
                    "Invalid bus evidence cannot be decoded into a trustworthy payload.");

            case UniversalFieldDeviceMenu.CONFIG_DESERIALIZER -> card(
                    "DESERIALIZER",
                    "parallel word = reconstruct(time-ordered serial symbols)",
                    "serial timing evidence",
                    "reacquire serial timing -> collect ordered symbols -> publish reconstructed bus word",
                    "Missing symbol/timing continuity prevents a fabricated complete word.");

            case UniversalFieldDeviceMenu.CONFIG_DIFF_RECEIVER -> card(
                    "DIFFERENTIAL RECEIVER",
                    "decoded state = classify(differential pair evidence)",
                    "pair evidence • output route",
                    "observe complementary pair -> validate differential relation -> recover bounded logic output",
                    "Common-mode or incomplete pair evidence remains diagnosable rather than silently decoded.");

            case UniversalFieldDeviceMenu.CONFIG_SINGLE_RELAY -> card(
                    "SINGLE RELAY",
                    "contact state follows pickup/dropout logic with configured timing/contact mode",
                    "pickup profile • timing • contact mode",
                    "observe coil/control -> apply pickup timing/state -> switch explicit contact path",
                    "Control and switched payload remain separate ports; relay state does not invent payload evidence.");

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
