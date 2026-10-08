package dev.redstoneengineering.ui.ldlib;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.redstoneengineering.ui.menu.FieldDeviceMenu;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.appliedenergistics.yoga.YogaFlexDirection;

import java.util.Locale;

/**
 * Final shared LDLib2 fallback HMI for lightweight FieldDevice kinds.
 *
 * <p>This class is presentation-only. It consumes synchronized FieldDeviceMenu
 * snapshots and submits validated operator intent back through the existing menu.
 * It never reads the world and never imports a physics solver.</p>
 */
public final class EnhancedFieldDeviceLdUi {
    private EnhancedFieldDeviceLdUi() {}

    public static ModularUI create(FieldDeviceMenu m, Player player) {
        var root = new UIElement().addClass("panel_bg");
        root.layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(8).gapAll(6));
        root.addChildren(
                RseLdUiComponents.title("RSE FIELD DEVICE • " + deviceName(m.kind())),
                RseLdUiComponents.tabbedWorkspace(
                        720, 430, 900,
                        new String[]{"Overview", "Configure", "Topology", "Evidence", "Integrity", "Transport"},
                        new UIElement[]{
                                RseLdUiComponents.workspacePage(identityPanel(m), modelPanel(m), livePanel(m)),
                                RseLdUiComponents.workspacePage(controlPanel(m)),
                                RseLdUiComponents.workspacePage(routePanel(m)),
                                RseLdUiComponents.workspacePage(evidencePanel(m), RseLdUiComponents.authorityFooter()),
                                RseLdUiComponents.workspacePage(sourceMediumIntegrityPanel(m)),
                                RseLdUiComponents.workspacePage(discreteTransportPanel(m))
                        }
                )
        );
        return RseLdUiComponents.responsiveUi(root, player, 740, 470);
    }

    private static UIElement identityPanel(FieldDeviceMenu m) {
        return new UIElement().addClass("panel_bg").layout(l -> l.paddingAll(5).gapAll(3)).addChildren(
                new Label().setText("PIONEER PATTERN • SHARED FIELD DEVICE"),
                RseLdUiComponents.liveRow("ROLE", "device", () -> deviceRole(m.kind())),
                RseLdUiComponents.liveRow("FAMILY", "domain", () -> family(m.kind())),
                RseLdUiComponents.liveRow("CONTROLS", "operator", () -> operatorMode(m.kind())),
                RseLdUiComponents.liveRow("HEALTH", "state", () ->
                        !m.topologyValid() ? "FAIL-CLOSED TOPOLOGY"
                                : m.dataValid() ? "VALID" : "NO / INVALID EVIDENCE")
        );
    }

    private static UIElement modelPanel(FieldDeviceMenu m) {
        return new UIElement().addClass("panel_bg").layout(l -> l.paddingAll(5).gapAll(3)).addChildren(
                new Label().setText("MODEL / CONTRACT"),
                RseLdUiComponents.formulaCard(modelContract(m.kind())),
                RseLdUiComponents.liveRow("MEASURED", metricLabel(m.kind(), 0), () -> Integer.toString(m.primary())),
                RseLdUiComponents.liveRow("MEASURED", metricLabel(m.kind(), 1), () -> Integer.toString(m.secondary())),
                RseLdUiComponents.liveRow("STATE", metricLabel(m.kind(), 2), () -> Integer.toString(m.tertiary())),
                new Label().setText("server state → synchronized HMI evidence • no hidden client physics")
        );
    }

    private static UIElement livePanel(FieldDeviceMenu m) {
        return new UIElement().addClass("panel_bg").layout(l -> l.paddingAll(5).gapAll(3)).addChildren(
                new Label().setText("LIVE MECHANISM / EVIDENCE"),
                RseLdUiComponents.liveRow("EVIDENCE", "PortQuality", () -> m.evidenceQuality().name()),
                RseLdUiComponents.liveRow("EVIDENCE", "quality", () -> m.qualityPercent() + "%"),
                RseLdUiComponents.liveRow("EVIDENCE", "sources / drivers", () -> Integer.toString(m.driverCount())),
                RseLdUiComponents.liveRow("TOPOLOGY", "ports / links", () ->
                        m.portCount() + " / " + m.connectionCount()),
                RseLdUiComponents.liveRow("TOPOLOGY", "connection mask", () ->
                        "0x" + Integer.toHexString(m.connectionMask()).toUpperCase(Locale.ROOT)),
                RseLdUiComponents.liveRow("AUTHORITY", "policy", () ->
                        m.topologyValid() ? "SERVER SYNCHRONIZED" : "FAIL-CLOSED")
        );
    }

    private static UIElement controlPanel(FieldDeviceMenu m) {
        var panel = new UIElement().addClass("panel_bg");
        panel.layout(l -> l.paddingAll(5).gapAll(4));
        panel.addChild(new Label().setText("FORMULA-LINKED CONTROLS"));

        if (directEntryKind(m.kind())) {
            var input = new TextField().setNumbersOnlyInt(0, 127);
            input.layout(l -> l.width(120));
            input.bind(DataBindingBuilder.string(
                    () -> Integer.toString(controlValue(m)),
                    value -> {
                        try {
                            int parsed = Integer.parseInt(value);
                            if (validDirectValue(m.kind(), parsed)) m.applyPrimaryEngineeringValueFromUi(parsed);
                        } catch (NumberFormatException ignored) {
                        }
                    }
            ).build());
            panel.addChildren(
                    RseLdUiComponents.liveRow("ADJUSTABLE", formulaSymbol(m.kind()), () -> Integer.toString(controlValue(m))),
                    new UIElement().layout(l -> l.flexDirection(YogaFlexDirection.ROW).gapAll(6)).addChildren(
                            new Label().setText("Exact engineering value").layout(l -> l.width(140)),
                            input,
                            new Label().setText(directRangeLabel(m.kind())).layout(l -> l.flex(1))
                    )
            );
        } else if (hasOperatorActions(m.kind())) {
            panel.addChild(new Label().setText(
                    "SERVER-AUTHORITATIVE ACTIONS • use the controls below; no invented numeric coefficient"));
        } else {
            panel.addChild(new Label().setText("READ-ONLY HMI • no fake control"));
        }

        if (m.kind() == FieldDeviceMenu.KIND_TERMINAL || m.kind() == FieldDeviceMenu.KIND_PNEUMATIC_VALVE) {
            panel.addChild(RseLdUiComponents.serverAction(
                    m.kind() == FieldDeviceMenu.KIND_PNEUMATIC_VALVE ? "Toggle valve" : "Toggle Vanilla / Cable",
                    m::toggleFromUi));
        }
        if (m.kind() == FieldDeviceMenu.KIND_REFERENCE || m.kind() == FieldDeviceMenu.KIND_OPTICAL_EMITTER) {
            panel.addChild(new UIElement().layout(l -> l.flexDirection(YogaFlexDirection.ROW).gapAll(5)).addChildren(
                    RseLdUiComponents.serverAction("Preset 0", () -> m.presetFromUi(0)),
                    RseLdUiComponents.serverAction("Preset 5", () -> m.presetFromUi(5)),
                    RseLdUiComponents.serverAction("Preset 10", () -> m.presetFromUi(10)),
                    RseLdUiComponents.serverAction("Preset 15", () -> m.presetFromUi(15))
            ));
        }
        return panel;
    }

    private static boolean hasOperatorActions(int kind) {
        return kind == FieldDeviceMenu.KIND_TERMINAL
                || kind == FieldDeviceMenu.KIND_PNEUMATIC_VALVE
                || kind == FieldDeviceMenu.KIND_REFERENCE
                || kind == FieldDeviceMenu.KIND_OPTICAL_EMITTER;
    }

    private static String operatorMode(int kind) {
        if (directEntryKind(kind)) return "DIRECT NUMERIC • Configure tab";
        if (hasOperatorActions(kind)) return "SERVER ACTIONS • Configure tab";
        return "OBSERVER / PHYSICAL MODEL • read-only";
    }

    private static UIElement routePanel(FieldDeviceMenu m) {
        var panel = new UIElement().addClass("panel_bg");
        panel.layout(l -> l.paddingAll(5).gapAll(4));
        panel.addChildren(
                new Label().setText("MECHANISM FLOW • RX → MODEL / STATE → TX"),
                RseLdUiComponents.liveRow("ROUTE", "facing", () -> facingName(m.facingOrdinal())),
                RseLdUiComponents.liveRow("ROUTE", "endpoint flags", () ->
                        "RX=" + m.hasInputEndpoint() + " • TX=" + m.hasOutputEndpoint()
                                + " • series=" + m.seriesConfigurable())
        );
        var buttons = new UIElement();
        buttons.layout(l -> l.flexDirection(YogaFlexDirection.ROW).gapAll(5));
        if (m.seriesConfigurable()) buttons.addChild(RseLdUiComponents.serverAction("Cycle direction ▶", m::cycleDirectionForward));
        if (m.hasInputEndpoint()) buttons.addChild(RseLdUiComponents.serverAction("Cycle RX ▶", m::cycleInputForward));
        if (m.hasOutputEndpoint()) buttons.addChild(RseLdUiComponents.serverAction("Cycle TX ▶", m::cycleOutputForward));
        panel.addChild(buttons);
        return panel;
    }

    private static UIElement evidencePanel(FieldDeviceMenu m) {
        return new UIElement().addClass("panel_bg").layout(l -> l.paddingAll(5).gapAll(3)).addChildren(
                new Label().setText("ENGINEERING / DIAGNOSTIC HINTS"),
                new Label().setText(engineeringHint(m.kind())),
                new Label().setText(diagnosticHint(m.kind())),
                new Label().setText(fixedTransportPolicy(m.kind())),
                new Label().bind(DataBindingBuilder.componentS2C(() ->
                        Component.literal(legacyEngineeringContract(m.kind()))
                ).build()),
                new Label().setText("Client presentation only • mutation, topology and solver authority remain server-owned.")
        );
    }

    private static UIElement sourceMediumIntegrityPanel(FieldDeviceMenu m) {
        var panel = new UIElement().addClass("panel_bg");
        panel.layout(l -> l.paddingAll(5).gapAll(3));
        if (!isSourceMediumIntegrityDevice(m.kind())) {
            panel.addChild(new Label().setText("SOURCE / MEDIUM INTEGRITY • not the primary contract for this device"));
            return panel;
        }
        panel.addChildren(
                new Label().setText("PIONEER PATTERN • SOURCE / MEDIUM INTEGRITY"),
                new Label().setText("PortQuality • configured zero remains VALID evidence"),
                new Label().setText("valid zero ≠ no source"),
                new Label().setText("multi-source=TOPOLOGY_ERROR; truncated scan=STALE"),
                new Label().setText("duplicate channel or truncated scan invalidates trustworthy evidence"),
                new Label().setText("STALE • SERVER EVIDENCE INCOMPLETE"),
                new Label().setText("TOPOLOGY ERROR • CONFLICT / INVALID PATH")
        );
        return panel;
    }

    private static UIElement discreteTransportPanel(FieldDeviceMenu m) {
        var panel = new UIElement().addClass("panel_bg");
        panel.layout(l -> l.paddingAll(5).gapAll(3));
        if (!isDiscreteTransportDevice(m.kind())) {
            panel.addChild(new Label().setText("DISCRETE TRANSPORT MODEL • not applicable to this device"));
            return panel;
        }
        panel.addChildren(
                new Label().setText("PIONEER PATTERN • RSE DISCRETE TRANSPORT MODEL"),
                RseLdUiComponents.liveRow("MODEL", "transport", () -> discreteTransportModel(m.kind())),
                RseLdUiComponents.liveRow("TOPOLOGY", "medium", () -> discreteTransportTopology(m.kind())),
                new Label().setText("server-owned packet/event state • HMI exposes the implemented bounded abstraction only")
        );
        return panel;
    }

    private static boolean isDiscreteTransportDevice(int kind) {
        return switch (kind) {
            case FieldDeviceMenu.KIND_MECHANICAL_EXCITER,
                 FieldDeviceMenu.KIND_SLIME_VIBRATION,
                 FieldDeviceMenu.KIND_MECHANICAL_RECEIVER,
                 FieldDeviceMenu.KIND_HONEY_DAMPER,
                 FieldDeviceMenu.KIND_SCULK_INTERFACE,
                 FieldDeviceMenu.KIND_HYDRO_TUBE,
                 FieldDeviceMenu.KIND_HYDRO_EXCITER,
                 FieldDeviceMenu.KIND_HYDRO_RECEIVER,
                 FieldDeviceMenu.KIND_PHONON_CONDUIT,
                 FieldDeviceMenu.KIND_THERMAL_ENCODER,
                 FieldDeviceMenu.KIND_THERMAL_RECEIVER -> true;
            default -> false;
        };
    }

    private static String discreteTransportModel(int kind) {
        return switch (kind) {
            case FieldDeviceMenu.KIND_SLIME_VIBRATION ->
                    "HOP: A_next=max(0,A-1); RETAIN @4t: A←max(0,A-2), Q←max(0,Q-10)";
            case FieldDeviceMenu.KIND_HONEY_DAMPER ->
                    "HOP: A_next=max(0,A-4), node Q=80; RETAIN @4t: A←max(0,A-4), Q←max(0,Q-20)";
            case FieldDeviceMenu.KIND_MECHANICAL_RECEIVER ->
                    "OUT: y_R=valid?min(15,A):0; RETAIN @4t: A←max(0,A-2), Q←max(0,Q-5)";
            case FieldDeviceMenu.KIND_HYDRO_TUBE,
                 FieldDeviceMenu.KIND_HYDRO_EXCITER ->
                    "Lm={water:1,milk-model:2,lava:3} • RSE DISCRETE MODEL: this is a bounded game-domain pressure-packet network";
            case FieldDeviceMenu.KIND_HYDRO_RECEIVER ->
                    "OUT: y_R=valid?min(15,A):0; RETAIN @4t: A←max(0,A-1), Q←max(0,Q-5)";
            case FieldDeviceMenu.KIND_PHONON_CONDUIT ->
                    "HOP: A_next=max(0,A-1); RETAIN @8t: A←max(0,A-2), Q←max(0,Q-10) • PHONON_THERMAL is a finite-bandwidth event-packet abstraction";
            case FieldDeviceMenu.KIND_THERMAL_ENCODER ->
                    "PHONON_THERMAL is a finite-bandwidth event-packet abstraction • not a Fourier heat-transfer or continuously driven temperature-field solver";
            case FieldDeviceMenu.KIND_THERMAL_RECEIVER ->
                    "OUT: y_R=valid?min(15,A):0; RETAIN @8t: A←max(0,A-1), Q←max(0,Q-5)";
            case FieldDeviceMenu.KIND_SCULK_INTERFACE ->
                    "Event counters are retained server evidence • SCULK / CALIBRATED-SENSOR EVENT CODE";
            default -> "MECHANICAL_VIBRATION packet amplitude / quality / lifetime are server-authoritative";
        };
    }

    private static String discreteTransportTopology(int kind) {
        return switch (kind) {
            case FieldDeviceMenu.KIND_HONEY_DAMPER ->
                    "MECHANICAL_VIBRATION • SIX-WAY • HIGH-DAMPING PACKET";
            case FieldDeviceMenu.KIND_MECHANICAL_EXCITER,
                 FieldDeviceMenu.KIND_SLIME_VIBRATION,
                 FieldDeviceMenu.KIND_MECHANICAL_RECEIVER ->
                    "MECHANICAL_VIBRATION • SIX-WAY • LOW-LOSS PACKET";
            case FieldDeviceMenu.KIND_HYDRO_TUBE,
                 FieldDeviceMenu.KIND_HYDRO_EXCITER,
                 FieldDeviceMenu.KIND_HYDRO_RECEIVER ->
                    "HYDROACOUSTIC • SIX-WAY • MEDIUM-DEPENDENT LOSS";
            case FieldDeviceMenu.KIND_PHONON_CONDUIT,
                 FieldDeviceMenu.KIND_THERMAL_ENCODER,
                 FieldDeviceMenu.KIND_THERMAL_RECEIVER ->
                    "PHONON_THERMAL • SIX-WAY • FINITE-BANDWIDTH PACKET";
            case FieldDeviceMenu.KIND_SCULK_INTERFACE ->
                    "SCULK / CALIBRATED-SENSOR EVENT CODE";
            default -> "DISCRETE TRANSPORT";
        };
    }

    private static String legacyEngineeringContract(int k) {
        return switch (k) {
            case FieldDeviceMenu.KIND_DIFFERENTIAL_DRIVER -> "REDSTONE → DIFFERENTIAL";
            case FieldDeviceMenu.KIND_DIFFERENTIAL_RECEIVER -> "DIFFERENTIAL → REDSTONE";
            case FieldDeviceMenu.KIND_AMETHYST_DUST -> "RESONANCE BUS";
            case FieldDeviceMenu.KIND_AMETHYST_TUNED -> "TUNED AMETHYST RESONATOR";
            case FieldDeviceMenu.KIND_AMETHYST_SPECTRUM -> "SPECTRUM ANALYZER • OBSERVER";
            case FieldDeviceMenu.KIND_MECHANICAL_RECEIVER -> "MECHANICAL VIBRATION RECEIVER";
            case FieldDeviceMenu.KIND_SHIELDED_INSTRUMENT_CABLE -> "SHIELDED INSTRUMENT BUS";
            case FieldDeviceMenu.KIND_WATCHDOG -> "HEARTBEAT WATCHDOG";
            case FieldDeviceMenu.KIND_SERVO_ACTUATOR -> "MECHATRONIC_POSITION OUTPUT";
            case FieldDeviceMenu.KIND_REDUNDANT_VOTER -> "2oo3 REDUNDANT VOTER";
            case FieldDeviceMenu.KIND_FAULT_LATCH -> "RESET input with priority over FAULT";
            case FieldDeviceMenu.KIND_OPERATIONS_MONITOR -> "OPERATIONS MONITOR • OBSERVER • READ-ONLY CPS / RELIABILITY DEVICE";
            case FieldDeviceMenu.KIND_HYDRO_TUBE -> "HYDROACOUSTIC • BIDIRECTIONAL PRESSURE PATH";
            case FieldDeviceMenu.KIND_PHONON_CONDUIT -> "PHONON_THERMAL • BIDIRECTIONAL PULSE PATH";
            case FieldDeviceMenu.KIND_AIR_COMPRESSOR -> "PNEUMATIC COMPRESSED-AIR OUTPUT";
            case FieldDeviceMenu.KIND_PNEUMATIC_PIPE -> "PNEUMATIC • SIX-WAY BIDIRECTIONAL PIPE";
            case FieldDeviceMenu.KIND_PNEUMATIC_RECEIVER -> "PNEUMATIC → REDSTONE";
            case FieldDeviceMenu.KIND_SIGNAL_TAP -> "NON-INVASIVE SIGNAL TAP";
            case FieldDeviceMenu.KIND_RANGE_SENSOR -> "SENSING APERTURE • NO WIRED PORT";
            case FieldDeviceMenu.KIND_LAPIS_LINE, FieldDeviceMenu.KIND_LAPIS_SOURCE ->
                    "LAPIS_PRECISION • FOUR HORIZONTAL OUTPUTS";
            case FieldDeviceMenu.KIND_QUARTZ_LINE, FieldDeviceMenu.KIND_QUARTZ_OSCILLATOR ->
                    "QUARTZ_TIMING • FOUR HORIZONTAL OUTPUTS";
            case FieldDeviceMenu.KIND_PULSE_SHAPER -> "Pulse remaining";
            case FieldDeviceMenu.KIND_PNEUMATIC_PROPORTIONAL_VALVE -> "PROPORTIONAL VALVE";
            case FieldDeviceMenu.KIND_PNEUMATIC_RELIEF_VALVE -> "RELIEF ARMED";
            case FieldDeviceMenu.KIND_PNEUMATIC_CYLINDER -> "PNEUMATIC ACTUATOR";
            case FieldDeviceMenu.KIND_ELECTROMAGNET -> "COPPER → MAGNETIC";
            case FieldDeviceMenu.KIND_PERMANENT_MAGNET -> "PERMANENT FIELD SOURCE";
            case FieldDeviceMenu.KIND_INDUCTION_COIL -> "MAGNETIC INDUCTION";
            case FieldDeviceMenu.KIND_MAGNETIC_FIELD_SENSOR -> "MAGNETIC FIELD SENSOR";
            case FieldDeviceMenu.KIND_MAGNETIC_GRADIENT_METER -> "MAGNETIC GRADIENT";
            default -> "No additional legacy diagnostic contract for this device.";
        };
    }

    private static boolean discreteExciterAdjustable(int k) {
        return k == FieldDeviceMenu.KIND_MECHANICAL_EXCITER || k == FieldDeviceMenu.KIND_HYDRO_EXCITER;
    }

    private static String fixedTransportPolicy(int k) {
        return discreteExciterAdjustable(k)
                ? "exact server-backed frequency"
                : "read-only implemented model • read-only transport contract";
    }

    private static boolean directEntryKind(int k) {
        return switch (k) {
            case FieldDeviceMenu.KIND_PROBE,
                 FieldDeviceMenu.KIND_FILTER,
                 FieldDeviceMenu.KIND_REFERENCE,
                 FieldDeviceMenu.KIND_LAPIS_SOURCE,
                 FieldDeviceMenu.KIND_DIGITAL_REGENERATOR,
                 FieldDeviceMenu.KIND_PRESSURE_REGULATOR,
                 FieldDeviceMenu.KIND_PNEUMATIC_RELIEF_VALVE,
                 FieldDeviceMenu.KIND_PERMANENT_MAGNET,
                 FieldDeviceMenu.KIND_INDUCTION_COIL,
                 FieldDeviceMenu.KIND_OPTICAL_EMITTER,
                 FieldDeviceMenu.KIND_OPTICAL_CHANNEL_FILTER,
                 FieldDeviceMenu.KIND_OPTICAL_ATTENUATOR,
                 FieldDeviceMenu.KIND_MECHANICAL_EXCITER,
                 FieldDeviceMenu.KIND_HYDRO_EXCITER -> true;
            default -> false;
        };
    }

    private static boolean validDirectValue(int k, int value) {
        return switch (k) {
            case FieldDeviceMenu.KIND_PROBE -> value >= 0 && value <= 3;
            case FieldDeviceMenu.KIND_FILTER -> value >= 1 && value <= 4;
            case FieldDeviceMenu.KIND_REFERENCE -> value >= 0 && value <= 15;
            case FieldDeviceMenu.KIND_LAPIS_SOURCE -> value >= 0 && value <= 100 && value % 5 == 0;
            case FieldDeviceMenu.KIND_DIGITAL_REGENERATOR -> value >= 0 && value <= 2;
            case FieldDeviceMenu.KIND_PRESSURE_REGULATOR,
                 FieldDeviceMenu.KIND_PNEUMATIC_RELIEF_VALVE -> value >= 25 && value <= 100 && value % 25 == 0;
            case FieldDeviceMenu.KIND_PERMANENT_MAGNET -> value >= 1 && value <= 15;
            case FieldDeviceMenu.KIND_INDUCTION_COIL -> value >= 1 && value <= 4;
            case FieldDeviceMenu.KIND_OPTICAL_EMITTER,
                 FieldDeviceMenu.KIND_OPTICAL_CHANNEL_FILTER -> value >= 0 && value <= 15;
            case FieldDeviceMenu.KIND_OPTICAL_ATTENUATOR -> value >= 0 && value <= 8;
            case FieldDeviceMenu.KIND_MECHANICAL_EXCITER,
                 FieldDeviceMenu.KIND_HYDRO_EXCITER -> value >= 1 && value <= 15;
            default -> false;
        };
    }

    private static int controlValue(FieldDeviceMenu m) {
        return switch (m.kind()) {
            case FieldDeviceMenu.KIND_PROBE -> m.secondary();
            case FieldDeviceMenu.KIND_FILTER -> m.tertiary();
            case FieldDeviceMenu.KIND_REFERENCE -> m.primary();
            case FieldDeviceMenu.KIND_DIGITAL_REGENERATOR -> m.tertiary();
            case FieldDeviceMenu.KIND_PRESSURE_REGULATOR -> m.secondary();
            case FieldDeviceMenu.KIND_MECHANICAL_EXCITER,
                 FieldDeviceMenu.KIND_HYDRO_EXCITER -> m.secondary();
            default -> m.tertiary() != 0 ? m.tertiary() : m.primary();
        };
    }

    private static String formulaSymbol(int k) {
        return switch (k) {
            case FieldDeviceMenu.KIND_PROBE -> "channel";
            case FieldDeviceMenu.KIND_FILTER -> "Δmax";
            case FieldDeviceMenu.KIND_REFERENCE -> "y_R";
            case FieldDeviceMenu.KIND_LAPIS_SOURCE -> "y_L";
            case FieldDeviceMenu.KIND_DIGITAL_REGENERATOR -> "minimumQuality / threshold";
            case FieldDeviceMenu.KIND_PRESSURE_REGULATOR -> "P_set";
            case FieldDeviceMenu.KIND_PNEUMATIC_RELIEF_VALVE -> "P_relief";
            case FieldDeviceMenu.KIND_PERMANENT_MAGNET -> "B_src";
            case FieldDeviceMenu.KIND_INDUCTION_COIL -> "N_turns";
            case FieldDeviceMenu.KIND_OPTICAL_EMITTER -> "I_emit";
            case FieldDeviceMenu.KIND_OPTICAL_CHANNEL_FILTER -> "channel";
            case FieldDeviceMenu.KIND_OPTICAL_ATTENUATOR -> "loss";
            case FieldDeviceMenu.KIND_MECHANICAL_EXCITER,
                 FieldDeviceMenu.KIND_HYDRO_EXCITER -> "f";
            default -> "parameter";
        };
    }

    private static String directRangeLabel(int k) {
        return switch (k) {
            case FieldDeviceMenu.KIND_PROBE -> "0..3";
            case FieldDeviceMenu.KIND_FILTER -> "1..4 /sample";
            case FieldDeviceMenu.KIND_REFERENCE -> "0..15 redstone";
            case FieldDeviceMenu.KIND_LAPIS_SOURCE -> "0..100 Lapis • step 5";
            case FieldDeviceMenu.KIND_DIGITAL_REGENERATOR -> "0..2 threshold profile";
            case FieldDeviceMenu.KIND_PRESSURE_REGULATOR,
                 FieldDeviceMenu.KIND_PNEUMATIC_RELIEF_VALVE -> "{25, 50, 75, 100} pressure";
            case FieldDeviceMenu.KIND_PERMANENT_MAGNET -> "1..15 B-index";
            case FieldDeviceMenu.KIND_INDUCTION_COIL -> "1..4 turns";
            case FieldDeviceMenu.KIND_OPTICAL_EMITTER -> "0..15 intensity";
            case FieldDeviceMenu.KIND_OPTICAL_CHANNEL_FILTER -> "channel 0..15";
            case FieldDeviceMenu.KIND_OPTICAL_ATTENUATOR -> "loss 0..8";
            case FieldDeviceMenu.KIND_MECHANICAL_EXCITER,
                 FieldDeviceMenu.KIND_HYDRO_EXCITER -> "1..15 frequency index";
            default -> "read-only";
        };
    }

    private static String adjustmentLabel(int k) {
        return switch (k) {
            case FieldDeviceMenu.KIND_PROBE -> "Channel";
            case FieldDeviceMenu.KIND_FILTER -> "Slew";
            case FieldDeviceMenu.KIND_REFERENCE -> "Output";
            case FieldDeviceMenu.KIND_LAPIS_SOURCE -> "Value";
            case FieldDeviceMenu.KIND_DIGITAL_REGENERATOR -> "Threshold";
            case FieldDeviceMenu.KIND_PRESSURE_REGULATOR -> "Setpoint";
            case FieldDeviceMenu.KIND_PNEUMATIC_RELIEF_VALVE -> "Relief";
            case FieldDeviceMenu.KIND_PERMANENT_MAGNET -> "Strength";
            case FieldDeviceMenu.KIND_INDUCTION_COIL -> "Turns";
            case FieldDeviceMenu.KIND_OPTICAL_EMITTER -> "Intensity";
            case FieldDeviceMenu.KIND_OPTICAL_CHANNEL_FILTER -> "Channel";
            case FieldDeviceMenu.KIND_OPTICAL_ATTENUATOR -> "Loss";
            case FieldDeviceMenu.KIND_MECHANICAL_EXCITER,
                 FieldDeviceMenu.KIND_HYDRO_EXCITER -> "Frequency";
            default -> "Readback";
        };
    }

    private static String deviceName(int k) {
        return switch (k) {
            case FieldDeviceMenu.KIND_PROBE -> "SIGNAL PROBE";
            case FieldDeviceMenu.KIND_FILTER -> "PRECISION FILTER";
            case FieldDeviceMenu.KIND_REFERENCE -> "REDSTONE REFERENCE SOURCE";
            case FieldDeviceMenu.KIND_TERMINAL -> "REDSTONE CABLE TERMINAL";
            case FieldDeviceMenu.KIND_REDSTONE_CABLE -> "REDSTONE SIGNAL CABLE";
            case FieldDeviceMenu.KIND_REDSTONE_JUNCTION -> "JUNCTION POINT";
            case FieldDeviceMenu.KIND_INSTRUMENT_CABLE -> "INSTRUMENT CABLE";
            case FieldDeviceMenu.KIND_DATA_BUS_8 -> "8-BIT DATA BUS";
            case FieldDeviceMenu.KIND_ENCODER -> "REDSTONE BYTE ENCODER";
            case FieldDeviceMenu.KIND_DECODER -> "BYTE TO REDSTONE DECODER";
            case FieldDeviceMenu.KIND_SERIAL_LINE -> "SERIAL DATA LINE";
            case FieldDeviceMenu.KIND_SERIALIZER -> "SERIALIZER";
            case FieldDeviceMenu.KIND_DESERIALIZER -> "DESERIALIZER";
            case FieldDeviceMenu.KIND_DIFFERENTIAL_PAIR -> "DIFFERENTIAL DATA PAIR";
            case FieldDeviceMenu.KIND_DIGITAL_REGENERATOR -> "DIGITAL REGENERATOR";
            case FieldDeviceMenu.KIND_DIFFERENTIAL_DRIVER -> "DIFFERENTIAL DRIVER";
            case FieldDeviceMenu.KIND_DIFFERENTIAL_RECEIVER -> "DIFFERENTIAL RECEIVER";
            case FieldDeviceMenu.KIND_RADIO_TRANSMITTER -> "RADIO TRANSMITTER";
            case FieldDeviceMenu.KIND_RADIO_RECEIVER -> "RADIO RECEIVER";
            case FieldDeviceMenu.KIND_FREE_OPTICAL_TRANSMITTER -> "FREE-SPACE OPTICAL TX";
            case FieldDeviceMenu.KIND_FREE_OPTICAL_RECEIVER -> "FREE-SPACE OPTICAL RX";
            case FieldDeviceMenu.KIND_QUARTZ_DIVIDER -> "QUARTZ CLOCK DIVIDER";
            case FieldDeviceMenu.KIND_QUARTZ_STABILITY -> "QUARTZ STABILITY MONITOR";
            case FieldDeviceMenu.KIND_AMETHYST_RESONATOR -> "AMETHYST RESONATOR";
            case FieldDeviceMenu.KIND_AMETHYST_DUST -> "AMETHYST RESONANCE DUST";
            case FieldDeviceMenu.KIND_AMETHYST_FILTER -> "AMETHYST FREQUENCY FILTER";
            case FieldDeviceMenu.KIND_AMETHYST_TUNED -> "AMETHYST TUNED RESONATOR";
            case FieldDeviceMenu.KIND_AMETHYST_SPECTRUM -> "AMETHYST SPECTRUM ANALYZER";
            case FieldDeviceMenu.KIND_MECHANICAL_EXCITER -> "MECHANICAL EXCITER";
            case FieldDeviceMenu.KIND_SLIME_VIBRATION -> "SLIME VIBRATION CONDUIT";
            case FieldDeviceMenu.KIND_MECHANICAL_RECEIVER -> "MECHANICAL RECEIVER";
            case FieldDeviceMenu.KIND_HONEY_DAMPER -> "HONEY VIBRATION DAMPER";
            case FieldDeviceMenu.KIND_SCULK_INTERFACE -> "SCULK VIBRATION INTERFACE";
            case FieldDeviceMenu.KIND_HYDRO_TUBE -> "HYDROACOUSTIC TUBE";
            case FieldDeviceMenu.KIND_HYDRO_EXCITER -> "HYDROACOUSTIC EXCITER";
            case FieldDeviceMenu.KIND_HYDRO_RECEIVER -> "HYDROACOUSTIC RECEIVER";
            case FieldDeviceMenu.KIND_PHONON_CONDUIT -> "PHONON CONDUIT";
            case FieldDeviceMenu.KIND_THERMAL_ENCODER -> "THERMAL PULSE ENCODER";
            case FieldDeviceMenu.KIND_THERMAL_RECEIVER -> "THERMAL PULSE RECEIVER";
            case FieldDeviceMenu.KIND_SHIELDED_INSTRUMENT_CABLE -> "SHIELDED INSTRUMENT CABLE";
            case FieldDeviceMenu.KIND_WATCHDOG -> "WATCHDOG";
            case FieldDeviceMenu.KIND_SERVO_ACTUATOR -> "SERVO ACTUATOR";
            case FieldDeviceMenu.KIND_SERVO_POSITION_SENSOR -> "SERVO POSITION SENSOR";
            case FieldDeviceMenu.KIND_REDUNDANT_VOTER -> "REDUNDANT VOTER";
            case FieldDeviceMenu.KIND_FAULT_LATCH -> "FAULT LATCH";
            case FieldDeviceMenu.KIND_OPERATIONS_MONITOR -> "OPERATIONS MONITOR";
            case FieldDeviceMenu.KIND_AIR_COMPRESSOR -> "AIR COMPRESSOR";
            case FieldDeviceMenu.KIND_PNEUMATIC_PIPE -> "PNEUMATIC PIPE";
            case FieldDeviceMenu.KIND_AIR_RESERVOIR -> "AIR RESERVOIR";
            case FieldDeviceMenu.KIND_PRESSURE_REGULATOR -> "PRESSURE REGULATOR";
            case FieldDeviceMenu.KIND_PNEUMATIC_RECEIVER -> "PNEUMATIC RECEIVER";
            case FieldDeviceMenu.KIND_PNEUMATIC_VALVE -> "PNEUMATIC VALVE";
            case FieldDeviceMenu.KIND_PNEUMATIC_CHECK_VALVE -> "PNEUMATIC CHECK VALVE";
            case FieldDeviceMenu.KIND_PNEUMATIC_FLOW_METER -> "PNEUMATIC FLOW METER";
            case FieldDeviceMenu.KIND_EDGE_DETECTOR -> "EDGE DETECTOR";
            case FieldDeviceMenu.KIND_PULSE_SHAPER -> "PULSE SHAPER";
            case FieldDeviceMenu.KIND_SIGNAL_TAP -> "SIGNAL TAP";
            case FieldDeviceMenu.KIND_RANGE_SENSOR -> "RANGE SENSOR";
            case FieldDeviceMenu.KIND_LAPIS_LINE -> "LAPIS SIGNAL LINE";
            case FieldDeviceMenu.KIND_LAPIS_SOURCE -> "LAPIS PRECISION SOURCE";
            case FieldDeviceMenu.KIND_QUARTZ_LINE -> "QUARTZ TIMING LINE";
            case FieldDeviceMenu.KIND_QUARTZ_OSCILLATOR -> "QUARTZ OSCILLATOR";
            case FieldDeviceMenu.KIND_PNEUMATIC_PROPORTIONAL_VALVE -> "PNEUMATIC PROPORTIONAL VALVE";
            case FieldDeviceMenu.KIND_PNEUMATIC_RELIEF_VALVE -> "PNEUMATIC RELIEF VALVE";
            case FieldDeviceMenu.KIND_PNEUMATIC_CYLINDER -> "PNEUMATIC CYLINDER";
            case FieldDeviceMenu.KIND_ELECTROMAGNET -> "ELECTROMAGNET";
            case FieldDeviceMenu.KIND_PERMANENT_MAGNET -> "PERMANENT MAGNET";
            case FieldDeviceMenu.KIND_INDUCTION_COIL -> "INDUCTION COIL";
            case FieldDeviceMenu.KIND_MAGNETIC_FIELD_SENSOR -> "MAGNETIC FIELD SENSOR";
            case FieldDeviceMenu.KIND_MAGNETIC_GRADIENT_METER -> "MAGNETIC GRADIENT METER";
            case FieldDeviceMenu.KIND_OPTICAL_FIBER -> "OPTICAL FIBER";
            case FieldDeviceMenu.KIND_OPTICAL_EMITTER -> "OPTICAL EMITTER";
            case FieldDeviceMenu.KIND_OPTICAL_RECEIVER -> "OPTICAL RECEIVER";
            case FieldDeviceMenu.KIND_OPTICAL_POWER_METER -> "OPTICAL POWER METER";
            case FieldDeviceMenu.KIND_OPTICAL_SPLITTER -> "OPTICAL SPLITTER";
            case FieldDeviceMenu.KIND_OPTICAL_CHANNEL_FILTER -> "OPTICAL CHANNEL FILTER";
            case FieldDeviceMenu.KIND_OPTICAL_ATTENUATOR -> "OPTICAL ATTENUATOR";
            case FieldDeviceMenu.KIND_OPTICAL_FIBER_JUNCTION -> "OPTICAL FIBER JUNCTION";
            default -> "UNKNOWN FIELD DEVICE";
        };
    }

    /** Second explicit taxonomy pass: every real kind owns a visible model/family contract. */
    private static String modelContract(int k) {
        return switch (k) {
            case FieldDeviceMenu.KIND_PROBE -> "OBSERVE: selected instrument channel → synchronized sample; no backdrive";
            case FieldDeviceMenu.KIND_FILTER -> "PROCESS: y[n+1] = y[n] + clamp(x[n]-y[n], -Δmax, +Δmax)";
            case FieldDeviceMenu.KIND_REFERENCE -> "SOURCE: y_R = configured Redstone reference 0..15";
            case FieldDeviceMenu.KIND_TERMINAL -> "BOUNDARY: Vanilla Redstone ↔ cable domain; explicit mode owns direction";
            case FieldDeviceMenu.KIND_REDSTONE_CABLE -> "MEDIUM: hop loss = 1; strongest-source resolution";
            case FieldDeviceMenu.KIND_REDSTONE_JUNCTION -> "TOPOLOGY: UP ↔ DOWN only; same medium; conversion = NONE";
            case FieldDeviceMenu.KIND_INSTRUMENT_CABLE -> "MEDIUM: 4 measurement channels; observer network continuity";
            case FieldDeviceMenu.KIND_DATA_BUS_8 -> "PROTOCOL: 8-bit payload • 0..255 • contention consumes margin";
            case FieldDeviceMenu.KIND_ENCODER -> "FIXED CONVERSION: y_byte = x_R ∈ [0,15]  (no rescale)";
            case FieldDeviceMenu.KIND_DECODER -> "FIXED CONVERSION: y_R = valid ? min(15, x_byte) : 0";
            case FieldDeviceMenu.KIND_SERIAL_LINE -> "PROTOCOL: byte-frame transport • fixed link timing";
            case FieldDeviceMenu.KIND_SERIALIZER -> "FIXED CONVERSION: serial_byte = bus_byte ; frame = 8 t/word";
            case FieldDeviceMenu.KIND_DESERIALIZER -> "FIXED CONVERSION: bus_byte = serial_byte ; watchdog = 16 t";
            case FieldDeviceMenu.KIND_DIFFERENTIAL_PAIR -> "PROTOCOL: 1-bit balanced logic";
            case FieldDeviceMenu.KIND_DIGITAL_REGENERATOR -> "PROCESS: serial quality threshold → regenerated byte evidence";
            case FieldDeviceMenu.KIND_DIFFERENTIAL_DRIVER -> "FIXED CONVERSION: b = (x_R > 0) ? 1 : 0";
            case FieldDeviceMenu.KIND_DIFFERENTIAL_RECEIVER -> "FIXED CONVERSION: y_R = (valid ∧ b=1) ? 15 : 0";
            case FieldDeviceMenu.KIND_RADIO_TRANSMITTER -> "LINK: payload + selected channel → bounded radio evidence";
            case FieldDeviceMenu.KIND_RADIO_RECEIVER -> "OBSERVE: decoded radio payload requires valid link evidence";
            case FieldDeviceMenu.KIND_FREE_OPTICAL_TRANSMITTER -> "LINK: free-space optical payload + channel";
            case FieldDeviceMenu.KIND_FREE_OPTICAL_RECEIVER -> "OBSERVE: free-space optical decode is server-authoritative";
            case FieldDeviceMenu.KIND_QUARTZ_DIVIDER -> "PROCESS: valid input ⇒ T_out = min(4096, N·T_in)";
            case FieldDeviceMenu.KIND_QUARTZ_STABILITY -> "OBSERVE: |e_T| = |T_meas - T_nominal|";
            case FieldDeviceMenu.KIND_AMETHYST_RESONATOR -> "WAVE: resonant source evidence; frequency is a model index";
            case FieldDeviceMenu.KIND_AMETHYST_DUST -> "MEDIUM: resonance packet preserves frequency identity";
            case FieldDeviceMenu.KIND_AMETHYST_FILTER -> "PROCESS: A_out = (f_in=f_target) ? max(0,A_in-1) : 0";
            case FieldDeviceMenu.KIND_AMETHYST_TUNED -> "PROCESS: tuned resonance uses server Q/f0 configuration";
            case FieldDeviceMenu.KIND_AMETHYST_SPECTRUM -> "OBSERVE: E[f]=ΣA_i(f), f_dom=argmax(E[f]) • radius 6 • 10t scan • bands 1..15 • observer-only • read-only";
            case FieldDeviceMenu.KIND_MECHANICAL_EXCITER -> "SOURCE: frequency 1..15 + amplitude packet • conflict-aware • exact server-backed frequency";
            case FieldDeviceMenu.KIND_SLIME_VIBRATION -> "HOP: A_next=max(0,A-1); RETAIN @4t: A←max(0,A-2), Q←max(0,Q-10)";
            case FieldDeviceMenu.KIND_MECHANICAL_RECEIVER -> "CONVERSION: mechanical packet → Redstone 0..15";
            case FieldDeviceMenu.KIND_HONEY_DAMPER -> "HOP: A_next=max(0,A-4); damping is fixed";
            case FieldDeviceMenu.KIND_SCULK_INTERFACE -> "SCULK / CALIBRATED-SENSOR EVENT CODE";
            case FieldDeviceMenu.KIND_HYDRO_TUBE -> "HYDROACOUSTIC: medium-dependent packet loss";
            case FieldDeviceMenu.KIND_HYDRO_EXCITER -> "SOURCE: hydro frequency 1..15 + amplitude packet • exact server-backed frequency";
            case FieldDeviceMenu.KIND_HYDRO_RECEIVER -> "CONVERSION: hydro packet → Redstone 0..15";
            case FieldDeviceMenu.KIND_PHONON_CONDUIT -> "PHONON_THERMAL: TTL=8t; retain A−2, Q−10";
            case FieldDeviceMenu.KIND_THERMAL_ENCODER -> "SOURCE: event packet; source clears after 1t";
            case FieldDeviceMenu.KIND_THERMAL_RECEIVER -> "CONVERSION: thermal event packet → Redstone";
            case FieldDeviceMenu.KIND_SHIELDED_INSTRUMENT_CABLE -> "MEDIUM: shielded instrument transport; exposed segments reduce confidence";
            case FieldDeviceMenu.KIND_WATCHDOG -> "STATE: heartbeat age vs timeout; missing evidence fails safe";
            case FieldDeviceMenu.KIND_SERVO_ACTUATOR -> "ACTUATOR: command → bounded position state";
            case FieldDeviceMenu.KIND_SERVO_POSITION_SENSOR -> "OBSERVE: y_R = round(condition_PRECISION(x_servo)) • observer-only • read-only";
            case FieldDeviceMenu.KIND_REDUNDANT_VOTER -> "STATE: spread ≤ tolerance; invalid channel evidence fails closed";
            case FieldDeviceMenu.KIND_FAULT_LATCH -> "STATE: fault ≥ threshold latches until explicit server reset";
            case FieldDeviceMenu.KIND_OPERATIONS_MONITOR -> "OBSERVE: plant state/KPI evidence; never drives process";
            case FieldDeviceMenu.KIND_AIR_COMPRESSOR -> "PNEUMATIC: P_cmd = round(100 · u_R / 15)";
            case FieldDeviceMenu.KIND_PNEUMATIC_PIPE -> "PNEUMATIC: passive pressure transport";
            case FieldDeviceMenu.KIND_AIR_RESERVOIR -> "PNEUMATIC: P_store[n+1] = min(P_line, P_store[n] + 5) when charging";
            case FieldDeviceMenu.KIND_PRESSURE_REGULATOR -> "PNEUMATIC: bounded P_set ∈ {25,50,75,100}";
            case FieldDeviceMenu.KIND_PNEUMATIC_RECEIVER -> "OBSERVE: pressure → synchronized receiver evidence";
            case FieldDeviceMenu.KIND_PNEUMATIC_VALVE -> "PNEUMATIC: OPEN ⇒ BACK ↔ FRONT ; CLOSED ⇒ isolated";
            case FieldDeviceMenu.KIND_PNEUMATIC_CHECK_VALVE -> "PNEUMATIC: permitted flow = BACK → FRONT only ; reverse blocked";
            case FieldDeviceMenu.KIND_PNEUMATIC_FLOW_METER -> "OBSERVE: y_flow = condition_PRECISION(flow_proxy(P_in,P_out,path)) • observer-only • read-only";
            case FieldDeviceMenu.KIND_EDGE_DETECTOR -> "PROCESS: edge mode(x[n-1],x[n]) → pulse";
            case FieldDeviceMenu.KIND_PULSE_SHAPER -> "PROCESS: rising edge → y=15 for W ticks";
            case FieldDeviceMenu.KIND_SIGNAL_TAP -> "FIXED: y_through = y_tap = x_in ; tap never back-drives input";
            case FieldDeviceMenu.KIND_RANGE_SENSOR -> "OBSERVE: bounded range response; scan completeness is evidence";
            case FieldDeviceMenu.KIND_LAPIS_LINE -> "MEDIUM: 0..100 lossless line • single-source topology";
            case FieldDeviceMenu.KIND_LAPIS_SOURCE -> "SOURCE: y_L = configured 0..100 Lapis precision value";
            case FieldDeviceMenu.KIND_QUARTZ_LINE -> "MEDIUM: clock period evidence • single-source topology";
            case FieldDeviceMenu.KIND_QUARTZ_OSCILLATOR -> "SOURCE: server period profile → timing evidence";
            case FieldDeviceMenu.KIND_PNEUMATIC_PROPORTIONAL_VALVE -> "PNEUMATIC: opening = valid(u_UP) ? u_UP : 0";
            case FieldDeviceMenu.KIND_PNEUMATIC_RELIEF_VALVE -> "PNEUMATIC: vent above configured P_relief";
            case FieldDeviceMenu.KIND_PNEUMATIC_CYLINDER -> "PNEUMATIC: x_target = clamp(round(15 · P / 100),0,15)";
            case FieldDeviceMenu.KIND_ELECTROMAGNET -> "FIELD: drive/configuration → server magnetic field evidence";
            case FieldDeviceMenu.KIND_PERMANENT_MAGNET -> "FIELD: B_src is a bounded source-strength index";
            case FieldDeviceMenu.KIND_INDUCTION_COIL -> "FIELD: N_turns + changing field → synchronized EMF evidence";
            case FieldDeviceMenu.KIND_MAGNETIC_FIELD_SENSOR -> "OBSERVE: server field sample; client does not solve B";
            case FieldDeviceMenu.KIND_MAGNETIC_GRADIENT_METER -> "OBSERVE: ΔB_axis = B(+axis,r=6) − B(−axis,r=6) • observer-only • read-only";
            case FieldDeviceMenu.KIND_OPTICAL_FIBER -> "MEDIUM: passive optical continuity • no conversion";
            case FieldDeviceMenu.KIND_OPTICAL_EMITTER -> "SOURCE: configured intensity/channel → optical network";
            case FieldDeviceMenu.KIND_OPTICAL_RECEIVER -> "OBSERVE: VALID ⇔ one physical input ∧ one driver ∧ I>0";
            case FieldDeviceMenu.KIND_OPTICAL_POWER_METER -> "OBSERVE: measurement={I,channel,PortQuality}; drive=NONE";
            case FieldDeviceMenu.KIND_OPTICAL_SPLITTER -> "OPTICAL: I_A = I_B = floor(I_in/2); quantization loss ≤1";
            case FieldDeviceMenu.KIND_OPTICAL_CHANNEL_FILTER -> "OPTICAL: selected channel passes; others rejected";
            case FieldDeviceMenu.KIND_OPTICAL_ATTENUATOR -> "OPTICAL: I_out=max(0,I_in-loss)";
            case FieldDeviceMenu.KIND_OPTICAL_FIBER_JUNCTION -> "MEDIUM: service splice • SERVICE_OPEN = hard isolation";
            default -> "CONTRACT: server state → synchronized HMI evidence; no hidden client physics";
        };
    }

    private static String family(int k) {
        return switch (k) {
            case FieldDeviceMenu.KIND_REDSTONE_CABLE, FieldDeviceMenu.KIND_REDSTONE_JUNCTION,
                 FieldDeviceMenu.KIND_INSTRUMENT_CABLE, FieldDeviceMenu.KIND_SHIELDED_INSTRUMENT_CABLE,
                 FieldDeviceMenu.KIND_DATA_BUS_8, FieldDeviceMenu.KIND_SERIAL_LINE,
                 FieldDeviceMenu.KIND_DIFFERENTIAL_PAIR, FieldDeviceMenu.KIND_LAPIS_LINE,
                 FieldDeviceMenu.KIND_QUARTZ_LINE, FieldDeviceMenu.KIND_AMETHYST_DUST,
                 FieldDeviceMenu.KIND_SLIME_VIBRATION, FieldDeviceMenu.KIND_HONEY_DAMPER,
                 FieldDeviceMenu.KIND_HYDRO_TUBE, FieldDeviceMenu.KIND_PHONON_CONDUIT,
                 FieldDeviceMenu.KIND_OPTICAL_FIBER, FieldDeviceMenu.KIND_OPTICAL_FIBER_JUNCTION -> "INTERCONNECT";
            case FieldDeviceMenu.KIND_AIR_COMPRESSOR, FieldDeviceMenu.KIND_PNEUMATIC_PIPE,
                 FieldDeviceMenu.KIND_AIR_RESERVOIR, FieldDeviceMenu.KIND_PRESSURE_REGULATOR,
                 FieldDeviceMenu.KIND_PNEUMATIC_RECEIVER, FieldDeviceMenu.KIND_PNEUMATIC_VALVE,
                 FieldDeviceMenu.KIND_PNEUMATIC_CHECK_VALVE, FieldDeviceMenu.KIND_PNEUMATIC_FLOW_METER,
                 FieldDeviceMenu.KIND_PNEUMATIC_PROPORTIONAL_VALVE, FieldDeviceMenu.KIND_PNEUMATIC_RELIEF_VALVE,
                 FieldDeviceMenu.KIND_PNEUMATIC_CYLINDER -> "PNEUMATIC";
            case FieldDeviceMenu.KIND_ELECTROMAGNET, FieldDeviceMenu.KIND_PERMANENT_MAGNET,
                 FieldDeviceMenu.KIND_INDUCTION_COIL, FieldDeviceMenu.KIND_MAGNETIC_FIELD_SENSOR,
                 FieldDeviceMenu.KIND_MAGNETIC_GRADIENT_METER -> "MAGNETIC";
            case FieldDeviceMenu.KIND_OPTICAL_EMITTER, FieldDeviceMenu.KIND_OPTICAL_RECEIVER,
                 FieldDeviceMenu.KIND_OPTICAL_POWER_METER, FieldDeviceMenu.KIND_OPTICAL_SPLITTER,
                 FieldDeviceMenu.KIND_OPTICAL_CHANNEL_FILTER, FieldDeviceMenu.KIND_OPTICAL_ATTENUATOR,
                 FieldDeviceMenu.KIND_FREE_OPTICAL_TRANSMITTER, FieldDeviceMenu.KIND_FREE_OPTICAL_RECEIVER -> "OPTICAL";
            case FieldDeviceMenu.KIND_ENCODER, FieldDeviceMenu.KIND_DECODER, FieldDeviceMenu.KIND_SERIALIZER,
                 FieldDeviceMenu.KIND_DESERIALIZER, FieldDeviceMenu.KIND_DIGITAL_REGENERATOR,
                 FieldDeviceMenu.KIND_DIFFERENTIAL_DRIVER, FieldDeviceMenu.KIND_DIFFERENTIAL_RECEIVER,
                 FieldDeviceMenu.KIND_RADIO_TRANSMITTER, FieldDeviceMenu.KIND_RADIO_RECEIVER -> "COMMUNICATIONS";
            case FieldDeviceMenu.KIND_AMETHYST_RESONATOR, FieldDeviceMenu.KIND_AMETHYST_FILTER,
                 FieldDeviceMenu.KIND_AMETHYST_TUNED, FieldDeviceMenu.KIND_AMETHYST_SPECTRUM,
                 FieldDeviceMenu.KIND_MECHANICAL_EXCITER, FieldDeviceMenu.KIND_MECHANICAL_RECEIVER,
                 FieldDeviceMenu.KIND_SCULK_INTERFACE, FieldDeviceMenu.KIND_HYDRO_EXCITER,
                 FieldDeviceMenu.KIND_HYDRO_RECEIVER, FieldDeviceMenu.KIND_THERMAL_ENCODER,
                 FieldDeviceMenu.KIND_THERMAL_RECEIVER -> "WAVE / METROLOGY";
            case FieldDeviceMenu.KIND_WATCHDOG, FieldDeviceMenu.KIND_SERVO_ACTUATOR,
                 FieldDeviceMenu.KIND_SERVO_POSITION_SENSOR, FieldDeviceMenu.KIND_REDUNDANT_VOTER,
                 FieldDeviceMenu.KIND_FAULT_LATCH, FieldDeviceMenu.KIND_OPERATIONS_MONITOR -> "CPS / RELIABILITY";
            case FieldDeviceMenu.KIND_PROBE, FieldDeviceMenu.KIND_FILTER, FieldDeviceMenu.KIND_REFERENCE,
                 FieldDeviceMenu.KIND_TERMINAL, FieldDeviceMenu.KIND_QUARTZ_DIVIDER,
                 FieldDeviceMenu.KIND_QUARTZ_STABILITY, FieldDeviceMenu.KIND_EDGE_DETECTOR,
                 FieldDeviceMenu.KIND_PULSE_SHAPER, FieldDeviceMenu.KIND_SIGNAL_TAP,
                 FieldDeviceMenu.KIND_RANGE_SENSOR, FieldDeviceMenu.KIND_LAPIS_SOURCE,
                 FieldDeviceMenu.KIND_QUARTZ_OSCILLATOR -> "SIGNAL / TIMING";
            default -> "ENGINEERING DEVICE";
        };
    }

    private static String deviceRole(int k) {
        return switch (k) {
            case FieldDeviceMenu.KIND_REFERENCE, FieldDeviceMenu.KIND_LAPIS_SOURCE,
                 FieldDeviceMenu.KIND_QUARTZ_OSCILLATOR, FieldDeviceMenu.KIND_OPTICAL_EMITTER,
                 FieldDeviceMenu.KIND_PERMANENT_MAGNET, FieldDeviceMenu.KIND_AIR_COMPRESSOR,
                 FieldDeviceMenu.KIND_RADIO_TRANSMITTER, FieldDeviceMenu.KIND_FREE_OPTICAL_TRANSMITTER,
                 FieldDeviceMenu.KIND_MECHANICAL_EXCITER, FieldDeviceMenu.KIND_HYDRO_EXCITER,
                 FieldDeviceMenu.KIND_THERMAL_ENCODER -> "SOURCE";
            case FieldDeviceMenu.KIND_SERVO_ACTUATOR, FieldDeviceMenu.KIND_PNEUMATIC_CYLINDER,
                 FieldDeviceMenu.KIND_ELECTROMAGNET -> "ACTUATOR";
            case FieldDeviceMenu.KIND_REDSTONE_CABLE, FieldDeviceMenu.KIND_REDSTONE_JUNCTION,
                 FieldDeviceMenu.KIND_INSTRUMENT_CABLE, FieldDeviceMenu.KIND_SHIELDED_INSTRUMENT_CABLE,
                 FieldDeviceMenu.KIND_DATA_BUS_8, FieldDeviceMenu.KIND_SERIAL_LINE,
                 FieldDeviceMenu.KIND_DIFFERENTIAL_PAIR, FieldDeviceMenu.KIND_LAPIS_LINE,
                 FieldDeviceMenu.KIND_QUARTZ_LINE, FieldDeviceMenu.KIND_AMETHYST_DUST,
                 FieldDeviceMenu.KIND_SLIME_VIBRATION, FieldDeviceMenu.KIND_HONEY_DAMPER,
                 FieldDeviceMenu.KIND_HYDRO_TUBE, FieldDeviceMenu.KIND_PHONON_CONDUIT,
                 FieldDeviceMenu.KIND_OPTICAL_FIBER, FieldDeviceMenu.KIND_OPTICAL_FIBER_JUNCTION -> "PASSIVE MEDIUM";
            case FieldDeviceMenu.KIND_PROBE, FieldDeviceMenu.KIND_QUARTZ_STABILITY,
                 FieldDeviceMenu.KIND_AMETHYST_SPECTRUM, FieldDeviceMenu.KIND_SERVO_POSITION_SENSOR,
                 FieldDeviceMenu.KIND_PNEUMATIC_FLOW_METER, FieldDeviceMenu.KIND_RANGE_SENSOR,
                 FieldDeviceMenu.KIND_MAGNETIC_FIELD_SENSOR, FieldDeviceMenu.KIND_MAGNETIC_GRADIENT_METER,
                 FieldDeviceMenu.KIND_OPTICAL_RECEIVER, FieldDeviceMenu.KIND_OPTICAL_POWER_METER,
                 FieldDeviceMenu.KIND_OPERATIONS_MONITOR -> "OBSERVER";
            case FieldDeviceMenu.KIND_TERMINAL -> "BOUNDARY";
            default -> "PROCESSOR";
        };
    }

    private static String metricLabel(int k, int slot) {
        if (slot == 0) return switch (k) {
            case FieldDeviceMenu.KIND_ENCODER -> "REDSTONE IN";
            case FieldDeviceMenu.KIND_DECODER -> "BYTE IN";
            case FieldDeviceMenu.KIND_SERIALIZER -> "BYTE IN";
            case FieldDeviceMenu.KIND_DESERIALIZER -> "SERIAL IN";
            case FieldDeviceMenu.KIND_PNEUMATIC_FLOW_METER -> "FLOW";
            case FieldDeviceMenu.KIND_PNEUMATIC_CYLINDER -> "PRESSURE";
            case FieldDeviceMenu.KIND_OPTICAL_RECEIVER, FieldDeviceMenu.KIND_OPTICAL_POWER_METER -> "INTENSITY";
            default -> "PRIMARY";
        };
        if (slot == 1) return switch (k) {
            case FieldDeviceMenu.KIND_ENCODER -> "BYTE OUT";
            case FieldDeviceMenu.KIND_DECODER -> "REDSTONE OUT";
            case FieldDeviceMenu.KIND_SERIALIZER -> "SERIAL OUT";
            case FieldDeviceMenu.KIND_DESERIALIZER -> "BYTE OUT";
            case FieldDeviceMenu.KIND_PNEUMATIC_FLOW_METER -> "Δ PRESSURE";
            case FieldDeviceMenu.KIND_PNEUMATIC_CYLINDER -> "POSITION";
            case FieldDeviceMenu.KIND_OPTICAL_RECEIVER, FieldDeviceMenu.KIND_OPTICAL_POWER_METER -> "CHANNEL";
            default -> "SECONDARY";
        };
        return switch (k) {
            case FieldDeviceMenu.KIND_PNEUMATIC_CYLINDER -> "TARGET";
            case FieldDeviceMenu.KIND_DIGITAL_REGENERATOR -> "THRESHOLD";
            case FieldDeviceMenu.KIND_INDUCTION_COIL -> "TURNS";
            case FieldDeviceMenu.KIND_OPTICAL_ATTENUATOR -> "LOSS";
            default -> "TERTIARY";
        };
    }

    private static boolean isSourceMediumIntegrityDevice(int k) {
        return switch (k) {
            case FieldDeviceMenu.KIND_REFERENCE, FieldDeviceMenu.KIND_TERMINAL,
                 FieldDeviceMenu.KIND_REDSTONE_CABLE, FieldDeviceMenu.KIND_REDSTONE_JUNCTION,
                 FieldDeviceMenu.KIND_INSTRUMENT_CABLE, FieldDeviceMenu.KIND_SHIELDED_INSTRUMENT_CABLE,
                 FieldDeviceMenu.KIND_DATA_BUS_8, FieldDeviceMenu.KIND_SERIAL_LINE,
                 FieldDeviceMenu.KIND_DIFFERENTIAL_PAIR, FieldDeviceMenu.KIND_LAPIS_LINE,
                 FieldDeviceMenu.KIND_LAPIS_SOURCE, FieldDeviceMenu.KIND_QUARTZ_LINE,
                 FieldDeviceMenu.KIND_QUARTZ_OSCILLATOR, FieldDeviceMenu.KIND_AMETHYST_DUST,
                 FieldDeviceMenu.KIND_OPTICAL_FIBER, FieldDeviceMenu.KIND_OPTICAL_FIBER_JUNCTION -> true;
            default -> false;
        };
    }

    private static String engineeringHint(int k) {
        return "ENGINEERING: " + family(k) + " • " + adjustmentLabel(k)
                + (directEntryKind(k) ? " uses exact engineering-value entry" : " follows synchronized server evidence");
    }

    private static String diagnosticHint(int k) {
        return "DIAGNOSTIC: " + modelContract(k)
                + " • topology=" + (k == FieldDeviceMenu.KIND_UNKNOWN ? "unsupported" : "fail-closed on incompatible evidence");
    }

    private static String facingName(int ordinal) {
        Direction[] directions = Direction.values();
        if (ordinal < 0 || ordinal >= directions.length) return "N/A";
        return directions[ordinal].getName().toUpperCase(Locale.ROOT);
    }
}
