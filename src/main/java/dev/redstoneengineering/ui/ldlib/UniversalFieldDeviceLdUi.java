package dev.redstoneengineering.ui.ldlib;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.redstoneengineering.block.CalibrationModuleBlock;
import dev.redstoneengineering.block.CopperCapacitorBlock;
import dev.redstoneengineering.block.PwmControllerBlock;
import dev.redstoneengineering.block.SampleHoldBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.ui.menu.UniversalFieldDeviceMenu;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
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
        root.layout(l -> l.width(660).paddingAll(8).gapAll(6));
        root.addChildren(
                RseLdUiComponents.title("UNIVERSAL ENGINEERING HMI"),
                RseLdUiComponents.liveRow("LIVE STATE", "HEALTH", menu::operationalHealthLabel),
                RseLdUiComponents.liveRow("EVIDENCE", "quality", menu::evidenceStateLabel),
                RseLdUiComponents.liveRow("I/O", "route", menu::portRouteLabel),
                RseLdUiComponents.formulaCard(() -> universalContract(menu.configKind())),
                portsPanel(menu),
                parameterPanel(menu),
                pioneerPanel(menu),
                RseLdUiComponents.authorityFooter()
        );
        return ModularUI.of(
                UI.of(root, StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.GDP)),
                player
        );
    }

    private static UIElement portsPanel(UniversalFieldDeviceMenu menu) {
        var panel = new UIElement().addClass("panel_bg");
        panel.layout(l -> l.paddingAll(5).gapAll(3));
        panel.addChild(new Label().setText("DECLARED ENGINEERING PORTS"));
        for (Direction side : Direction.values()) {
            panel.addChild(RseLdUiComponents.liveRow("PORT", side.getName().toUpperCase(Locale.ROOT),
                    () -> portText(menu, side)));
        }
        panel.addChild(new UIElement().layout(l -> l.flexDirection(YogaFlexDirection.ROW).gapAll(6)).addChildren(
                RseLdUiComponents.serverAction("Cycle direction ▶", menu::cycleWholeRouteForward),
                RseLdUiComponents.serverAction("Cycle RX ▶", menu::cycleInputForward),
                RseLdUiComponents.serverAction("Cycle TX ▶", menu::cycleOutputForward)
        ));
        return panel;
    }

    private static String portText(UniversalFieldDeviceMenu menu, Direction side) {
        if (!menu.hasPort(side)) return "—";
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
        var primary = new TextField();
        primary.layout(l -> l.width(110));
        primary.bind(DataBindingBuilder.string(
                () -> primaryDirectKind(menu.configKind()) ? primaryDisplay(menu) : "",
                value -> applyPrimary(menu, value)
        ).build());

        var secondary = new TextField();
        secondary.layout(l -> l.width(110));
        secondary.bind(DataBindingBuilder.string(
                () -> secondaryDirectKind(menu.configKind()) ? secondaryDisplay(menu) : "",
                value -> applySecondary(menu, value)
        ).build());

        var panel = new UIElement().addClass("panel_bg");
        panel.layout(l -> l.paddingAll(5).gapAll(4));
        panel.addChildren(
                new Label().setText("FORMULA PARAMETER WORKBENCH"),
                RseLdUiComponents.liveRow("MODEL", "config", () -> configName(menu.configKind())),
                RseLdUiComponents.liveRow("CONTROL", primarySymbol(menu.configKind()),
                        () -> primaryDisplay(menu) + " • " + primaryRange(menu.configKind())),
                new UIElement().layout(l -> l.flexDirection(YogaFlexDirection.ROW).gapAll(6)).addChildren(
                        new Label().setText("PRIMARY").layout(l -> l.width(72)),
                        primary,
                        RseLdUiComponents.serverAction("Cycle primary ▶", menu::cyclePrimaryForward)
                ),
                RseLdUiComponents.liveRow("CONTROL", secondarySymbol(menu.configKind()),
                        () -> secondaryDisplay(menu) + " • " + secondaryRange(menu.configKind())),
                new UIElement().layout(l -> l.flexDirection(YogaFlexDirection.ROW).gapAll(6)).addChildren(
                        new Label().setText("SECONDARY").layout(l -> l.width(72)),
                        secondary,
                        RseLdUiComponents.serverAction("Cycle secondary ▶", menu::cycleSecondaryForward)
                ),
                new UIElement().layout(l -> l.flexDirection(YogaFlexDirection.ROW).gapAll(6)).addChildren(
                        RseLdUiComponents.serverAction("Explicit action", menu::runConfigAction),
                        RseLdUiComponents.serverAction("Toggle", menu::toggleConfiguration)
                ),
                RseLdUiComponents.liveRow("EVIDENCE", "process", () -> menu.pioneerProcessEvidenceQuality().name())
        );
        return panel;
    }

    private static UIElement pioneerPanel(UniversalFieldDeviceMenu menu) {
        var panel = new UIElement().addClass("panel_bg");
        panel.layout(l -> l.paddingAll(5).gapAll(3));
        panel.addChildren(
                new Label().setText("PIONEER / PROCESS SNAPSHOT"),
                RseLdUiComponents.liveRow("PROCESS", "kind", () -> Integer.toString(menu.pioneerProcessKind())),
                RseLdUiComponents.liveRow("STATE", "p1", () -> Integer.toString(menu.pioneerProcessPrimary())),
                RseLdUiComponents.liveRow("STATE", "p2", () -> Integer.toString(menu.pioneerProcessSecondary())),
                RseLdUiComponents.liveRow("STATE", "p3", () -> Integer.toString(menu.pioneerProcessTertiary())),
                RseLdUiComponents.liveRow("MEASUREMENT", "kind", () -> Integer.toString(menu.pioneerMeasurementKind())),
                RseLdUiComponents.liveRow("EVIDENCE", "measurement", () -> menu.pioneerEvidenceQuality().name())
        );
        return panel;
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
        if (kind == UniversalFieldDeviceMenu.CONFIG_MOLECULAR_RECEIVER) return Integer.toString(menu.pioneerProcessQuinary());
        if (kind == UniversalFieldDeviceMenu.CONFIG_COPPER_CAPACITOR) return Integer.toString(CopperCapacitorBlock.tauTicks(menu.configPrimary()));
        if (kind == UniversalFieldDeviceMenu.CONFIG_LAPIS_NOISE) return String.format(Locale.ROOT, "%.2f", menu.configPrimary() * 0.05);
        if (kind == UniversalFieldDeviceMenu.CONFIG_PWM) return Integer.toString(PwmControllerBlock.periodFor(menu.configPrimary()));
        if (kind == UniversalFieldDeviceMenu.CONFIG_QUARTZ_OSCILLATOR) return Integer.toString(menu.pioneerProcessPrimary());
        if (kind == UniversalFieldDeviceMenu.CONFIG_THERMAL_HEATER) return Integer.toString(menu.pioneerProcessSecondary());
        if (kind == UniversalFieldDeviceMenu.CONFIG_SAMPLE_HOLD) return SampleHoldBlock.modeName(menu.configPrimary());
        if (kind == UniversalFieldDeviceMenu.CONFIG_CALIBRATION) return CalibrationModuleBlock.profileName(menu.configPrimary());
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
            case UniversalFieldDeviceMenu.CONFIG_MOLECULAR_RECEIVER -> "{6,9,12,16} gain";
            case UniversalFieldDeviceMenu.CONFIG_ALARM -> "1..3 severity";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_CAPACITOR -> "{2,4,8,16} ticks";
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_NOISE -> "0.00..1.00 Lapis • step 0.05";
            case UniversalFieldDeviceMenu.CONFIG_PWM -> "{4,8,16,32} ticks";
            case UniversalFieldDeviceMenu.CONFIG_QUARTZ_OSCILLATOR -> "{2,4,8,16,32} ticks";
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_HEATER -> "{1,2,4,8} R-eq";
            case UniversalFieldDeviceMenu.CONFIG_QUARTZ_PHASE_DELAY -> "1..16 ticks";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_VOLTAGE_SOURCE -> "0..15 V-eq";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_LOAD, UniversalFieldDeviceMenu.CONFIG_COPPER_SERIES_RESISTOR -> "1..15 R-eq";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_FUSE -> "1..15 I-eq";
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_MASS, UniversalFieldDeviceMenu.CONFIG_THERMAL_RADIATOR -> "1..4";
            default -> "profile / state";
        };
    }

    private static String secondaryRange(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_RANGE -> "{8,16,32,64} blocks";
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
            case UniversalFieldDeviceMenu.CONFIG_SAFETY_INTERLOCK -> "SAFETY: PERMIT=15 iff A>0 ∧ B>0 ∧ C>0";
            default -> "CONTRACT: expose real server ports, parameters and evidence only; no client-side physics";
        };
    }
}
