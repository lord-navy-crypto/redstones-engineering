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
                                        parameterPanel(m)
                                ),
                                RseLdUiComponents.workspacePage(
                                        statePanel(m),
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
        var field = new TextField().setNumbersOnlyInt(0, 160);
        field.layout(l -> l.width(110));
        field.bind(DataBindingBuilder.string(
                () -> adjustable(m.kind()) ? Integer.toString(parameterValue(m)) : "",
                value -> {
                    if (!adjustable(m.kind())) return;
                    try { m.applyParameterFromUi(Integer.parseInt(value)); }
                    catch (NumberFormatException ignored) {}
                }
        ).build());

        var p = new UIElement().addClass("panel_bg");
        p.layout(l -> l.paddingAll(5).gapAll(4));
        p.addChildren(
                new Label().setText("PIONEER PATTERN • RELIABILITY / SAFE STATE"),
                RseLdUiComponents.liveRow("DEVICE", "role", () -> deviceName(m.kind())),
                RseLdUiComponents.liveRow(adjustable(m.kind()) ? "ADJUSTABLE" : "FIXED",
                        parameterSymbol(m.kind()),
                        () -> adjustable(m.kind())
                                ? parameterValue(m) + " • " + parameterRange(m.kind()) + " • direct entry"
                                : "observer-only / read only"),
                new UIElement().layout(l -> l.flexDirection(YogaFlexDirection.ROW).gapAll(6)).addChildren(
                        new Label().setText("DIRECT ENTRY").layout(l -> l.width(92)),
                        field,
                        RseLdUiComponents.serverAction("Maintenance action", m::runMaintenance)
                ),
                RseLdUiComponents.liveRow("ACTION","maintenance",()->maintenanceName(m.kind())),
                new Label().setText("Maintenance is an explicit server action, not a hidden state edit.")
        );
        return p;
    }

    private static UIElement statePanel(ReliabilitySystemMenu m) {
        var p = new UIElement().addClass("panel_bg");
        p.layout(l -> l.paddingAll(5).gapAll(3));
        p.addChildren(
                RseLdUiComponents.liveRow("STATE", "quality", () -> m.quality().name()),
                RseLdUiComponents.liveRow("STATE", "headline", () -> stateName(m)),
                RseLdUiComponents.liveRow("MEASURED", primarySymbol(m.kind()), () -> Integer.toString(m.primary())),
                RseLdUiComponents.liveRow("MEASURED", secondarySymbol(m.kind()), () -> Integer.toString(m.secondary())),
                RseLdUiComponents.liveRow("DERIVED", tertiarySymbol(m.kind()), () -> Integer.toString(m.tertiary())),
                RseLdUiComponents.liveRow("EVIDENCE", auxiliarySymbol(m.kind()), () -> Integer.toString(m.auxiliary())),
                RseLdUiComponents.liveRow("EVIDENCE", "extra", () ->
                        m.extraA() + " / " + m.extraB() + " / " + m.extraC()),
                new Label().setText("Numerical zero and missing/invalid evidence remain distinct; safe-state logic is never inferred from UI presentation alone.")
        );
        return p;
    }

    private static UIElement routePanel(ReliabilitySystemMenu m) {
        var p = new UIElement().addClass("panel_bg");
        p.layout(l -> l.paddingAll(5).gapAll(4));
        p.addChildren(
                new Label().setText("PHYSICAL ROUTE • SERVER OWNED"),
                RseLdUiComponents.liveRow("ROUTE", "facing", () -> m.facing().getName().toUpperCase()),
                new UIElement().layout(l -> l.flexDirection(YogaFlexDirection.ROW).gapAll(6)).addChildren(
                        RseLdUiComponents.serverAction("Cycle direction ▶", m::cycleWholeRouteForward),
                        RseLdUiComponents.serverAction("Cycle RX ▶", m::cycleInputForward),
                        RseLdUiComponents.serverAction("Cycle TX ▶", m::cycleOutputForward)
                ),
                new Label().setText("Routing stays on Route; maintenance actions use the same server methods as Shift-right-click.")
        );
        return p;
    }

    private static UIElement evidencePanel(ReliabilitySystemMenu m) {
        var p = new UIElement().addClass("panel_bg");
        p.layout(l -> l.paddingAll(5).gapAll(3));
        p.addChildren(
                new Label().setText("RELIABILITY EVIDENCE"),
                RseLdUiComponents.liveRow("COUNTER", "events A", () -> Integer.toString(m.tertiary())),
                RseLdUiComponents.liveRow("COUNTER", "events B", () -> Integer.toString(m.auxiliary())),
                RseLdUiComponents.liveRow("COUNTER", "retained A/B", () -> m.extraA() + " / " + m.extraB()),
                RseLdUiComponents.liveRow("STATE", "device", () -> stateName(m)),
                new Label().setText("Counters are retained server evidence; opening the HMI never manufactures events.")
        );
        return p;
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
                    "alarm = (heartbeat seen ∧ age ≥ timeout) ? 15 : 0";
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
            case ReliabilitySystemMenu.KIND_WATCHDOG -> m.extraA() > 0 ? "TIMEOUT" : "HEALTHY";
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
