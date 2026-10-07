package dev.redstoneengineering.ui.ldlib;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.redstoneengineering.ui.menu.MediaConversionMenu;
import net.minecraft.world.entity.player.Player;
import org.appliedenergistics.yoga.YogaFlexDirection;

public final class MediaConversionLdUi {
    private MediaConversionLdUi() {}

    public static ModularUI create(MediaConversionMenu menu, Player player){
        var root=new UIElement().addClass("panel_bg");
        root.layout(l->l.width(520).paddingAll(8).gapAll(6));
        root.addChildren(
                RseLdUiComponents.title(menu.redstoneToLapis() ? "REDSTONE → LAPIS SCALER" : "LAPIS → REDSTONE QUANTIZER"),
                new Label().setText("FORMULA-FIRST MEDIA BOUNDARY • SERVER-SYNCHRONIZED OBSERVER"),
                RseLdUiComponents.formulaCard(()->menu.redstoneToLapis()
                        ? "y_L = round(100 · x_R / 15)"
                        : "y_R = round(15 · x_L / 100)"),
                RseLdUiComponents.liveRow("MEASURED","input",()->Integer.toString(menu.inputValue())),
                RseLdUiComponents.liveRow("DERIVED","output",()->Integer.toString(menu.outputValue())),
                RseLdUiComponents.liveRow("EVIDENCE","input quality",()->menu.inputQuality().name()),
                RseLdUiComponents.liveRow("EVIDENCE","output quality",()->menu.outputQuality().name()),
                quantization(menu),
                RseLdUiComponents.liveRow("COMMISSIONING","status",()->menu.commissioningStatus().name()),
                RseLdUiComponents.liveRow("I/O","route",()->menu.inputFace().getName().toUpperCase()+" → "+menu.outputFace().getName().toUpperCase()),
                new UIElement().layout(l->l.flexDirection(YogaFlexDirection.ROW).gapAll(6)).addChildren(
                        RseLdUiComponents.serverAction("Cycle RX ▶",menu::cycleRxForward),
                        RseLdUiComponents.serverAction("Cycle TX ▶",menu::cycleTxForward)
                ),
                new Label().setText("NO NEW SOURCE PRECISION • conversion changes representation, not information content."),
                RseLdUiComponents.authorityFooter()
        );
        return ModularUI.of(UI.of(root, StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.GDP)),player);
    }

    private static UIElement quantization(MediaConversionMenu menu){
        var p=new UIElement().addClass("panel_bg"); p.layout(l->l.paddingAll(5).gapAll(3));
        if(menu.redstoneToLapis()){
            p.addChildren(
                    RseLdUiComponents.liveRow("FIXED","code spacing",()->Integer.toString(menu.sourceSpacing())),
                    new Label().setText("UPSCALED REPRESENTATION — NO NEW SOURCE PRECISION"),
                    new Label().setText("Redstone→Lapis expands representation only; source code spacing remains visible.")
            );
        }else{
            p.addChildren(
                    RseLdUiComponents.liveRow("DERIVED","x_reconstructed",()->Integer.toString(menu.reconstructedLapis())),
                    RseLdUiComponents.liveRow("EVIDENCE","Quantization loss |e_q|",()->Integer.toString(menu.quantizationLoss())),
                    new Label().setText("Quantization error is explicit evidence at the Lapis→Redstone boundary.")
            );
        }
        return p;
    }
}
