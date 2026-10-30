package dev.redstoneengineering.client.ui;

import com.mojang.blaze3d.platform.Window;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.diagnostics.RseDiagnosticEntry;
import dev.redstoneengineering.diagnostics.RseDiagnosticSeverity;
import dev.redstoneengineering.diagnostics.RseDiagnostics;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Read-only, session-local diagnostics console with native-pixel text and clipboard export. */
public final class RseDiagnosticsScreen extends Screen {
    private enum Filter {
        ALL("ALL"),
        WARNINGS("WARN+"),
        ERRORS("ERROR");

        private final String label;
        Filter(String label) { this.label=label; }
    }

    private static final int PANEL=0xFF11161B;
    private static final int PANEL_2=0xFF1C242C;
    private static final int BORDER=0xFF697681;
    private static final int TEXT=0xFFE8EDF2;
    private static final int MUTED=0xFF98A5B0;
    private static final int INFO=0xFF9EC8FF;
    private static final int GOOD=0xFF68D391;
    private static final int WARN=0xFFF6C453;
    private static final int ERROR=0xFFF06A6A;
    private static final int ACCENT=0xFFE05555;

    private final Screen parent;
    private Filter filter=Filter.ALL;
    private int page;
    private String feedback="";
    private int feedbackTicks;

    public RseDiagnosticsScreen(Screen parent) {
        super(Component.literal("RSE Diagnostics Console"));
        this.parent=parent;
    }

    @Override
    protected void init() {
        int bottom=Math.max(0,height-27);
        int gap=3;
        int usable=Math.max(1,width-24-gap*5);
        int backWidth=Math.max(26,usable*16/100);
        int copyWidth=Math.max(34,usable*20/100);
        int clearWidth=Math.max(26,usable*14/100);
        int filterWidth=Math.max(42,usable*24/100);
        int arrowWidth=Math.max(24,(usable-backWidth-copyWidth-clearWidth-filterWidth)/2);
        int total=backWidth+copyWidth+clearWidth+filterWidth+arrowWidth*2+gap*5;
        int x=Math.max(0,(width-total)/2);

        addRenderableWidget(Button.builder(Component.literal("Back"),button->onClose())
                .bounds(x,bottom,backWidth,20).build());
        x+=backWidth+gap;
        addRenderableWidget(Button.builder(Component.literal("Copy"),button->copyReport())
                .bounds(x,bottom,copyWidth,20).build());
        x+=copyWidth+gap;
        addRenderableWidget(Button.builder(Component.literal("Clear"),button->{
                    RseDiagnostics.clear();
                    page=0;
                    showFeedback("SESSION BUFFER CLEARED");
                }).bounds(x,bottom,clearWidth,20).build());
        x+=clearWidth+gap;
        addRenderableWidget(Button.builder(Component.literal("Filter"),button->{
                    filter=Filter.values()[(filter.ordinal()+1)%Filter.values().length];
                    page=0;
                    rebuildWidgets();
                }).bounds(x,bottom,filterWidth,20).build());
        x+=filterWidth+gap;
        addRenderableWidget(Button.builder(Component.literal("<"),button->page++)
                .bounds(x,bottom,arrowWidth,20).build());
        x+=arrowWidth+gap;
        addRenderableWidget(Button.builder(Component.literal(">"),button->page=Math.max(0,page-1))
                .bounds(x,bottom,arrowWidth,20).build());
    }

    @Override
    public void tick() {
        super.tick();
        if(feedbackTicks>0)feedbackTicks--;
        if(feedbackTicks==0)feedback="";
    }

    @Override
    public boolean keyPressed(int keyCode,int scanCode,int modifiers) {
        if(keyCode==GLFW.GLFW_KEY_C) {
            copyReport();
            return true;
        }
        if(keyCode==GLFW.GLFW_KEY_LEFT) {
            page++;
            return true;
        }
        if(keyCode==GLFW.GLFW_KEY_RIGHT) {
            page=Math.max(0,page-1);
            return true;
        }
        return super.keyPressed(keyCode,scanCode,modifiers);
    }

    @Override
    public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        // Do not invoke Screen.renderBackground(): it may run the vanilla
        // background-blur post-process. Keep the complete console opaque.
        RseCrispGuiBackdrop.render(graphics, width, height);
        graphics.fill(8,8,width-8,height-34,BORDER);
        graphics.fill(10,10,width-10,height-36,PANEL);
        graphics.fill(16,60,width-16,height-43,PANEL_2);
        graphics.fill(16,54,width-16,56,ACCENT);

        graphics.drawString(font,title,18,17,TEXT,false);
        graphics.drawString(font,truncateToWidth(runtimeSummary(),width-36),18,30,MUTED,false);

        int warnCount=RseDiagnostics.count(RseDiagnosticSeverity.WARN);
        int errorCount=RseDiagnostics.count(RseDiagnosticSeverity.ERROR);
        String counts="ENTRIES "+RseDiagnostics.size()+"/"+RseDiagnostics.MAX_ENTRIES
                +"  •  WARN "+warnCount+"  •  ERROR "+errorCount;
        graphics.drawString(font,truncateToWidth(counts,width-36),18,43,MUTED,false);

        graphics.drawString(font,"OBSERVER ONLY",23,64,GOOD,false);
        String view="VIEW "+filter.label;
        graphics.drawString(font,view,width-font.width(view)-23,64,INFO,false);
        if(!feedback.isEmpty()) {
            graphics.drawString(font,truncateToWidth(feedback,width-36),18,height-41,GOOD,false);
        }

        renderEntries(graphics);
        super.render(graphics,mouseX,mouseY,partialTick);
    }

    private void renderEntries(GuiGraphics graphics) {
        List<RseDiagnosticEntry> entries=filteredEntries();
        int rows=Math.max(1,(height-56-82)/12);
        int maxPage=entries.isEmpty()?0:Math.max(0,(entries.size()-1)/rows);
        page=Math.min(page,maxPage);
        int endExclusive=Math.max(0,entries.size()-page*rows);
        int start=Math.max(0,endExclusive-rows);
        int y=82;
        if(entries.isEmpty()) {
            graphics.drawString(font,"No matching RSE diagnostics have been captured in this session.",
                    23,y,MUTED,false);
            graphics.drawString(font,"Run the device, reproduce the issue, then return here to inspect evidence.",
                    23,y+16,INFO,false);
            return;
        }

        for(int i=start;i<endExclusive;i++) {
            RseDiagnosticEntry entry=entries.get(i);
            int color=severityColor(entry.severity());
            String source=shortSource(entry.source());
            String message=oneLine(entry.message());
            String line="["+RseDiagnostics.timeLabel(entry)+"] ["+entry.severity()+"] "+source+" — "+message;
            graphics.drawString(font,truncateToWidth(line,width-52),23,y,color,false);
            y+=12;
        }
        graphics.drawString(font,"Page "+(page+1)+"/"+(maxPage+1)+" • newest entries are on page 1",
                23,height-56,MUTED,false);
    }

    private List<RseDiagnosticEntry> filteredEntries() {
        List<RseDiagnosticEntry> result=new ArrayList<>();
        for(RseDiagnosticEntry entry:RseDiagnostics.snapshot()) {
            if(filter==Filter.ERRORS && entry.severity()!=RseDiagnosticSeverity.ERROR)continue;
            if(filter==Filter.WARNINGS && entry.severity()==RseDiagnosticSeverity.INFO)continue;
            result.add(entry);
        }
        return result;
    }

    private void copyReport() {
        Minecraft minecraft=Minecraft.getInstance();
        minecraft.keyboardHandler.setClipboard(
                RseDiagnostics.exportReport(runtimeSummary()+"\n"+displaySummary()));
        showFeedback("REPORT COPIED • C");
    }

    private String displaySummary() {
        Window window=Minecraft.getInstance().getWindow();
        return "Display framebuffer: "+window.getWidth()+"x"+window.getHeight()
                +" | GUI scale: "+String.format(Locale.ROOT,"%.3f",window.getGuiScale())
                +" | GUI scaled: "+window.getGuiScaledWidth()+"x"+window.getGuiScaledHeight();
    }

    private void showFeedback(String message) {
        feedback=message;
        feedbackTicks=60;
    }

    private String runtimeSummary() {
        String rseVersion=versionOf(RedstoneEngineering.MOD_ID);
        String neoForgeVersion=versionOf("neoforge");
        String minecraftVersion=SharedConstants.getCurrentVersion().getName();
        String javaVersion=System.getProperty("java.version","unknown");
        return "RSE "+rseVersion+" • MC "+minecraftVersion+" • NeoForge "+neoForgeVersion+" • Java "+javaVersion;
    }

    private static String versionOf(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container->container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    private int severityColor(RseDiagnosticSeverity severity) {
        return switch(severity) {
            case INFO -> INFO;
            case WARN -> WARN;
            case ERROR -> ERROR;
        };
    }

    private static String shortSource(String source) {
        int dot=source.lastIndexOf('.');
        return dot>=0 && dot+1<source.length()?source.substring(dot+1):source;
    }

    private static String oneLine(String value) {
        return value.replace('\n',' ').replace('\t',' ').trim();
    }

    private String truncateToWidth(String value,int maxWidth) {
        if(maxWidth<=0)return "";
        if(font.width(value)<=maxWidth)return value;
        String suffix="…";
        int end=value.length();
        while(end>0 && font.width(value.substring(0,end)+suffix)>maxWidth)end--;
        return value.substring(0,end)+suffix;
    }

    @Override
    public void onClose() {
        if(minecraft!=null)minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {return false;}
}
