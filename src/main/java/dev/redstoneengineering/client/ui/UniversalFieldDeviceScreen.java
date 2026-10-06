package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.block.CalibrationModuleBlock;
import dev.redstoneengineering.block.FaultInjectorBlock;
import dev.redstoneengineering.block.PwmControllerBlock;
import dev.redstoneengineering.block.SampleHoldBlock;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.ui.menu.UniversalFieldDeviceMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Universal six-face engineering HMI backed only by synchronized server snapshots. */
public final class UniversalFieldDeviceScreen extends EngineeringScreen<UniversalFieldDeviceMenu> {
    private EditBox primaryDirectInput;
    private Button primaryDirectApply;
    private EditBox secondaryDirectInput;
    private Button secondaryDirectApply;
    private Button primaryPrevious;
    private Button primaryNext;
    private Button secondaryPrevious;
    private Button secondaryNext;
    private Button action;
    private Button toggle;

    public UniversalFieldDeviceScreen(UniversalFieldDeviceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected int virtualContentWidth(Section section) {
        return switch (section) {
            case OVERVIEW, CONFIGURE -> 1040;
            case PORTS, DIAGNOSTICS, HISTORY -> 960;
        };
    }

    @Override
    protected int virtualContentHeight(Section section) {
        return switch (section) {
            case OVERVIEW -> 620;
            case PORTS -> 420;
            case CONFIGURE -> 680;
            case DIAGNOSTICS, HISTORY -> 560;
        };
    }

    @Override
    protected void addDeviceWidgets() {
        int gap = 14;
        int pairWidth = Math.min(196, Math.max(132, (imageWidth - 76 - gap) / 2));
        int totalWidth = pairWidth * 2 + gap;
        int startX = leftPos + (imageWidth - totalWidth) / 2;
        int primaryY = topPos + 104;
        int secondaryY = topPos + 144;

        primaryPrevious = addConfigureWidget(Button.builder(
                Component.literal("◀ Previous"),
                button -> sendMenuButton(UniversalFieldDeviceMenu.BUTTON_CONFIG_PRIMARY_PREVIOUS)
        ).bounds(startX, primaryY, pairWidth, 20).build());
        primaryNext = addConfigureWidget(Button.builder(
                Component.literal("Next ▶"),
                button -> sendMenuButton(UniversalFieldDeviceMenu.BUTTON_CONFIG_PRIMARY_NEXT)
        ).bounds(startX + pairWidth + gap, primaryY, pairWidth, 20).build());
        secondaryPrevious = addConfigureWidget(Button.builder(
                Component.literal("◀ Range"),
                button -> sendMenuButton(UniversalFieldDeviceMenu.BUTTON_CONFIG_SECONDARY_PREVIOUS)
        ).bounds(startX, secondaryY, pairWidth, 20).build());
        secondaryNext = addConfigureWidget(Button.builder(
                Component.literal("Range ▶"),
                button -> sendMenuButton(UniversalFieldDeviceMenu.BUTTON_CONFIG_SECONDARY_NEXT)
        ).bounds(startX + pairWidth + gap, secondaryY, pairWidth, 20).build());
        action = addConfigureWidget(Button.builder(
                Component.literal("Action"),
                button -> sendMenuButton(UniversalFieldDeviceMenu.BUTTON_CONFIG_ACTION)
        ).bounds(startX, secondaryY, totalWidth, 20).build());
        toggle = addConfigureWidget(Button.builder(
                Component.literal("Toggle"),
                button -> sendMenuButton(UniversalFieldDeviceMenu.BUTTON_CONFIG_TOGGLE)
        ).bounds(startX, secondaryY, totalWidth, 20).build());

        primaryDirectInput = addConfigureWidget(new EditBox(
                this.font, startX, primaryY, pairWidth, 20, Component.literal("Formula parameter value")));
        primaryDirectInput.setMaxLength(3);
        primaryDirectInput.setFilter(this::numericEntryText);
        primaryDirectApply = addConfigureWidget(Button.builder(
                Component.literal("Apply exact value"),
                button -> submitDirectPrimaryValue()
        ).bounds(startX + pairWidth + gap, primaryY, pairWidth, 20).build());

        secondaryDirectInput = addConfigureWidget(new EditBox(
                this.font, startX, secondaryY, pairWidth, 20, Component.literal("Secondary formula parameter value")));
        secondaryDirectInput.setMaxLength(3);
        secondaryDirectInput.setFilter(this::numericEntryText);
        secondaryDirectApply = addConfigureWidget(Button.builder(
                Component.literal("Apply secondary value"),
                button -> submitDirectSecondaryValue()
        ).bounds(startX + pairWidth + gap, secondaryY, pairWidth, 20).build());
    }

    @Override
    protected void syncDeviceWidgetLabels() {
        int kind = menu.configKind();
        boolean configure = isConfigureSection();
        boolean primary = kind == UniversalFieldDeviceMenu.CONFIG_LAPIS_TRANSDUCER
                || kind == UniversalFieldDeviceMenu.CONFIG_LAPIS_RANGE
                || kind == UniversalFieldDeviceMenu.CONFIG_MOLECULAR_RECEIVER
                || kind == UniversalFieldDeviceMenu.CONFIG_ALARM
                || kind == UniversalFieldDeviceMenu.CONFIG_SAMPLE_HOLD
                || kind == UniversalFieldDeviceMenu.CONFIG_CALIBRATION
                || kind == UniversalFieldDeviceMenu.CONFIG_PWM
                || kind == UniversalFieldDeviceMenu.CONFIG_FAULT_INJECTOR
                || kind == UniversalFieldDeviceMenu.CONFIG_COPPER_VOLTAGE_SOURCE
                || kind == UniversalFieldDeviceMenu.CONFIG_COPPER_LOAD
                || kind == UniversalFieldDeviceMenu.CONFIG_COPPER_SERIES_RESISTOR
                || kind == UniversalFieldDeviceMenu.CONFIG_COPPER_CAPACITOR
                || kind == UniversalFieldDeviceMenu.CONFIG_COPPER_FUSE
                || kind == UniversalFieldDeviceMenu.CONFIG_LAPIS_NOISE
                || kind == UniversalFieldDeviceMenu.CONFIG_QUARTZ_OSCILLATOR
                || kind == UniversalFieldDeviceMenu.CONFIG_QUARTZ_PHASE_DELAY
                || kind == UniversalFieldDeviceMenu.CONFIG_THERMAL_MASS
                || kind == UniversalFieldDeviceMenu.CONFIG_THERMAL_HEATER
                || kind == UniversalFieldDeviceMenu.CONFIG_THERMAL_RADIATOR;
        boolean secondary = kind == UniversalFieldDeviceMenu.CONFIG_LAPIS_RANGE
                || kind == UniversalFieldDeviceMenu.CONFIG_LAPIS_NOISE
                || kind == UniversalFieldDeviceMenu.CONFIG_QUARTZ_OSCILLATOR;
        boolean hasAction = kind == UniversalFieldDeviceMenu.CONFIG_MOLECULAR_RECEIVER
                || kind == UniversalFieldDeviceMenu.CONFIG_ALARM
                || kind == UniversalFieldDeviceMenu.CONFIG_SAMPLE_HOLD
                || kind == UniversalFieldDeviceMenu.CONFIG_FAULT_INJECTOR
                || kind == UniversalFieldDeviceMenu.CONFIG_SEQUENCE_CONTROLLER
                || kind == UniversalFieldDeviceMenu.CONFIG_SAFETY_INTERLOCK
                || kind == UniversalFieldDeviceMenu.CONFIG_TOPOLOGY_DEBUGGER
                || kind == UniversalFieldDeviceMenu.CONFIG_COPPER_FUSE
                || kind == UniversalFieldDeviceMenu.CONFIG_IRON_CORE;
        boolean hasToggle = kind == UniversalFieldDeviceMenu.CONFIG_PWM;

        String primaryName = primaryControlName(kind);
        String primaryValue = primaryControlValue(kind);
        boolean directPrimary = directPrimaryNumericKind(kind);

        if (primaryDirectInput != null) {
            primaryDirectInput.visible = configure && directPrimary;
            primaryDirectInput.active = configure && directPrimary;
            if (primaryDirectInput.visible && !primaryDirectInput.isFocused()) {
                String expected = directPrimaryDisplayValue(kind);
                if (!expected.equals(primaryDirectInput.getValue())) primaryDirectInput.setValue(expected);
            }
        }
        if (primaryDirectApply != null) {
            primaryDirectApply.visible = configure && directPrimary;
            primaryDirectApply.active = configure && directPrimary && directPrimaryInputValid(kind);
            primaryDirectApply.setMessage(Component.literal("Apply " + formulaParameterSymbol(kind)));
        }

        if (primaryPrevious != null) {
            primaryPrevious.visible = configure && primary && !directPrimary;
            primaryPrevious.setMessage(Component.literal("◀ " + primaryName + " • " + primaryValue));
        }

        boolean directSecondary = directSecondaryNumericKind(kind);
        if (secondaryDirectInput != null) {
            secondaryDirectInput.visible = configure && directSecondary;
            secondaryDirectInput.active = configure && directSecondary;
            if (secondaryDirectInput.visible && !secondaryDirectInput.isFocused()) {
                String expected = directSecondaryDisplayValue(kind);
                if (!expected.equals(secondaryDirectInput.getValue())) secondaryDirectInput.setValue(expected);
            }
        }
        if (secondaryDirectApply != null) {
            secondaryDirectApply.visible = configure && directSecondary;
            secondaryDirectApply.active = configure && directSecondary && directSecondaryInputValid(kind);
            secondaryDirectApply.setMessage(Component.literal("Apply " + secondaryFormulaParameterSymbol(kind)));
        }
        if (primaryNext != null) {
            primaryNext.visible = configure && primary && !directPrimary;
            primaryNext.setMessage(Component.literal(primaryName + " • " + primaryValue + " ▶"));
        }
        if (secondaryPrevious != null) {
            secondaryPrevious.visible = configure && secondary && !directSecondary;
            if (kind == UniversalFieldDeviceMenu.CONFIG_LAPIS_NOISE) secondaryPrevious.setMessage(Component.literal("◀ Noise"));
            else if (kind == UniversalFieldDeviceMenu.CONFIG_QUARTZ_OSCILLATOR) secondaryPrevious.setMessage(Component.literal("◀ Jitter"));
            else secondaryPrevious.setMessage(Component.literal("◀ Range"));
        }
        if (secondaryNext != null) {
            secondaryNext.visible = configure && secondary && !directSecondary;
            if (kind == UniversalFieldDeviceMenu.CONFIG_LAPIS_NOISE) secondaryNext.setMessage(Component.literal("Noise ▶"));
            else if (kind == UniversalFieldDeviceMenu.CONFIG_QUARTZ_OSCILLATOR) secondaryNext.setMessage(Component.literal("Jitter ▶"));
            else secondaryNext.setMessage(Component.literal("Range ▶"));
        }
        if (action != null) {
            action.visible = configure && hasAction;
            action.active = kind != UniversalFieldDeviceMenu.CONFIG_ALARM || menu.configSecondary() == 2;
            if (kind == UniversalFieldDeviceMenu.CONFIG_MOLECULAR_RECEIVER) action.setMessage(Component.literal("Reset measurement history"));
            else if (kind == UniversalFieldDeviceMenu.CONFIG_ALARM) action.setMessage(Component.literal(menu.configSecondary() == 2 ? "Acknowledge active alarm" : "Alarm already clear / acknowledged"));
            else if (kind == UniversalFieldDeviceMenu.CONFIG_SAMPLE_HOLD) action.setMessage(Component.literal("Clear held value"));
            else if (kind == UniversalFieldDeviceMenu.CONFIG_FAULT_INJECTOR) action.setMessage(Component.literal("Reset fault statistics"));
            else if (kind == UniversalFieldDeviceMenu.CONFIG_SEQUENCE_CONTROLLER) action.setMessage(Component.literal("Reset sequence to IDLE"));
            else if (kind == UniversalFieldDeviceMenu.CONFIG_SAFETY_INTERLOCK) action.setMessage(Component.literal("Reset diagnostic counters"));
            else if (kind == UniversalFieldDeviceMenu.CONFIG_TOPOLOGY_DEBUGGER) action.setMessage(Component.literal("Reset scan counters"));
            else if (kind == UniversalFieldDeviceMenu.CONFIG_COPPER_FUSE) action.setMessage(Component.literal("Reset fuse latch • re-evaluate next tick"));
            else if (kind == UniversalFieldDeviceMenu.CONFIG_IRON_CORE) action.setMessage(Component.literal("Demagnetize core"));
        }
        if (toggle != null) {
            toggle.visible = configure && hasToggle;
            toggle.setMessage(Component.literal("Invert output • " + (menu.configSecondary() != 0 ? "ON" : "OFF")));
        }
    }

    @Override
    protected void renderSection(GuiGraphics graphics, Section section) {
        switch (section) {
            case OVERVIEW -> overview(graphics);
            case PORTS -> ports(graphics);
            case CONFIGURE -> configure(graphics);
            case DIAGNOSTICS -> diagnostics(graphics);
            case HISTORY -> history(graphics);
        }
    }

    private void overview(GuiGraphics g) {
        if (menu.pioneerProcessKind() != UniversalFieldDeviceMenu.PIONEER_PROCESS_NONE) {
            processPioneerOverview(g);
            return;
        }
        if (menu.pioneerMeasurementKind() != UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_NONE) {
            measurementPioneerOverview(g);
            return;
        }
        int declared = Integer.bitCount(menu.declaredPortMask());
        int attention = attentionCount();
        statusBadge(g, title.getString().toUpperCase(), attention == 0 ? GOOD : WARN, 16, 80);
        statusBadge(g, lapisPrecisionMeasurementPresent() ? "LAPIS PRECISION" : "UNIVERSAL HMI", INFO, 207, 80);
        labelValue(g, "Declared interfaces", Integer.toString(declared), 106);
        labelValue(g, "Physical route", routeText(), 124);
        labelValue(g, "Attention ports", Integer.toString(attention), 142);
        sectionRule(g, 161);
        safeText(g, "Every displayed value and quality is synchronized from the logical server.", 16, 174, TEXT);
        safeText(g, lapisPrecisionMeasurementPresent()
                        ? "Lapis measurement uses the 0..100 precision-information domain; valid zero remains real evidence."
                        : "Use Ports for physical faces, Configure for parameters, and Route for real orientation.",
                16, 191, lapisPrecisionMeasurementPresent() ? INFO : MUTED);
    }


    private void processPioneerOverview(GuiGraphics g) {
        int kind = menu.pioneerProcessKind();
        PortQuality evidence = menu.pioneerProcessEvidenceQuality();
        statusBadge(g, title.getString().toUpperCase(), qualityColor(evidence), 16, 80);
        statusBadge(g, kind >= UniversalFieldDeviceMenu.PIONEER_PROCESS_IRON_CORE
                ? "PIONEER WAVE 17 • MATERIAL / STORAGE / THERMAL"
                : kind >= UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_NOISE
                ? "PIONEER WAVE 16 • ACTIVE SOURCE / TIMING"
                : kind >= UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_WIRE
                ? "PIONEER WAVE 15 • COPPER ELECTRICAL"
                : "PIONEER WAVE 14 • SIGNAL / TRANSDUCTION", INFO, 207, 80);
        formulaCard(g, processEquation(kind), 108);

        switch (kind) {
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_CALIBRATION -> {
                variableRole(g, "MEASURED", "x_obs", Integer.toString(menu.pioneerProcessPrimary()), "redstone", 137);
                variableRole(g, "MEASURED", "x_ref", Integer.toString(menu.pioneerProcessSecondary()), "redstone", 153);
                variableRole(g, "DERIVED", "y", Integer.toString(menu.pioneerProcessTertiary()), "redstone", 169);
                variableRole(g, "ADJUSTABLE", "profile", CalibrationModuleBlock.profileName(menu.pioneerProcessQuaternary()), "", 185);
                variableRole(g, "EVIDENCE", "bias", String.format(java.util.Locale.ROOT, "%+d", menu.pioneerProcessQuinary()), "redstone", 201);
                variableRole(g, "EVIDENCE", "samples", Integer.toString(menu.pioneerProcessSenary()), "count", 217);
            }
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_SAMPLE_HOLD -> {
                variableRole(g, "SOLVER", "y_hold", Integer.toString(menu.pioneerProcessPrimary()), "redstone", 137);
                variableRole(g, "EVIDENCE", "captures", Integer.toString(menu.pioneerProcessSecondary()), "count", 153);
                variableRole(g, "EVIDENCE", "age", menu.pioneerProcessTertiary() < 0 ? "NONE" : Integer.toString(menu.pioneerProcessTertiary()), "ticks", 169);
                variableRole(g, "ADJUSTABLE", "edge", SampleHoldBlock.modeName(menu.pioneerProcessQuaternary()), "", 185);
                variableRole(g, "MEASURED", "trigger", Integer.toString(menu.pioneerProcessQuinary()), "redstone", 201);
                variableRole(g, "MEASURED", "reset", Integer.toString(menu.pioneerProcessSenary()), "redstone", 217);
            }
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_PWM -> {
                variableRole(g, "MEASURED", "u", Integer.toString(menu.pioneerProcessPrimary()), "redstone", 137);
                variableRole(g, "ADJUSTABLE", "T", Integer.toString(menu.pioneerProcessSecondary()), "ticks", 153);
                variableRole(g, "DERIVED", "N_on", Integer.toString(menu.pioneerProcessTertiary()), "ticks", 169);
                variableRole(g, "DERIVED", "D_eff", String.format(java.util.Locale.ROOT, "%.1f", menu.pioneerProcessQuaternary() / 10.0), "%", 185);
                variableRole(g, "EVIDENCE", "e_q", String.format(java.util.Locale.ROOT, "%+.1f", menu.pioneerProcessQuinary() / 10.0), "%", 201);
                variableRole(g, "SOLVER", "phase", Integer.toString(menu.pioneerProcessSenary()), "ticks", 217);
            }
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_TEMPERATURE,
                 UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_MAGNETIC,
                 UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_OPTICAL,
                 UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_VOLTAGE -> {
                variableRole(g, "MEASURED", processInputSymbol(kind),
                        String.format(java.util.Locale.ROOT, "%.2f", menu.pioneerProcessPrimary() / 100.0),
                        processInputUnit(kind), 137);
                variableRole(g, "DERIVED", "y_L",
                        String.format(java.util.Locale.ROOT, "%.2f", menu.pioneerProcessSecondary() / 100.0),
                        "Lapis", 153);
                variableRole(g, "PROFILE", "Δt_sample", Integer.toString(menu.pioneerProcessTertiary()), "ticks", 169);
                variableRole(g, "PROFILE", "resolution", Integer.toString(menu.pioneerProcessQuaternary()), "/100", 185);
                variableRole(g, "PROFILE", "noise", "±" + menu.pioneerProcessQuinary(), "/100", 201);
                variableRole(g, "PROFILE", "latency", Integer.toString(menu.pioneerProcessSenary()), "samples", 217);
            }
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_WIRE,
                 UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_JUNCTION -> {
                variableRole(g, "MEASURED", "V_node", Integer.toString(menu.pioneerProcessPrimary()), "V-eq", 137);
                variableRole(g, "EVIDENCE", "drivers", Integer.toString(menu.pioneerProcessSecondary()), "sources", 153);
                variableRole(g, "TOPOLOGY", "ports", Integer.toString(menu.pioneerProcessTertiary()), "faces", 169);
                variableRole(g, "EVIDENCE", "quality", evidence.name(), "", 185);
            }
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_VOLTAGE_SOURCE -> {
                variableRole(g, "ADJUSTABLE", "V_set", Integer.toString(menu.pioneerProcessPrimary()), "V-eq", 137);
                variableRole(g, "TOPOLOGY", "output faces", Integer.toString(menu.pioneerProcessSecondary()), "faces", 153);
                variableRole(g, "DERIVED", "V_out", Integer.toString(menu.pioneerProcessPrimary()), "V-eq", 169);
                variableRole(g, "EVIDENCE", "quality", evidence.name(), "", 185);
            }
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_LOAD -> {
                variableRole(g, "MEASURED", "V", Integer.toString(menu.pioneerProcessPrimary()), "V-eq", 137);
                variableRole(g, "ADJUSTABLE", "R", Integer.toString(menu.pioneerProcessSecondary()), "R-eq", 153);
                variableRole(g, "DERIVED", "I", String.format(java.util.Locale.ROOT, "%.3f", menu.pioneerProcessTertiary()/1000.0), "I-eq", 169);
                variableRole(g, "DERIVED", "P", String.format(java.util.Locale.ROOT, "%.3f", menu.pioneerProcessQuaternary()/1000.0), "P-eq", 185);
                variableRole(g, "EVIDENCE", "feeds", Integer.toString(menu.pioneerProcessQuinary()), "physical", 201);
            }
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_SERIES_RESISTOR -> {
                variableRole(g, "MEASURED", "V_in", Integer.toString(menu.pioneerProcessPrimary()), "V-eq", 137);
                variableRole(g, "ADJUSTABLE", "R_s", Integer.toString(menu.pioneerProcessSecondary()), "R-eq", 153);
                variableRole(g, "SOLVER", "R_load", String.format(java.util.Locale.ROOT, "%.3f", menu.pioneerProcessTertiary()/1000.0), "R-eq", 169);
                variableRole(g, "DERIVED", "V_out", Integer.toString(menu.pioneerProcessQuaternary()), "V-eq", 185);
                variableRole(g, "DERIVED", "I", String.format(java.util.Locale.ROOT, "%.3f", menu.pioneerProcessQuinary()/1000.0), "I-eq", 201);
                variableRole(g, "EVIDENCE", "evaluated", menu.pioneerProcessSenary()!=0 ? "YES" : "NO", "", 217);
            }
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_CAPACITOR -> {
                variableRole(g, "MEASURED", "V_in", Integer.toString(menu.pioneerProcessPrimary()), "V-eq", 137);
                variableRole(g, "ADJUSTABLE", "C_index", Integer.toString(menu.pioneerProcessSecondary()), "1..4", 153);
                variableRole(g, "PROFILE", "τ", Integer.toString(menu.pioneerProcessTertiary()), "ticks", 169);
                variableRole(g, "SOLVER", "charge", Integer.toString(menu.pioneerProcessQuaternary()), "%", 185);
                variableRole(g, "DERIVED", "V_out", Integer.toString(menu.pioneerProcessQuinary()), "V-eq", 201);
                variableRole(g, "EVIDENCE", "evaluated", menu.pioneerProcessSenary()!=0 ? "YES" : "NO", "", 217);
            }
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_FUSE -> {
                variableRole(g, "MEASURED", "V_in", Integer.toString(menu.pioneerProcessPrimary()), "V-eq", 137);
                variableRole(g, "ADJUSTABLE", "I_rating", Integer.toString(menu.pioneerProcessSecondary()), "I-eq", 153);
                variableRole(g, "SOLVER", "R_eq", String.format(java.util.Locale.ROOT, "%.3f", menu.pioneerProcessTertiary()/1000.0), "R-eq", 169);
                variableRole(g, "DERIVED", "I", String.format(java.util.Locale.ROOT, "%.3f", menu.pioneerProcessQuaternary()/1000.0), "I-eq", 185);
                variableRole(g, "DERIVED", "V_out", Integer.toString(menu.pioneerProcessQuinary()), "V-eq", 201);
                variableRole(g, "STATE", "trip latch", menu.pioneerProcessSenary()!=0 ? "TRIPPED" : "ARMED", "", 217);
            }
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_NOISE -> {
                variableRole(g, "ADJUSTABLE", "μ", String.format(java.util.Locale.ROOT, "%.2f", menu.pioneerProcessPrimary()/100.0), "Lapis", 137);
                variableRole(g, "ADJUSTABLE", "|η|max", String.format(java.util.Locale.ROOT, "%.2f", menu.pioneerProcessSecondary()/100.0), "Lapis", 153);
                variableRole(g, "MEASURED", "y[n]", String.format(java.util.Locale.ROOT, "%.2f", menu.pioneerProcessTertiary()/100.0), "Lapis", 169);
                variableRole(g, "PROFILE", "Δt_sample", Integer.toString(menu.pioneerProcessQuaternary()), "ticks", 185);
                variableRole(g, "EVIDENCE", "initialized", menu.pioneerProcessQuinary()!=0 ? "YES" : "NO", "", 201);
            }
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_QUARTZ_OSCILLATOR -> {
                variableRole(g, "ADJUSTABLE", "T_nom", Integer.toString(menu.pioneerProcessPrimary()), "ticks", 137);
                variableRole(g, "ADJUSTABLE", "J", "±"+menu.pioneerProcessSecondary(), "ticks", 153);
                variableRole(g, "MEASURED", "Δt_half,last", menu.pioneerProcessSenary()!=0 ? Integer.toString(menu.pioneerProcessTertiary()) : "NOT_READY", "ticks", 169);
                variableRole(g, "EVIDENCE", "jitter offset", menu.pioneerProcessSenary()!=0 ? String.format(java.util.Locale.ROOT, "%+d", menu.pioneerProcessQuaternary()) : "N/A", "ticks", 185);
                variableRole(g, "STATE", "clock", menu.pioneerProcessQuinary()!=0 ? "HIGH" : "LOW", "", 201);
                variableRole(g, "EVIDENCE", "interval", menu.pioneerProcessSenary()!=0 ? "REALIZED" : "AWAITING", "", 217);
            }
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_QUARTZ_PHASE_DELAY -> {
                variableRole(g, "MEASURED", "T_in", Integer.toString(menu.pioneerProcessPrimary()), "ticks", 137);
                variableRole(g, "ADJUSTABLE", "D", Integer.toString(menu.pioneerProcessSecondary()), "ticks", 153);
                variableRole(g, "SOLVER", "pending", Integer.toString(menu.pioneerProcessTertiary()), "ticks", 169);
                variableRole(g, "STATE", "pulse_out", menu.pioneerProcessQuaternary()!=0 ? "HIGH" : "LOW", "", 185);
                variableRole(g, "EVIDENCE", "edge history", menu.pioneerProcessQuinary()!=0 ? "ARMED" : "NOT_READY", "", 201);
            }
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_QUARTZ_LAPIS_SAMPLER -> {
                variableRole(g, "MEASURED", "x_L", String.format(java.util.Locale.ROOT, "%.2f", menu.pioneerProcessPrimary()/100.0), "Lapis", 137);
                variableRole(g, "MEASURED", "clock", menu.pioneerProcessSecondary()!=0 ? "HIGH" : "LOW", "", 153);
                variableRole(g, "SOLVER", "y_hold", String.format(java.util.Locale.ROOT, "%.2f", menu.pioneerProcessTertiary()/100.0), "Lapis", 169);
                variableRole(g, "EVIDENCE", "held quality", evidence.name(), "", 185);
            }
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_SOUL_INJECTOR -> {
                variableRole(g, "MEASURED", "u_R", Integer.toString(menu.pioneerProcessPrimary()), "redstone", 137);
                variableRole(g, "DERIVED", "packet", Integer.toString(menu.pioneerProcessSecondary()), "flux", 153);
                variableRole(g, "TOPOLOGY", "attached nodes", Integer.toString(menu.pioneerProcessTertiary()), "nodes", 169);
                variableRole(g, "TOPOLOGY", "output faces", Integer.toString(menu.pioneerProcessQuaternary()), "faces", 185);
                variableRole(g, "EVIDENCE", "command", evidence.name(), "", 201);
            }
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_SOUL_METER -> {
                variableRole(g, "MEASURED", "Q_s", Integer.toString(menu.pioneerProcessPrimary()), "flux", 137);
                variableRole(g, "DERIVED", "y_R", Integer.toString(menu.pioneerProcessSecondary()), "redstone", 153);
                variableRole(g, "MODEL", "floor(15·Q_s/100)", Integer.toString(menu.pioneerProcessTertiary()), "redstone", 169);
                variableRole(g, "EVIDENCE", "source", evidence.name(), "", 185);
            }
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_MOLECULAR_RECEIVER -> {
                variableRole(g, "MEASURED", "c_raw", Integer.toString(menu.pioneerProcessPrimary()), "index", 137);
                variableRole(g, "SOLVER", "c_filt", Integer.toString(menu.pioneerProcessSecondary()), "index", 153);
                variableRole(g, "EVIDENCE", "peak", Integer.toString(menu.pioneerProcessTertiary()), "index", 169);
                variableRole(g, "ADJUSTABLE", "sensitivity", Integer.toString(menu.pioneerProcessQuaternary()), "0..3", 185);
                variableRole(g, "PROFILE", "gain", Integer.toString(menu.pioneerProcessQuinary()), "index", 201);
                variableRole(g, "DERIVED", "y_R", Integer.toString(menu.pioneerProcessSenary()), "redstone", 217);
            }
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_IRON_CORE -> {
                variableRole(g, "MEASURED", "B_applied", Integer.toString(menu.pioneerProcessPrimary()), "B-index", 137);
                variableRole(g, "PROFILE", "B_threshold", Integer.toString(menu.pioneerProcessSecondary()), "B-index", 153);
                variableRole(g, "PROFILE", "scan radius", Integer.toString(menu.pioneerProcessTertiary()), "blocks", 169);
                variableRole(g, "STATE", "remanence", menu.pioneerProcessQuaternary()!=0 ? "MAGNETIZED" : "SOFT", "", 185);
                variableRole(g, "EVIDENCE", "coverage", menu.pioneerProcessQuinary()!=0 ? "COMPLETE" : "INCOMPLETE", "", 201);
            }
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_MASS -> {
                variableRole(g, "STATE", "T", Integer.toString(menu.pioneerProcessPrimary()), "T-index", 137);
                variableRole(g, "MEASURED", "T_env", Integer.toString(menu.pioneerProcessSecondary()), "T-index", 153);
                variableRole(g, "MEASURED", "T_neighbor", Integer.toString(menu.pioneerProcessTertiary()), "T-index", 169);
                variableRole(g, "SOLVER", "T_target", Integer.toString(menu.pioneerProcessQuaternary()), "T-index", 185);
                variableRole(g, "ADJUSTABLE", "C_index", Integer.toString(menu.pioneerProcessQuinary()), "1..4", 201);
                variableRole(g, "DERIVED", "ΔT_max", Integer.toString(menu.pioneerProcessSenary()), "index/tick", 217);
            }
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_HEATER -> {
                variableRole(g, "MEASURED", "V", Integer.toString(menu.pioneerProcessPrimary()), "V-eq", 137);
                variableRole(g, "ADJUSTABLE", "R", Integer.toString(menu.pioneerProcessSecondary()), "R-eq", 153);
                variableRole(g, "DERIVED", "I", String.format(java.util.Locale.ROOT, "%.3f", menu.pioneerProcessTertiary()/1000.0), "I-eq", 169);
                variableRole(g, "DERIVED", "P", String.format(java.util.Locale.ROOT, "%.3f", menu.pioneerProcessQuaternary()/1000.0), "P-eq", 185);
                variableRole(g, "STATE", "T", Integer.toString(menu.pioneerProcessQuinary()), "T-index", 201);
                variableRole(g, "SOLVER", "T_target", Integer.toString(menu.pioneerProcessSenary()), "T-index", 217);
            }
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_RADIATOR -> {
                variableRole(g, "ADJUSTABLE", "k_cool", Integer.toString(menu.pioneerProcessPrimary()), "index/tick", 137);
                variableRole(g, "MEASURED", "N_mass", Integer.toString(menu.pioneerProcessSecondary()), "bodies", 153);
                variableRole(g, "MEASURED", "T_avg", Integer.toString(menu.pioneerProcessTertiary()), "T-index", 169);
                variableRole(g, "MEASURED", "T_hot", Integer.toString(menu.pioneerProcessQuaternary()), "T-index", 185);
                variableRole(g, "PROFILE", "T_floor", Integer.toString(menu.pioneerProcessQuinary()), "T-index", 201);
                variableRole(g, "PROFILE", "Δt", Integer.toString(menu.pioneerProcessSenary()), "ticks", 217);
            }
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_CALORIMETER -> {
                variableRole(g, "MEASURED", "T", Integer.toString(menu.pioneerProcessPrimary()), "T-index", 137);
                variableRole(g, "MEASURED", "ΔT_20t", String.format(java.util.Locale.ROOT, "%+d", menu.pioneerProcessSecondary()), "T-index", 153);
                variableRole(g, "MEASURED", "C_sum", Integer.toString(menu.pioneerProcessTertiary()), "capacity index", 169);
                variableRole(g, "DERIVED", "C·ΔT", String.format(java.util.Locale.ROOT, "%+d", menu.pioneerProcessQuaternary()), "relative heat", 185);
                variableRole(g, "EVIDENCE", "bodies", Integer.toString(menu.pioneerProcessQuinary()), "count", 201);
                variableRole(g, "EVIDENCE", "history", menu.pioneerProcessSenary()!=0 ? "INITIALIZED" : "NOT_READY", "", 217);
            }
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_SOUL_CONDUIT -> {
                variableRole(g, "STATE", "J", Integer.toString(menu.pioneerProcessPrimary()), "flux", 137);
                variableRole(g, "EVIDENCE", "age", menu.pioneerProcessSecondary()<0 ? "NONE" : Integer.toString(menu.pioneerProcessSecondary()), "ticks", 153);
                variableRole(g, "EVIDENCE", "quality", Integer.toString(menu.pioneerProcessTertiary()), "%", 169);
                variableRole(g, "PROFILE", "decay period", Integer.toString(menu.pioneerProcessQuaternary()), "ticks", 185);
                variableRole(g, "TOPOLOGY", "ports", Integer.toString(menu.pioneerProcessQuinary()), "faces", 201);
            }
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_SOUL_RESERVOIR -> {
                variableRole(g, "STATE", "Q_s", Integer.toString(menu.pioneerProcessPrimary()), "flux", 137);
                variableRole(g, "EVIDENCE", "age", menu.pioneerProcessSecondary()<0 ? "NONE" : Integer.toString(menu.pioneerProcessSecondary()), "ticks", 153);
                variableRole(g, "EVIDENCE", "quality", Integer.toString(menu.pioneerProcessTertiary()), "%", 169);
                variableRole(g, "PROFILE", "decay period", Integer.toString(menu.pioneerProcessQuaternary()), "ticks", 185);
                variableRole(g, "TOPOLOGY", "ports", Integer.toString(menu.pioneerProcessQuinary()), "faces", 201);
            }
            default -> { }
        }

        evidenceRow(g, "EVIDENCE", evidence.name(), "server snapshot", processEvidenceNote(evidence), 239);
        String controlHint = processControlHint(kind);
        sectionRule(g, 244);
        if (!controlHint.isBlank()) {
            statusLine(g, "FORMULA-LINKED CONTROL", controlHint, INFO, 260);
            wrappedText(g, processInterpretation(kind), 24, 286, workspaceWidth() - 48, MUTED);
        } else {
            statusLine(g, "CONTROL SURFACE", "OBSERVER / PROFILE-OWNED • no fabricated knob", MUTED, 260);
            wrappedText(g, processInterpretation(kind), 24, 286, workspaceWidth() - 48, MUTED);
        }
    }

    private String processControlHint(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_CALIBRATION -> "Configure → transfer profile";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_SAMPLE_HOLD -> "Configure → trigger edge + clear held value";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_PWM -> "Configure → period T + invert";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_TEMPERATURE,
                 UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_MAGNETIC,
                 UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_OPTICAL,
                 UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_VOLTAGE -> "Configure → conditioning profile";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_VOLTAGE_SOURCE -> "Configure → V_set";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_LOAD -> "Configure → R";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_SERIES_RESISTOR -> "Configure → R_s";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_CAPACITOR -> "Configure → C profile / τ";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_FUSE -> "Configure → I_rating + reset latch";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_NOISE -> "Configure → μ + |η|max";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_QUARTZ_OSCILLATOR -> "Configure → T_nom + jitter J";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_QUARTZ_PHASE_DELAY -> "Configure → delay D";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_MOLECULAR_RECEIVER -> "Configure → sensitivity + reset history";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_IRON_CORE -> "Configure → explicit demagnetize";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_MASS -> "Configure → capacity index C";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_HEATER -> "Configure → resistance R";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_RADIATOR -> "Configure → cooling coefficient k_cool";
            default -> "";
        };
    }

    private String processEquation(int kind) {
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
                    "MODEL: V_out = V_set on six Copper OUTPUT faces; 0 ≤ V_set ≤ 15";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_LOAD ->
                    "MODEL: I = V/R; P = V·I; terminal accepts exactly one legitimate Copper feed";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_SERIES_RESISTOR ->
                    "MODEL: V_out = V_in·R_load/(R_s+R_load); I = V_in/(R_s+R_load)";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_CAPACITOR ->
                    "MODEL: q_target=round(100·V_in/15); Δq_step=sign(Δq)·max(1,|Δq|/τ); V_out=round(15·q/100)";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_FUSE ->
                    "MODEL: I=V_in/R_eq; TRIPPED ← TRIPPED ∨ (I>I_rating); V_out=TRIPPED ? 0 : V_in";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_JUNCTION ->
                    "MODEL: explicit multi-port splice shares one resolved Copper node; >1 driver is TOPOLOGY_ERROR";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_NOISE ->
                    "MODEL: y[n]=clamp(μ + η_det(n,pos),0,100); |η_det|≤η_max; Δt=4 ticks";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_QUARTZ_OSCILLATOR ->
                    "MODEL: half-interval=max(1,T_nom/2 + j), j∈[-J,+J]; each tick toggles clock state";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_QUARTZ_PHASE_DELAY ->
                    "MODEL: real post-init rising edge ⇒ pending=D; countdown→0 emits one-tick QUARTZ pulse";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_QUARTZ_LAPIS_SAMPLER ->
                    "MODEL: valid QUARTZ rising edge ⇒ y_hold←x_L; otherwise y_hold[n]=y_hold[n-1]";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_SOUL_INJECTOR ->
                    "MODEL (fictional): valid UP redstone u>0 ⇒ packet=4u into each attached side Soul node";
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
                    "MODEL: T=mean(adjacent thermal masses); ΔT_20t=T[n]-T[n-1]; relative heat=C_sum·ΔT";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_SOUL_CONDUIT ->
                    "MODEL (fictional): transient Soul Flux J decays by 1 each 20 ticks; zero/absent conduit flux is NO_SIGNAL";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_SOUL_RESERVOIR ->
                    "MODEL (fictional): stored Q_s decays by 1 each 40 ticks; initialized empty Q_s=0 remains VALID storage state";
            default -> "MODEL: server-authoritative signal transformation";
        };
    }

    private String processInterpretation(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_CALIBRATION ->
                    "OBSERVED and REFERENCE remain separate physical inputs. The selected profile changes only the calibrated transfer; residual evidence compares the calibrated output against the reference.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_SAMPLE_HOLD ->
                    "Capture state is server-owned. Reading the HMI never creates an edge or capture; Clear held value is an explicit action and physical TRIGGER/RESET ports remain authoritative.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_PWM ->
                    "Duty-cycle quantization is visible as realized on-ticks and e_q. Period and invert are real server controls; INHIBIT remains a separate physical safety input.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_TEMPERATURE ->
                    "Thermal input is normalized directly on the server, then the selected SensorModel profile applies sampling period, resolution, noise and latency before driving Lapis.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_MAGNETIC ->
                    "Magnetic field magnitude uses the existing radius-6 coverage-aware server sample. Incomplete field coverage remains STALE rather than becoming a fabricated zero.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_OPTICAL ->
                    "Optical intensity and its source/topology quality are observed on the server before profile conditioning; numeric zero is independent from source validity.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_VOLTAGE ->
                    "Copper voltage comes from DomainNetwork while CopperObservationSupport owns source/topology quality. The transducer does not promote an isolated numeric zero to VALID.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_WIRE ->
                    "Planar wire exposes only real connected faces. Vertical transitions and branch points belong to explicit junction topology; numeric zero is separate from NO_SIGNAL or TOPOLOGY_ERROR.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_VOLTAGE_SOURCE ->
                    "V_set is the real BlockState control. The source owns six Copper OUTPUT faces; changing it recomputes the authoritative Copper network rather than editing client display state.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_LOAD ->
                    "The load is INPUT-only and cannot back-drive another sink. Multiple adjacent feeds fail closed as TOPOLOGY_ERROR instead of silently selecting a voltage.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_SERIES_RESISTOR ->
                    "R_load and current shown here are retained from the last server tick that evaluated the divider. Opening the HMI never performs another load-network scan.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_CAPACITOR ->
                    "Charge is retained server state and can legitimately source a decaying output after input removal. C_index changes the implemented τ proxy; it does not invent SI capacitance.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_FUSE ->
                    "Trip state is latched by the authoritative server protection pass. Reset clears the latch request only; safe output is trusted again only after a complete server re-evaluation.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_COPPER_JUNCTION ->
                    "The junction is the explicit branch/splice device. Port count is the physical connected-face count; multiple active drivers are visible topology evidence, not merged silently.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_NOISE ->
                    "The deterministic noise source is an intentional commissioning/fault-injection device. Zero is a legitimate sample; μ and η_max are real server controls, not client-only knobs.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_QUARTZ_OSCILLATOR ->
                    "Nominal period and jitter are configuration; last-half and realized offset are retained evidence from an actual server scheduling interval. Changing configuration makes old interval evidence unavailable.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_QUARTZ_PHASE_DELAY ->
                    "Reconnect-high initializes edge history but never fabricates a rising edge. Delay changes clear pending/output runtime so the new configuration must observe a real edge.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_QUARTZ_LAPIS_SAMPLER ->
                    "The sampler has three distinct physical roles: BACK Lapis input, LEFT Quartz trigger, FRONT held Lapis output. Opening the HMI never captures a sample.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_SOUL_INJECTOR ->
                    "Soul Flux is explicitly Minecraft-fictional. The injector only writes adjacent loaded Soul nodes when its real UP redstone command is valid; absent side nodes are not virtual outputs.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_SOUL_METER ->
                    "The meter is observer/converter only. A present zero-charge node is distinct from NO_SIGNAL/STALE evidence, and invalid Soul evidence forces redstone output to zero.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_MOLECULAR_RECEIVER ->
                    "The UP free-space aperture is fixed at the implemented radius. Incomplete chunk coverage is STALE and retains the last filtered value; sensitivity changes only the implemented gain.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_IRON_CORE ->
                    "The core models bounded hysteresis/remanence, not a full B-H curve. Only complete applied-field coverage can magnetize it; inspection never changes state, while Demagnetize is an explicit server action.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_MASS ->
                    "This is the existing coarse lumped thermal model. Capacity changes both the maximum temperature step and update cadence; temperature remains world state rather than a wire signal.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_HEATER ->
                    "The heater converts an authoritative Copper terminal observation into bounded thermal state using the existing reduced P=V²/R rule. Invalid electrical evidence contributes zero drive.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_RADIATOR ->
                    "The radiator is passive: it only reduces adjacent Thermal Mass temperatures above the ambient floor. It never refrigerates a body below the model ambient.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_THERMAL_CALORIMETER ->
                    "The calorimeter is observer-only. History is retained by the server every 20 ticks; opening the HMI neither samples a new interval nor mutates neighboring thermal bodies.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_SOUL_CONDUIT ->
                    "Soul Flux is explicitly Minecraft-fictional. Conduit charge is transient transport state, so decayed/absent zero is NO_SIGNAL rather than a stored zero measurement.";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_SOUL_RESERVOIR ->
                    "Soul Flux is explicitly Minecraft-fictional. Reservoir zero is a known empty storage state after initialization and therefore remains VALID, unlike absent transient conduit flux.";
            default -> "Server-authoritative process evidence.";
        };
    }

    private String processInputSymbol(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_TEMPERATURE -> "T_norm";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_MAGNETIC -> "B_norm";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_OPTICAL -> "I_norm";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_VOLTAGE -> "V_norm";
            default -> "x";
        };
    }

    private String processInputUnit(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_TEMPERATURE -> "T-index /100";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_MAGNETIC -> "B-level norm";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_OPTICAL -> "intensity norm";
            case UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_VOLTAGE -> "V-level norm";
            default -> "";
        };
    }

    private String processEvidenceNote(PortQuality quality) {
        return switch (quality) {
            case VALID -> "trust current server transformation";
            case SATURATED -> "bounded result; inspect range/profile";
            case NOT_READY -> "await first authoritative runtime result";
            case STALE -> "restore source/coverage and reacquire";
            case NO_SIGNAL -> "no trustworthy physical source";
            case TOPOLOGY_ERROR -> "resolve competing/invalid topology";
            default -> "repair evidence before use";
        };
    }

    private void measurementPioneerOverview(GuiGraphics g) {
        int kind = menu.pioneerMeasurementKind();
        PortQuality evidence = menu.pioneerEvidenceQuality();
        statusBadge(g, title.getString().toUpperCase(), qualityColor(evidence), 16, 80);
        statusBadge(g, "PIONEER WAVE 13 • MEASUREMENT", INFO, 207, 80);
        formulaCard(g, measurementEquation(kind), 108);

        switch (kind) {
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_TEMPERATURE -> {
                variableRole(g, "MEASURED", "T_target", Integer.toString(menu.pioneerSecondary()), "T-index", 137);
                variableRole(g, "SOLVER", "T_cached", Integer.toString(menu.pioneerPrimary()), "T-index", 153);
                variableRole(g, "MEASURED", "N_bodies", Integer.toString(menu.pioneerTertiary()), "thermal bodies", 169);
                variableRole(g, "EVIDENCE", "coverage", menu.pioneerQuaternary() + "/6", "faces", 185);
            }
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_LIGHT -> {
                variableRole(g, "MEASURED", "B_local", Integer.toString(menu.pioneerPrimary()), "light", 137);
                variableRole(g, "DERIVED", "y", Integer.toString(menu.pioneerSecondary()), "redstone", 153);
                variableRole(g, "PROFILE", "sensor profile", "BALANCED (" + menu.pioneerTertiary() + ")", "", 169);
                variableRole(g, "SOLVER", "Δt_sample", Integer.toString(menu.pioneerQuaternary()), "ticks", 185);
            }
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_TANK -> {
                variableRole(g, "MEASURED", "h", Integer.toString(menu.pioneerPrimary()), "fluid blocks", 137);
                variableRole(g, "EVIDENCE", "coverage", menu.pioneerSecondary() + "/" + menu.pioneerTertiary(), "cells", 153);
                variableRole(g, "DERIVED", "y", Integer.toString(menu.pioneerQuaternary()), "redstone", 169);
                variableRole(g, "PROFILE", "conditioning", "PRECISION", "", 185);
            }
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_ENTITY_DENSITY -> {
                variableRole(g, "MEASURED", "N", Integer.toString(menu.pioneerPrimary()), "living entities", 137);
                variableRole(g, "EVIDENCE", "coverage", menu.pioneerSecondary() != 0 ? "COMPLETE" : "INCOMPLETE", "", 153);
                variableRole(g, "DERIVED", "y", Integer.toString(menu.pioneerTertiary()), "redstone", 169);
                variableRole(g, "SOLVER", "r_xy", Integer.toString(menu.pioneerQuaternary()), "blocks", 185);
            }
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_LAPIS_METER -> {
                variableRole(g, "MEASURED", "m", String.format(java.util.Locale.ROOT, "%.2f", menu.pioneerPrimary() / 100.0), "Lapis", 137);
                variableRole(g, "EVIDENCE", "quality", evidence.name(), "", 153);
                variableRole(g, "PROFILE", "range", "0.00..1.00", "Lapis", 169);
                variableRole(g, "DERIVED", "display resolution", "0.01", "Lapis", 185);
            }
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_LAPIS_RANGE -> {
                String distance = menu.pioneerPrimary() < 0 ? "NO TARGET" : Integer.toString(menu.pioneerPrimary());
                variableRole(g, "MEASURED", "d", distance, "blocks", 137);
                variableRole(g, "ADJUSTABLE", "R", Integer.toString(menu.pioneerSecondary()), "blocks", 153);
                variableRole(g, "DERIVED", "y_L", String.format(java.util.Locale.ROOT, "%.2f", menu.pioneerTertiary() / 100.0), "Lapis", 169);
                variableRole(g, "PROFILE", "conditioning", lapisProfileName(menu.pioneerQuaternary()), "", 185);
            }
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_ANALOG_INDICATOR -> {
                variableRole(g, "MEASURED", "x_back", Integer.toString(menu.pioneerPrimary()), "redstone", 137);
                variableRole(g, "DERIVED", "display", Integer.toString(menu.pioneerSecondary()), "redstone", 153);
                variableRole(g, "EVIDENCE", "source quality", evidence.name(), "", 169);
                variableRole(g, "PROFILE", "range", "0..15", "redstone", 185);
            }
            default -> { }
        }

        evidenceRow(g, "EVIDENCE", evidence.name(), "server snapshot", measurementEvidenceNote(kind, evidence), 207);
        wrappedText(g, measurementInterpretation(kind), 16, 230, workspaceWidth() - 24, MUTED);
    }

    private String measurementEquation(int kind) {
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

    private String measurementInterpretation(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_TEMPERATURE ->
                    "Six-face thermal coverage is part of the evidence. Incomplete chunk coverage retains the last trustworthy cached temperature rather than manufacturing an ambient reading.";
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_LIGHT ->
                    "UP is the physical light aperture. Conditioning is server-owned; the FRONT redstone output is a bounded representation, not the raw brightness source itself.";
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_TANK ->
                    "The scan stops at the first loaded empty cell or the 16-block ceiling. Unloaded cells are unknown coverage, never an inferred empty boundary.";
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_ENTITY_DENSITY ->
                    "The occupancy query is accepted only when every chunk touched by the aperture is loaded. Counts above 15 remain SATURATED evidence rather than silently becoming an ordinary 15.";
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_LAPIS_METER ->
                    "This block is observer-only. A real zero-valued Lapis sample is valid evidence; conflict or stale aperture evidence is reported separately from the numeric value.";
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_LAPIS_RANGE ->
                    "R is a real server-bound range control. Changing range/profile invalidates prior sampled output before reacquisition; the client does not recompute the range scan.";
            case UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_ANALOG_INDICATOR ->
                    "The indicator is readout-only. STALE evidence preserves the previous display, while a connected source configured to zero remains a valid zero measurement.";
            default -> "Server-authoritative measurement evidence.";
        };
    }

    private String measurementEvidenceNote(int kind, PortQuality quality) {
        if (quality == PortQuality.VALID) return "trust current measurement";
        if (quality == PortQuality.SATURATED) return "value is bounded; inspect range";
        if (quality == PortQuality.NOT_READY) return "await first authoritative sample";
        if (quality == PortQuality.STALE) return "restore coverage / reacquire";
        if (quality == PortQuality.NO_SIGNAL) return kind == UniversalFieldDeviceMenu.PIONEER_MEASUREMENT_LAPIS_RANGE
                ? "no target inside selected range" : "no trustworthy source";
        return "repair evidence before use";
    }

    private void ports(GuiGraphics g) {
        statusBadge(g, "ALL SIX PHYSICAL FACES", INFO, 16, 80);
        Direction[] order = {Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        int y = 102;
        for (Direction side : order) {
            portLine(g, side, y);
            y += 18;
        }
    }

    private void portLine(GuiGraphics g, Direction side, int y) {
        String face = side.getName().toUpperCase();
        if (!menu.hasPort(side)) {
            statusLine(g, face, "NO DECLARED ENGINEERING PORT", MUTED, y);
            return;
        }
        String role = menu.isBidirectional(side) ? "BIDIR"
                : menu.isInput(side) ? "INPUT"
                : menu.isOutput(side) ? "OUTPUT" : "PASSIVE";
        PortQuality quality = menu.quality(side);
        String value = menu.value(side) + " [" + menu.minimum(side) + ".." + menu.maximum(side) + "]";
        String text = role + " • " + menu.domain(side).label() + " • " + menu.portKind(side).name()
                + " • " + value + " • " + quality.name();
        statusLine(g, face, text, qualityColor(quality), y);
    }

    private void configure(GuiGraphics g) {
        int kind = menu.configKind();
        switch (kind) {
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_TRANSDUCER -> {
                statusBadge(g, "MEASUREMENT CONDITIONING", INFO, 16, 80);
                labelValue(g, "Profile", lapisProfileName(menu.configPrimary()) + " (" + menu.configPrimary() + ")", 101);
                if (menu.pioneerProcessKind() >= UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_TEMPERATURE
                        && menu.pioneerProcessKind() <= UniversalFieldDeviceMenu.PIONEER_PROCESS_LAPIS_VOLTAGE) {
                    labelValue(g, "Sample period", menu.pioneerProcessTertiary() + " ticks", 141);
                    labelValue(g, "Resolution / noise", menu.pioneerProcessQuaternary() + "/100 • ±"
                            + menu.pioneerProcessQuinary() + "/100", 159);
                    labelValue(g, "Latency", menu.pioneerProcessSenary() + " sample(s)", 177);
                    safeText(g, "All four quantities are synchronized from the server profile; Route owns the physical sensing boundary.", 16, 203, MUTED);
                } else {
                    safeText(g, "Profile changes sampling period, resolution, noise and latency on the server.", 16, 148, TEXT);
                    safeText(g, "Direction belongs on Route; no routing control is duplicated here.", 16, 168, MUTED);
                }
            }
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_RANGE -> {
                statusBadge(g, "RANGE MEASUREMENT CONDITIONING", INFO, 16, 80);
                labelValue(g, "Profile", lapisProfileName(menu.configPrimary()) + " (" + menu.configPrimary() + ")", 101);
                labelValue(g, "Maximum range", menu.configSecondary() + " blocks", 141);
                safeText(g, "Changing profile or range invalidates the old sample before resampling.", 16, 188, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_MOLECULAR_RECEIVER -> {
                statusBadge(g, "MOLECULAR RECEIVER", INFO, 16, 80);
                labelValue(g, "Sensitivity", Integer.toString(menu.configPrimary()), 101);
                labelValue(g, "Retained peak", Integer.toString(menu.configSecondary()), 141);
                safeText(g, "Reset clears filtered/peak history; the fixed UP aperture remains unchanged.", 16, 188, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_ALARM -> {
                int alarmState = menu.configSecondary();
                String label = alarmState == 0 ? "ALARM • CLEAR" : alarmState == 2 ? "ALARM • ACTIVE / UNACK" : "ALARM • ACTIVE / ACK";
                int color = alarmState == 0 ? GOOD : alarmState == 2 ? BAD : WARN;
                statusBadge(g, label, color, 16, 80);
                labelValue(g, "Severity", Integer.toString(menu.configPrimary()), 101);
                labelValue(g, "Operator state", alarmStateName(alarmState), 141);
                safeText(g, "ACK changes operator-attention state; process RESET / CLEAR remains a physical input.", 16, 188, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_SAMPLE_HOLD -> {
                statusBadge(g, "SAMPLE & HOLD", INFO, 16, 80);
                labelValue(g, "Trigger mode", SampleHoldBlock.modeName(menu.configPrimary()), 101);
                labelValue(g, "Captures", Integer.toString(menu.configSecondary()), 141);
                safeText(g, "Clear held value is an operator action; TRIGGER and RESET remain physical ports.", 16, 188, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_CALIBRATION -> {
                statusBadge(g, "CALIBRATION PROFILE", INFO, 16, 80);
                labelValue(g, "Transfer", CalibrationModuleBlock.profileName(menu.configPrimary()), 101);
                safeText(g, "OBSERVED, REFERENCE and CALIBRATED faces rotate together on Route.", 16, 148, TEXT);
            }
            case UniversalFieldDeviceMenu.CONFIG_PWM -> {
                statusBadge(g, "PWM CONTROL", INFO, 16, 80);
                labelValue(g, "Period", PwmControllerBlock.periodFor(menu.configPrimary()) + " ticks", 101);
                labelValue(g, "Invert", menu.configSecondary() != 0 ? "ON" : "OFF", 141);
                safeText(g, "COMMAND, PWM OUT and INHIBIT rotate as one physical interface layout on Route.", 16, 188, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_FAULT_INJECTOR -> {
                boolean armed = menu.configSecondary() != 0;
                statusBadge(g, armed ? "FAULT INJECTOR • ARMED" : "FAULT INJECTOR • SAFE", armed ? WARN : GOOD, 16, 80);
                labelValue(g, "Fault mode", FaultInjectorBlock.modeLabelFor(menu.configPrimary()), 101);
                labelValue(g, "ARM state", armed ? "ARMED / INJECTION ACTIVE" : "SAFE / PASS-THROUGH", 141);
                safeText(g, "Reset statistics preserves ARM state and last I/O evidence; FAULT ARM remains a physical input.", 16, 188, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_SEQUENCE_CONTROLLER -> {
                statusBadge(g, "SEQUENCE CONTROLLER", menu.configPrimary() == 0 ? MUTED : GOOD, 16, 80);
                labelValue(g, "Current state", sequenceStepName(menu.configPrimary()), 101);
                labelValue(g, "Completed cycles", Integer.toString(menu.configSecondary()), 141);
                safeText(g, "Operator reset returns runtime state to IDLE; wired RESET remains independent.", 16, 188, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_SAFETY_INTERLOCK -> {
                boolean evaluated = menu.configPrimary() >= 0;
                boolean permit = menu.configSecondary() != 0;
                statusBadge(g, !evaluated ? "INTERLOCK • REACQUIRING" : permit ? "INTERLOCK • PERMIT" : "INTERLOCK • BLOCKED",
                        !evaluated ? INFO : permit ? GOOD : WARN, 16, 80);
                labelValue(g, "Missing permissives", failedPermissives(menu.configPrimary()), 101);
                labelValue(g, "Permit output", permit ? "15 / ENABLED" : "0 / BLOCKED", 141);
                safeText(g, !evaluated
                                ? "Diagnostic counters were reset; permissive evidence will repopulate on the next evaluation."
                                : "Reset counters does not bypass permissives A/B/C or force the permit output.",
                        16, 188, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_TOPOLOGY_DEBUGGER -> {
                statusBadge(g, attentionCount() == 0 ? "TOPOLOGY • NOMINAL" : "TOPOLOGY • ISSUE", attentionCount() == 0 ? GOOD : WARN, 16, 80);
                labelValue(g, "Scan count", Integer.toString(menu.configPrimary()), 101);
                labelValue(g, "Target mode", menu.configSecondary() != 0 ? "VANILLA REDSTONE" : "ENGINEERING PORTS", 141);
                safeText(g, "SCAN is opposite alarm TX; reset clears retained scan counters only.", 16, 188, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_COPPER_VOLTAGE_SOURCE -> {
                statusBadge(g, "COPPER SOURCE CONTROL", INFO, 16, 80);
                labelValue(g, "V_set", menu.configPrimary() + " V-eq", 101);
                labelValue(g, "Range / step / default", "0..15 / 1 / 12", 141);
                safeText(g, "Previous clamps at 0; Next wraps 15→0, matching the established block interaction.", 16, 178, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_COPPER_LOAD -> {
                statusBadge(g, "COPPER LOAD CONTROL", INFO, 16, 80);
                labelValue(g, "R", menu.configPrimary() + " R-eq", 101);
                labelValue(g, "Range / step / default", "1..15 / 1 / 4", 141);
                safeText(g, "Changing R recomputes connected Copper components on the server.", 16, 178, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_COPPER_SERIES_RESISTOR -> {
                statusBadge(g, "SERIES RESISTOR CONTROL", INFO, 16, 80);
                labelValue(g, "R_s", menu.configPrimary() + " R-eq", 101);
                labelValue(g, "Range / step / default", "1..15 / 1 / 4", 141);
                safeText(g, "Changing R_s invalidates old V_out/load evidence until the next authoritative evaluation.", 16, 178, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_COPPER_CAPACITOR -> {
                statusBadge(g, "RC STORAGE PROFILE", INFO, 16, 80);
                labelValue(g, "C_index", Integer.toString(menu.configPrimary() + 1), 101);
                labelValue(g, "τ proxy", menu.pioneerProcessTertiary() + " ticks", 141);
                labelValue(g, "Profiles / default", "1..4 / default 2", 159);
                safeText(g, "τ is an RSE discrete response proxy, not an SI capacitance claim.", 16, 190, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_COPPER_FUSE -> {
                statusBadge(g, menu.configSecondary()!=0 ? "FUSE • TRIPPED" : "FUSE • ARMED",
                        menu.configSecondary()!=0 ? BAD : GOOD, 16, 80);
                labelValue(g, "I_rating", menu.configPrimary() + " I-eq", 101);
                labelValue(g, "Range / step / default", "1..15 / 1 / 4", 141);
                safeText(g, "Reset clears the latch request; protection must re-evaluate complete load evidence before READY.", 16, 190, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_NOISE -> {
                statusBadge(g, "LAPIS NOISE SOURCE", INFO, 16, 80);
                labelValue(g, "Baseline μ", String.format(java.util.Locale.ROOT, "%.2f", menu.configPrimary()*0.05), 101);
                labelValue(g, "Noise bound", "±"+String.format(java.util.Locale.ROOT, "%.2f", menu.configSecondary()*0.02), 141);
                labelValue(g, "Ranges / defaults", "μ 0..1 step .05 / .50 • noise 0.. .20 step .02 / .06", 159);
                safeText(g, "Configuration resets the immediate sample to baseline; scheduled deterministic noise resumes server-side.", 16, 190, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_QUARTZ_OSCILLATOR -> {
                statusBadge(g, "QUARTZ LAB SOURCE", INFO, 16, 80);
                labelValue(g, "Nominal period", menu.pioneerProcessPrimary()+" ticks", 101);
                labelValue(g, "Jitter bound", "±"+menu.configSecondary()+" ticks", 141);
                labelValue(g, "Period profiles", "2 / 4 / 8 / 16 / 32 ticks • default 8", 159);
                safeText(g, "Last realized half-interval is evidence, not a configuration prediction.", 16, 190, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_QUARTZ_PHASE_DELAY -> {
                statusBadge(g, "QUARTZ EDGE DELAY", INFO, 16, 80);
                labelValue(g, "Delay D", menu.configPrimary()+" ticks", 101);
                labelValue(g, "Range / step / default", "1..16 / 1 / 2 ticks", 141);
                safeText(g, "Changing D clears pending/output state; only a later real rising edge can schedule a pulse.", 16, 178, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_IRON_CORE -> {
                statusBadge(g, menu.configPrimary()!=0 ? "IRON CORE • REMANENT" : "IRON CORE • SOFT", menu.configPrimary()!=0 ? WARN : INFO, 16, 80);
                labelValue(g, "Magnetization", menu.configPrimary()!=0 ? "MAGNETIZED" : "SOFT", 101);
                labelValue(g, "Threshold / radius", menu.pioneerProcessSecondary()+" / "+menu.pioneerProcessTertiary()+" blocks", 141);
                safeText(g, "Demagnetize is explicit; a complete strong external-field scan may magnetize the core again.", 16, 178, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_MASS -> {
                statusBadge(g, "THERMAL MASS CONTROL", INFO, 16, 80);
                labelValue(g, "Capacity index C", Integer.toString(menu.configPrimary()), 101);
                labelValue(g, "Range / step / default", "1..4 / 1 / 2", 141);
                labelValue(g, "Current max step", menu.pioneerProcessSenary()+" T-index", 159);
                safeText(g, "C also sets the server update cadence to 5C ticks.", 16, 190, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_HEATER -> {
                statusBadge(g, "THERMAL HEATER CONTROL", INFO, 16, 80);
                labelValue(g, "Resistance R", menu.pioneerProcessSecondary()+" R-eq", 101);
                labelValue(g, "Profiles / default", "1 / 2 / 4 / 8 • default 2", 141);
                safeText(g, "Changing R recomputes the Copper boundary and thermal target on the server.", 16, 178, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_RADIATOR -> {
                statusBadge(g, "PASSIVE RADIATOR CONTROL", INFO, 16, 80);
                labelValue(g, "Cooling coefficient", Integer.toString(menu.configPrimary()), 101);
                labelValue(g, "Range / step / default", "1..4 / 1 / 2", 141);
                safeText(g, "Cooling remains bounded by the ambient floor; this control cannot create active refrigeration.", 16, 178, MUTED);
            }
            default -> {
                boolean rotatable = menu.rotatableSeriesAxis();
                statusBadge(g, lapisPrecisionMeasurementPresent() ? "PRECISION OBSERVER • NO PROCESS PARAMETER" : "NO UNIVERSAL PARAMETERS",
                        lapisPrecisionMeasurementPresent() ? INFO : MUTED, 16, 80);
                labelValue(g, "Current route", routeText(), 106);
                safeText(g, lapisPrecisionMeasurementPresent()
                                ? "The selected measurement face samples precision information; it does not drive or quantize the source."
                                : rotatable
                                ? "This device has a real routable interface; change it on Route, not Configure."
                                : "This device has no shared configurable parameter in the universal HMI.",
                        16, 132, TEXT);
                safeText(g, lapisPrecisionMeasurementPresent()
                                ? "Precision identity is observational: 0..100 engineering scale with 0.01 display resolution."
                                : "No client-side physics or hidden port mutation is performed.",
                        16, 152, lapisPrecisionMeasurementPresent() ? INFO : MUTED);
            }
        }
        formulaCard(g, universalContract(kind), 218);
        renderFormulaParameterWorkbench(g, kind, 252);
        wrappedText(g, "Universal HMI rule: controls express server intent only; Route owns physical interfaces; diagnostics/history never invent process state that the underlying device does not retain.", 16, 338, workspaceWidth() - 24, MUTED);
    }

    private boolean directPrimaryNumericKind(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.CONFIG_COPPER_VOLTAGE_SOURCE,
                 UniversalFieldDeviceMenu.CONFIG_COPPER_LOAD,
                 UniversalFieldDeviceMenu.CONFIG_COPPER_SERIES_RESISTOR,
                 UniversalFieldDeviceMenu.CONFIG_COPPER_FUSE,
                 UniversalFieldDeviceMenu.CONFIG_LAPIS_NOISE,
                 UniversalFieldDeviceMenu.CONFIG_QUARTZ_PHASE_DELAY,
                 UniversalFieldDeviceMenu.CONFIG_THERMAL_MASS,
                 UniversalFieldDeviceMenu.CONFIG_THERMAL_RADIATOR -> true;
            default -> false;
        };
    }

    private int directPrimaryMinimum(int kind) {
        return kind == UniversalFieldDeviceMenu.CONFIG_COPPER_VOLTAGE_SOURCE ? 0 : 1;
    }

    private int directPrimaryMaximum(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.CONFIG_COPPER_VOLTAGE_SOURCE,
                 UniversalFieldDeviceMenu.CONFIG_COPPER_LOAD,
                 UniversalFieldDeviceMenu.CONFIG_COPPER_SERIES_RESISTOR,
                 UniversalFieldDeviceMenu.CONFIG_COPPER_FUSE -> 15;
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_NOISE -> 20;
            case UniversalFieldDeviceMenu.CONFIG_QUARTZ_PHASE_DELAY -> 16;
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_MASS,
                 UniversalFieldDeviceMenu.CONFIG_THERMAL_RADIATOR -> 4;
            default -> -1;
        };
    }

    private boolean directSecondaryNumericKind(int kind) {
        return kind == UniversalFieldDeviceMenu.CONFIG_LAPIS_NOISE
                || kind == UniversalFieldDeviceMenu.CONFIG_QUARTZ_OSCILLATOR;
    }

    private int directSecondaryMinimum(int kind) {
        return 0;
    }

    private int directSecondaryMaximum(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_NOISE -> 10;
            case UniversalFieldDeviceMenu.CONFIG_QUARTZ_OSCILLATOR -> 3;
            default -> -1;
        };
    }

    private boolean numericEntryText(String value) {
        if (value.isEmpty()) return true;
        int dots = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '.') {
                if (++dots > 1) return false;
            } else if (!Character.isDigit(c)) {
                return false;
            }
        }
        return true;
    }

    private String directPrimaryDisplayValue(int kind) {
        if (kind == UniversalFieldDeviceMenu.CONFIG_LAPIS_NOISE) {
            return String.format(java.util.Locale.ROOT, "%.2f", menu.configPrimary() * 0.05);
        }
        return Integer.toString(menu.configPrimary());
    }

    private String directSecondaryDisplayValue(int kind) {
        if (kind == UniversalFieldDeviceMenu.CONFIG_LAPIS_NOISE) {
            return String.format(java.util.Locale.ROOT, "%.2f", menu.configSecondary() * 0.02);
        }
        return Integer.toString(menu.configSecondary());
    }

    private Integer directPrimaryParsedValue(int kind) {
        if (primaryDirectInput == null) return null;
        try {
            double entered = Double.parseDouble(primaryDirectInput.getValue());
            if (kind == UniversalFieldDeviceMenu.CONFIG_LAPIS_NOISE) {
                int raw = (int) Math.round(entered / 0.05);
                return Math.abs(entered - raw * 0.05) < 0.0001 ? raw : null;
            }
            int raw = (int) Math.round(entered);
            return Math.abs(entered - raw) < 0.0001 ? raw : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean directPrimaryInputValid(int kind) {
        Integer value = directPrimaryParsedValue(kind);
        return value != null && directPrimaryNumericKind(kind)
                && value >= directPrimaryMinimum(kind) && value <= directPrimaryMaximum(kind);
    }

    private Integer directSecondaryParsedValue(int kind) {
        if (secondaryDirectInput == null) return null;
        try {
            double entered = Double.parseDouble(secondaryDirectInput.getValue());
            if (kind == UniversalFieldDeviceMenu.CONFIG_LAPIS_NOISE) {
                int raw = (int) Math.round(entered / 0.02);
                return Math.abs(entered - raw * 0.02) < 0.0001 ? raw : null;
            }
            int raw = (int) Math.round(entered);
            return Math.abs(entered - raw) < 0.0001 ? raw : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean directSecondaryInputValid(int kind) {
        Integer value = directSecondaryParsedValue(kind);
        return value != null && directSecondaryNumericKind(kind)
                && value >= directSecondaryMinimum(kind) && value <= directSecondaryMaximum(kind);
    }

    private void submitDirectPrimaryValue() {
        int kind = menu.configKind();
        Integer value = directPrimaryParsedValue(kind);
        if (value == null || !directPrimaryInputValid(kind)) return;
        sendMenuButton(UniversalFieldDeviceMenu.BUTTON_CONFIG_PRIMARY_DIRECT_BASE + value);
        if (primaryDirectInput != null) primaryDirectInput.setFocused(false);
    }

    private void submitDirectSecondaryValue() {
        int kind = menu.configKind();
        Integer value = directSecondaryParsedValue(kind);
        if (value == null || !directSecondaryInputValid(kind)) return;
        sendMenuButton(UniversalFieldDeviceMenu.BUTTON_CONFIG_SECONDARY_DIRECT_BASE + value);
        if (secondaryDirectInput != null) secondaryDirectInput.setFocused(false);
    }

    private String directPrimaryRangeLabel(int kind) {
        if (kind == UniversalFieldDeviceMenu.CONFIG_LAPIS_NOISE) return "0.00..1.00 Lapis • step 0.05";
        return directPrimaryMinimum(kind) + ".." + directPrimaryMaximum(kind);
    }

    private String directSecondaryRangeLabel(int kind) {
        if (kind == UniversalFieldDeviceMenu.CONFIG_LAPIS_NOISE) return "0.00..0.20 Lapis • step 0.02";
        if (kind == UniversalFieldDeviceMenu.CONFIG_QUARTZ_OSCILLATOR) return "0..3 ticks";
        return directSecondaryMinimum(kind) + ".." + directSecondaryMaximum(kind);
    }

    private void renderFormulaParameterWorkbench(GuiGraphics g, int kind, int y) {
        String symbol = formulaParameterSymbol(kind);
        if (symbol.isBlank()) {
            statusLine(g, "FORMULA PARAMETER WORKBENCH", "READ-ONLY • no model coefficient owned by this HMI", MUTED, y);
            return;
        }

        statusLine(g, "FORMULA PARAMETER WORKBENCH",
                symbol + " = " + primaryControlValue(kind) + " • SERVER-BACKED", INFO, y);
        String editMode = directPrimaryNumericKind(kind)
                ? "DIRECT ENTRY • " + directPrimaryRangeLabel(kind)
                : "DISCRETE / PROFILE • cycle valid states";
        safeText(g, editMode, 16, y + 22, directPrimaryNumericKind(kind) ? GOOD : INFO);
        safeText(g, "Changing this variable sends operator intent to the server; derived values and evidence remain server-computed.",
                16, y + 42, TEXT);
        safeText(g, formulaParameterImpact(kind), 16, y + 62, MUTED);

        String secondarySymbol = secondaryFormulaParameterSymbol(kind);
        if (!secondarySymbol.isBlank()) {
            statusLine(g, "SECOND PARAMETER",
                    secondarySymbol + " = " + secondaryControlValue(kind) + " • SERVER-BACKED", INFO, y + 82);
            if (directSecondaryNumericKind(kind)) {
                safeText(g, "DIRECT ENTRY • " + directSecondaryRangeLabel(kind),
                        16, y + 102, GOOD);
            }
        }
    }

    private String formulaParameterSymbol(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_TRANSDUCER, UniversalFieldDeviceMenu.CONFIG_LAPIS_RANGE -> "profile";
            case UniversalFieldDeviceMenu.CONFIG_MOLECULAR_RECEIVER -> "sensitivity";
            case UniversalFieldDeviceMenu.CONFIG_ALARM -> "severity";
            case UniversalFieldDeviceMenu.CONFIG_SAMPLE_HOLD -> "trigger edge";
            case UniversalFieldDeviceMenu.CONFIG_CALIBRATION -> "transfer profile";
            case UniversalFieldDeviceMenu.CONFIG_PWM -> "T";
            case UniversalFieldDeviceMenu.CONFIG_FAULT_INJECTOR -> "fault mode";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_VOLTAGE_SOURCE -> "V_set";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_LOAD -> "R";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_SERIES_RESISTOR -> "R_s";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_CAPACITOR -> "C_index";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_FUSE -> "I_rating";
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_NOISE -> "μ";
            case UniversalFieldDeviceMenu.CONFIG_QUARTZ_OSCILLATOR -> "T_nom";
            case UniversalFieldDeviceMenu.CONFIG_QUARTZ_PHASE_DELAY -> "D";
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_MASS -> "C_index";
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_HEATER -> "R";
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_RADIATOR -> "k_cool";
            default -> "";
        };
    }

    private String primaryControlValue(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.CONFIG_PWM -> menu.pioneerProcessSecondary() + " ticks";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_VOLTAGE_SOURCE -> menu.configPrimary() + " V-eq";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_LOAD, UniversalFieldDeviceMenu.CONFIG_COPPER_SERIES_RESISTOR -> menu.configPrimary() + " R-eq";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_CAPACITOR -> (menu.configPrimary() + 1) + " • τ=" + menu.pioneerProcessTertiary() + " ticks";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_FUSE -> menu.configPrimary() + " I-eq";
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_NOISE -> String.format(java.util.Locale.ROOT, "%.2f Lapis", menu.configPrimary() * 0.05);
            case UniversalFieldDeviceMenu.CONFIG_QUARTZ_OSCILLATOR -> menu.pioneerProcessPrimary() + " ticks";
            case UniversalFieldDeviceMenu.CONFIG_QUARTZ_PHASE_DELAY -> menu.configPrimary() + " ticks";
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_MASS -> Integer.toString(menu.configPrimary());
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_HEATER -> menu.pioneerProcessSecondary() + " R-eq";
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_RADIATOR -> menu.configPrimary() + " index/tick";
            default -> Integer.toString(menu.configPrimary());
        };
    }

    private String secondaryFormulaParameterSymbol(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_RANGE -> "range";
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_NOISE -> "|η|max";
            case UniversalFieldDeviceMenu.CONFIG_QUARTZ_OSCILLATOR -> "J";
            default -> "";
        };
    }

    private String secondaryControlValue(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_RANGE -> menu.configSecondary() + " blocks";
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_NOISE -> "±" + String.format(java.util.Locale.ROOT, "%.2f Lapis", menu.configSecondary() * 0.02);
            case UniversalFieldDeviceMenu.CONFIG_QUARTZ_OSCILLATOR -> "±" + menu.configSecondary() + " ticks";
            default -> "";
        };
    }

    private String formulaParameterImpact(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.CONFIG_PWM -> "Equation link: T changes PWM quantization, N_on and effective duty cycle.";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_VOLTAGE_SOURCE -> "Equation link: V_set is the source term propagated into the Copper network.";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_LOAD -> "Equation link: I=V/R and P=V²/R; changing R changes both derived quantities.";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_SERIES_RESISTOR -> "Equation link: R_s participates in the divider/load solution for V_out and I.";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_CAPACITOR -> "Equation link: C_index selects the discrete τ response profile used by the RC storage model.";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_FUSE -> "Equation link: I_rating is the trip threshold evaluated against server-computed current.";
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_NOISE -> "Equation link: y[n]=μ+η[n], with η bounded by the secondary noise parameter.";
            case UniversalFieldDeviceMenu.CONFIG_QUARTZ_OSCILLATOR -> "Equation link: T_nom sets the nominal period; J bounds realized scheduling jitter.";
            case UniversalFieldDeviceMenu.CONFIG_QUARTZ_PHASE_DELAY -> "Equation link: D sets the post-edge delay before the output pulse.";
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_MASS -> "Equation link: C_index limits ΔT per update and sets the thermal update cadence.";
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_HEATER -> "Equation link: I=V/R and P=V²/R feed the server thermal target.";
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_RADIATOR -> "Equation link: k_cool bounds passive cooling toward the ambient floor.";
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_RANGE -> "Model link: profile changes sensor conditioning; range independently bounds spatial sampling.";
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_TRANSDUCER -> "Model link: profile selects sampling period, resolution, noise and latency.";
            case UniversalFieldDeviceMenu.CONFIG_MOLECULAR_RECEIVER -> "Model link: sensitivity changes the receiver gain/profile used by the server filter.";
            case UniversalFieldDeviceMenu.CONFIG_SAMPLE_HOLD -> "State-equation link: trigger-edge selection determines when y_hold acquires a new sample.";
            case UniversalFieldDeviceMenu.CONFIG_CALIBRATION -> "Model link: transfer profile selects the server calibration mapping.";
            default -> "This control changes authoritative device state; downstream values remain server-derived evidence.";
        };
    }

    private String primaryControlName(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_TRANSDUCER, UniversalFieldDeviceMenu.CONFIG_LAPIS_RANGE -> "Profile";
            case UniversalFieldDeviceMenu.CONFIG_MOLECULAR_RECEIVER -> "Sensitivity";
            case UniversalFieldDeviceMenu.CONFIG_ALARM -> "Severity";
            case UniversalFieldDeviceMenu.CONFIG_SAMPLE_HOLD -> "Trigger edge";
            case UniversalFieldDeviceMenu.CONFIG_CALIBRATION -> "Transfer";
            case UniversalFieldDeviceMenu.CONFIG_PWM -> "Period";
            case UniversalFieldDeviceMenu.CONFIG_FAULT_INJECTOR -> "Fault mode";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_VOLTAGE_SOURCE -> "Voltage";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_LOAD -> "Resistance";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_SERIES_RESISTOR -> "R_s";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_CAPACITOR -> "C profile";
            case UniversalFieldDeviceMenu.CONFIG_COPPER_FUSE -> "I rating";
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_NOISE -> "Baseline μ";
            case UniversalFieldDeviceMenu.CONFIG_QUARTZ_OSCILLATOR -> "Period";
            case UniversalFieldDeviceMenu.CONFIG_QUARTZ_PHASE_DELAY -> "Delay D";
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_MASS -> "Capacity C";
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_HEATER -> "Resistance R";
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_RADIATOR -> "Cooling k";
            default -> "Parameter";
        };
    }

    private String universalContract(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_TRANSDUCER -> "MODEL: profile owns sampling / resolution / noise / latency";
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_RANGE -> "MODEL: profile + bounded range; changing either invalidates stale sample";
            case UniversalFieldDeviceMenu.CONFIG_MOLECULAR_RECEIVER -> "ACTION: reset retained measurement history ≠ change fixed aperture";
            case UniversalFieldDeviceMenu.CONFIG_ALARM -> "STATE: ACK changes operator attention; physical RESET/CLEAR remains external";
            case UniversalFieldDeviceMenu.CONFIG_SAMPLE_HOLD -> "STATE: held value changes on configured trigger; CLEAR is explicit operator action";
            case UniversalFieldDeviceMenu.CONFIG_CALIBRATION -> "MODEL: profile-owned transfer; OBSERVED / REFERENCE / CALIBRATED remain distinct";
            case UniversalFieldDeviceMenu.CONFIG_PWM -> "MODEL: period profile + polarity inversion; INHIBIT remains physical";
            case UniversalFieldDeviceMenu.CONFIG_FAULT_INJECTOR -> "STATE: ARM gates injection; reset statistics does not disarm";
            case UniversalFieldDeviceMenu.CONFIG_SEQUENCE_CONTROLLER -> "STATE: sequence runtime is server-owned; operator reset returns to IDLE";
            case UniversalFieldDeviceMenu.CONFIG_SAFETY_INTERLOCK -> "STATE: permit follows server permissive evidence; diagnostics reset never bypasses";
            case UniversalFieldDeviceMenu.CONFIG_TOPOLOGY_DEBUGGER -> "EVIDENCE: scan target + counters are observer diagnostics, not topology mutation";
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_NOISE -> "MODEL: deterministic bounded source; baseline and noise bound are real server parameters";
            case UniversalFieldDeviceMenu.CONFIG_QUARTZ_OSCILLATOR -> "MODEL: nominal period + bounded scheduling jitter; realized interval is retained evidence";
            case UniversalFieldDeviceMenu.CONFIG_QUARTZ_PHASE_DELAY -> "MODEL: post-init rising-edge delay; configuration change invalidates pending runtime";
            case UniversalFieldDeviceMenu.CONFIG_IRON_CORE -> "STATE: bounded remanence; explicit demagnetize does not bypass future applied-field evaluation";
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_MASS -> "MODEL: capacity controls bounded step size and update cadence in the lumped thermal state";
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_HEATER -> "MODEL: resistance selects the existing Copper→thermal P=V²/R response";
            case UniversalFieldDeviceMenu.CONFIG_THERMAL_RADIATOR -> "MODEL: passive sink cools only above the ambient floor";
            default -> "CONTRACT: expose real ports/evidence only; no hidden universal physics";
        };
    }

    private void diagnostics(GuiGraphics g) {
        statusBadge(g, attentionCount() == 0 ? "PORT EVIDENCE NOMINAL" : "PORT EVIDENCE ATTENTION",
                attentionCount() == 0 ? GOOD : WARN, 16, 80);
        int y = 104;
        for (Direction side : Direction.values()) {
            if (!menu.hasPort(side)) continue;
            PortQuality quality = menu.quality(side);
            statusLine(g, side.getName().toUpperCase(), quality.name() + " • " + menu.domain(side).label(), qualityColor(quality), y);
            y += 18;
        }
        if (y == 104) safeText(g, "No EngineeringPortProvider interfaces are declared by this block.", 16, 108, WARN);
        if (lapisPrecisionMeasurementPresent() && y <= 190) {
            sectionRule(g, y + 2);
            statusLine(g, "Precision identity", lapisPrecisionText(), lapisPrecisionColor(), y + 14);
            safeText(g, lapisPrecisionNextAction(), 16, y + 34, lapisPrecisionColor());
        }
    }

    private void history(GuiGraphics g) {
        statusBadge(g, "LIVE ONLY • NO RETAINED HISTORY", INFO, 16, 80);
        labelValue(g, "Current evidence", "SYNCHRONIZED SNAPSHOT", 108);
        safeText(g, "Retained chronology belongs in analyzers, monitors, or the Diagnostic Tablet.", 16, 132, INFO);
        if (lapisPrecisionMeasurementPresent()) {
            labelValue(g, "Precision medium", "LAPIS • 0..100 • 0.01 display", 158);
            safeText(g, "History remains external even when the current precision sample is VALID.", 16, 180, MUTED);
        }
    }

    private int attentionCount() {
        int count = 0;
        for (Direction side : Direction.values()) {
            if (!menu.hasPort(side)) continue;
            PortQuality q = menu.quality(side);
            if (q != PortQuality.VALID) count++;
        }
        return count;
    }

    private boolean lapisPrecisionMeasurementPresent() {
        for (Direction side : Direction.values()) {
            if (!menu.hasPort(side)) continue;
            if (menu.domain(side) == EngineeringDomain.LAPIS && menu.portKind(side) == PortKind.MEASUREMENT) return true;
        }
        return false;
    }

    private Direction lapisMeasurementSide() {
        for (Direction side : Direction.values()) {
            if (!menu.hasPort(side)) continue;
            if (menu.domain(side) == EngineeringDomain.LAPIS && menu.portKind(side) == PortKind.MEASUREMENT) return side;
        }
        return null;
    }

    private String lapisPrecisionText() {
        Direction side = lapisMeasurementSide();
        if (side == null) return "N/A";
        PortQuality quality = menu.quality(side);
        if (quality != PortQuality.VALID) return quality.name() + " • precision unavailable";
        return String.format(java.util.Locale.ROOT, "%.2f • 0.01 resolution • VALID", menu.value(side) / 100.0);
    }

    private int lapisPrecisionColor() {
        Direction side = lapisMeasurementSide();
        return side == null ? MUTED : qualityColor(menu.quality(side));
    }

    private String lapisPrecisionNextAction() {
        Direction side = lapisMeasurementSide();
        if (side == null) return "";
        return switch (menu.quality(side)) {
            case VALID -> "NEXT • use this value as precision reference evidence; compare changes before quantizing to redstone.";
            case TOPOLOGY_ERROR -> "NEXT • resolve competing Lapis sources before selecting a precision value.";
            case STALE -> "NEXT • restore the sampled aperture/topology and reacquire precision evidence.";
            case NO_SIGNAL -> "NEXT • connect a unique Lapis precision source to the selected measurement face.";
            case SATURATED -> "NEXT • inspect measurement range/profile before treating the value as precise.";
            default -> "NEXT • repair the measurement evidence before using it as a reference.";
        };
    }

    private String routeText() {
        int ordinal = menu.facingOrdinal();
        if (ordinal < 0 || ordinal >= Direction.values().length) return "FIXED / NO ROUTE";
        Direction facing = Direction.values()[ordinal];
        return switch (menu.routeKind()) {
            case UniversalFieldDeviceMenu.ROUTE_SERIES_AXIS ->
                    facing.getOpposite().getName().toUpperCase() + " IN → " + facing.getName().toUpperCase() + " OUT";
            case UniversalFieldDeviceMenu.ROUTE_ENDPOINT_FRONT -> "FRONT = " + facing.getName().toUpperCase();
            case UniversalFieldDeviceMenu.ROUTE_PROBE_AXIS ->
                    "TEST " + facing.getName().toUpperCase() + " ↔ BUS " + facing.getOpposite().getName().toUpperCase();
            case UniversalFieldDeviceMenu.ROUTE_TERMINAL_INTERFACE ->
                    "INTERFACE AXIS " + facing.getName().toUpperCase() + " ↔ " + facing.getOpposite().getName().toUpperCase();
            case UniversalFieldDeviceMenu.ROUTE_MEASUREMENT_FACE -> "MEASURE = " + facing.getName().toUpperCase();
            case UniversalFieldDeviceMenu.ROUTE_FIXED_APERTURE_OUTPUT_FRONT ->
                    "UP APERTURE • FRONT OUT = " + facing.getName().toUpperCase();
            case UniversalFieldDeviceMenu.ROUTE_MULTI_PORT_LAYOUT ->
                    "MULTI-PORT LAYOUT • FRONT = " + facing.getName().toUpperCase();
            default -> "FIXED / NO ROUTE";
        };
    }

    private String routeKindLabel() {
        return switch (menu.routeKind()) {
            case UniversalFieldDeviceMenu.ROUTE_SERIES_AXIS -> "SERIES I/O ROUTING";
            case UniversalFieldDeviceMenu.ROUTE_ENDPOINT_FRONT -> "ENDPOINT FRONT ROUTING";
            case UniversalFieldDeviceMenu.ROUTE_PROBE_AXIS -> "PROBE AXIS ROUTING";
            case UniversalFieldDeviceMenu.ROUTE_TERMINAL_INTERFACE -> "TERMINAL INTERFACE ROUTING";
            case UniversalFieldDeviceMenu.ROUTE_MEASUREMENT_FACE -> "MEASUREMENT FACE ROUTING";
            case UniversalFieldDeviceMenu.ROUTE_FIXED_APERTURE_OUTPUT_FRONT -> "OUTPUT FRONT ROUTING";
            case UniversalFieldDeviceMenu.ROUTE_MULTI_PORT_LAYOUT -> "MULTI-PORT LAYOUT ROUTING";
            default -> "READ-ONLY CONFIGURATION";
        };
    }

    private String routeRule() {
        return switch (menu.routeKind()) {
            case UniversalFieldDeviceMenu.ROUTE_SERIES_AXIS -> "INPUT = opposite(OUTPUT)";
            case UniversalFieldDeviceMenu.ROUTE_ENDPOINT_FRONT -> "FRONT selects the endpoint-facing side";
            case UniversalFieldDeviceMenu.ROUTE_PROBE_AXIS -> "TEST and BUS remain opposite";
            case UniversalFieldDeviceMenu.ROUTE_TERMINAL_INTERFACE -> "Vanilla and cable interfaces remain opposite";
            case UniversalFieldDeviceMenu.ROUTE_MEASUREMENT_FACE -> "Only the selected face is sampled";
            case UniversalFieldDeviceMenu.ROUTE_FIXED_APERTURE_OUTPUT_FRONT -> "UP input stays fixed; FRONT output rotates";
            case UniversalFieldDeviceMenu.ROUTE_MULTI_PORT_LAYOUT -> "FRONT, BACK and side ports rotate together";
            default -> "DEVICE-SPECIFIC / FIXED";
        };
    }

    private String routeDescription() {
        return switch (menu.routeKind()) {
            case UniversalFieldDeviceMenu.ROUTE_SERIES_AXIS -> "Route cycles the complete directional interface layout on the server.";
            case UniversalFieldDeviceMenu.ROUTE_ENDPOINT_FRONT -> "Route changes the real FRONT endpoint orientation and its physical connection side.";
            case UniversalFieldDeviceMenu.ROUTE_PROBE_AXIS -> "Route moves the real TEST aperture and the opposite BUS interface together.";
            case UniversalFieldDeviceMenu.ROUTE_TERMINAL_INTERFACE -> "Route rotates the real terminal interface pair and forces topology recalculation.";
            case UniversalFieldDeviceMenu.ROUTE_MEASUREMENT_FACE -> "Route changes the actual sampled face; no synthetic output axis is implied.";
            case UniversalFieldDeviceMenu.ROUTE_FIXED_APERTURE_OUTPUT_FRONT -> "Route changes only the real FRONT redstone output. The UP sensing aperture is fixed.";
            case UniversalFieldDeviceMenu.ROUTE_MULTI_PORT_LAYOUT -> "Route rotates the complete declared physical interface layout. Use Ports for the exact face roles.";
            default -> "This device has no universal routing action.";
        };
    }

    private String routeTooltip() {
        return switch (menu.routeKind()) {
            case UniversalFieldDeviceMenu.ROUTE_SERIES_AXIS -> "Cycle the server-authoritative directional interface layout.";
            case UniversalFieldDeviceMenu.ROUTE_ENDPOINT_FRONT -> "Cycle the server-authoritative FRONT endpoint direction.";
            case UniversalFieldDeviceMenu.ROUTE_PROBE_AXIS -> "Cycle the server-authoritative probe measurement axis.";
            case UniversalFieldDeviceMenu.ROUTE_TERMINAL_INTERFACE -> "Cycle the server-authoritative terminal interface axis.";
            case UniversalFieldDeviceMenu.ROUTE_MEASUREMENT_FACE -> "Cycle the server-authoritative measurement face.";
            case UniversalFieldDeviceMenu.ROUTE_FIXED_APERTURE_OUTPUT_FRONT -> "Cycle only the server-authoritative FRONT output direction.";
            case UniversalFieldDeviceMenu.ROUTE_MULTI_PORT_LAYOUT -> "Rotate every declared directional port together on the server.";
            default -> "No universal route action is available.";
        };
    }

    private static String sequenceStepName(int step) {
        return step <= 0 ? "IDLE" : "STEP " + Math.min(4, step);
    }

    private static String alarmStateName(int state) {
        return state == 0 ? "CLEAR" : state == 2 ? "ACTIVE • UNACKNOWLEDGED" : "ACTIVE • ACKNOWLEDGED";
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

    private static String lapisProfileName(int profile) {
        return switch (Math.max(0, Math.min(3, profile))) {
            case 0 -> "FAST";
            case 1 -> "BALANCED";
            case 2 -> "PRECISION";
            default -> "RUGGED";
        };
    }

    private static int qualityColor(PortQuality quality) {
        return switch (quality) {
            case VALID -> GOOD;
            case SATURATED, STALE, NO_SIGNAL, NOT_READY -> WARN;
            case FAULT, DOMAIN_MISMATCH, TOPOLOGY_ERROR -> BAD;
        };
    }
}
