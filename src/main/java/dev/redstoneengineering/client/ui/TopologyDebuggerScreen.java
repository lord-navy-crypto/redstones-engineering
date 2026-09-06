package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.diagnostics.redstone.VanillaRedstoneTargetHistory;
import dev.redstoneengineering.diagnostics.redstone.VanillaRedstoneTargetSnapshot;
import dev.redstoneengineering.ui.menu.TopologyDebuggerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Read-only graphical Vanilla Engineering Overlay surfaced through the Topology Debugger. */
public final class TopologyDebuggerScreen extends EngineeringScreen<TopologyDebuggerMenu> {
    private final EngineeringChartRenderer.Series targetTimeline;

    public TopologyDebuggerScreen(TopologyDebuggerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.targetTimeline = new EngineeringChartRenderer.Series() {
            @Override
            public int size() {
                return VanillaRedstoneTargetHistory.DISPLAY_SAMPLES;
            }

            @Override
            public int valueAt(int slot) {
                return menu.timelineValue(slot);
            }

            @Override
            public long gameTimeAt(int slot) {
                return menu.timelineGameTime(slot);
            }
        };
    }

    @Override
    protected void addDeviceWidgets() {
        // Diagnostics-only overlay: vanilla target configuration is deliberately immutable here.
    }

    @Override
    protected void renderSection(GuiGraphics graphics, Section section) {
        switch (section) {
            case OVERVIEW -> renderOverview(graphics);
            case PORTS -> renderStructure(graphics);
            case CONFIGURE -> renderReadOnlyContract(graphics);
            case DIAGNOSTICS -> renderDiagnostics(graphics);
            case HISTORY -> renderHistory(graphics);
        }
    }

    private void renderOverview(GuiGraphics graphics) {
        statusBadge(graphics, kindName(menu.targetKind()), kindColor(menu.targetKind()), 16, 80);
        statusBadge(graphics, targetState(), targetStateColor(), 150, 80);
        labelValue(graphics, readoutLabel(), signalReadout(), 103);
        labelValue(graphics, "Facing", facingName(menu.targetFacing()), 119);
        labelValue(graphics, "Configuration", configurationSummary(), 135);
        labelValue(graphics, "Observed target events", menu.timelineCount() + " / "
                + VanillaRedstoneTargetHistory.DISPLAY_SAMPLES, 151);
        labelValue(graphics, "Observed target span", menu.timelineSpanTicks() + " gt", 167);
        if (menu.signalValue() >= 0) signalBar(graphics, menu.signalValue(), 183);
    }

    private void renderStructure(GuiGraphics graphics) {
        statusLine(graphics, "DOMAIN", "VANILLA REDSTONE • OBSERVER ONLY", GOOD, 80);
        labelValue(graphics, "Relevant nodes", Integer.toString(menu.nodeCount()), 98);
        labelValue(graphics, "Dust", menu.poweredDustCount() + " powered / " + menu.dustCount(), 114);
        labelValue(graphics, "Timing components", Integer.toString(menu.timingComponentCount()), 130);
        labelValue(graphics, "Max dust power", menu.maxDustPower() + " / 15", 146);
        labelValue(graphics, "Local fanout proxy", Integer.toString(menu.fanoutProxy()), 162);
        statusLine(graphics, "BOUNDARY", boundaryState(), boundaryColor(), 180);
    }

    private void renderReadOnlyContract(GuiGraphics graphics) {
        statusLine(graphics, "MODE", "DIAGNOSTICS ONLY", GOOD, 80);
        graphics.drawString(font, "VANILLA BEHAVIOR IMMUTABILITY", 16, 102, ACCENT, false);
        graphics.drawString(font, "This overlay measures and explains the target.", 16, 120, TEXT, false);
        graphics.drawString(font, "It does not change repeater timing, comparator logic,", 16, 136, TEXT, false);
        graphics.drawString(font, "observer pulses, dust propagation, QC, or update order.", 16, 152, TEXT, false);
        graphics.drawString(font, "Runtime/timing/order fields are observed evidence,", 16, 174, MUTED, false);
        graphics.drawString(font, "not a replacement redstone solver or causal proof.", 16, 188, MUTED, false);
    }

    private void renderDiagnostics(GuiGraphics graphics) {
        statusLine(graphics, "RUNTIME", menu.runtimeEvents() + " NeighborNotify observations", INFO, 80);
        labelValue(graphics, "Observed transitions", Integer.toString(menu.runtimeTransitions()), 98);
        labelValue(graphics, "Unique event sources", Integer.toString(menu.runtimeSources()), 114);
        labelValue(graphics, "Active event ticks", Integer.toString(menu.activeTicks()), 130);
        labelValue(graphics, "Observed span", menu.observedSpanTicks() + " gt", 146);
        labelValue(graphics, "Inter-transition", interval(menu.minInterTransitionTicks(), menu.maxInterTransitionTicks()), 162);
        labelValue(graphics, "Complete observed pulses", pulseSummary(), 178);
        graphics.drawString(font, "QC candidates " + menu.qcCandidates()
                + " • observer returns " + menu.observerReturns()
                + " • order evidence " + confidenceName(menu.orderConfidence()) + " (" + menu.orderScore() + ")",
                16, 196, MUTED, false);
    }

    private void renderHistory(GuiGraphics graphics) {
        graphics.drawString(font, historyTitle(), 16, 80, TEXT, false);
        graphics.drawString(font, "TARGET EVENTS " + menu.timelineCount()
                + " • SIGNAL CHANGES " + menu.timelineTransitions(), 150, 80, MUTED, false);
        int x = 16;
        int width = 288;
        EngineeringChartRenderer.drawFrame(graphics, x, 94, width, 70, 0, 15, true);
        EngineeringChartRenderer.drawWaveform(graphics, targetTimeline, x, 94, width, 70, 0, 15, ACCENT);
        EngineeringChartRenderer.drawChangeMarkers(graphics, targetTimeline, x, 94, width, 70, WARN);
        EngineeringChartRenderer.drawGameTimeAxis(graphics, font, targetTimeline, x, 168, width);
        graphics.drawString(font, "Exact target-source observations only; gaps mean no retained target event.",
                16, 187, MUTED, false);
        graphics.drawString(font, "Timebase: logical-server gameTime. No sub-tick or causal-order claim.",
                16, 200, MUTED, false);
    }

    private String historyTitle() {
        return menu.targetKind() == VanillaRedstoneTargetSnapshot.DUST
                ? "OBSERVED DUST POWER 0..15"
                : "OBSERVED TARGET ACTIVE STATE 0 / 15";
    }

    private String readoutLabel() {
        return menu.targetKind() == VanillaRedstoneTargetSnapshot.DUST ? "Dust power" : "Observed active state";
    }

    private String signalReadout() {
        return menu.signalValue() < 0 ? "N/A" : menu.signalValue() + " / 15";
    }

    private String targetState() {
        return switch (menu.activeState()) {
            case 1 -> "OBSERVED ACTIVE";
            case 0 -> "OBSERVED INACTIVE";
            default -> "STATE N/A";
        };
    }

    private int targetStateColor() {
        return switch (menu.activeState()) {
            case 1 -> GOOD;
            case 0 -> MUTED;
            default -> WARN;
        };
    }

    private String configurationSummary() {
        if (menu.targetKind() == VanillaRedstoneTargetSnapshot.REPEATER) {
            String locked = menu.locked() < 0 ? "" : menu.locked() != 0 ? " • LOCKED" : " • unlocked";
            return menu.configuredDelayGameTicks() + " gt" + locked;
        }
        if (menu.targetKind() == VanillaRedstoneTargetSnapshot.COMPARATOR) {
            return menu.targetMode() == 1 ? "SUBTRACT" : menu.targetMode() == 0 ? "COMPARE" : "N/A";
        }
        return "READ-ONLY VANILLA STATE";
    }

    private String boundaryState() {
        if (menu.traversalCapped()) return "PROFILE TRUNCATED";
        if (menu.unloadedBoundaries() > 0) return "UNLOADED BOUNDARY ×" + menu.unloadedBoundaries();
        return "COMPLETE WITHIN BOUNDS";
    }

    private int boundaryColor() {
        return menu.traversalCapped() || menu.unloadedBoundaries() > 0 ? WARN : GOOD;
    }

    private String pulseSummary() {
        if (menu.completePulses() <= 0) return "N/A";
        return menu.completePulses() + " • " + menu.minPulseWidthTicks() + ".." + menu.maxPulseWidthTicks() + " gt";
    }

    private static String interval(int min, int max) {
        return min < 0 || max < 0 ? "N/A" : min + ".." + max + " gt";
    }

    private static String kindName(int kind) {
        return switch (kind) {
            case VanillaRedstoneTargetSnapshot.DUST -> "REDSTONE DUST";
            case VanillaRedstoneTargetSnapshot.REPEATER -> "REPEATER";
            case VanillaRedstoneTargetSnapshot.COMPARATOR -> "COMPARATOR";
            case VanillaRedstoneTargetSnapshot.OBSERVER -> "OBSERVER";
            case VanillaRedstoneTargetSnapshot.PISTON -> "PISTON";
            case VanillaRedstoneTargetSnapshot.DISPENSER_DROPPER -> "DISPENSER / DROPPER";
            case VanillaRedstoneTargetSnapshot.SOURCE -> "REDSTONE SOURCE";
            default -> "VANILLA REDSTONE";
        };
    }

    private static int kindColor(int kind) {
        return kind == VanillaRedstoneTargetSnapshot.OTHER ? MUTED : ACCENT;
    }

    private static String facingName(int ordinal) {
        Direction[] directions = Direction.values();
        return ordinal < 0 || ordinal >= directions.length ? "N/A" : directions[ordinal].getName().toUpperCase();
    }

    private static String confidenceName(int code) {
        return switch (code) {
            case 1 -> "LOW";
            case 2 -> "MEDIUM";
            case 3 -> "HIGH";
            default -> "NONE";
        };
    }
}
