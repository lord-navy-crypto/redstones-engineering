package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.block.OperationsMonitorBlock;
import dev.redstoneengineering.diagnostics.IndustrialOperationsAssessment;
import dev.redstoneengineering.diagnostics.events.SystemEventKind;
import dev.redstoneengineering.ui.menu.OperationsMonitorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Dedicated observer-only IOE console with bounded plant event and incident evidence. */
public final class OperationsMonitorScreen extends EngineeringScreen<OperationsMonitorMenu> {
    private static final int EVENT_CELL_WIDTH = 33;
    private static final int EVENT_CELL_GAP = 2;

    public OperationsMonitorScreen(OperationsMonitorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void renderSection(GuiGraphics graphics, Section section) {
        switch (section) {
            case OVERVIEW -> renderOverview(graphics);
            case PORTS -> renderPorts(graphics);
            case CONFIGURE -> renderConfigure(graphics);
            case DIAGNOSTICS -> renderDiagnostics(graphics);
            case HISTORY -> renderHistory(graphics);
        }
    }

    private void renderOverview(GuiGraphics graphics) {
        statusBadge(graphics, "OPERATIONS • " + menu.state().name(), stateColor(menu.state()), 16, 80);
        labelValue(graphics, "Queue / WIP", menu.queue() + " / 15", 105);
        labelValue(graphics, "Queue pressure", menu.queuePressurePercent() + "%", 121);
        labelValue(graphics, "Throughput last60s", menu.throughput() + " cycles/min", 137);
        labelValue(graphics, "Downtime", formatTicks(menu.downtimeTicks()), 153);
        statusLine(graphics, "Dominant constraint", menu.dominantConstraint().name(), constraintColor(menu.dominantConstraint()), 169);
    }

    private void renderPorts(GuiGraphics graphics) {
        statusLine(graphics, "DOWN", "MACHINE RUNNING • MEASUREMENT INPUT", GOOD, 88);
        statusLine(graphics, "UP", "COMPLETED-CYCLE • TRIGGER INPUT", GOOD, 108);
        statusLine(graphics, "N / S / E / W", "QUEUE / WIP • MEASUREMENT INPUTS", INFO, 128);
        graphics.drawString(font, "Observer-only: this block measures operations state and never drives the plant.", 16, 154, MUTED, false);
    }

    private void renderConfigure(GuiGraphics graphics) {
        statusBadge(graphics, "OBSERVER ONLY", INFO, 16, 82);
        graphics.drawString(font, "No process-control command is exposed from this screen.", 16, 108, TEXT, false);
        graphics.drawString(font, "Shift + right-click resets monitor statistics only.", 16, 128, TEXT, false);
        graphics.drawString(font, "Plant event evidence remains independent of monitor lifecycle/reset.", 16, 148, MUTED, false);
    }

    private void renderDiagnostics(GuiGraphics graphics) {
        statusLine(graphics, "System state", menu.state().name(), stateColor(menu.state()), 82);
        statusLine(graphics, "Dominant constraint", menu.dominantConstraint().name(), constraintColor(menu.dominantConstraint()), 100);
        statusLine(graphics, "Latest incident", menu.incidentPresent() ? "EVIDENCE AVAILABLE" : "NONE", menu.incidentPresent() ? WARN : GOOD, 118);
        labelValue(graphics, "First-out source", menu.incidentPresent() ? firstOutLocation() : "—", 136);
        labelValue(graphics, "Incident span", menu.incidentPresent() ? formatTicks(menu.incidentDurationTicks()) : "—", 154);
        labelValue(graphics, "Follow-up evidence", menu.incidentPresent() ? evidenceText() : "0", 172);
    }

    private void renderHistory(GuiGraphics graphics) {
        statusBadge(graphics, "PLANT EVENT TIMELINE • 8-EVENT TAIL", INFO, 16, 79);
        graphics.drawString(font,
                "recent/retained " + menu.recentEvents() + "/" + menu.retainedEvents()
                        + " • abnormal " + menu.recentAbnormalEvents(),
                16, 99, TEXT, false);
        statusLine(graphics, "FIRST OUT", firstOutText(), firstOutColor(), 116);

        int x0 = 18;
        int y0 = 137;
        int height = 38;
        for (int slot = 0; slot < OperationsMonitorMenu.EVENT_SLOTS; slot++) {
            int x = x0 + slot * (EVENT_CELL_WIDTH + EVENT_CELL_GAP);
            int kind = menu.eventKindOrdinal(slot);
            graphics.fill(x, y0, x + EVENT_CELL_WIDTH, y0 + height, 0xFF0C1014);
            if (kind < 0) {
                graphics.drawString(font, "—", x + 13, y0 + 14, MUTED, false);
                continue;
            }

            int color = eventColor(slot);
            graphics.fill(x, y0, x + EVENT_CELL_WIDTH, y0 + 3, color);
            if (menu.eventAbnormal(slot)) graphics.fill(x, y0, x + 3, y0 + height, color);
            if (slot == menu.firstOutSlot()) drawFirstOutFrame(graphics, x, y0, EVENT_CELL_WIDTH, height);

            graphics.drawString(font, shortKind(kind), x + 5, y0 + 8, color, false);
            graphics.drawString(font, ageText(menu.eventAgeTicks(slot)), x + 5, y0 + 22, MUTED, false);
            if (slot == menu.firstOutSlot()) graphics.drawString(font, "F", x + EVENT_CELL_WIDTH - 8, y0 + 8, WARN, false);
        }

        graphics.drawString(font, "oldest", 18, 180, MUTED, false);
        graphics.drawString(font, "newest →", 244, 180, MUTED, false);
    }

    private void drawFirstOutFrame(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + 1, WARN);
        graphics.fill(x, y + height - 1, x + width, y + height, WARN);
        graphics.fill(x, y, x + 1, y + height, WARN);
        graphics.fill(x + width - 1, y, x + width, y + height, WARN);
    }

    private String firstOutText() {
        if (menu.firstOutKindOrdinal() < 0) return "none";
        String visible = menu.firstOutSlot() >= 0 ? "visible" : "before tail";
        return shortKind(menu.firstOutKindOrdinal()) + " S" + menu.firstOutSeverity()
                + " • " + ageText(menu.firstOutAgeTicks())
                + " • " + firstOutLocation() + " • " + visible;
    }

    private String firstOutLocation() {
        return "Δ(" + signed(menu.firstOutDx()) + "," + signed(menu.firstOutDy()) + "," + signed(menu.firstOutDz()) + ")";
    }

    private String evidenceText() {
        return menu.downstreamObservations() + " downstream • "
                + menu.abnormalDownstreamObservations() + " abnormal • trace "
                + menu.evidenceTraceEntries() + "/12";
    }

    private int firstOutColor() {
        return menu.firstOutKindOrdinal() < 0 ? GOOD : severityColor(menu.firstOutSeverity());
    }

    private int eventColor(int slot) {
        int severity = menu.eventSeverity(slot);
        if (menu.eventAbnormal(slot) && severity < 2) return WARN;
        return severityColor(severity);
    }

    private static int severityColor(int severity) {
        return switch (severity) {
            case 3 -> BAD;
            case 2 -> WARN;
            case 1 -> INFO;
            default -> GOOD;
        };
    }

    private static int stateColor(OperationsMonitorBlock.SystemState state) {
        return switch (state) {
            case NOMINAL -> GOOD;
            case CONGESTED, NOISY, UNSTABLE -> WARN;
            case OVERLOADED, SAFETY_LIMITED -> WARN;
            case FAILED -> BAD;
        };
    }

    private static int constraintColor(IndustrialOperationsAssessment.Constraint constraint) {
        return switch (constraint) {
            case NONE -> GOOD;
            case STARVED, BLOCKED, HIGH_WIP, UNSTABLE -> WARN;
            case SAFETY_LIMITED -> WARN;
            case FAILED -> BAD;
        };
    }

    private static String shortKind(int ordinal) {
        SystemEventKind[] kinds = SystemEventKind.values();
        if (ordinal < 0 || ordinal >= kinds.length) return "?";
        return switch (kinds[ordinal]) {
            case ALARM_RAISED -> "ALM+";
            case ALARM_ACKNOWLEDGED -> "ACK";
            case ALARM_CLEARED -> "ALM-";
            case INTERLOCK_TRIPPED -> "TRIP";
            case INTERLOCK_READY -> "RDY";
            case ELECTRICAL_TRIP -> "E-TRP";
            case ELECTRICAL_READY -> "E-RDY";
            case SEQUENCE_STARTED -> "SEQ+";
            case SEQUENCE_STEP -> "STEP";
            case SEQUENCE_COMPLETED -> "DONE";
            case SEQUENCE_RESET -> "RST";
            case TOPOLOGY_ISSUE -> "TOPO";
            case TOPOLOGY_CLEAR -> "CLR";
            case OPERATIONS_STATE_CHANGED -> "OPS";
        };
    }

    private static String ageText(int ticks) {
        if (ticks < 0) return "—";
        if (ticks < 20) return ticks + "t";
        int seconds = ticks / 20;
        return seconds < 100 ? seconds + "s" : ">99s";
    }

    private static String formatTicks(int ticks) {
        return String.format(java.util.Locale.ROOT, "%.1fs", Math.max(0, ticks) / 20.0);
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }
}
