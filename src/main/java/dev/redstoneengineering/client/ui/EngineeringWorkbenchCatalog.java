package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.ui.menu.*;

/**
 * Client-only presentation catalog for the equations and processing ideas already represented by
 * server-authoritative RSE devices. It never evaluates physics or acceptance logic.
 */
public final class EngineeringWorkbenchCatalog {
    public record ModelCard(String family, String equation, String parameters, String process, String boundary) {}

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
