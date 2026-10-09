package dev.redstoneengineering.ui.ldlib;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.ui.menu.DigitalCommunicationMenu;
import net.minecraft.world.entity.player.Player;
import org.appliedenergistics.yoga.YogaFlexDirection;

public final class DigitalCommunicationLdUi {
    private DigitalCommunicationLdUi() {}

    public static ModularUI create(DigitalCommunicationMenu m, Player player) {
        var root = new UIElement().addClass("panel_bg");
        root.layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(8).gapAll(6));
        root.addChildren(
                RseLdUiComponents.title("DIGITAL COMMUNICATION ENGINEERING HMI"),
                RseLdUiComponents.tabbedWorkspace(
                        660, 400, 910,
                        new String[]{"Overview", "Details", "Controls", "Evidence"},
                        new UIElement[]{
                                RseLdUiComponents.workspacePage(
                                        RseLdUiComponents.formulaCard(() -> communicationEquation(m)),
                                        overviewPanel(m)
                                ),
                                RseLdUiComponents.workspacePage(
                                        mediumPanel(m),
                                        parameterPanel(m)
                                ),
                                RseLdUiComponents.workspacePage(
                                        routePanel(m),
                                        diagnosticsPanel(m)
                                ),
                                RseLdUiComponents.workspacePage(
                                        RseLdUiComponents.authorityFooter()
                                )
                        }
                )
        );
        return RseLdUiComponents.responsiveUi(root, player, 680, 440);
    }

    private static UIElement overviewPanel(DigitalCommunicationMenu m) {
        var p = new UIElement().addClass("panel_bg");
        p.layout(l -> l.paddingAll(5).gapAll(3));
        p.addChildren(
                new Label().setText("PIONEER PATTERN • COMMUNICATION MODEL"),
                RseLdUiComponents.liveRow("DEVICE", "type", () -> deviceName(m.kind())),
                RseLdUiComponents.liveRow("EVIDENCE", "server snapshot", () -> m.snapshotReady()
                        ? "SYNCED • converter and medium inspected" : "NOT READY • awaiting server inspection"),
                RseLdUiComponents.liveRow("INPUT", m.inputDomain().label(), () -> verifiedValue(m.inputValue(), m.inputDomain(), m.inputQuality())),
                RseLdUiComponents.liveRow("OUTPUT", m.outputDomain().label(), () -> verifiedValue(m.outputValue(), m.outputDomain(), m.outputQuality())),
                RseLdUiComponents.liveRow("CONTRACT", "transform", () -> m.snapshotReady()
                        ? m.inputDomain().label() + " → " + m.outputDomain().label()
                        : "NOT READY • port domain pending"),
                RseLdUiComponents.note("The screen presents server-synchronized link evidence only; it does not recalculate bus/serial/differential physics on the client.")
        );
        return p;
    }

    private static UIElement mediumPanel(DigitalCommunicationMenu m) {
        var p = new UIElement().addClass("panel_bg");
        p.layout(l -> l.paddingAll(5).gapAll(3));
        p.addChildren(
                RseLdUiComponents.liveRow("MEDIUM", "identity", () -> mediumName(m.mediumDomain())),
                RseLdUiComponents.liveRow("MEASURED", "Q_link", () -> mediumQualityText(m)),
                RseLdUiComponents.liveRow("MEASURED", "age", () -> mediumAgeText(m)),
                RseLdUiComponents.liveRow("TOPOLOGY", "drivers", () -> m.snapshotReady()
                        ? m.mediumDriverCount()+" observed • "+(m.mediumAgeTicks()<0?"NO MEDIUM SAMPLE":"medium sample available")
                        : "NOT READY • driver inspection pending"),
                RseLdUiComponents.liveRow("METRIC", mediumMetricLabel(m), () -> mediumMetricValue(m)),
                RseLdUiComponents.liveRow("TRADE-OFF", "medium", () -> mediumTradeoff(m)),
                RseLdUiComponents.note("Quality and freshness remain independent synchronized evidence.")
        );
        return p;
    }

    private static UIElement parameterPanel(DigitalCommunicationMenu m) {
        var q = new TextField().setNumbersOnlyInt(20, 60);
        q.layout(l -> l.width(100));
        q.bind(DataBindingBuilder.string(
                () -> m.snapshotReady() && m.kind() == DigitalCommunicationMenu.KIND_REGENERATOR
                        ? Integer.toString(thresholdPercent(m)) : "",
                value -> {
                    if (m.kind() != DigitalCommunicationMenu.KIND_REGENERATOR) return;
                    try { m.setRegeneratorThresholdFromUi(Integer.parseInt(value)); }
                    catch (NumberFormatException ignored) {}
                }
        ).build());

        var p = new UIElement().addClass("panel_bg");
        p.layout(l -> l.paddingAll(5).gapAll(4));
        p.addChildren(
                RseLdUiComponents.liveRow(
                        m.kind() == DigitalCommunicationMenu.KIND_REGENERATOR ? "ADJUSTABLE" : "FIXED",
                        "Q_min",
                        () -> m.kind() != DigitalCommunicationMenu.KIND_REGENERATOR
                                ? "not applicable / fixed transform"
                                : m.snapshotReady()
                                    ? thresholdPercent(m) + "% • {20,40,60}% • direct entry"
                                    : "NOT READY • threshold awaiting server"
                ),
                RseLdUiComponents.note(m.kind() == DigitalCommunicationMenu.KIND_REGENERATOR
                        ? "Only 20%, 40% and 60% are supported; selection is validated by the server."
                        : "Fixed converter: no adjustable regeneration threshold.")
        );
        if (m.kind() == DigitalCommunicationMenu.KIND_REGENERATOR) {
            p.addChildren(
                    new UIElement().layout(l -> l.flexDirection(YogaFlexDirection.ROW)
                            .flexWrap(dev.vfyjxf.taffy.style.FlexWrap.WRAP).gapAll(6)).addChildren(
                            new Label().setText("DIRECT ENTRY").layout(l -> l.width(92)), q
                    ),
                    new UIElement().layout(l -> l.flexDirection(YogaFlexDirection.ROW)
                            .flexWrap(dev.vfyjxf.taffy.style.FlexWrap.WRAP).gapAll(6)).addChildren(
                            RseLdUiComponents.serverAction("Q_min 20%", () -> { m.setRegeneratorThresholdFromUi(20); }),
                            RseLdUiComponents.serverAction("Q_min 40%", () -> { m.setRegeneratorThresholdFromUi(40); }),
                            RseLdUiComponents.serverAction("Q_min 60%", () -> { m.setRegeneratorThresholdFromUi(60); })
                    )
            );
        }
        return p;
    }

    private static UIElement routePanel(DigitalCommunicationMenu m) {
        var p = new UIElement().addClass("panel_bg");
        p.layout(l -> l.paddingAll(5).gapAll(4));
        p.addChildren(
                new Label().setText("PHYSICAL ROUTE • SERVER OWNED"),
                RseLdUiComponents.liveRow("RX", "face", () -> m.snapshotReady()
                        ? m.inputDirection().getName().toUpperCase() : "NOT READY • route pending"),
                RseLdUiComponents.liveRow("TX", "face", () -> m.snapshotReady()
                        ? m.outputDirection().getName().toUpperCase() : "NOT READY • route pending"),
                new UIElement().layout(l -> l.flexDirection(YogaFlexDirection.ROW).flexWrap(dev.vfyjxf.taffy.style.FlexWrap.WRAP).gapAll(6)).addChildren(
                        RseLdUiComponents.serverAction("Cycle direction ▶", m::cycleWholeRouteForward),
                        RseLdUiComponents.serverAction("Cycle RX ▶", m::cycleRxForward),
                        RseLdUiComponents.serverAction("Cycle TX ▶", m::cycleTxForward)
                ),
                RseLdUiComponents.note("Route owns physical RX/TX direction; Configure does not duplicate orientation authority.")
        );
        return p;
    }

    private static UIElement diagnosticsPanel(DigitalCommunicationMenu m) {
        var p = new UIElement().addClass("panel_bg");
        p.layout(l -> l.paddingAll(5).gapAll(3));
        p.addChildren(
                RseLdUiComponents.liveRow("DIAGNOSIS", "link", () -> diagnosis(m)),
                RseLdUiComponents.liveRow("NEXT", "action", () -> nextAction(m)),
                RseLdUiComponents.liveRow("EVIDENCE", "current", () -> mediumHeadline(m)),
                RseLdUiComponents.note("This directional communication HMI exposes authoritative current evidence."),
                RseLdUiComponents.note("It does not synthesize packet history that the server does not retain.")
        );
        return p;
    }

    private static String communicationEquation(DigitalCommunicationMenu m) {
        if (m.mediumDomain() == EngineeringDomain.SERIAL_DATA)
            return "U = min(100%, 100 · T_frame / Δt_arrival)";
        if (m.mediumDomain() == EngineeringDomain.DATA_BUS_8)
            return "Q_bus = max(35, 100 - loadingPenalty - contentionPenalty)";
        if (m.mediumDomain() == EngineeringDomain.DIFFERENTIAL_DATA)
            return "payload = 1 bit ; Q_link = server margin evidence";
        return "transform: " + m.inputDomain().label() + " → " + m.outputDomain().label();
    }

    private static String diagnosis(DigitalCommunicationMenu m) {
        if (!m.snapshotReady()) return "LINK NOT READY • WAITING FOR SERVER SNAPSHOT";
        // Never announce a healthy bus, serial link or domain converter when
        // its authoritative input/output quality is a hard fault or not ready.
        PortQuality input = m.inputQuality();
        PortQuality output = m.outputQuality();
        if (input == PortQuality.TOPOLOGY_ERROR || output == PortQuality.TOPOLOGY_ERROR)
            return "LINK TOPOLOGY / DRIVER CONFLICT";
        if (input == PortQuality.DOMAIN_MISMATCH || output == PortQuality.DOMAIN_MISMATCH)
            return "LINK DOMAIN MISMATCH • FAULT";
        if (input == PortQuality.FAULT || output == PortQuality.FAULT)
            return "LINK INPUT / OUTPUT FAULT";
        if (input == PortQuality.NOT_READY || output == PortQuality.NOT_READY)
            return "LINK NOT READY • EVIDENCE INCOMPLETE";
        if (input == PortQuality.SATURATED || output == PortQuality.SATURATED)
            return "LINK SATURATED • VERIFY MARGIN";
        if (m.inputQuality() == PortQuality.NO_SIGNAL) return "NO INPUT LINK EVIDENCE";
        if (m.inputQuality() == PortQuality.STALE) return "STALE INPUT LINK EVIDENCE";
        if (m.outputQuality() == PortQuality.NO_SIGNAL) return "NO VALID OUTPUT AFTER TRANSFORM";
        if (m.outputQuality() == PortQuality.STALE) return "STALE OUTPUT LINK EVIDENCE";
        // A valid converter port alone does not certify that the adjacent
        // bus/serial/differential medium has produced any timed observation.
        if (m.mediumDomain() != EngineeringDomain.GENERIC && m.mediumAgeTicks() < 0)
            return "MEDIUM NOT READY • NO TIMED SAMPLE";

        if (m.mediumDomain() == EngineeringDomain.DATA_BUS_8) {
            if (m.mediumMetricC() > 0) return "8-BIT BUS DRIVER CONFLICT OBSERVED";
            if (m.mediumMetricB() > 0) return "8-BIT BUS CONTENTION CONSUMING MARGIN";
            if (m.mediumQualityPercent() < 70) return "8-BIT BUS LOADING MARGIN LOW";
            return "8-BIT PARALLEL BUS HEALTHY";
        }
        if (m.mediumDomain() == EngineeringDomain.SERIAL_DATA) {
            if (m.mediumDriverCount() > 1) return "SERIAL MULTI-DRIVER CONFLICT";
            if (m.mediumMetricB() >= 90) return "SERIAL LINK NEAR UTILIZATION LIMIT";
            if (m.mediumQualityPercent() < 70) return "SERIAL LINK QUALITY MARGINAL";
            if (m.kind() == DigitalCommunicationMenu.KIND_REGENERATOR && m.auxiliary() < thresholdPercent(m))
                return "SERIAL QUALITY BELOW REGENERATION THRESHOLD";
            return "SERIAL FRAME TIMING PRESENT";
        }
        if (m.mediumDomain() == EngineeringDomain.DIFFERENTIAL_DATA) {
            if (m.mediumDriverCount() > 1) return "DIFFERENTIAL MULTI-DRIVER CONFLICT";
            if (m.mediumQualityPercent() < 70) return "DIFFERENTIAL LINK MARGIN EXHAUSTED";
            return "DIFFERENTIAL HIGH-INTEGRITY LINK VALID";
        }
        return "DOMAIN CONVERSION VALID";
    }

    private static String nextAction(DigitalCommunicationMenu m) {
        String d = diagnosis(m);
        if (d.contains("FAULT") || d.contains("NOT READY") || d.contains("SATURATED"))
            return "resolve server link quality, topology and measurement coverage before accepting a healthy link";
        if (d.contains("CONFLICT")) return "isolate multiple drivers or invalid link topology before decoding data";
        if (d.contains("CONTENTION")) return "reduce redundant bus driving; same-value multi-drive still consumes margin";
        if (d.contains("LOADING")) return "shorten or segment the 8-bit bus";
        if (d.contains("UTILIZATION")) return "reduce frame demand or move payload to a wider local bus";
        if (d.contains("BELOW")) return "improve serial link quality or lower Q_min only with commissioning evidence";
        if (d.contains("MARGIN")) return "shorten the differential link or remove topology faults";
        return "link evidence coherent; choose medium by payload width, wiring, timing and integrity";
    }

    private static String mediumHeadline(DigitalCommunicationMenu m) {
        if (!m.snapshotReady()) return "NOT READY • medium inspection pending";
        if (m.mediumDomain() != EngineeringDomain.GENERIC && m.mediumAgeTicks() < 0)
            return "NOT READY • no synchronized timed-medium evidence";
        if (m.mediumDomain() == EngineeringDomain.DATA_BUS_8)
            return "8-bit parallel • nodes=" + m.mediumMetricA() + " • drivers=" + m.mediumDriverCount();
        if (m.mediumDomain() == EngineeringDomain.SERIAL_DATA)
            return "serial • period=" + Math.max(1, m.mediumMetricA()) + "t • util=" + m.mediumMetricB() + "%";
        if (m.mediumDomain() == EngineeringDomain.DIFFERENTIAL_DATA)
            return "1-bit high-integrity • drivers=" + m.mediumDriverCount();
        return "NO COMMUNICATION MEDIUM";
    }

    private static String mediumMetricLabel(DigitalCommunicationMenu m) {
        if (m.mediumDomain() == EngineeringDomain.DATA_BUS_8) return "Contention / conflicts";
        if (m.mediumDomain() == EngineeringDomain.SERIAL_DATA) return "Period / utilization / nodes";
        if (m.mediumDomain() == EngineeringDomain.DIFFERENTIAL_DATA) return "Payload width";
        return "Medium metric";
    }

    private static String mediumMetricValue(DigitalCommunicationMenu m) {
        if (!m.snapshotReady()) return "NOT READY • medium inspection pending";
        if (m.mediumAgeTicks() < 0) return "NOT READY • no synchronized medium sample";
        if (m.mediumDomain() == EngineeringDomain.DATA_BUS_8)
            return m.mediumMetricB() + " / " + m.mediumMetricC() + " frames";
        if (m.mediumDomain() == EngineeringDomain.SERIAL_DATA)
            return Math.max(1, m.mediumMetricA()) + "t / " + m.mediumMetricB() + "% / " + m.mediumMetricC();
        if (m.mediumDomain() == EngineeringDomain.DIFFERENTIAL_DATA)
            return m.mediumMetricA() + " bit";
        return "N/A";
    }

    private static String mediumTradeoff(DigitalCommunicationMenu m) {
        if (m.mediumDomain() == EngineeringDomain.DATA_BUS_8)
            return "highest local payload width; loading and driver coordination cost margin";
        if (m.mediumDomain() == EngineeringDomain.SERIAL_DATA)
            return "fewer conductors; frame timing and utilization become the engineering limit";
        if (m.mediumDomain() == EngineeringDomain.DIFFERENTIAL_DATA)
            return "one-bit payload density; stronger link margin suits discrete control and protection state";
        return "no universal communication medium is implied";
    }

    private static String mediumName(EngineeringDomain d) {
        return switch (d) {
            case DATA_BUS_8 -> "8-BIT DATA BUS";
            case SERIAL_DATA -> "SERIAL DATA";
            case DIFFERENTIAL_DATA -> "DIFFERENTIAL DATA";
            default -> "DIGITAL LINK";
        };
    }

    private static String mediumQualityText(DigitalCommunicationMenu m) {
        if (!m.snapshotReady()) return "NOT READY • medium quality pending";
        return m.mediumAgeTicks() < 0 ? "N/A" : m.mediumQualityPercent() + "%";
    }

    private static String mediumAgeText(DigitalCommunicationMenu m) {
        if (!m.snapshotReady()) return "NOT READY • no server medium sample";
        return m.mediumAgeTicks() < 0 ? "NO SAMPLE" : m.mediumAgeTicks() + "t";
    }

    private static int thresholdPercent(DigitalCommunicationMenu m) {
        return switch (m.parameter()) { case 0 -> 20; case 1 -> 40; default -> 60; };
    }

    private static String verifiedValue(int value, EngineeringDomain domain, PortQuality quality) {
        if (quality != PortQuality.VALID && quality != PortQuality.SATURATED)
            return "NOT READY • " + quality.name() + " (value withheld)";
        return valueText(value, domain) + " • " + quality.name();
    }

    private static String valueText(int value, EngineeringDomain domain) {
        return switch (domain) {
            case DATA_BUS_8, SERIAL_DATA -> String.format("0x%02X", value & 0xFF);
            case DIFFERENTIAL_DATA -> Integer.toString(value & 1);
            default -> Integer.toString(value);
        };
    }

    private static String deviceName(int kind) {
        return switch (kind) {
            case DigitalCommunicationMenu.KIND_ENCODER -> "REDSTONE BYTE ENCODER";
            case DigitalCommunicationMenu.KIND_DECODER -> "BYTE TO REDSTONE DECODER";
            case DigitalCommunicationMenu.KIND_SERIALIZER -> "SERIALIZER";
            case DigitalCommunicationMenu.KIND_DESERIALIZER -> "DESERIALIZER";
            case DigitalCommunicationMenu.KIND_REGENERATOR -> "DIGITAL REGENERATOR";
            case DigitalCommunicationMenu.KIND_DIFF_DRIVER -> "DIFFERENTIAL DRIVER";
            case DigitalCommunicationMenu.KIND_DIFF_RECEIVER -> "DIFFERENTIAL RECEIVER";
            default -> "DIGITAL COMMUNICATION";
        };
    }
}
