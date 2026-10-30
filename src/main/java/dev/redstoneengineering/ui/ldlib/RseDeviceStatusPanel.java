package dev.redstoneengineering.ui.ldlib;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import dev.redstoneengineering.ui.menu.EngineeringDeviceMenu;
import net.minecraft.world.entity.player.Player;

/**
 * Compact, shared STATUS view for all dedicated RSE instrument/menu families.
 * Existing formula/configuration pages stay intact; this is read-only and
 * never performs a tick, updates an output, or constructs false measurements.
 *
 * All blocks covered by the FieldDevice and Universal shared families use
 * their existing status implementations; specialist menus reuse this panel.
 */
public final class RseDeviceStatusPanel {
    private RseDeviceStatusPanel() {}

    /** Actual server-synchronized state; never treat initial client slots as healthy. */
    public static UIElement summary(EngineeringDeviceMenu menu) {
        var panel = new UIElement().addClass("panel_bg");
        panel.layout(l -> l.widthPercent(100).paddingAll(6).gapAll(5));
        panel.addChildren(
                new Label().setText("STATUS • SERVER DEVICE REPORT"),
                RseLdUiComponents.statusRail(
                        RseLdUiComponents.statusTile("HEALTH",
                                () -> menu.operationalHealthLabel()),
                        RseLdUiComponents.statusTile("PORT QUALITY",
                                () -> menu.operationalHealth() == EngineeringDeviceMenu.HEALTH_UNVERIFIED
                                        ? "NOT READY" : menu.evidenceStateLabel()),
                        RseLdUiComponents.statusTile("ROUTE / PORTS",
                                () -> menu.operationalHealth() == EngineeringDeviceMenu.HEALTH_UNVERIFIED
                                        ? "NOT READY" : menu.portRouteLabel())
                ),
                RseLdUiComponents.liveRow("DEVICE", "name", menu::deviceDisplayName),
                RseLdUiComponents.liveRow("TOPOLOGY", "role", () ->
                        menu.operationalHealth() == EngineeringDeviceMenu.HEALTH_UNVERIFIED
                                ? "NOT READY" : menu.topologyRoleLabel()),
                RseLdUiComponents.liveRow("STATUS", "next check", menu::operatorCheckLabel),
                RseLdUiComponents.note(
                        "Configuration and physical measurement are separate; missing evidence is not numeric zero.")
        );
        return panel;
    }

    /**
     * Keeps older detailed diagnostic/reference content available on demand
     * without flooding the Status page with boilerplate.
     * Toggle is client-only; no menu clicks or backend operations.
     */
    public static UIElement statusPage(EngineeringDeviceMenu menu, UIElement originalDetails) {
        var details = originalDetails;
        details.setDisplay(false);
        final boolean[] expanded = {false};
        var toggle = new Button().setText("Show technical details ▼");
        toggle.setOnClick(event -> {
            expanded[0] = !expanded[0];
            details.setDisplay(expanded[0]);
            toggle.setText(expanded[0] ? "Hide technical details ▲" : "Show technical details ▼");
        });
        toggle.layout(l -> l.height(20).minWidth(160).paddingAll(4));
        return RseLdUiComponents.workspacePage(summary(menu), toggle, details);
    }

    /** A dedicated Status tab for screen families which originally had none. */
    public static UIElement statusPage(EngineeringDeviceMenu menu) {
        return RseLdUiComponents.workspacePage(summary(menu));
    }
}
