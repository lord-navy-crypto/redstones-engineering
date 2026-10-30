package dev.redstoneengineering.ui.ldlib;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import dev.redstoneengineering.client.ui.EngineeringPlot;
import dev.redstoneengineering.ui.menu.LogicAnalyzerMenu;

public final class LogicAnalyzerPlotElement extends UIElement {
    private static final int[] COLORS = {0xFF66C2FF,0xFF7DDB8A,0xFFFFC857,0xFFE879F9};
    private final LogicAnalyzerMenu menu;

    public LogicAnalyzerPlotElement(LogicAnalyzerMenu menu) {
        this.menu = menu;
        layout(l -> l.height(120).widthPercent(100));
    }

    @Override
    public void drawBackgroundAdditional(GUIContext g) {
        // Never paint a fabricated minimum-size plot outside our actual LDLib2 element.
        int allocatedWidth = Math.round(getSizeWidth());
        int allocatedHeight = Math.round(getSizeHeight());
        if (allocatedWidth < 40 || allocatedHeight < 60) return;
        final int inset = 4;
        int x = Math.round(getPositionX()) + inset;
        int y = Math.round(getPositionY()) + inset;
        int w = allocatedWidth - 2 * inset;
        int h = allocatedHeight - 2 * inset;
        if (w < 65 || h < 70) return;
        // Reserve bottom space for display-slot indices and keep the digital
        // channel labels outside the four waveform lanes.
        x += 9;
        y += 3;
        w -= 13;
        h -= 22;
        EngineeringPlot.analogFrame(g.graphics,x,y,w,h);
        int traceX = x + 17;
        int traceWidth = w - 21;
        EngineeringPlot.slotAxisLabels(g.graphics,traceX,y,traceWidth,h,16);
        // An empty capture is explicitly empty, not a LOW signal.
        if (menu.sampleCount() <= 0) {
            EngineeringPlot.emptyPlotLabel(g.graphics,x,y,w,h,"NO CAPTURED SAMPLES");
            return;
        }
        int laneH = Math.max(12, (h - 12) / 4);
        for(int ch=0;ch<4;ch++) {
            final int c = ch;
            int laneY = y + 4 + ch * laneH;
            EngineeringPlot.digitalLaneLabel(g.graphics,"" + (char)('A' + ch),traceX,laneY,laneH-2);
            EngineeringPlot.digitalTrace(g.graphics,16,slot->menu.displayState(c,slot),
                    traceX,laneY,traceWidth,laneH-2,COLORS[ch]);
        }
        // Cursors require a real valid same-channel pair in captured data.
        if (validCursorPair()) {
            EngineeringPlot.verticalMarker(g.graphics,menu.cursorA(),16,traceX,y,traceWidth,h,0xFF68D391);
            EngineeringPlot.verticalMarker(g.graphics,menu.cursorB(),16,traceX,y,traceWidth,h,0xFFC084FC);
        }
    }
    private boolean validCursorPair() {
        int a = menu.cursorA();
        int b = menu.cursorB();
        if (a < 0 || a >= 16 || b < 0 || b >= 16) return false;
        for (int channel = 0; channel < 4; channel++) {
            if (menu.displayState(channel, a) >= 0 && menu.displayState(channel, b) >= 0) {
                return true;
            }
        }
        return false;
    }
}
