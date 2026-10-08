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
        EngineeringPlot.analogFrame(g.graphics,x,y,w,h);
        int laneH=Math.max(12,(h-16)/4);
        for(int ch=0;ch<4;ch++){
            final int c=ch;
            int laneY=y+6+ch*laneH;
            EngineeringPlot.digitalTrace(g.graphics,16,slot->menu.displayState(c,slot),x+8,laneY,w-16,laneH-2,COLORS[ch]);
        }
        EngineeringPlot.verticalMarker(g.graphics,menu.cursorA(),16,x+8,y,w-16,h,0xFF68D391);
        EngineeringPlot.verticalMarker(g.graphics,menu.cursorB(),16,x+8,y,w-16,h,0xFFC084FC);
    }
}
