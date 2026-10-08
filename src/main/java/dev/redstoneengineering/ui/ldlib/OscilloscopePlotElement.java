package dev.redstoneengineering.ui.ldlib;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import dev.redstoneengineering.client.ui.EngineeringPlot;
import dev.redstoneengineering.ui.menu.OscilloscopeMenu;

/**
 * Render-only LDLib2 plot element over already synchronized oscilloscope samples.
 *
 * <p>No world reads, sampling, trigger decisions or signal calculations happen here.</p>
 */
public final class OscilloscopePlotElement extends UIElement {
    private static final int CHANNEL_A = 0xFFE05555;
    private static final int CHANNEL_B = 0xFF62B0FF;
    private static final int TRIGGER = 0xFFF6C453;
    private static final int CURSOR_A = 0xFF68D391;
    private static final int CURSOR_B = 0xFFC084FC;

    private final OscilloscopeMenu menu;

    public OscilloscopePlotElement(OscilloscopeMenu menu) {
        this.menu = menu;
        layout(l -> l.height(150).widthPercent(100));
    }

    @Override
    public void drawBackgroundAdditional(GUIContext g) {
        // Respect the allocated layout box; never overflow when GUI Scale is high.
        int allocatedWidth = Math.round(getSizeWidth());
        int allocatedHeight = Math.round(getSizeHeight());
        if (allocatedWidth < 40 || allocatedHeight < 40) return;
        final int inset = 4;
        int x = Math.round(getPositionX()) + inset;
        int y = Math.round(getPositionY()) + inset;
        int width = allocatedWidth - 2 * inset;
        int height = allocatedHeight - 2 * inset;

        EngineeringPlot.analogFrame(g.graphics, x, y, width, height);
        plotChannel(g, 0, CHANNEL_A, x, y, width, height);
        plotChannel(g, 1, CHANNEL_B, x, y, width, height);
        EngineeringPlot.horizontalMarker(g.graphics, menu.triggerLevel(), 0, 15, x, y, width, height, TRIGGER);
        EngineeringPlot.verticalMarker(g.graphics, menu.cursorA(), 16, x, y, width, height, CURSOR_A);
        EngineeringPlot.verticalMarker(g.graphics, menu.cursorB(), 16, x, y, width, height, CURSOR_B);
    }

    private void plotChannel(GUIContext g, int channel, int color, int x, int y, int width, int height) {
        EngineeringPlot.analogTrace(
                g.graphics,
                16,
                slot -> menu.displaySample(channel, slot),
                0,
                15,
                x,
                y,
                width,
                height,
                color
        );
    }
}
