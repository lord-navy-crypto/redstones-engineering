package dev.redstoneengineering.ui.ldlib;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import dev.redstoneengineering.block.SignalAnalyzerBlock;
import dev.redstoneengineering.client.ui.EngineeringPlot;
import dev.redstoneengineering.ui.menu.SignalAnalyzerMenu;

public final class SignalAnalyzerPlotElement extends UIElement {
    private final SignalAnalyzerMenu menu;

    public SignalAnalyzerPlotElement(SignalAnalyzerMenu menu) {
        this.menu = menu;
        layout(l -> l.height(110).widthPercent(100));
    }

    @Override
    public void drawBackgroundAdditional(GUIContext g) {
        int x=Math.round(getPositionX()), y=Math.round(getPositionY());
        int w=Math.max(100,Math.round(getSizeWidth())), h=Math.max(70,Math.round(getSizeHeight()));
        EngineeringPlot.analogFrame(g.graphics,x,y,w,h);
        EngineeringPlot.analogTrace(g.graphics, SignalAnalyzerBlock.DISPLAY_SAMPLES,
                menu::sample,0,15,x+6,y+6,w-12,h-12,0xFF62B0FF);
        if(menu.windowCount()>0){
            int mean=Math.max(0,Math.min(15,Math.round(menu.average100()/100.0f)));
            EngineeringPlot.horizontalMarker(g.graphics,mean,0,15,x+6,y+6,w-12,h-12,0xFF68D391);
        }
        if(menu.validWindowCount()>1 && menu.peakToPeak()>0){
            EngineeringPlot.horizontalMarker(g.graphics,windowMin(),0,15,x+6,y+6,w-12,h-12,0xFF8A94A6);
            EngineeringPlot.horizontalMarker(g.graphics,windowMax(),0,15,x+6,y+6,w-12,h-12,0xFFF6C453);
        }
    }

    private int windowMin(){
        int min=16;
        for(int i=0;i<SignalAnalyzerBlock.DISPLAY_SAMPLES;i++){
            int v=menu.sample(i);
            if(v>=0) min=Math.min(min,v);
        }
        return min==16?0:min;
    }

    private int windowMax(){
        int max=0;
        for(int i=0;i<SignalAnalyzerBlock.DISPLAY_SAMPLES;i++){
            int v=menu.sample(i);
            if(v>=0) max=Math.max(max,v);
        }
        return max;
    }
}
