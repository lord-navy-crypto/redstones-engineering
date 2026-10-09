package dev.redstoneengineering.ui.ldlib;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.redstoneengineering.block.CalibrationModuleBlock;
import dev.redstoneengineering.block.CopperCapacitorBlock;
import dev.redstoneengineering.block.FaultInjectorBlock;
import dev.redstoneengineering.block.PwmControllerBlock;
import dev.redstoneengineering.block.SampleHoldBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.ui.menu.UniversalFieldDeviceMenu;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import dev.vfyjxf.taffy.style.FlexWrap;
import org.appliedenergistics.yoga.YogaFlexDirection;

import java.util.Locale;

/**
 * LDLib2 universal engineering HMI.
 *
 * <p>This is intentionally generic: one synchronized tree serves the many
 * EngineeringPortProvider block families handled by UniversalFieldDeviceMenu.
 * Physics and process state remain server-owned.</p>
 */
public final class UniversalFieldDeviceLdUi {
    private UniversalFieldDeviceLdUi() {}

    public static ModularUI create(UniversalFieldDeviceMenu menu, Player player) {
        var root = new UIElement().addClass("panel_bg");
        root.layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(10).gapAll(8));

        var overview = page(
                RseLdUiComponents.liveRow("LIVE STATE", "HEALTH", menu::operationalHealthLabel),
                RseLdUiComponents.liveRow("EVIDENCE", "snapshot", () ->
                        menu.snapshotReady() ? "SERVER PORTS SYNCED" : "NOT READY • first port snapshot pending"),
                RseLdUiComponents.liveRow("EVIDENCE", "quality", menu::evidenceStateLabel),
                RseLdUiComponents.liveRow("I/O", "route", menu::portRouteLabel),
                modelPreviewPanel(menu),
                mechanismPanel(menu),
                portsPanel(menu)
        );
        var configure = page(
                parameterPanel(menu),
                systemStatePanel(menu)
        );
        var pioneer = page(pioneerPanel(menu));
        var evidence = page(
                historyPolicyPanel(menu),
                RseLdUiComponents.authorityFooter()
        );

        configure.setDisplay(false);
        pioneer.setDisplay(false);
        evidence.setDisplay(false);

        var workspace = new ScrollerView()
                .scrollerStyle(style -> style
                        .mode(ScrollerMode.BOTH)
                        .verticalScrollDisplay(ScrollDisplay.AUTO)
                        .horizontalScrollDisplay(ScrollDisplay.AUTO)
                        .minScrollPixel(8)
                        .maxScrollPixel(64));
        workspace.layout(l -> l.flex(1));
        workspace.viewPort(view -> view.layout(l -> l.paddingAll(8)));
        workspace.viewContainer(view -> view.layout(l -> l.widthPercent(100).minWidth(440).paddingAll(8).gapAll(8)));
        workspace.addScrollViewChildren(overview, configure, pioneer, evidence);

        var tabs = RseLdUiComponents.standaloneTabs(workspace,
                new String[]{"Overview", "Configure", "Pioneer", "Evidence"},
                new UIElement[]{overview, configure, pioneer, evidence});

        root.addChildren(
                RseLdUiComponents.title("UNIVERSAL ENGINEERING HMI"),
                tabs,
                RseLdUiComponents.note("SCROLL • wheel Y • Shift+wheel X"),
                workspace
        );
        return RseLdUiComponents.responsiveUi(root, player, 620, 430);
    }

    private static UIElement page(UIElement... children) {
        var page = new UIElement();
        page.layout(l -> l.widthPercent(100).paddingAll(12).gapAll(10));
        page.addChildren(children);
        return page;
    }

    /**
     * Put the block's actual Pioneer law above the fold. The full six-value
     * measurements and interpretation remain available on the Pioneer tab.
     * Never turn observed/derived/solver variables into fake tuning controls.
     */
    private static UIElement modelPreviewPanel(UniversalFieldDeviceMenu menu) {
        int kind = menu.configKind();
        var panel = new UIElement().addClass("panel_bg");
        panel.layout(l -> l.paddingAll(8).gapAll(7));
        panel.addChild(new Label().setText("PIONEER • ACTUAL IMPLEMENTED MODEL"));
        if (menu.pioneerProcessKind() != UniversalFieldDeviceMenu.PIONEER_PROCESS_NONE) {
            panel.addChild(RseLdUiComponents.formulaCard(processEquation(menu.pioneerProcessKind())));
        } else if (menu.pioneerMeasurementKind() != UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_NONE) {
            panel.addChild(RseLdUiComponents.formulaCard(measurementEquation(menu.pioneerMeasurementKind())));
        } else {
            panel.addChild(RseLdUiComponents.formulaCard(universalContract(menu.configKind())));
        }
        if (kind == UniversalFieldDeviceMenu.CONFIG_SAFETY_INTERLOCK) {
            // Interpret only the last server-evaluated failure mask, not client-side
            // redstone sampling. An unknown mask must not be presented as permission.
            panel.addChild(RseLdUiComponents.liveRow("SAFETY", "evaluated mask",
                    () -> menu.configPrimary() < 0 ? "NOT EVALUATED" : Integer.toString(menu.configPrimary())));
            panel.addChild(RseLdUiComponents.liveRow("SAFETY", "permissives A / B / C",
                    () -> interlockInputs(menu.configPrimary())));
            panel.addChild(RseLdUiComponents.liveRow("OUTPUT", "permit",
                    () -> menu.configPrimary() < 0 ? "UNVERIFIED • request safe 0"
                            : menu.configSecondary() != 0 ? "15 • PERMIT" : "0 • BLOCKED"));
        }
        // The old Overview showed the equation but concealed its live operands
        // behind the Pioneer tab. Preview actual synchronized process/metrology
        // values here for every supported Universal device family.
        appendPioneerLivePreview(menu, panel);
        if (primaryDirectKind(kind) || primaryCycleKind(kind)) {
            panel.addChild(RseLdUiComponents.liveRow(
                    "ADJUSTABLE", primarySymbol(kind),
                    () -> primaryDisplay(menu) + " • " + primaryRange(kind)));
        }
        if (secondaryDirectKind(kind)) {
            panel.addChild(RseLdUiComponents.liveRow(
                    "ADJUSTABLE", secondarySymbol(kind),
                    () -> secondaryDisplay(menu) + " • " + secondaryRange(kind)));
        }
        if (kind != UniversalFieldDeviceMenu.CONFIG_NONE) {
            panel.addChild(RseLdUiComponents.liveRow("CONFIG", "server model",
                    () -> configName(menu.configKind())));
        } else {
            panel.addChild(RseLdUiComponents.fixedRow("tuning",
                    () -> "NO SHARED CONTROL",
                    "measurement, solver and derived values are not operator knobs"));
        }
        panel.addChild(new Label().setText(
                "Configure → validated server parameters / actions; Pioneer → physical variables and evidence."));
        return panel;
    }

    private static void appendPioneerLivePreview(UniversalFieldDeviceMenu menu, UIElement panel) {
        int process = menu.pioneerProcessKind();
        int measurement = menu.pioneerMeasurementKind();
        if (process != UniversalFieldDeviceMenu.PIONEER_PROCESS_NONE) {
            String[] labels = processLabels(process);
            String[] units = processUnits(process);
            String[] roles = processRoles(process);
            for (int i=0, shown=0; i<labels.length && shown<3; i++) {
                if (labels[i].isBlank()) continue;
                final int slot=i;
                final String unit=units[i];
                panel.addChild(RseLdUiComponents.liveRow(
                        roles[i], labels[i],
                        () -> processValue(menu, process, slot)+(unit.isBlank()?"":" "+unit)));
                shown++;
            }
            panel.addChild(RseLdUiComponents.liveRow(
                    "EVIDENCE", "Pioneer process quality",
                    () -> menu.pioneerProcessEvidenceQuality().name()));
        } else if (measurement != UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_NONE) {
            String[] labels=measurementLabels(measurement);
            String[] units=measurementUnits(measurement);
            String[] roles=measurementRoles(measurement);
            for (int i=0, shown=0; i<labels.length && shown<3; i++) {
                if (labels[i].isBlank()) continue;
                final int slot=i;
                final String unit=units[i];
                panel.addChild(RseLdUiComponents.liveRow(
                        roles[i], labels[i],
                        () -> measurementValue(menu, measurement, slot)+(unit.isBlank()?"":" "+unit)));
                shown++;
            }
            panel.addChild(RseLdUiComponents.liveRow(
                    "EVIDENCE", "Pioneer measurement quality",
                    () -> menu.pioneerEvidenceQuality().name()));
        }
        // Only a concise preview lives on Overview. The full six-slot
        // contract, authority, coverage and interpretation remain on Pioneer.
    }

    private static UIElement portsPanel(UniversalFieldDeviceMenu menu) {
        var panel = new UIElement().addClass("panel_bg");
        panel.layout(l -> l.paddingAll(5).gapAll(3));
        panel.addChild(new Label().setText("DECLARED ENGINEERING PORTS"));
        for (Direction side : Direction.values()) {
            panel.addChild(RseLdUiComponents.liveRow("PORT", side.getName().toUpperCase(Locale.ROOT),
                    () -> portText(menu, side)));
        }
        var routeControls = new UIElement().layout(l -> l.flexDirection(YogaFlexDirection.ROW).flexWrap(FlexWrap.WRAP).gapAll(6));
        boolean hasRouteControl = false;
        if (menu.routeKind() != UniversalFieldDeviceMenu.ROUTE_NONE) {
            routeControls.addChild(RseLdUiComponents.serverAction("Cycle direction ▶", menu::cycleWholeRouteForward));
            hasRouteControl = true;
        }
        if (menu.hasInputEndpoint()) {
            routeControls.addChild(RseLdUiComponents.serverAction("Cycle RX ▶", menu::cycleInputForward));
            hasRouteControl = true;
        }
        if (menu.hasOutputEndpoint()) {
            routeControls.addChild(RseLdUiComponents.serverAction("Cycle TX ▶", menu::cycleOutputForward));
            hasRouteControl = true;
        }
        if (hasRouteControl) {
            panel.addChild(routeControls);
        } else {
            panel.addChild(new Label().setText(
                    "READ-ONLY TOPOLOGY • no server-supported route mutation for this device"));
        }
        return panel;
    }

    private static UIElement mechanismPanel(UniversalFieldDeviceMenu menu) {
        var panel = new UIElement().addClass("panel_bg");
        panel.layout(l -> l.paddingAll(5).gapAll(3));
        panel.addChildren(
                new Label().setText("MECHANISM FLOW • SERVER-AUTHORITATIVE"),
                RseLdUiComponents.liveRow("RX / INPUT", "endpoint",
                        () -> menu.hasInputEndpoint()
                                 ? (menu.snapshotReady() ? "DECLARED • synchronized port evidence"
                                         : "DECLARED • data pending") : "NONE"),
                RseLdUiComponents.liveRow("MODEL", "contract",
                        () -> universalContract(menu.configKind())),
                RseLdUiComponents.liveRow("STATE", "snapshot", menu::operationalHealthLabel),
                RseLdUiComponents.liveRow("TX / OUTPUT", "endpoint",
                        () -> menu.hasOutputEndpoint()
                                 ? (menu.snapshotReady() ? "DECLARED • synchronized port evidence"
                                         : "DECLARED • data pending") : "NONE"),
                RseLdUiComponents.liveRow("EVIDENCE", "quality", menu::evidenceStateLabel),
                new Label().setText(
                        "Client presents synchronized evidence and validated operator intent; "
                                + "routing, physics, process state and mutations remain server-owned.")
        );
        return panel;
    }

    private static String portText(UniversalFieldDeviceMenu menu, Direction side) {
        if (!menu.hasPort(side)) return "—";
        if (!menu.snapshotReady()) return "DECLARED • NOT READY • awaiting server port value / domain / quality";
        String io = menu.isBidirectional(side) ? "BIDIR"
                : menu.isInput(side) ? "RX"
                : menu.isOutput(side) ? "TX" : "PASSIVE";
        return io + " • " + menu.domain(side).label()
                + " • " + menu.portKind(side).name()
                + " • value=" + menu.value(side)
                + " [" + menu.minimum(side) + ".." + menu.maximum(side) + "]"
                + " • " + menu.quality(side).name();
    }

    private static UIElement parameterPanel(UniversalFieldDeviceMenu menu) {
        int kind = menu.configKind();
        var panel = new UIElement().addClass("panel_bg");
        panel.layout(l -> l.paddingAll(5).gapAll(4));
        panel.addChildren(
                new Label().setText("FORMULA PARAMETER WORKBENCH"),
                RseLdUiComponents.liveRow("MODEL", "config", () -> configName(menu.configKind()))
        );

        if (kind == UniversalFieldDeviceMenu.CONFIG_NONE) {
            panel.addChildren(
                    RseLdUiComponents.fixedRow("configuration", () -> "NONE",
                            "this device exposes measurement/process evidence but no shared tunable"),
                    new Label().setText("READ-ONLY HMI • no fake control is created for a server model with no configurable coefficient")
            );
        } else {
            addPrimaryControl(panel, menu, kind);
            addSecondaryControl(panel, menu, kind);

            if (hasExplicitAction(kind)) {
                panel.addChildren(
                        RseLdUiComponents.liveRow("ACTION", "operator", () -> actionLabel(menu)),
                        RseLdUiComponents.serverAction("Execute explicit action", menu::runConfigAction)
                );
            }

            if (hasToggle(kind)) {
                panel.addChildren(
                        RseLdUiComponents.liveRow("TOGGLE", "invert",
                                () -> menu.configSecondary() != 0 ? "INVERTED" : "NORMAL"),
                        RseLdUiComponents.serverAction("Toggle invert", menu::toggleConfiguration)
                );
            }
        }

        if (menu.pioneerProcessKind() != UniversalFieldDeviceMenu.PIONEER_PROCESS_NONE) {
            panel.addChild(RseLdUiComponents.liveRow(
                    "EVIDENCE", "process", () -> menu.pioneerProcessEvidenceQuality().name()));
        }

        panel.addChild(new Label().setText(
                "Universal HMI rule: numeric values use exact entry; discrete modes use one-button cycle; "
                        + "actions/toggles appear only when the server model actually supports them; no hidden universal physics."));
        return panel;
    }

    private static void addPrimaryControl(UIElement panel, UniversalFieldDeviceMenu menu, int kind) {
        if (primaryDirectKind(kind)) {
            var input = new TextField();
            input.layout(l -> l.width(130));
            input.bind(DataBindingBuilder.string(
                    () -> primaryDisplay(menu),
                    value -> applyPrimary(menu, value)
            ).build());

            panel.addChildren(
                    RseLdUiComponents.liveRow("ADJUSTABLE", primarySymbol(kind),
                            () -> primaryDisplay(menu) + " • " + primaryRange(kind)),
                    new UIElement().layout(l -> l.flexDirection(YogaFlexDirection.ROW).flexWrap(FlexWrap.WRAP).gapAll(6)).addChildren(
                            new Label().setText("DIRECT").layout(l -> l.width(72)),
                            input,
                            new Label().setText(primaryRange(kind)).layout(l -> l.flex(1))
                    )
            );
            return;
        }

        if (primaryCycleKind(kind)) {
            panel.addChildren(
                    RseLdUiComponents.liveRow("ADJUSTABLE", primarySymbol(kind),
                            () -> primaryDisplay(menu) + " • " + primaryRange(kind)),
                    RseLdUiComponents.serverAction(
                            "Cycle " + primarySymbol(kind) + " ▶",
                            menu::cyclePrimaryForward)
            );
        }
    }

    private static void addSecondaryControl(UIElement panel, UniversalFieldDeviceMenu menu, int kind) {
        if (!secondaryDirectKind(kind)) return;

        var input = new TextField();
        input.layout(l -> l.width(130));
        input.bind(DataBindingBuilder.string(
                () -> secondaryDisplay(menu),
                value -> applySecondary(menu, value)
        ).build());

        panel.addChildren(
                RseLdUiComponents.liveRow("ADJUSTABLE", secondarySymbol(kind),
                        () -> secondaryDisplay(menu) + " • " + secondaryRange(kind)),
                new UIElement().layout(l -> l.flexDirection(YogaFlexDirection.ROW).flexWrap(FlexWrap.WRAP).gapAll(6)).addChildren(
                        new Label().setText("DIRECT").layout(l -> l.width(72)),
                        input,
                        new Label().setText(secondaryRange(kind)).layout(l -> l.flex(1))
                )
        );
    }

    private static boolean primaryCycleKind(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_TRANSDUCER,
                 UniversalFieldDeviceMenu.CONFIG_LAPIS_RANGE,
                 UniversalFieldDeviceMenu.CONFIG_SAMPLE_HOLD,
                 UniversalFieldDeviceMenu.CONFIG_CALIBRATION,
                 UniversalFieldDeviceMenu.CONFIG_FAULT_INJECTOR -> true;
            default -> false;
        };
    }

    private static boolean hasExplicitAction(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.CONFIG_MOLECULAR_RECEIVER,
                 UniversalFieldDeviceMenu.CONFIG_ALARM,
                 UniversalFieldDeviceMenu.CONFIG_SAMPLE_HOLD,
                 UniversalFieldDeviceMenu.CONFIG_FAULT_INJECTOR,
                 UniversalFieldDeviceMenu.CONFIG_SEQUENCE_CONTROLLER,
                 UniversalFieldDeviceMenu.CONFIG_SAFETY_INTERLOCK,
                 UniversalFieldDeviceMenu.CONFIG_TOPOLOGY_DEBUGGER,
                 UniversalFieldDeviceMenu.CONFIG_COPPER_FUSE,
                 UniversalFieldDeviceMenu.CONFIG_IRON_CORE -> true;
            default -> false;
        };
    }

    private static boolean hasToggle(int kind) {
        return kind == UniversalFieldDeviceMenu.CONFIG_PWM;
    }

    private static UIElement systemStatePanel(UniversalFieldDeviceMenu menu) {
        var panel = new UIElement().addClass("panel_bg");
        panel.layout(l -> l.paddingAll(5).gapAll(3));
        panel.addChildren(
                new Label().setText("SYSTEM / OPERATOR STATE"),
                RseLdUiComponents.liveRow("STATE", "device", () -> systemHeadline(menu)),
                RseLdUiComponents.liveRow("STATE", "detail", () -> systemDetail(menu))
        );
        if (menu.configKind() == UniversalFieldDeviceMenu.CONFIG_SAFETY_INTERLOCK) {
            panel.addChildren(
                    RseLdUiComponents.liveRow("PERMISSIVE", "A / B / C", () -> interlockInputs(menu.configPrimary())),
                    RseLdUiComponents.liveRow("EVIDENCE", "blocked / permitted evaluations", () ->
                            menu.configPrimary() < 0 ? "NOT EVALUATED" :
                                    menu.interlockBlockedTicks() + " / " + menu.interlockPermittedTicks()),
                    RseLdUiComponents.liveRow("EVIDENCE", "permit transitions", () ->
                            menu.configPrimary() < 0 ? "NOT EVALUATED" :
                                    Integer.toString(menu.interlockTransitions())),
                    new Label().setText("Counts are retained server evaluations (nominal 2-tick cycle), not elapsed game ticks.")
            );
        }
        if (hasExplicitAction(menu.configKind())) {
            panel.addChildren(
                    RseLdUiComponents.liveRow("ACTION", "operator", () -> actionLabel(menu)),
                    RseLdUiComponents.serverAction("Execute explicit action", menu::runConfigAction)
            );
        } else {
            panel.addChild(RseLdUiComponents.fixedRow(
                    "operator action", () -> "NONE",
                    "no fake action is exposed when the authoritative server model has no explicit action"));
        }
        return panel;
    }

    private static String systemHeadline(UniversalFieldDeviceMenu menu) {
        return switch (menu.configKind()) {
            case UniversalFieldDeviceMenu.CONFIG_ALARM -> switch (menu.configSecondary()) {
                case 2 -> "ALARM • ACTIVE / UNACK";
                case 1 -> "ALARM • ACTIVE / ACK";
                default -> "ALARM • CLEAR";
            };
            case UniversalFieldDeviceMenu.CONFIG_FAULT_INJECTOR ->
                    menu.configSecondary() != 0 ? "FAULT INJECTOR • ARMED" : "FAULT INJECTOR • SAFE";
            case UniversalFieldDeviceMenu.CONFIG_SEQUENCE_CONTROLLER ->
                    menu.configPrimary() <= 0 ? "SEQUENCE • IDLE" : "SEQUENCE • STEP " + Math.min(4, menu.configPrimary());
            case UniversalFieldDeviceMenu.CONFIG_SAFETY_INTERLOCK ->
                    menu.configPrimary() < 0 ? "INTERLOCK • REACQUIRING"
                            : menu.configSecondary() != 0 ? "INTERLOCK • PERMIT" : "INTERLOCK • BLOCKED";
            case UniversalFieldDeviceMenu.CONFIG_TOPOLOGY_DEBUGGER ->
                    "TOPOLOGY DEBUGGER • SYNCHRONIZED";
            default -> "SERVER-SYNCHRONIZED DEVICE STATE";
        };
    }

    private static String systemDetail(UniversalFieldDeviceMenu menu) {
        return switch (menu.configKind()) {
            case UniversalFieldDeviceMenu.CONFIG_ALARM ->
                    menu.configSecondary() == 2 ? "attention=UNACKNOWLEDGED • ACK is allowed"
                            : menu.configSecondary() == 1 ? "attention=ACKNOWLEDGED • process alarm may remain latched"
                            : "condition clear";
            case UniversalFieldDeviceMenu.CONFIG_FAULT_INJECTOR ->
                    menu.configSecondary() != 0 ? "ARMED / INJECTION ACTIVE" : "SAFE / PASS-THROUGH";
            case UniversalFieldDeviceMenu.CONFIG_SEQUENCE_CONTROLLER ->
                    "Completed cycles = " + menu.configSecondary();
            case UniversalFieldDeviceMenu.CONFIG_SAFETY_INTERLOCK ->
                    "Missing permissives = " + failedPermissives(menu.configPrimary())
                            + " • permit=" + (menu.configPrimary() < 0 ? "UNVERIFIED" : menu.configSecondary() != 0 ? "15" : "0");
            case UniversalFieldDeviceMenu.CONFIG_TOPOLOGY_DEBUGGER ->
                    "Target mode = " + (menu.configSecondary() != 0 ? "VANILLA REDSTONE" : "ENGINEERING PORTS")
                            + " • scan count=" + menu.configPrimary();
            default -> "Current evidence = SYNCHRONIZED SNAPSHOT";
        };
    }

    private static String actionLabel(UniversalFieldDeviceMenu menu) {
        return switch (menu.configKind()) {
            case UniversalFieldDeviceMenu.CONFIG_MOLECULAR_RECEIVER -> "Reset measurement history";
            case UniversalFieldDeviceMenu.CONFIG_ALARM ->
                    menu.configSecondary() == 2 ? "Acknowledge active alarm" : "Alarm already clear / acknowledged";
            case UniversalFieldDeviceMenu.CONFIG_SAMPLE_HOLD -> "Clear held value";
            case UniversalFieldDeviceMenu.CONFIG_FAULT_INJECTOR -> "Reset fault statistics";
            case UniversalFieldDeviceMenu.CONFIG_SEQUENCE_CONTROLLER -> "Reset sequence to IDLE";
            case UniversalFieldDeviceMenu.CONFIG_SAFETY_INTERLOCK -> "Reset diagnostic counters";
            case UniversalFieldDeviceMenu.CONFIG_TOPOLOGY_DEBUGGER -> "Reset scan counters";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_FUSE -> "Reset fuse latch • re-evaluate next tick";
            case UniversalFieldDeviceMenu.CONFIG_IRON_CORE -> "Demagnetize core";
            default -> "No explicit operator action";
        };
    }

    private static String interlockInputs(int mask) {
        if (mask < 0) return "A=? / B=? / C=? • NOT EVALUATED";
        return "A=" + ((mask & 1) == 0 ? "PERMIT" : "BLOCK")
                + " / B=" + ((mask & 2) == 0 ? "PERMIT" : "BLOCK")
                + " / C=" + ((mask & 4) == 0 ? "PERMIT" : "BLOCK");
    }

    private static String failedPermissives(int mask) {
        if (mask < 0) return "NOT EVALUATED";
        if (mask == 0) return "NONE";
        StringBuilder missing = new StringBuilder();
        if ((mask & 1) != 0) missing.append("A");
        if ((mask & 2) != 0) missing.append(missing.isEmpty() ? "B" : ", B");
        if ((mask & 4) != 0) missing.append(missing.isEmpty() ? "C" : ", C");
        return missing.toString();
    }

    private static UIElement historyPolicyPanel(UniversalFieldDeviceMenu menu) {
        var panel = new UIElement().addClass("panel_bg");
        panel.layout(l -> l.paddingAll(5).gapAll(3));
        panel.addChildren(
                new Label().setText("LIVE ONLY • NO RETAINED HISTORY"),
                new Label().setText("SYNCHRONIZED SNAPSHOT"),
                RseLdUiComponents.liveRow("Current evidence", "snapshot", menu::evidenceStateLabel),
                new Label().setText("Retained chronology belongs in analyzers, monitors, or the Diagnostic Tablet.")
        );
        return panel;
    }

    private static UIElement pioneerPanel(UniversalFieldDeviceMenu menu) {
        if (menu.pioneerProcessKind() != UniversalFieldDeviceMenu.PIONEER_PROCESS_NONE) {
            return processPioneerPanel(menu);
        }
        if (menu.pioneerMeasurementKind() != UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_NONE) {
            return measurementPioneerPanel(menu);
        }
        var panel = new UIElement().addClass("panel_bg");
        panel.layout(l -> l.paddingAll(5).gapAll(3));
        panel.addChildren(
                new Label().setText("PIONEER / PROCESS SNAPSHOT"),
                new Label().setText("No dedicated Pioneer measurement/process contract for this block."),
                RseLdUiComponents.liveRow("EVIDENCE", "quality", menu::evidenceStateLabel)
        );
        return panel;
    }

    private static UIElement processPioneerPanel(UniversalFieldDeviceMenu menu) {
        int kind = menu.pioneerProcessKind();
        var panel = new UIElement().addClass("panel_bg");
        panel.layout(l -> l.paddingAll(5).gapAll(3));
        panel.addChildren(
                new Label().setText(processWaveTitle(kind)),
                RseLdUiComponents.formulaCard(processEquation(kind))
        );
        String[] labels = processLabels(kind);
        String[] units = processUnits(kind);
        String[] roles = processRoles(kind);
        for (int i = 0; i < labels.length; i++) {
            final int slot = i;
            final String label = labels[i];
            final String unit = units[i];
            final String role = roles[i];
            if (label.isBlank()) continue;
            panel.addChild(RseLdUiComponents.liveRow(
                    role,
                    label,
                    () -> processValue(menu, kind, slot) + (unit.isBlank() ? "" : " " + unit)
            ));
        }
        panel.addChildren(
                RseLdUiComponents.liveRow("EVIDENCE", "quality", () -> menu.pioneerProcessEvidenceQuality().name()),
                new Label().setText(processInterpretation(kind))
        );
        return panel;
    }

    private static UIElement measurementPioneerPanel(UniversalFieldDeviceMenu menu) {
        int kind = menu.pioneerMeasurementKind();
        var panel = new UIElement().addClass("panel_bg");
        panel.layout(l -> l.paddingAll(5).gapAll(3));
        panel.addChildren(
                new Label().setText("PIONEER WAVE 13 • MEASUREMENT"),
                RseLdUiComponents.formulaCard(measurementEquation(kind))
        );
        String[] labels = measurementLabels(kind);
        String[] units = measurementUnits(kind);
        String[] roles = measurementRoles(kind);
        for (int i = 0; i < labels.length; i++) {
            final int slot = i;
            final String label = labels[i];
            final String unit = units[i];
            final String role = roles[i];
            if (label.isBlank()) continue;
            panel.addChild(RseLdUiComponents.liveRow(
                    role,
                    label,
                    () -> measurementValue(menu, kind, slot) + (unit.isBlank() ? "" : " " + unit)
            ));
        }
        panel.addChildren(
                RseLdUiComponents.liveRow("EVIDENCE", "quality", () -> menu.pioneerEvidenceQuality().name()),
                new Label().setText(measurementInterpretation(kind))
        );
        return panel;
    }

    private static String processWaveTitle(int kind) {
        if (kind >= UniversalFieldDeviceMenu.PIONEER_PROCESS_IRON_CORE) return "PIONEER WAVE 17 • MATERIAL / STORAGE / THERMAL";
        if (kind >= UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_NOISE) return "PIONEER WAVE 16 • ACTIVE SOURCE / TIMING";
        if (kind >= UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_WIRE) return "PIONEER WAVE 15 • COPPER ELECTRICAL";
        return "PIONEER WAVE 14 • SIGNAL / TRANSDUCTION";
    }

    private static String[] processLabels(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_CALIBRATION -> a("x_obs","x_ref","y","profile","bias","samples");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_SAMPLE_HOLD -> a("y_hold","captures","age","edge","trigger","reset");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_PWM -> a("u","T","N_on","D_eff","e_q","phase");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_TEMPERATURE -> a("T_norm","y_L","Δt_sample","resolution","noise","latency");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_MAGNETIC -> a("B_norm","y_L","Δt_sample","resolution","noise","latency");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_OPTICAL -> a("I_norm","y_L","Δt_sample","resolution","noise","latency");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_VOLTAGE -> a("V_norm","y_L","Δt_sample","resolution","noise","latency");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_WIRE, UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_JUNCTION -> a("V_node","drivers","ports","","","");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_VOLTAGE_SOURCE -> a("V_set","output faces","","","","");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_LOAD -> a("V","R","I","P","feeds","");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_SERIES_RESISTOR -> a("V_in","R_s","R_load","V_out","I","evaluated");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_CAPACITOR -> a("V_in","C_index","τ","charge","V_out","evaluated");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_FUSE -> a("V_in","I_rating","R_eq","I","V_out","trip latch");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_NOISE -> a("μ","|η|max","y[n]","Δt_sample","initialized","");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_QUARTZ_OSCILLATOR -> a("T_nom","J","Δt_half,last","jitter offset","clock","timing ready");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_QUARTZ_PHASE_DELAY -> a("input signal","D","pending","pulse_out","edge history","");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_QUARTZ_LAPIS_SAMPLER -> a("x_L","clock","y_hold","hold quality","","");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_SOUL_INJECTOR -> a("u_R","packet","attached nodes","output faces","","");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_SOUL_METER -> a("Q_s","y_R","y_R(model)","","","");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_MOLECULAR_RECEIVER -> a("c_raw","c_filt","peak","sensitivity index","g","y_R");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_IRON_CORE -> a("B_applied","B_threshold","scan radius","remanence","coverage","");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_MASS -> a("T","T_env","T_neighbor","T_target","C_index","ΔT_max");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_HEATER -> a("V","R","I","P","T","T_target");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_RADIATOR -> a("k_cool","N_mass","T_avg","T_hot","T_floor","Δt");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_CALORIMETER -> a("T","ΔT_20t","C_mean","C·ΔT","N_mass","history ready");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_SOUL_CONDUIT -> a("J","age","quality","decay period","ports","");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_SOUL_RESERVOIR -> a("Q_s","age","quality","decay period","ports","");
            default -> a("p1","p2","p3","p4","p5","p6");
        };
    }

    private static String[] processUnits(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_LOAD,
                 UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_HEATER -> a("V-eq","R-eq","I-eq","P-eq","","");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_SERIES_RESISTOR -> a("V-eq","R-eq","R-eq","V-eq","I-eq","");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_CAPACITOR -> a("V-eq","profile","ticks","%","V-eq","");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_FUSE -> a("V-eq","I-eq","R-eq","I-eq","V-eq","");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_NOISE -> a("Lapis","Lapis","Lapis","ticks","","");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_QUARTZ_OSCILLATOR -> a("ticks","ticks","ticks","ticks","","");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_QUARTZ_LAPIS_SAMPLER -> a("Lapis","pulse","Lapis","","","");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_SOUL_METER -> a("charge","redstone","redstone","","","");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_QUARTZ_PHASE_DELAY -> a("pulse","ticks","ticks","","","");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_MASS -> a("T-index","T-index","T-index","T-index","index","T-index");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_RADIATOR -> a("index/tick","bodies","T-index","T-index","T-index","ticks");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_CALORIMETER -> a("T-index","T-index","capacity units","relative heat","bodies","");
            default -> a("","","","","","");
        };
    }

    private static String[] processRoles(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_WIRE,
                 UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_JUNCTION ->
                    a("MEASURED","EVIDENCE","TOPOLOGY","","","");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_VOLTAGE_SOURCE ->
                    a("ADJUSTABLE","TOPOLOGY","","","","");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_PWM -> a("MEASURED","ADJUSTABLE","DERIVED","DERIVED","EVIDENCE","SOLVER");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_LOAD -> a("MEASURED","ADJUSTABLE","DERIVED","DERIVED","EVIDENCE","");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_SERIES_RESISTOR -> a("MEASURED","ADJUSTABLE","SOLVER","DERIVED","DERIVED","EVIDENCE");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_CAPACITOR -> a("MEASURED","ADJUSTABLE","PROFILE","SOLVER","DERIVED","EVIDENCE");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_FUSE -> a("MEASURED","ADJUSTABLE","SOLVER","DERIVED","DERIVED","STATE");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_NOISE -> a("ADJUSTABLE","ADJUSTABLE","MEASURED","PROFILE","EVIDENCE","");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_QUARTZ_OSCILLATOR -> a("ADJUSTABLE","ADJUSTABLE","MEASURED","EVIDENCE","STATE","EVIDENCE");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_QUARTZ_LAPIS_SAMPLER -> a("MEASURED","MEASURED","STATE","EVIDENCE","","");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_SOUL_METER -> a("MEASURED","OUTPUT","DERIVED","","","");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_MOLECULAR_RECEIVER -> a("MEASURED","STATE","EVIDENCE","ADJUSTABLE","PROFILE","OUTPUT");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_CALORIMETER -> a("MEASURED","MEASURED","SOLVER","DERIVED","MEASURED","EVIDENCE");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_MASS -> a("STATE","MEASURED","MEASURED","SOLVER","ADJUSTABLE","DERIVED");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_HEATER -> a("MEASURED","ADJUSTABLE","DERIVED","DERIVED","STATE","SOLVER");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_RADIATOR ->
                    a("ADJUSTABLE","MEASURED","MEASURED","MEASURED","FIXED","FIXED");
            default -> a("STATE","STATE","STATE","STATE","STATE","STATE");
        };
    }

    private static String processValue(UniversalFieldDeviceMenu m, int kind, int slot) {
        int raw = switch (slot) {
            case 0 -> m.pioneerProcessPrimary();
            case 1 -> m.pioneerProcessSecondary();
            case 2 -> m.pioneerProcessTertiary();
            case 3 -> m.pioneerProcessQuaternary();
            case 4 -> m.pioneerProcessQuinary();
            default -> m.pioneerProcessSenary();
        };
        if (kind == UniversalFieldDeviceMenu.PIONEER_PROCESS_CALIBRATION && slot == 3) return CalibrationModuleBlock.profileName(raw);
        if (kind == UniversalFieldDeviceMenu.PIONEER_PROCESS_SAMPLE_HOLD && slot == 3) return SampleHoldBlock.modeName(raw);
        if (kind == UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_NOISE && slot <= 2) return String.format(Locale.ROOT, "%.2f", raw / 100.0);
        // This sampler keeps Lapis in integer hundredths on the server.
        if (kind == UniversalFieldDeviceMenu.PIONEER_PROCESS_QUARTZ_LAPIS_SAMPLER) {
            if (slot == 0 || slot == 2) return String.format(Locale.ROOT, "%.2f", raw / 100.0);
            if (slot == 3) {
                PortQuality[] qualities = PortQuality.values();
                return raw >= 0 && raw < qualities.length ? qualities[raw].name() : "UNKNOWN";
            }
        }
        if (kind == UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_LOAD && (slot == 2 || slot == 3)) return String.format(Locale.ROOT, "%.3f", raw / 1000.0);
        if (kind == UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_SERIES_RESISTOR && (slot == 2 || slot == 4)) return String.format(Locale.ROOT, "%.3f", raw / 1000.0);
        if (kind == UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_FUSE && (slot == 2 || slot == 3)) return String.format(Locale.ROOT, "%.3f", raw / 1000.0);
        if (kind == UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_HEATER && (slot == 2 || slot == 3)) return String.format(Locale.ROOT, "%.3f", raw / 1000.0);
        if ((kind == UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_TEMPERATURE
                || kind == UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_MAGNETIC
                || kind == UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_OPTICAL
                || kind == UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_VOLTAGE) && slot <= 1)
            return String.format(Locale.ROOT, "%.2f", raw / 100.0);
        if ((kind == UniversalFieldDeviceMenu.PIONEER_PROCESS_SOUL_CONDUIT
                || kind == UniversalFieldDeviceMenu.PIONEER_PROCESS_SOUL_RESERVOIR) && slot == 1 && raw < 0) return "NONE";
        return Integer.toString(raw);
    }

    private static String processEquation(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_CALIBRATION ->
                    "MODEL: y = profile(x_obs); residual = y - x_ref; profile ∈ {FULL, LOW, MID, HIGH, INVERT}";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_SAMPLE_HOLD ->
                    "MODEL: configured trigger edge ⇒ y_hold ← x; otherwise y_hold[n]=y_hold[n-1]; RESET ⇒ 0";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_PWM ->
                    "MODEL: N_on=round((u/15)·T); PWM=15 when phase<N_on else 0; INHIBIT ⇒ 0";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_TEMPERATURE ->
                    "MODEL: x=clamp(T_index,0,100); y_L = SensorModel.condition(x, profile)";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_MAGNETIC ->
                    "MODEL: x=round(100·clamp(B,0,15)/15); y_L = SensorModel.condition(x, profile)";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_OPTICAL ->
                    "MODEL: x=round(100·clamp(I,0,15)/15); y_L = SensorModel.condition(x, profile)";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_VOLTAGE ->
                    "MODEL: x=round(100·clamp(V,0,15)/15); y_L = SensorModel.condition(x, profile)";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_WIRE ->
                    "MODEL: V_node = resolved Copper registry value; quality depends on topology, scan completeness and driver count";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_VOLTAGE_SOURCE ->
                    "MODEL: V_out=V_set on declared Copper OUTPUT faces";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_LOAD ->
                    "MODEL: I = V/R; P = V·I";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_SERIES_RESISTOR ->
                    "MODEL: V_out = V_in·R_load/(R_s+R_load); I = V_in/(R_s+R_load)";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_CAPACITOR ->
                    "MODEL: q_target=round(100·V_in/15); q approaches q_target using τ∈{2,4,8,16} ticks";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_FUSE ->
                    "MODEL: TRIPPED ← TRIPPED ∨ (I>I_rating); trip state is server-latched protection evidence";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_JUNCTION ->
                    "MODEL: explicit Copper splice; >1 active driver = TOPOLOGY_ERROR";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_NOISE ->
                    "MODEL: y[n]=clamp(μ + η_det(n,pos),0,100); η_det is deterministic and bounded by |η|max";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_QUARTZ_OSCILLATOR ->
                    "MODEL: half-interval=max(1,T_nom/2 + j), with bounded scheduling jitter j";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_QUARTZ_PHASE_DELAY ->
                    "MODEL: real post-init rising edge ⇒ pending=D; countdown completion emits the pulse";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_QUARTZ_LAPIS_SAMPLER ->
                    "MODEL: valid QUARTZ rising edge ⇒ y_hold←x_L; opening the HMI never captures a sample";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_SOUL_INJECTOR ->
                    "MODEL (fictional): packet=4u_R into attached loaded Soul nodes";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_SOUL_METER ->
                    "MODEL (fictional): y_R=floor(15·Q_s/100) only when Soul measurement quality is VALID";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_MOLECULAR_RECEIVER ->
                    "MODEL: c_raw=clamp(round(g·Σ r_cloud/(1+d²)),0,15); c_filt approaches c_raw by 1 every 5 ticks";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_IRON_CORE ->
                    "MODEL: complete radius-2 scan ∧ B_applied≥8 ⇒ MAGNETIZED; remanence persists until explicit demagnetize";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_MASS ->
                    "MODEL: T_target=floor((2·T_env+T_neighbor)/3); T←approach(T,T_target,max(1,5-C)); Δt=5C ticks";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_HEATER ->
                    "MODEL: P=V²/R; T_target=clamp(20+round(P/3),20,100); T←approach(T,T_target,3) every 2 ticks";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_RADIATOR ->
                    "MODEL: every 10 ticks each adjacent mass T>20 ⇒ T←max(20,T-k_cool)";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_CALORIMETER ->
                    "MODEL: Δt_history = 20 ticks • read-only retained interval; T=mean(adjacent thermal masses); ΔT_20t=T[n]-T[n-1]; relative heat=C_sum·ΔT";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_SOUL_CONDUIT ->
                    "MODEL (fictional): transient Soul Flux J decays by 1 each 20 ticks; zero/absent conduit flux is NO_SIGNAL";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_SOUL_RESERVOIR ->
                    "MODEL (fictional): stored Q_s decays by 1 each 40 ticks; initialized empty Q_s=0 remains VALID storage state";
            default -> "MODEL: server-authoritative process transformation and retained evidence";
        };
    }

    private static String processInterpretation(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_CAPACITOR -> "τ is the visible discrete response constant; it is an RSE proxy, not an SI capacitance claim. Visible τ selects the implemented discrete response profile.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_QUARTZ_OSCILLATOR -> "Configuration and realized interval remain separate: the latter is retained server evidence.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_SOUL_INJECTOR -> "Soul Flux is explicitly Minecraft-fictional; packet = 4·u_R • read-only law. Quality and topology remain server evidence.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_SOUL_METER -> "Soul Flux is explicitly Minecraft-fictional; y_R=floor(15·Q_s/100) • read-only. Invalid evidence forces safe zero.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_SOUL_CONDUIT -> "Soul Flux is explicitly Minecraft-fictional; read-only decay law. Quality and topology remain server evidence.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_SOUL_RESERVOIR -> "Soul Flux is explicitly Minecraft-fictional; read-only storage law. Initialized empty storage remains distinct from absent transient flux.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_MOLECULAR_RECEIVER -> "g is ADJUSTABLE; gain map is FIXED: server-supported {6,9,12,16} set.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_IRON_CORE -> "B_threshold and scan radius are FIXED implemented limits; remanence is server state.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_WIRE -> "network law is FIXED / read-only topology contract.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_SERIES_RESISTOR -> "R_load and current are retained from the authoritative server tick. Opening the HMI never performs another load-network scan.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_JUNCTION -> "network law is FIXED: explicit splice • >1 driver = TOPOLOGY_ERROR; read-only topology contract.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_QUARTZ_LAPIS_SAMPLER -> "trigger is FIXED: QUARTZ rising edge; opening the HMI never captures a sample.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_RADIATOR -> "T_floor is FIXED at the model ambient floor; passive cooling cannot refrigerate below it.";
            default -> "Values above are synchronized server state; opening the HMI never performs a second physics/network solve.";
        };
    }

    private static String[] measurementLabels(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_TEMPERATURE -> a("T_cached","T_target","N_bodies","coverage");
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_LIGHT -> a("B_local","y","profile","Δt_sample");
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_TANK -> a("h","scanned","expected","y");
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_ENTITY_DENSITY -> a("N","coverage","y","r_xy");
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_LAPIS_METER -> a("m","","","");
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_LAPIS_RANGE -> a("d","R","y_L","profile");
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_ANALOG_INDICATOR -> a("x_back","display","","");
            default -> a("m1","m2","m3","m4");
        };
    }

    private static String[] measurementUnits(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_TEMPERATURE -> a("T-index","T-index","bodies","faces");
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_LIGHT -> a("light","redstone","","ticks");
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_TANK -> a("blocks","cells","cells","redstone");
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_ENTITY_DENSITY -> a("entities","","redstone","blocks");
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_LAPIS_METER -> a("Lapis","","","");
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_LAPIS_RANGE -> a("blocks","blocks","Lapis","");
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_ANALOG_INDICATOR -> a("redstone","redstone","","");
            default -> a("","","","");
        };
    }

    private static String[] measurementRoles(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_TEMPERATURE -> a("SOLVER","MEASURED","MEASURED","EVIDENCE");
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_LIGHT ->
                    a("MEASURED","DERIVED","PROFILE","FIXED");
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_TANK ->
                    a("MEASURED","EVIDENCE","EVIDENCE","DERIVED");
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_ENTITY_DENSITY ->
                    a("MEASURED","EVIDENCE","DERIVED","FIXED");
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_ANALOG_INDICATOR ->
                    a("MEASURED","DERIVED","","");
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_LAPIS_RANGE -> a("MEASURED","ADJUSTABLE","DERIVED","PROFILE");
            default -> a("MEASURED","DERIVED","PROFILE","EVIDENCE");
        };
    }

    private static String measurementValue(UniversalFieldDeviceMenu m, int kind, int slot) {
        int raw = switch (slot) {
            case 0 -> m.pioneerPrimary();
            case 1 -> m.pioneerSecondary();
            case 2 -> m.pioneerTertiary();
            default -> m.pioneerQuaternary();
        };
        if (kind == UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_LAPIS_METER && slot == 0)
            return String.format(Locale.ROOT, "%.2f", raw / 100.0);
        if ((kind == UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_LIGHT && slot == 2)
                || (kind == UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_LAPIS_RANGE && slot == 3))
            return UniversalFieldDeviceMenu.profileNameForUi(raw);
        if (kind == UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_LAPIS_RANGE && slot == 0 && raw < 0) return "NO TARGET";
        if (kind == UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_LAPIS_RANGE && slot == 2)
            return String.format(Locale.ROOT, "%.2f", raw / 100.0);
        return Integer.toString(raw);
    }

    private static String measurementEquation(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_TEMPERATURE ->
                    "MODEL: T_target = N>0 ? floor(ΣT_body / N) : T_environment; update only with 6/6 coverage";
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_LIGHT ->
                    "MODEL: y = round(condition(B_local, BALANCED)), 0 ≤ y ≤ 15";
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_TANK ->
                    "MODEL: h = contiguous loaded fluid cells above; y = condition(min(15,h), PRECISION)";
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_ENTITY_DENSITY ->
                    "MODEL: N = living entities in AABB inflate(4,2,4); y = condition(min(15,N), BALANCED)";
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_LAPIS_METER ->
                    "MODEL: m = unique Lapis sample on selected face; display = m / 100";
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_LAPIS_RANGE ->
                    "MODEL: target ⇒ x = round(100·d/R); no target=NO_SIGNAL; incomplete aperture=STALE";
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_ANALOG_INDICATOR ->
                    "MODEL: display = clamp(x_back,0,15); source quality remains independent of numeric zero";
            default -> "MODEL: synchronized server measurement evidence";
        };
    }

    private static String measurementInterpretation(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_TEMPERATURE -> "aperture is FIXED: 6 adjacent faces. Incomplete six-face coverage retains the last trustworthy cached temperature instead of manufacturing ambient data.";
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_LAPIS_METER -> "Observer-only: a real zero Lapis sample remains VALID evidence; conflict/stale quality is separate.";
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_LAPIS_RANGE -> "R is a real server-bound range control; changing range/profile invalidates stale sampled output before reacquisition.";
            default -> "Measurement values and quality are synchronized from the server; the client does not re-sample the world.";
        };
    }

    private static String[] a(String... values) {
        return values;
    }

    private static void applyPrimary(UniversalFieldDeviceMenu menu, String text) {
        Integer raw = parsePrimary(menu.configKind(), text);
        if (raw != null) menu.applyPrimaryRawTargetFromUi(raw);
    }

    private static void applySecondary(UniversalFieldDeviceMenu menu, String text) {
        Integer raw = parseSecondary(menu.configKind(), text);
        if (raw != null) menu.applySecondaryRawTargetFromUi(raw);
    }

    private static Integer parsePrimary(int kind, String text) {
        if (!primaryDirectKind(kind) || text == null || text.isBlank()) return null;
        try {
            double entered = Double.parseDouble(text);
            if (kind == UniversalFieldDeviceMenu.CONFIG_MOLECULAR_RECEIVER)
                return entered == 6 ? 0 : entered == 9 ? 1 : entered == 12 ? 2 : entered == 16 ? 3 : null;
            if (kind == UniversalFieldDeviceMenu.CONFIG_COPPER_CAPACITOR)
                return entered == 2 ? 0 : entered == 4 ? 1 : entered == 8 ? 2 : entered == 16 ? 3 : null;
            if (kind == UniversalFieldDeviceMenu.CONFIG_LAPIS_NOISE) {
                int raw = (int)Math.round(entered / 0.05);
                return Math.abs(entered - raw * 0.05) < 0.0001 ? raw : null;
            }
            if (kind == UniversalFieldDeviceMenu.CONFIG_PWM)
                return entered == 4 ? 0 : entered == 8 ? 1 : entered == 16 ? 2 : entered == 32 ? 3 : null;
            if (kind == UniversalFieldDeviceMenu.CONFIG_QUARTZ_OSCILLATOR)
                return entered == 2 ? 0 : entered == 4 ? 1 : entered == 8 ? 2 : entered == 16 ? 3 : entered == 32 ? 4 : null;
            if (kind == UniversalFieldDeviceMenu.CONFIG_THERMAL_HEATER)
                return entered == 1 ? 0 : entered == 2 ? 1 : entered == 4 ? 2 : entered == 8 ? 3 : null;
            int raw = (int)Math.round(entered);
            return Math.abs(entered - raw) < 0.0001 ? raw : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Integer parseSecondary(int kind, String text) {
        if (!secondaryDirectKind(kind) || text == null || text.isBlank()) return null;
        try {
            double entered = Double.parseDouble(text);
            if (kind == UniversalFieldDeviceMenu.CONFIG_LAPIS_RANGE)
                return entered == 8 ? 0 : entered == 16 ? 1 : entered == 32 ? 2 : entered == 64 ? 3 : null;
            if (kind == UniversalFieldDeviceMenu.CONFIG_LAPIS_NOISE) {
                int raw = (int)Math.round(entered / 0.02);
                return Math.abs(entered - raw * 0.02) < 0.0001 ? raw : null;
            }
            int raw = (int)Math.round(entered);
            return Math.abs(entered - raw) < 0.0001 ? raw : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean primaryDirectKind(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.CONFIG_MOLECULAR_RECEIVER,
                 UniversalFieldDeviceMenu.CONFIG_ALARM,
                 UniversalFieldDeviceMenu.CONFIG_PWM,
                 UniversalFieldDeviceMenu.CONFIG_COPPER_CAPACITOR,
                 UniversalFieldDeviceMenu.CONFIG_COPPER_VOLTAGE_SOURCE,
                 UniversalFieldDeviceMenu.CONFIG_COPPER_LOAD,
                 UniversalFieldDeviceMenu.CONFIG_COPPER_SERIES_RESISTOR,
                 UniversalFieldDeviceMenu.CONFIG_COPPER_FUSE,
                 UniversalFieldDeviceMenu.CONFIG_LAPIS_NOISE,
                 UniversalFieldDeviceMenu.CONFIG_QUARTZ_OSCILLATOR,
                 UniversalFieldDeviceMenu.CONFIG_QUARTZ_PHASE_DELAY,
                 UniversalFieldDeviceMenu.CONFIG_THERMAL_MASS,
                 UniversalFieldDeviceMenu.CONFIG_THERMAL_HEATER,
                 UniversalFieldDeviceMenu.CONFIG_THERMAL_RADIATOR -> true;
            default -> false;
        };
    }

    private static boolean secondaryDirectKind(int kind) {
        return kind == UniversalFieldDeviceMenu.CONFIG_LAPIS_RANGE
                || kind == UniversalFieldDeviceMenu.CONFIG_LAPIS_NOISE
                || kind == UniversalFieldDeviceMenu.CONFIG_QUARTZ_OSCILLATOR;
    }

    private static String primaryDisplay(UniversalFieldDeviceMenu menu) {
        int kind = menu.configKind();
        if (kind == UniversalFieldDeviceMenu.CONFIG_LAPIS_TRANSDUCER
                || kind == UniversalFieldDeviceMenu.CONFIG_LAPIS_RANGE)
            return menu.configPrimaryProfileName();
        if (kind == UniversalFieldDeviceMenu.CONFIG_MOLECULAR_RECEIVER) return Integer.toString(menu.pioneerProcessQuinary());
        if (kind == UniversalFieldDeviceMenu.CONFIG_COPPER_CAPACITOR) return Integer.toString(CopperCapacitorBlock.tauTicks(menu.configPrimary()));
        if (kind == UniversalFieldDeviceMenu.CONFIG_LAPIS_NOISE) return String.format(Locale.ROOT, "%.2f", menu.configPrimary() * 0.05);
        if (kind == UniversalFieldDeviceMenu.CONFIG_PWM) return Integer.toString(PwmControllerBlock.periodFor(menu.configPrimary()));
        if (kind == UniversalFieldDeviceMenu.CONFIG_QUARTZ_OSCILLATOR) return Integer.toString(menu.pioneerProcessPrimary());
        if (kind == UniversalFieldDeviceMenu.CONFIG_THERMAL_HEATER) return Integer.toString(menu.pioneerProcessSecondary());
        if (kind == UniversalFieldDeviceMenu.CONFIG_SAMPLE_HOLD) return SampleHoldBlock.modeName(menu.configPrimary());
        if (kind == UniversalFieldDeviceMenu.CONFIG_CALIBRATION) return CalibrationModuleBlock.profileName(menu.configPrimary());
        if (kind == UniversalFieldDeviceMenu.CONFIG_FAULT_INJECTOR) return FaultInjectorBlock.modeLabelFor(menu.configPrimary());
        return Integer.toString(menu.configPrimary());
    }

    private static String secondaryDisplay(UniversalFieldDeviceMenu menu) {
        int kind = menu.configKind();
        if (kind == UniversalFieldDeviceMenu.CONFIG_LAPIS_NOISE) return String.format(Locale.ROOT, "%.2f", menu.configSecondary() * 0.02);
        if (kind == UniversalFieldDeviceMenu.CONFIG_LAPIS_RANGE) return Integer.toString(menu.configSecondary());
        return Integer.toString(menu.configSecondary());
    }

    private static String primaryRange(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.CONFIG_MOLECULAR_RECEIVER -> "{6, 9, 12, 16} gain";
            case UniversalFieldDeviceMenu.CONFIG_ALARM -> "1..3 severity";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_CAPACITOR -> "{2, 4, 8, 16} ticks";
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_NOISE -> "0.00..1.00 Lapis • step 0.05";
            case UniversalFieldDeviceMenu.CONFIG_PWM -> "{4, 8, 16, 32} ticks";
            case UniversalFieldDeviceMenu.CONFIG_QUARTZ_OSCILLATOR -> "{2, 4, 8, 16, 32} ticks";
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_HEATER -> "{1, 2, 4, 8} R-eq";
            case UniversalFieldDeviceMenu.CONFIG_QUARTZ_PHASE_DELAY -> "1..16 ticks";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_VOLTAGE_SOURCE -> "0..15 V-eq";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_LOAD, UniversalFieldDeviceMenu.CONFIG_COPPER_SERIES_RESISTOR -> "1..15 R-eq";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_FUSE -> "1..15 I-eq";
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_MASS, UniversalFieldDeviceMenu.CONFIG_THERMAL_RADIATOR -> "1..4";
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_TRANSDUCER,
                 UniversalFieldDeviceMenu.CONFIG_LAPIS_RANGE,
                 UniversalFieldDeviceMenu.CONFIG_SAMPLE_HOLD,
                 UniversalFieldDeviceMenu.CONFIG_CALIBRATION,
                 UniversalFieldDeviceMenu.CONFIG_FAULT_INJECTOR -> "discrete server mode";
            default -> "state / read-only";
        };
    }

    private static String secondaryRange(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_RANGE -> "{8, 16, 32, 64} blocks";
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_NOISE -> "0.00..0.20 Lapis • step 0.02";
            case UniversalFieldDeviceMenu.CONFIG_QUARTZ_OSCILLATOR -> "0..3 ticks";
            default -> "N/A";
        };
    }

    private static String primarySymbol(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.CONFIG_PWM -> "T";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_VOLTAGE_SOURCE -> "V_set";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_LOAD -> "R";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_SERIES_RESISTOR -> "R_s";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_CAPACITOR -> "τ";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_FUSE -> "I_rating";
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_NOISE -> "μ";
            case UniversalFieldDeviceMenu.CONFIG_QUARTZ_OSCILLATOR -> "T_nom";
            case UniversalFieldDeviceMenu.CONFIG_QUARTZ_PHASE_DELAY -> "D";
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_MASS -> "C";
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_HEATER -> "R";
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_RADIATOR -> "k_cool";
            case UniversalFieldDeviceMenu.CONFIG_MOLECULAR_RECEIVER -> "g";
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_TRANSDUCER,
                 UniversalFieldDeviceMenu.CONFIG_LAPIS_RANGE -> "profile";
            case UniversalFieldDeviceMenu.CONFIG_SAMPLE_HOLD -> "trigger edge";
            case UniversalFieldDeviceMenu.CONFIG_CALIBRATION -> "profile";
            case UniversalFieldDeviceMenu.CONFIG_FAULT_INJECTOR -> "fault mode";
            default -> "primary";
        };
    }

    private static String secondarySymbol(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_RANGE -> "range";
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_NOISE -> "|η|max";
            case UniversalFieldDeviceMenu.CONFIG_QUARTZ_OSCILLATOR -> "J";
            default -> "secondary";
        };
    }

    private static String configName(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_TRANSDUCER -> "LAPIS TRANSDUCER";
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_RANGE -> "LAPIS RANGE";
            case UniversalFieldDeviceMenu.CONFIG_MOLECULAR_RECEIVER -> "MOLECULAR RECEIVER";
            case UniversalFieldDeviceMenu.CONFIG_ALARM -> "ALARM PROCESSOR";
            case UniversalFieldDeviceMenu.CONFIG_SAMPLE_HOLD -> "SAMPLE / HOLD";
            case UniversalFieldDeviceMenu.CONFIG_CALIBRATION -> "CALIBRATION";
            case UniversalFieldDeviceMenu.CONFIG_PWM -> "PWM";
            case UniversalFieldDeviceMenu.CONFIG_FAULT_INJECTOR -> "FAULT INJECTOR";
            case UniversalFieldDeviceMenu.CONFIG_SEQUENCE_CONTROLLER -> "SEQUENCE CONTROLLER";
            case UniversalFieldDeviceMenu.CONFIG_SAFETY_INTERLOCK -> "SAFETY INTERLOCK";
            case UniversalFieldDeviceMenu.CONFIG_TOPOLOGY_DEBUGGER -> "TOPOLOGY DEBUGGER";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_VOLTAGE_SOURCE -> "COPPER SOURCE";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_LOAD -> "COPPER LOAD";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_SERIES_RESISTOR -> "SERIES RESISTOR";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_CAPACITOR -> "RC STORAGE";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_FUSE -> "COPPER FUSE";
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_NOISE -> "LAPIS NOISE";
            case UniversalFieldDeviceMenu.CONFIG_QUARTZ_OSCILLATOR -> "QUARTZ OSCILLATOR";
            case UniversalFieldDeviceMenu.CONFIG_QUARTZ_PHASE_DELAY -> "QUARTZ PHASE DELAY";
            case UniversalFieldDeviceMenu.CONFIG_IRON_CORE -> "IRON CORE";
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_MASS -> "THERMAL MASS";
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_HEATER -> "THERMAL HEATER";
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_RADIATOR -> "THERMAL RADIATOR";
            default -> "READ-ONLY / NO SHARED CONFIG";
        };
    }

    private static String universalContract(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.CONFIG_PWM -> "MODEL: PWM period profile + polarity; server owns quantization and phase";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_LOAD -> "MODEL: I=V/R ; P=V²/R";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_SERIES_RESISTOR -> "MODEL: R_s participates in authoritative divider/load solution";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_CAPACITOR -> "MODEL: τ is an RSE discrete response proxy, not SI capacitance";
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_NOISE -> "MODEL: y[n] = μ + η[n], |η| ≤ configured bound";
            case UniversalFieldDeviceMenu.CONFIG_QUARTZ_OSCILLATOR -> "MODEL: T_nom + bounded scheduling jitter J";
            case UniversalFieldDeviceMenu.CONFIG_QUARTZ_PHASE_DELAY -> "MODEL: rising edge → wait D ticks → output pulse";
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_HEATER -> "MODEL: I=V/R ; P=V²/R feeds thermal target";
            case UniversalFieldDeviceMenu.CONFIG_ALARM -> "STATE: condition↑ latches severity; ACK changes attention state, not the process condition";
            case UniversalFieldDeviceMenu.CONFIG_FAULT_INJECTOR -> "STATE: ARM ? fault_mode(x) : x; reset statistics does not disarm";
            case UniversalFieldDeviceMenu.CONFIG_SEQUENCE_CONTROLLER -> "FSM: RESET∨¬RUN⇒step=0; ADVANCE moves through bounded sequence states";
            case UniversalFieldDeviceMenu.CONFIG_SAFETY_INTERLOCK -> "SAFETY: PERMIT=15 iff A>0 ∧ B>0 ∧ C>0";
            case UniversalFieldDeviceMenu.CONFIG_TOPOLOGY_DEBUGGER -> "OBSERVER: alarm=15 iff topology report hasIssue(); scan evidence is read-only";
            default -> "CONTRACT: expose real server ports, parameters and evidence only; no client-side physics";
        };
    }
}
