package dev.redstoneengineering.ui.ldlib;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import dev.redstoneengineering.client.ui.EngineeringPlot;
import dev.redstoneengineering.ui.menu.PidControllerMenu;

/** Render-only LDLib2 PID trend over synchronized authoritative telemetry. */
public final class PidTrendPlotElement extends UIElement {
    private static final int SP = 0xFFF6C453;
    private static final int PV = 0xFF68D391;
    private static final int OUT = 0xFF62B0FF;
    private final PidControllerMenu menu;

    public PidTrendPlotElement(PidControllerMenu menu) {
        this.menu = menu;
        layout(l -> l.height(160).widthPercent(100));
    }

    @Override
    public void drawBackgroundAdditional(GUIContext g) {
        int x=Math.round(getPositionX()), y=Math.round(getPositionY());
        int w=Math.max(60,Math.round(getSizeWidth())), h=Math.max(60,Math.round(getSizeHeight()));
        EngineeringPlot.analogFrame(g.graphics,x,y,w,h);
        EngineeringPlot.analogTrace(g.graphics,PidControllerMenu.TREND_SAMPLES,menu::trendSetpoint,0,15,x,y,w,h,SP);
        EngineeringPlot.analogTrace(g.graphics,PidControllerMenu.TREND_SAMPLES,menu::trendProcessValue,0,15,x,y,w,h,PV);
        EngineeringPlot.analogTrace(g.graphics,PidControllerMenu.TREND_SAMPLES,menu::trendControlOutput,0,15,x,y,w,h,OUT);
    }
}
