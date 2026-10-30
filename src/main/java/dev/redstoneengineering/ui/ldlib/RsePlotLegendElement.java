package dev.redstoneengineering.ui.ldlib;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.client.Minecraft;

/**
 * Render-only instrument legend. The swatch colors match actual plotted
 * channels/markers; text labels remain visible without relying on color alone.
 * Wrapping is computed against the allocated LDLib2 box, not window resolution.
 */
public final class RsePlotLegendElement extends UIElement {
    private final String[] labels;
    private final int[] colors;

    public RsePlotLegendElement(String[] labels, int[] colors) {
        if (labels.length == 0 || labels.length != colors.length) {
            throw new IllegalArgumentException("Legend needs matching labels and colors");
        }
        this.labels = labels.clone();
        this.colors = colors.clone();
        layout(l -> l.widthPercent(100).height(40));
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        int width = Math.round(getSizeWidth());
        int height = Math.round(getSizeHeight());
        if (width < 40 || height < 12) return;

        var font = Minecraft.getInstance().font;
        int startX = Math.round(getPositionX()) + 4;
        int right = Math.round(getPositionX()) + width - 4;
        int x = startX;
        int y = Math.round(getPositionY()) + 4;
        final int lineHeight = 15;

        for (int index = 0; index < labels.length; index++) {
            String label = labels[index];
            int itemWidth = 12 + 5 + font.width(label) + 12;
            if (x != startX && x + itemWidth > right) {
                x = startX;
                y += lineHeight;
            }
            if (y + lineHeight > Math.round(getPositionY()) + height) break;

            // Five-pixel-high line samples remain legible against the dark
            // instrument background; every color also gets a text identifier.
            context.graphics.fill(x, y + 4, x + 12, y + 9, colors[index]);
            context.graphics.drawString(font, label, x + 17, y + 2, 0xFFE7EEF5, false);
            x += itemWidth;
        }
    }
}
