package dev.redstoneengineering.ui.ldlib;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.ui.menu.ReliabilitySystemMenu;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import org.appliedenergistics.yoga.YogaFlexDirection;

public final class ReliabilitySystemLdUi {
    private ReliabilitySystemLdUi() {}

    public static ModularUI create(ReliabilitySystemMenu m, Player player) {
        var root = new UIElement().addClass("panel_bg");
        root.layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(8).gapAll(6));
        root.addChildren(
                RseLdUiComponents.title("RELIABILITY / SAFE-STATE ENGINEERING HMI"),
                RseLdUiComponents.tabbedWorkspace(
                        630, 400, 850,
                        new String[]{"Overview", "Configure", "Diagnostics"},
                        new UIElement[]{
                                RseLdUiComponents.workspacePage(
                                        RseLdUiComponents.formulaCard(() -> reliabilityEquation(m)),
                                        mechanismPanel(m),
                                        statePanel(m)
                                ),
                                RseLdUiComponents.workspacePage(
                                        parameterPanel(m),
                                        routePanel(m)
                                ),
                                RseLdUiComponents.workspacePage(
                                        evidencePanel(m),
                                        RseLdUiComponents.authorityFooter()
                                )
                        }
                )
        );
        return RseLdUiComponents.responsiveUi(root, player, 650, 440);
    }

    private static UIElement parameterPanel(ReliabilitySystemMenu m) {
        var p = new UIElement().addClass("panel_bg");
        p.layout(l -> l.paddingAll(5).gapAll(4));
        p.addChildren(
                new Label().setText("PIONEER PATTERN • RELIABILITY / SAFE STATE"),
                RseLdUiComponents.liveRow("DEVICE", "role", () -> deviceName(m.kind())),
                RseLdUiComponents.liveRow(adjustable(m.kind()) ? "ADJUSTABLE" : "FIXED",
                        parameterSymbol(m.kind()),
                        () -> adjustable(m.kind())
                                ? parameterValue(m) + " • " + parameterRange(m.kind())
                                : "observer-only / read only")
        );
        // A position sensor is an observer, not a configurator. Do not create
        // an inert editable field for a device that the server cannot tune.
        if (adjustable(m.kind())) {
            int limit = switch (m.kind()) {
                case ReliabilitySystemMenu.KIND_WATCHDOG -> 160;
                case ReliabilitySystemMenu.KIND_SERVO -> 3;
                case ReliabilitySystemMenu.KIND_VOTER -> 4;
                case ReliabilitySystemMenu.KIND_FAULT_LATCH -> 12;
                default -> 0;
            };
            var field = new TextField().setNumbersOnlyInt(0, limit);
            field.layout(l -> l.width(110));
            field.bind(DataBindingBuilder.string(
                    () -> Integer.toString(parameterValue(m)),
                    value -> {
                        try { m.applyParameterFromUi(Integer.parseInt(value)); }
                        catch (NumberFormatException ignored) {}
                    }
            ).build());
            p.addChild(new UIElement()
                    .layout(l -> l.flexDirection(YogaFlexDirection.ROW).flexWrap(dev.vfyjxf.taffy.style.FlexWrap.WRAP).gapAll(6))
                    .addChildren(
                            new Label().setText("DIRECT ENTRY").layout(l -> l.width(92)),
                            field));
        } else {
            p.addChild(RseLdUiComponents.fixedRow("operator setpoint",
                    () -> "NO EDITABLE PARAMETER",
                    "The servo position sensor only reports measured mechanical state"));
        }
        p.addChildren(
                RseLdUiComponents.serverAction("Maintenance action", m::runMaintenance),
                RseLdUiComponents.liveRow("ACTION","maintenance",()->maintenanceName(m.kind())),
                RseLdUiComponents.note("Maintenance is an explicit server action, not a hidden state edit."));
        return p;
    }

    private static UIElement statePanel(ReliabilitySystemMenu m) {
        var p = new UIElement().addClass("panel_bg");
        p.layout(l -> l.paddingAll(5).gapAll(3));
        p.addChildren(
                RseLdUiComponents.liveRow("STATE", "quality", () -> m.quality().name()),
                RseLdUiComponents.liveRow("STATE", "headline", () -> stateName(m)),
                RseLdUiComponents.liveRow("MEASURED", primarySymbol(m.kind()), () -> primaryReadout(m)),
                RseLdUiComponents.liveRow("MEASURED", secondarySymbol(m.kind()), () -> secondaryReadout(m)),
                RseLdUiComponents.liveRow("DERIVED", tertiarySymbol(m.kind()), () -> tertiaryReadout(m)),
                RseLdUiComponents.liveRow("EVIDENCE", auxiliarySymbol(m.kind()), () -> Integer.toString(m.auxiliary())),
                RseLdUiComponents.liveRow("EVIDENCE", "extra", () ->
                        m.extraA() + " / " + m.extraB() + " / " + m.extraC()),
                RseLdUiComponents.liveRow("PROVENANCE", "measurement", () -> evidenceInterpretation(m)),
                RseLdUiComponents.note("Numerical zero and missing/invalid evidence remain distinct; safe-state logic is never inferred from UI presentation alone.")
        );
        return p;
    }

    private static UIElement routePanel(ReliabilitySystemMenu m) {
        var p = new UIElement().addClass("panel_bg");
        p.layout(l -> l.paddingAll(5).gapAll(4));
        p.addChildren(
                new Label().setText("PHYSICAL ROUTE • SERVER OWNED"),
                RseLdUiComponents.liveRow("ROUTE", "facing", () -> m.facing().getName().toUpperCase()),
                new UIElement().layout(l -> l.flexDirection(YogaFlexDirection.ROW).flexWrap(dev.vfyjxf.taffy.style.FlexWrap.WRAP).gapAll(6)).addChildren(
                        RseLdUiComponents.serverAction("Cycle direction ▶", m::cycleWholeRouteForward),
                        RseLdUiComponents.serverAction("Cycle RX ▶", m::cycleInputForward),
                        RseLdUiComponents.serverAction("Cycle TX ▶", m::cycleOutputForward)
                ),
                RseLdUiComponents.note("Routing stays on Route; maintenance actions use the same server methods as Shift-right-click.")
        );
        return p;
    }

    /** Live operands are server snapshots; the HMI never re-votes or drives a permit. */
    private static UIElement mechanismPanel(ReliabilitySystemMenu m) {
        var p = new UIElement().addClass("panel_bg");
        p.layout(l -> l.paddingAll(5).gapAll(3));
        p.addChildren(
                new Label().setText("LIVE RELIABILITY MECHANISM • SYNCHRONIZED INPUTS"),
                RseLdUiComponents.liveRow("MODEL", "device", () -> deviceName(m.kind())),
                RseLdUiComponents.liveRow("MODEL", "operands", () -> switch (m.kind()) {
                    case ReliabilitySystemMenu.KIND_WATCHDOG ->
                            "age=" + m.primary() + "t / timeout=" + m.secondary() + "t";
                    case ReliabilitySystemMenu.KIND_SERVO ->
                            "x_cmd=" + m.secondary() + " / x=" + m.primary()
                                    + " / slew=" + m.extraC() + " units/t";
                    case ReliabilitySystemMenu.KIND_POSITION_SENSOR ->
                            "measured=" + m.primary() + " / feedback=" + m.secondary();
                    case ReliabilitySystemMenu.KIND_VOTER ->
                            "valid=" + m.secondary() + "/3 / spread=" + m.tertiary()
                                    + " / tolerance=" + m.auxiliary();
                    case ReliabilitySystemMenu.KIND_FAULT_LATCH ->
                            "T_fault=" + m.secondary() + " / latched=" + (m.extraA() != 0);
                    default -> "UNSUPPORTED";
                }),
                RseLdUiComponents.liveRow("DECISION", "server evidence", () -> stateName(m)),
                RseLdUiComponents.liveRow("EVIDENCE", "interpretation", () -> evidenceInterpretation(m))
        );
        return p;
    }

    private static UIElement evidencePanel(ReliabilitySystemMenu m) {
        var p = new UIElement().addClass("panel_bg");
        p.layout(l -> l.paddingAll(5).gapAll(3));
        p.addChildren(
                new Label().setText("DEVICE-SPECIFIC RELIABILITY EVIDENCE"),
                RseLdUiComponents.liveRow("QUALITY", "source", () -> m.quality().name()),
                RseLdUiComponents.liveRow("EVIDENCE", "retained / observed", () -> switch (m.kind()) {
                    case ReliabilitySystemMenu.KIND_WATCHDOG ->
                            "timeouts=" + m.tertiary() + " / heartbeat transitions=" + m.auxiliary();
                    case ReliabilitySystemMenu.KIND_SERVO ->
                            "soft-limit hits=" + m.extraB() + " / braking=" + (m.extraA() != 0);
                    case ReliabilitySystemMenu.KIND_POSITION_SENSOR ->
                            "metrology samples=" + m.tertiary() + " / quality=" + m.quality().name();
                    case ReliabilitySystemMenu.KIND_VOTER ->
                            "max spread=" + m.extraA() + " / disagreement events=" + m.extraB();
                    case ReliabilitySystemMenu.KIND_FAULT_LATCH ->
                            "trips=" + m.tertiary() + " / resets=" + m.auxiliary();
                    default -> "UNAVAILABLE";
                }),
                RseLdUiComponents.liveRow("SAFETY", "decision detail", () -> switch (m.kind()) {
                    case ReliabilitySystemMenu.KIND_WATCHDOG ->
                            m.extraA() > 0 ? "TIMEOUT ALARM • output=15" : "NO TIMEOUT OUTPUT • not proof of heartbeat";
                    case ReliabilitySystemMenu.KIND_SERVO ->
                            "velocity=" + m.tertiary() + " / error=" + m.auxiliary();
                    case ReliabilitySystemMenu.KIND_POSITION_SENSOR ->
                            "observer only • no configurable coefficient";
                    case ReliabilitySystemMenu.KIND_VOTER ->
                            "quorum=" + m.secondary() + "/3 • "
                                    + (m.quality() == PortQuality.VALID ? "AGREEMENT VALID"
                                    : "UNVERIFIED / DEGRADED • never mark healthy from zero");
                    case ReliabilitySystemMenu.KIND_FAULT_LATCH ->
                            "latched=" + (m.extraA() != 0) + " / reset input active=" + (m.extraB() != 0);
                    default -> "UNAVAILABLE";
                }),
                RseLdUiComponents.note("Counts and flags are retained server evidence; zero is never treated as proof of sensor presence.")
        );
        return p;
    }

    /** Retained histories, operator thresholds, and current sensor measurements
     * have distinct meanings. Do not convert a missing signal to a measured 0. */
    private static boolean currentEvidenceValid(ReliabilitySystemMenu m) {
        return m.quality() == PortQuality.VALID
                && (m.kind() != ReliabilitySystemMenu.KIND_POSITION_SENSOR || m.tertiary() > 0);
    }

    private static String primaryReadout(ReliabilitySystemMenu m) {
        if (currentEvidenceValid(m)) return Integer.toString(m.primary());
        if (m.kind() == ReliabilitySystemMenu.KIND_WATCHDOG)
            return m.primary() + " ticks • retained age, heartbeat unverified";
        return "NOT READY • " + m.quality().name() + " (raw=" + m.primary() + ")";
    }

    private static String secondaryReadout(ReliabilitySystemMenu m) {
        if (m.kind() == ReliabilitySystemMenu.KIND_WATCHDOG
                || m.kind() == ReliabilitySystemMenu.KIND_FAULT_LATCH)
            return m.secondary() + " • configured threshold";
        if (m.kind() == ReliabilitySystemMenu.KIND_SERVO)
            return m.secondary() + " • server command";
        return currentEvidenceValid(m) ? Integer.toString(m.secondary())
                : "NOT READY • " + m.quality().name() + " (raw=" + m.secondary() + ")";
    }

    private static String tertiaryReadout(ReliabilitySystemMenu m) {
        if (m.kind() == ReliabilitySystemMenu.KIND_WATCHDOG
                || m.kind() == ReliabilitySystemMenu.KIND_FAULT_LATCH
                || m.kind() == ReliabilitySystemMenu.KIND_POSITION_SENSOR)
            return m.tertiary() + " • retained count";
        return currentEvidenceValid(m) ? Integer.toString(m.tertiary())
                : "NOT READY • " + m.quality().name();
    }

    private static String evidenceInterpretation(ReliabilitySystemMenu m) {
        if (!currentEvidenceValid(m))
            return "NOT READY • raw/retained != current measured value";
        return "CURRENT QUALITY VALID • retained counters are history, not live signals";
    }

    private static boolean adjustable(int kind) {
        return kind == ReliabilitySystemMenu.KIND_WATCHDOG
                || kind == ReliabilitySystemMenu.KIND_SERVO
                || kind == ReliabilitySystemMenu.KIND_VOTER
                || kind == ReliabilitySystemMenu.KIND_FAULT_LATCH;
    }

    private static int parameterValue(ReliabilitySystemMenu m) {
        return switch (m.kind()) {
            case ReliabilitySystemMenu.KIND_WATCHDOG -> m.secondary();
            case ReliabilitySystemMenu.KIND_SERVO -> m.extraC();
            case ReliabilitySystemMenu.KIND_VOTER -> m.auxiliary();
            case ReliabilitySystemMenu.KIND_FAULT_LATCH -> m.secondary();
            default -> 0;
        };
    }

    private static String parameterSymbol(int kind) {
        return switch (kind) {
            case ReliabilitySystemMenu.KIND_WATCHDOG -> "timeout";
            case ReliabilitySystemMenu.KIND_SERVO -> "slew";
            case ReliabilitySystemMenu.KIND_VOTER -> "tolerance";
            case ReliabilitySystemMenu.KIND_FAULT_LATCH -> "T_fault";
            default -> "parameter";
        };
    }

    private static String parameterRange(int kind) {
        return switch (kind) {
            case ReliabilitySystemMenu.KIND_WATCHDOG -> "{20,40,80,160} ticks";
            case ReliabilitySystemMenu.KIND_SERVO -> "{1,2,3} units/tick";
            case ReliabilitySystemMenu.KIND_VOTER -> "{0,1,2,4} spread";
            case ReliabilitySystemMenu.KIND_FAULT_LATCH -> "{1,4,8,12} redstone";
            default -> "read only";
        };
    }

    private static String reliabilityEquation(ReliabilitySystemMenu m) {
        return switch (m.kind()) {
            case ReliabilitySystemMenu.KIND_WATCHDOG ->
                    "alarm = (age ≥ timeout) ? 15 : 0 • heartbeat provenance separate";
            case ReliabilitySystemMenu.KIND_SERVO ->
                    "POSITION: e=x_cmd-x, |Δx|≤slew ; VELOCITY: command maps to signed velocity";
            case ReliabilitySystemMenu.KIND_VOTER ->
                    "vote = median(3 valid) or rounded mean(2 valid) ; healthy ⇔ spread ≤ tolerance";
            case ReliabilitySystemMenu.KIND_FAULT_LATCH ->
                    "latched ← latched ∨ (fault ≥ threshold) ; reset explicitly clears";
            default -> "feedback = measured mechanical state → Redstone";
        };
    }

    /**
     * Safety/reliability labels must fail closed on absent or stale evidence.
     * A zero output / zero error is not proof of a healthy watchdog, agreeing
     * voter or safely positioned actuator if its physical input is unverified.
     */
    private static String stateName(ReliabilitySystemMenu m) {
        PortQuality quality = m.quality();
        if (m.kind() == ReliabilitySystemMenu.KIND_VOTER && quality == PortQuality.FAULT)
            return "DEGRADED VOTE • QUORUM OR DISAGREEMENT FAULT";
        if (quality == PortQuality.FAULT || quality == PortQuality.DOMAIN_MISMATCH
                || quality == PortQuality.TOPOLOGY_ERROR) {
            return "EVIDENCE FAULT • " + quality.name();
        }
        if (quality == PortQuality.NO_SIGNAL || quality == PortQuality.STALE
                || quality == PortQuality.NOT_READY) {
            return "UNVERIFIED • " + quality.name();
        }
        if (quality == PortQuality.SATURATED) return "SATURATED • CHECK INPUT";
        return switch (m.kind()) {
            case ReliabilitySystemMenu.KIND_WATCHDOG -> m.extraA() > 0 ? "TIMEOUT" : "NO TIMEOUT • HEARTBEAT UNPROVEN";
            case ReliabilitySystemMenu.KIND_SERVO -> m.extraA() == 1 ? "BRAKING" : m.auxiliary() == 0 ? "AT COMMAND" : "MOVING / ERROR";
            case ReliabilitySystemMenu.KIND_POSITION_SENSOR -> "MEASUREMENT VALID";
            case ReliabilitySystemMenu.KIND_VOTER -> m.extraC() == 1 ? "DEGRADED" : "NOMINAL";
            case ReliabilitySystemMenu.KIND_FAULT_LATCH -> m.extraA() == 1 ? "LATCHED" : "CLEAR";
            default -> "UNCLASSIFIED RELIABILITY DEVICE";
        };
    }

    private static String maintenanceName(int kind) {
        return switch (kind) {
            case ReliabilitySystemMenu.KIND_WATCHDOG -> "Reset watchdog diagnostics";
            case ReliabilitySystemMenu.KIND_SERVO -> "Home / reset trajectory";
            case ReliabilitySystemMenu.KIND_POSITION_SENSOR -> "Reset position metrology";
            case ReliabilitySystemMenu.KIND_VOTER -> "Reset voter diagnostics";
            default -> "Manual reset latch";
        };
    }

    private static String deviceName(int kind) {
        return switch (kind) {
            case ReliabilitySystemMenu.KIND_WATCHDOG -> "WATCHDOG";
            case ReliabilitySystemMenu.KIND_SERVO -> "SERVO ACTUATOR";
            case ReliabilitySystemMenu.KIND_POSITION_SENSOR -> "SERVO POSITION SENSOR";
            case ReliabilitySystemMenu.KIND_VOTER -> "REDUNDANT VOTER";
            default -> "FAULT LATCH";
        };
    }

    private static String primarySymbol(int kind) {
        return switch (kind) {
            case ReliabilitySystemMenu.KIND_WATCHDOG -> "age";
            case ReliabilitySystemMenu.KIND_SERVO -> "x";
            case ReliabilitySystemMenu.KIND_POSITION_SENSOR -> "mechanical";
            case ReliabilitySystemMenu.KIND_VOTER -> "vote";
            default -> "alarm";
        };
    }

    private static String secondarySymbol(int kind) {
        return switch (kind) {
            case ReliabilitySystemMenu.KIND_WATCHDOG -> "timeout";
            case ReliabilitySystemMenu.KIND_SERVO -> "x_cmd";
            case ReliabilitySystemMenu.KIND_POSITION_SENSOR -> "feedback";
            case ReliabilitySystemMenu.KIND_VOTER -> "valid";
            default -> "T_fault";
        };
    }

    private static String tertiarySymbol(int kind) {
        return switch (kind) {
            case ReliabilitySystemMenu.KIND_WATCHDOG -> "timeouts";
            case ReliabilitySystemMenu.KIND_SERVO -> "velocity";
            case ReliabilitySystemMenu.KIND_POSITION_SENSOR -> "samples";
            case ReliabilitySystemMenu.KIND_VOTER -> "spread";
            default -> "trips";
        };
    }

    private static String auxiliarySymbol(int kind) {
        return switch (kind) {
            case ReliabilitySystemMenu.KIND_WATCHDOG -> "transitions";
            case ReliabilitySystemMenu.KIND_SERVO -> "error";
            case ReliabilitySystemMenu.KIND_POSITION_SENSOR -> "evidence";
            case ReliabilitySystemMenu.KIND_VOTER -> "tolerance";
            default -> "resets";
        };
    }
}
