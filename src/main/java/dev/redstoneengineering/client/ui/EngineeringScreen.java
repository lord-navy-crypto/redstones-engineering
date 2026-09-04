package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.ui.menu.EngineeringDeviceMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared RSE engineering visual language.
 *
 * The screen renders server-synchronized menu data and emits bounded menu-button intent only.
 * It never computes physics, samples sensors, solves topology, or mutates controller state locally.
 */
public abstract class EngineeringScreen<M extends EngineeringDeviceMenu> extends AbstractContainerScreen<M> {
    protected enum Section {
        OVERVIEW("Overview", "Live engineering state"),
        PORTS("Ports", "Physical I/O contract"),
        CONFIGURE("Configure", "Bounded server-side controls"),
        DIAGNOSTICS("Diagnostics", "Health and boundary checks"),
        HISTORY("History", "Evidence and retained runs");

        private final String label;
        private final String subtitle;

        Section(String label, String subtitle) {
            this.label = label;
            this.subtitle = subtitle;
        }
    }

    protected static final int PANEL = 0xFF151A1F;
    protected static final int PANEL_2 = 0xFF202830;
    protected static final int BORDER = 0xFF66717B;
    protected static final int TEXT = 0xFFE8EDF2;
    protected static final int MUTED = 0xFF9BA8B3;
    protected static final int GOOD = 0xFF68D391;
    protected static final int WARN = 0xFFF6C453;
    protected static final int BAD = 0xFFF06A6A;
    protected static final int INFO = 0xFF9EC8FF;
    protected static final int ACCENT = 0xFFE05555;

    private Section section = Section.OVERVIEW;
    private final List<AbstractWidget> configureWidgets = new ArrayList<>();
    private final List<Button> sectionButtons = new ArrayList<>();

    protected EngineeringScreen(M menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 320;
        this.imageHeight = 214;
        this.titleLabelX = 12;
        this.titleLabelY = 10;
        this.inventoryLabelY = 1000;
    }

    @Override
    protected void init() {
        super.init();
        configureWidgets.clear();
        sectionButtons.clear();

        int tabY = topPos + 31;
        int x = leftPos + 8;
        for (Section candidate : Section.values()) {
            Section target = candidate;
            Button tab = Button.builder(
                    Component.literal(candidate.label),
                    button -> setSection(target)
            ).bounds(x, tabY, 59, 20).build();
            sectionButtons.add(addRenderableWidget(tab));
            x += 61;
        }
        addDeviceWidgets();
        updateWidgetVisibility();
        syncDeviceWidgetLabels();
    }

    protected void addDeviceWidgets() {
    }

    /** Refreshes client-only button labels from already synchronized menu data. */
    protected void syncDeviceWidgetLabels() {
    }

    protected final <T extends AbstractWidget> T addConfigureWidget(T widget) {
        configureWidgets.add(widget);
        return addRenderableWidget(widget);
    }

    protected final void sendMenuButton(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    private void setSection(Section section) {
        this.section = section;
        updateWidgetVisibility();
        syncDeviceWidgetLabels();
    }

    private void updateWidgetVisibility() {
        boolean visible = section == Section.CONFIGURE;
        for (AbstractWidget widget : configureWidgets) widget.visible = visible;
        for (int i = 0; i < sectionButtons.size(); i++) {
            sectionButtons.get(i).active = Section.values()[i] != section;
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        syncDeviceWidgetLabels();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, BORDER);
        graphics.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + imageHeight - 2, PANEL);
        graphics.fill(leftPos + 8, topPos + 58, leftPos + imageWidth - 8, topPos + imageHeight - 9, PANEL_2);
        graphics.fill(leftPos + 8, topPos + 27, leftPos + imageWidth - 8, topPos + 29, ACCENT);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 12, 9, TEXT, false);
        graphics.drawString(font, section.label.toUpperCase(), 13, 62, TEXT, false);
        graphics.drawString(font, section.subtitle, 92, 62, MUTED, false);
        renderSection(graphics, section);
        graphics.drawString(
                font,
                "SERVER AUTHORITATIVE  •  SYNCHRONIZED READBACK",
                13,
                imageHeight - 19,
                MUTED,
                false
        );
    }

    protected final void labelValue(GuiGraphics graphics, String label, String value, int y) {
        graphics.drawString(font, label, 16, y, MUTED, false);
        graphics.drawString(font, value, 154, y, TEXT, false);
    }

    protected final void statusLine(GuiGraphics graphics, String label, String value, int color, int y) {
        graphics.drawString(font, label, 16, y, MUTED, false);
        graphics.drawString(font, value, 154, y, color, false);
    }

    protected final void statusBadge(GuiGraphics graphics, String value, int color, int x, int y) {
        int width = font.width(value) + 12;
        graphics.fill(x, y, x + width, y + 14, 0xFF0C1014);
        graphics.fill(x, y, x + 3, y + 14, color);
        graphics.drawString(font, value, x + 7, y + 3, color, false);
    }

    protected final void sectionRule(GuiGraphics graphics, int y) {
        graphics.fill(16, y, imageWidth - 16, y + 1, 0xFF3A4650);
    }

    protected final void signalBar(GuiGraphics graphics, int value, int y) {
        int bounded = Math.max(0, Math.min(15, value));
        int x0 = 16;
        int x1 = 286;
        int interior = x1 - x0 - 2;
        int fillWidth = (bounded * interior) / 15;
        graphics.fill(x0, y, x1, y + 8, 0xFF0C1014);
        if (fillWidth > 0) graphics.fill(x0 + 1, y + 1, x0 + 1 + fillWidth, y + 7, ACCENT);

        for (int tick = 0; tick <= 15; tick += 5) {
            int tickX = x0 + 1 + (tick * interior) / 15;
            graphics.fill(tickX, y + 6, tickX + 1, y + 9, BORDER);
        }
        graphics.drawString(font, "0", x0, y + 11, MUTED, false);
        graphics.drawString(font, "5", x0 + interior / 3 - 2, y + 11, MUTED, false);
        graphics.drawString(font, "10", x0 + (interior * 2) / 3 - 5, y + 11, MUTED, false);
        graphics.drawString(font, "15", x1 - 11, y + 11, MUTED, false);
        graphics.drawString(font, bounded + " / 15", 245, y - 10, TEXT, false);
    }

    protected abstract void renderSection(GuiGraphics graphics, Section section);
}
