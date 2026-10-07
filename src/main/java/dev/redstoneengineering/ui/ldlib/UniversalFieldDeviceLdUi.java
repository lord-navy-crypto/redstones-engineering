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
                RseLdUiComponents.formulaCard(() -> processEquation(kind))
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
                RseLdUiComponents.formulaCard(() -> measurementEquation(kind))
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
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_VOLTAGE_SOURCE -> a("V_set","output faces","V_out","","","");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_LOAD -> a("V","R","I","P","feeds","");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_SERIES_RESISTOR -> a("V_in","R_s","R_load","V_out","I","evaluated");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_CAPACITOR -> a("V_in","C_index","τ","charge","V_out","evaluated");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_FUSE -> a("V_in","I_rating","R_eq","I","V_out","trip latch");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_NOISE -> a("μ","|η|max","y[n]","Δt_sample","initialized","");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_QUARTZ_OSCILLATOR -> a("T_nom","J","Δt_half,last","jitter offset","clock","interval");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_QUARTZ_PHASE_DELAY -> a("T_in","D","pending","pulse_out","edge history","");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_QUARTZ_LAPIS_SAMPLER -> a("x_L","clock","y_hold","","","");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_SOUL_INJECTOR -> a("u_R","packet","attached nodes","output faces","","");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_SOUL_METER -> a("Q_s","y_R","","","","");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_MOLECULAR_RECEIVER -> a("c_raw","c_filt","peak","","g","y_R");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_IRON_CORE -> a("B_applied","B_threshold","scan radius","remanence","coverage","");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_MASS -> a("T","T_env","T_neighbor","T_target","C_index","ΔT_max");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_HEATER -> a("V","R","I","P","T","T_target");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_RADIATOR -> a("k_cool","N_mass","T_avg","T_hot","T_floor","Δt");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_CALORIMETER -> a("T","ΔT_20t","C_sum","C·ΔT","bodies","history");
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
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_QUARTZ_PHASE_DELAY -> a("ticks","ticks","ticks","","","");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_MASS -> a("T-index","T-index","T-index","T-index","index","T-index");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_RADIATOR -> a("index/tick","bodies","T-index","T-index","T-index","ticks");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_CALORIMETER -> a("T-index","T-index","capacity","relative heat","bodies","");
            default -> a("","","","","","");
        };
    }

    private static String[] processRoles(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_PWM -> a("MEASURED","ADJUSTABLE","DERIVED","DERIVED","EVIDENCE","SOLVER");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_LOAD -> a("MEASURED","ADJUSTABLE","DERIVED","DERIVED","EVIDENCE","");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_SERIES_RESISTOR -> a("MEASURED","ADJUSTABLE","SOLVER","DERIVED","DERIVED","EVIDENCE");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_CAPACITOR -> a("MEASURED","ADJUSTABLE","PROFILE","SOLVER","DERIVED","EVIDENCE");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_FUSE -> a("MEASURED","ADJUSTABLE","SOLVER","DERIVED","DERIVED","STATE");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_NOISE -> a("ADJUSTABLE","ADJUSTABLE","MEASURED","PROFILE","EVIDENCE","");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_QUARTZ_OSCILLATOR -> a("ADJUSTABLE","ADJUSTABLE","MEASURED","EVIDENCE","STATE","EVIDENCE");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_MASS -> a("STATE","MEASURED","MEASURED","SOLVER","ADJUSTABLE","DERIVED");
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_HEATER -> a("MEASURED","ADJUSTABLE","DERIVED","DERIVED","STATE","SOLVER");
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
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_CALIBRATION -> "MODEL: y = profile(x_obs); residual = y - x_ref";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_SAMPLE_HOLD -> "MODEL: trigger edge ⇒ y_hold←x; otherwise y_hold[n]=y_hold[n-1]";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_PWM -> "MODEL: N_on=round((u/15)·T); output=15 when phase<N_on";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_LOAD -> "MODEL: I=V/R ; P=V²/R";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_SERIES_RESISTOR -> "MODEL: authoritative divider/load solution uses R_s and R_load";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_CAPACITOR -> "MODEL: τ∈{2,4,8,16} ticks controls discrete RC response";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_FUSE -> "MODEL: trip when server-computed I exceeds I_rating";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_NOISE -> "MODEL: y[n]=μ+η[n], |η|≤configured bound";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_QUARTZ_OSCILLATOR -> "MODEL: T_nom with bounded scheduling jitter J";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_QUARTZ_PHASE_DELAY -> "MODEL: real rising edge ⇒ wait D ticks ⇒ pulse";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_MOLECULAR_RECEIVER -> "MODEL: c_raw=clamp(round(g·Σ r_cloud/(1+d²)),0,15); c_filt approaches by 1/5t";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_IRON_CORE -> "MODEL: complete scan ∧ B_applied≥8 ⇒ remanent MAGNETIZED state";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_MASS -> "MODEL: T_target=floor((2·T_env+T_neighbor)/3); bounded approach; Δt=5C";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_HEATER -> "MODEL: P=V²/R; thermal target is server-derived";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_RADIATOR -> "MODEL: passive cooling only above ambient floor";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_CALORIMETER -> "MODEL: ΔT_20t and relative heat=C_sum·ΔT";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_SOUL_METER -> "MODEL (fictional): y_R=floor(15·Q_s/100) when Soul evidence is VALID";
            default -> "MODEL: server-authoritative process transformation and retained evidence";
        };
    }

    private static String processInterpretation(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_CAPACITOR -> "τ is an RSE discrete response proxy, not an SI capacitance claim.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_QUARTZ_OSCILLATOR -> "Configuration and realized interval remain separate: the latter is retained server evidence.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_RADIATOR -> "Passive cooling cannot refrigerate below the model ambient floor.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_SOUL_INJECTOR,
                 UniversalFieldDeviceMenu.PIONEER_PROCESS_SOUL_METER,
                 UniversalFieldDeviceMenu.PIONEER_PROCESS_SOUL_CONDUIT,
                 UniversalFieldDeviceMenu.PIONEER_PROCESS_SOUL_RESERVOIR -> "Soul Flux is explicitly Minecraft-fictional; quality and topology remain server evidence.";
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
        if (kind == UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_LAPIS_RANGE && slot == 0 && raw < 0) return "NO TARGET";
        if (kind == UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_LAPIS_RANGE && slot == 2)
            return String.format(Locale.ROOT, "%.2f", raw / 100.0);
        return Integer.toString(raw);
    }

    private static String measurementEquation(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_TEMPERATURE -> "MODEL: T_target=N>0 ? floor(ΣT_body/N) : T_environment; require 6/6 coverage";
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_LIGHT -> "MODEL: y=round(condition(B_local,BALANCED)), 0≤y≤15";
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_TANK -> "MODEL: h=contiguous loaded fluid cells; y=condition(min(15,h),PRECISION)";
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_ENTITY_DENSITY -> "MODEL: N=living entities in aperture; y=condition(min(15,N),BALANCED)";
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_LAPIS_METER -> "MODEL: m=unique Lapis sample on selected face; display=m/100";
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_LAPIS_RANGE -> "MODEL: target ⇒ x=round(100·d/R); no target=NO_SIGNAL; incomplete aperture=STALE";
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_ANALOG_INDICATOR -> "MODEL: display=clamp(x_back,0,15); quality is independent of numeric zero";
            default -> "MODEL: synchronized server measurement evidence";
        };
    }

    private static String measurementInterpretation(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_TEMPERATURE -> "Incomplete six-face coverage retains the last trustworthy cached temperature instead of manufacturing ambient data.";
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
