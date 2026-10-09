package dev.redstoneengineering.ui.ldlib;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.redstoneengineering.block.OperationsMonitorBlock;
import dev.redstoneengineering.diagnostics.IndustrialOperationsAssessment;
import dev.redstoneengineering.diagnostics.events.SystemEventKind;
import dev.redstoneengineering.ui.menu.OperationsMonitorMenu;
import net.minecraft.world.entity.player.Player;

/**
 * Observer-only LDLib2 Operations Monitor.
 *
 * <p>All values are bounded synchronized server evidence. This UI never reads the
 * world, advances jobs, mutates buffers, or fabricates KPI authority.</p>
 */
public final class OperationsMonitorLdUi {
    private OperationsMonitorLdUi() {}

    public static ModularUI create(OperationsMonitorMenu m, Player player) {
        var root = new UIElement().addClass("panel_bg");
        root.layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(8).gapAll(6));
        root.addChildren(
                RseLdUiComponents.title("OPERATIONS MONITOR • OBSERVER-ONLY PLANT EVIDENCE"),
                RseLdUiComponents.tabbedWorkspace(
                        760, 440, 1010,
                        new String[]{"Overview", "Details", "Controls", "Evidence"},
                        new UIElement[]{
                                RseLdUiComponents.workspacePage(
                                        overviewPanel(m),
                                        plantPanel(m)
                                ),
                                RseLdUiComponents.workspacePage(
                                        incidentPanel(m),
                                        timelinePanel(m)
                                ),
                                RseLdUiComponents.workspacePage(
                                        reliabilityPanel(m),
                                        authorityPanel()
                                ),
                                RseLdUiComponents.workspacePage(
                                        RseLdUiComponents.authorityFooter()
                                )
                        }
                )
        );
        return RseLdUiComponents.responsiveUi(root, player, 780, 480);
    }

    private static UIElement overviewPanel(OperationsMonitorMenu m) {
        return new UIElement().addClass("panel_bg").layout(l -> l.paddingAll(5).gapAll(3)).addChildren(
                new Label().setText("PIONEER PATTERN • PLANT STATE / KPI AUTHORITY"),
                RseLdUiComponents.formulaCard(() ->
                        "QUEUE = max(valid horizontal QUEUE/WIP sources); KPIs advance only when RUN + QUEUE are trustworthy"),
                RseLdUiComponents.liveRow("EVIDENCE", "server snapshot", () ->
                        m.snapshotReady() ? "SYNCED" : "NOT READY • waiting for server telemetry"),
                RseLdUiComponents.liveRow("STATE", "operations", () ->
                        m.snapshotReady() && m.snapshotReady() && m.telemetryReady() ? m.state().name() : "TELEMETRY INCOMPLETE"),
                RseLdUiComponents.liveRow("EVIDENCE", "coverage", () ->
                        m.snapshotReady() ? evidenceConfidencePercent(m) + "% • " + evidenceCoverageText(m)
                                : "NOT READY • uninitialized evidence coverage"),
                RseLdUiComponents.liveRow("MEASURED", "queue", () -> m.snapshotReady() && m.queueEvidenceSources()>0 ? m.queue() + "/15" : "UNAVAILABLE • no valid queue evidence"),
                RseLdUiComponents.liveRow("DERIVED", "queue pressure", () -> m.snapshotReady() && m.queueEvidenceSources()>0 ? m.queuePressurePercent() + "%" : "UNAVAILABLE • queue evidence missing"),
                RseLdUiComponents.fixedRow("KPI window", () -> "1200 ticks / 60 s", "server-owned throughput window"),
                RseLdUiComponents.liveRow("MEASURED", "throughput", () -> m.snapshotReady() && m.telemetryReady() && m.cycleEvidenceValid() ? m.throughput() + " cycles/min last60s" : "NOT READY • RUN / QUEUE / CYCLE evidence incomplete"),
                RseLdUiComponents.liveRow("MEASURED", "downtime", () ->
                        m.snapshotReady() ? formatTicks(m.downtimeTicks()) : "NOT READY • no server history"),
                RseLdUiComponents.liveRow("DERIVED", "state", () -> m.telemetryReady() ? m.state().name() : "TELEMETRY INCOMPLETE"),
                RseLdUiComponents.liveRow("DERIVED", "dominant constraint", () -> m.snapshotReady() && m.telemetryReady() ? m.dominantConstraint().name() : "UNVERIFIED • input evidence missing"),
                new Label().setText("FIXED STATE BANDS • queue≥13 OVERLOADED • queue≥9 CONGESTED • stopped+queued 600t ⇒ FAILED")
        );
    }

    private static UIElement plantPanel(OperationsMonitorMenu m) {
        return new UIElement().addClass("panel_bg").layout(l -> l.paddingAll(5).gapAll(3)).addChildren(
                new Label().setText("WORLD PLANT STATE"),
                RseLdUiComponents.liveRow("EVIDENCE COVERAGE", "world", () ->
                        m.snapshotReady() ? m.worldPlantCoverage().name() : "NOT READY • world snapshot pending"),
                RseLdUiComponents.liveRow("CONFIGURATION", "Workcells configured", () ->
                        m.snapshotReady()
                                ? m.worldPlantConfiguredWorkcells() + "/" + m.worldPlantWorkcells()
                                        + " • " + (m.worldPlantWorkcells()>0 ? configurationPercent(m)+"%" : "NO WORKCELLS")
                                : "NOT READY • world plant not inspected"),
                RseLdUiComponents.liveRow("WIP PRESSURE", "Buffers / WIP", () ->
                        m.snapshotReady()
                                ? (m.worldPlantBufferCapacityUnits()>0
                                    ? m.worldPlantBuffers()+" buffers • "+m.worldPlantUsedBufferUnits()+"/"
                                            +m.worldPlantBufferCapacityUnits()+" units • "
                                            +m.worldPlantWipPressurePercent()+"%"
                                    : m.worldPlantBuffers()+" buffers • NO CAPACITY EVIDENCE")
                                : "NOT READY • world plant not inspected"),
                RseLdUiComponents.liveRow("RESOURCE HEALTH", "Bound resources", () ->
                        m.snapshotReady()
                                ? m.worldPlantValidResources()+"/"+m.worldPlantBoundResources()
                                        +" valid • faults "+m.worldPlantFaultResources()
                                        +" • "+(m.worldPlantBoundResources()>0 ? resourceHealthPercent(m)+"%" : "NO BOUND RESOURCES")
                                : "NOT READY • world plant not inspected"),
                new Label().setText("WORLD PLANT STATE • PLANT KPIs • INCOMPLETE"),
                new Label().setText("Quality / reliability / delivery • WITHHELD • EVIDENCE MISSING"),
                new Label().setText("FPY / reject / rework — / — / —"),
                new Label().setText("Availability / failures — / — • Queue/job history is not persisted yet")
        );
    }

    private static UIElement incidentPanel(OperationsMonitorMenu m) {
        return new UIElement().addClass("panel_bg").layout(l -> l.paddingAll(5).gapAll(3)).addChildren(
                new Label().setText("INCIDENT / FIRST-OUT DRILL-DOWN"),
                RseLdUiComponents.liveRow("DIAGNOSIS", "system", () -> systemDiagnosis(m)),
                RseLdUiComponents.liveRow("STATE", "System state", () -> m.telemetryReady() ? m.state().name() : "TELEMETRY INCOMPLETE"),
                RseLdUiComponents.liveRow("INCIDENT", "Latest incident", () ->
                        !m.snapshotReady() ? "NOT READY • awaiting server incident ledger"
                                : m.incidentPresent() ? "EVIDENCE AVAILABLE" : "NONE"),
                RseLdUiComponents.liveRow("INCIDENT", "First-out source", () ->
                        m.snapshotReady() && m.incidentPresent() ? firstOutLocation(m) : "—"),
                RseLdUiComponents.liveRow("INCIDENT", "Incident span", () ->
                        m.snapshotReady() && m.incidentPresent() ? formatTicks(m.incidentDurationTicks()) : "—"),
                RseLdUiComponents.liveRow("INCIDENT", "Follow-up evidence", () ->
                        m.incidentPresent()
                                ? m.downstreamObservations() + " downstream • "
                                    + m.abnormalDownstreamObservations() + " abnormal • trace "
                                    + m.evidenceTraceEntries() + "/12"
                                : "0"),
                RseLdUiComponents.liveRow("NEXT", "action", () -> nextActionText(m))
        );
    }

    private static UIElement timelinePanel(OperationsMonitorMenu m) {
        var panel = new UIElement().addClass("panel_bg");
        panel.layout(l -> l.paddingAll(5).gapAll(3));
        panel.addChildren(
                new Label().setText("PLANT EVENT TIMELINE • 8-EVENT TAIL"),
                RseLdUiComponents.liveRow("EVENTS", "recent/retained", () ->
                        m.snapshotReady() ? m.recentEvents() + "/" + m.retainedEvents()
                                + " • abnormal " + m.recentAbnormalEvents()
                                : "NOT READY • no server event ledger"),
                RseLdUiComponents.liveRow("FIRST OUT", "event", () -> firstOutText(m))
        );
        for (int slot = 0; slot < OperationsMonitorMenu.EVENT_SLOTS; slot++) {
            final int index = slot;
            panel.addChild(RseLdUiComponents.liveRow(
                    index == m.firstOutSlot() ? "FIRST OUT" : "EVENT",
                    "#" + (index + 1),
                    () -> eventText(m, index)
            ));
        }
        panel.addChild(new Label().setText("Observer-only • oldest → newest synchronized chronology; no client event store"));
        return panel;
    }

    private static UIElement reliabilityPanel(OperationsMonitorMenu m) {
        return new UIElement().addClass("panel_bg").layout(l -> l.paddingAll(5).gapAll(3)).addChildren(
                new Label().setText("ELECTRICAL / COPPER RELIABILITY EVIDENCE"),
                RseLdUiComponents.liveRow("PROTECTION", "Electrical trips / recovered", () ->
                        m.electricalTripCount() + " / " + m.electricalRecoveryCount()),
                RseLdUiComponents.liveRow("PROTECTION", "Protection status", () ->
                        m.electricalActiveTripCount() > 0
                                ? "ACTIVE TRIP • repeat=" + m.electricalRepeatTripCount()
                                : "READY • repeat=" + m.electricalRepeatTripCount()),
                RseLdUiComponents.liveRow("PROTECTION", "Electrical downtime", () ->
                        formatTicks(m.electricalDowntimeTicks())),
                new Label().setText("MTBF/MTTR withheld • durable operating exposure / repair-cycle evidence not persisted"),
                RseLdUiComponents.liveRow("COPPER", "active degraded / failed", () ->
                        m.copperEvidenceActiveDegradedCount() + " / " + m.copperEvidenceActiveFailedCount()),
                RseLdUiComponents.liveRow("COPPER", "transitions D/F/R", () ->
                        m.copperEvidenceDegradedCount() + " / "
                                + m.copperEvidenceFailedCount() + " / "
                                + m.copperEvidenceRestoredCount()),
                RseLdUiComponents.liveRow("COPPER", "last fail / restore", () ->
                        optionalAge(m.copperEvidenceLastFailureAgeTicks()) + " / "
                                + optionalAge(m.copperEvidenceLastRestoreAgeTicks()))
        );
    }

    private static UIElement authorityPanel() {
        return new UIElement().addClass("panel_bg").layout(l -> l.paddingAll(5).gapAll(3)).addChildren(
                new Label().setText("OBSERVER AUTHORITY BOUNDARY"),
                new Label().setText("TELEMETRY • INCOMPLETE whenever RUN or QUEUE/WIP evidence is missing."),
                new Label().setText("KPIs advance only while RUN + at least one QUEUE source are trustworthy."),
                new Label().setText("Cycle timing requires observed LOW→HIGH edges; missing edges are NOT_READY, never invented."),
                new Label().setText("Observer-only: this block measures operations state and never drives the plant."),
                new Label().setText("KPIs stay WITHHELD until their world evidence exists."),
                new Label().setText("Missing RUN evidence never masquerades as a stopped machine."),
                new Label().setText("No client scheduler, queue mutation, buffer allocation, or world scan exists in this HMI.")
        );
    }

    private static int configurationPercent(OperationsMonitorMenu m) {
        int total = m.worldPlantWorkcells();
        if (total <= 0) return 0;
        return clampPercent((int) Math.round(m.worldPlantConfiguredWorkcells() * 100.0 / total));
    }

    private static int resourceHealthPercent(OperationsMonitorMenu m) {
        int total = m.worldPlantBoundResources();
        if (total <= 0) return 0;
        return clampPercent((int) Math.round(m.worldPlantValidResources() * 100.0 / total));
    }

    private static int evidenceConfidencePercent(OperationsMonitorMenu m) {
        int score = 0;
        if (m.runEvidenceValid()) score += 40;
        if (m.queueEvidenceSources() > 0) score += 40;
        if (m.cycleEvidenceValid()) score += 20;
        return score;
    }

    private static String evidenceCoverageText(OperationsMonitorMenu m) {
        if (evidenceConfidencePercent(m) == 100) return "RUN + QUEUE + CYCLE";
        if (!m.runEvidenceValid() && m.queueEvidenceSources() == 0) return "RUN + QUEUE MISSING";
        if (!m.runEvidenceValid()) return "RUN MISSING";
        if (m.queueEvidenceSources() == 0) return "QUEUE MISSING";
        return "CYCLE OPTIONAL";
    }

    private static String eventText(OperationsMonitorMenu m, int slot) {
        if (!m.snapshotReady()) return "NOT READY • server event tail pending";
        int kind = m.eventKindOrdinal(slot);
        if (kind < 0) return "—";
        return shortKind(kind) + " • S" + m.eventSeverity(slot) + " • "
                + ageText(m.eventAgeTicks(slot))
                + (slot == m.firstOutSlot() ? " • FIRST OUT" : "");
    }

    private static String firstOutText(OperationsMonitorMenu m) {
        if (!m.snapshotReady()) return "NOT READY • server first-out pending";
        if (m.firstOutKindOrdinal() < 0) return "none";
        String visible = m.firstOutSlot() >= 0 ? "visible" : "before tail";
        return shortKind(m.firstOutKindOrdinal()) + " S" + m.firstOutSeverity()
                + " • " + ageText(m.firstOutAgeTicks())
                + " • " + firstOutLocation(m) + " • " + visible;
    }

    private static String firstOutLocation(OperationsMonitorMenu m) {
        return "Δ(" + signed(m.firstOutDx()) + "," + signed(m.firstOutDy()) + "," + signed(m.firstOutDz()) + ")";
    }

    private static String systemDiagnosis(OperationsMonitorMenu m) {
        if (!m.snapshotReady()) return "NOT READY • WAITING FOR SERVER SNAPSHOT";
        // Known active protection and electrical evidence faults have higher
        // priority than missing RUN/QUEUE witnesses. A disconnected KPI
        // source must never hide a retained active trip from the operator.
        if (m.electricalActiveTripCount() > 0) return "ACTIVE PROTECTION LIMIT";
        if (m.copperEvidenceActiveFailedCount() > 0) return "COPPER EVIDENCE FAILURE";
        if (m.copperEvidenceActiveDegradedCount() > 0) return "COPPER EVIDENCE DEGRADED";
        if (!m.runEvidenceValid() || m.queueEvidenceSources() == 0) return "INSUFFICIENT EVIDENCE";
        if (m.incidentPresent()) return "INCIDENT TRACE AVAILABLE";
        return switch (m.state()) {
            case NOMINAL -> "PROCESS NOMINAL";
            case CONGESTED -> "QUEUE / WIP CONSTRAINT";
            case NOISY -> "UNSTABLE INPUT EVIDENCE";
            case UNSTABLE -> "PROCESS VARIABILITY";
            case OVERLOADED -> "CAPACITY PRESSURE";
            case SAFETY_LIMITED -> "SAFETY CONSTRAINT";
            case FAILED -> "PROCESS FAILURE";
        };
    }

    private static String nextActionText(OperationsMonitorMenu m) {
        if (!m.snapshotReady()) return "await synchronized plant and event evidence before diagnosing";
        if (m.electricalActiveTripCount() > 0) return "inspect protection first-out and downstream evidence before reset";
        if (m.copperEvidenceActiveFailedCount() > 0) return "repair Copper topology/domain evidence before using electrical measurements";
        if (m.copperEvidenceActiveDegradedCount() > 0) return "reacquire fresh Copper evidence; do not count degradation as protection downtime";
        if (!m.runEvidenceValid()) return "restore a trustworthy RUN source before interpreting KPIs";
        if (m.queueEvidenceSources() == 0) return "connect at least one trustworthy QUEUE/WIP source";
        if (m.incidentPresent()) return "follow FIRST OUT through the retained event tail before changing the process";
        if (m.state() == OperationsMonitorBlock.SystemState.CONGESTED
                || m.state() == OperationsMonitorBlock.SystemState.OVERLOADED)
            return "compare queue pressure with throughput before increasing input rate";
        if (m.state() == OperationsMonitorBlock.SystemState.NOISY
                || m.state() == OperationsMonitorBlock.SystemState.UNSTABLE)
            return "verify measurement quality and timing continuity before tuning control";
        return "evidence coherent; continue observation or compare against a deliberate test change";
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
            case ELECTRICAL_EVIDENCE_DEGRADED -> "E-DEG";
            case ELECTRICAL_EVIDENCE_FAILED -> "E-FAIL";
            case ELECTRICAL_EVIDENCE_RESTORED -> "E-OK";
            case SEQUENCE_STARTED -> "SEQ+";
            case SEQUENCE_STEP -> "STEP";
            case SEQUENCE_COMPLETED -> "DONE";
            case SEQUENCE_RESET -> "RST";
            case TOPOLOGY_ISSUE -> "TOPO";
            case TOPOLOGY_CLEAR -> "CLR";
            case OPERATIONS_STATE_CHANGED -> "OPS";
        };
    }

    private static int clampPercent(int value) { return Math.max(0, Math.min(100, value)); }
    private static String ageText(int ticks) {
        if (ticks < 0) return "—";
        if (ticks < 20) return ticks + "t";
        int seconds = ticks / 20;
        return seconds < 100 ? seconds + "s" : ">99s";
    }
    private static String optionalAge(int ticks) { return ticks < 0 ? "—" : ageText(ticks) + " ago"; }
    private static String formatTicks(int ticks) {
        return String.format(java.util.Locale.ROOT, "%.1fs", Math.max(0, ticks) / 20.0);
    }
    private static String signed(int value) { return value > 0 ? "+" + value : Integer.toString(value); }
}
