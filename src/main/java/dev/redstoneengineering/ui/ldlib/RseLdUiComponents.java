package dev.redstoneengineering.ui.ldlib;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import net.minecraft.network.chat.Component;
import org.appliedenergistics.yoga.YogaFlexDirection;

import java.util.function.Supplier;

/** Reusable LDLib2 primitives for formula-first RSE engineering HMIs. */
public final class RseLdUiComponents {
    private RseLdUiComponents() {}

    public static Label title(String text) {
        return new Label().setText(text);
    }

    public static UIElement formulaCard(Supplier<String> equation) {
        return new UIElement()
                .addClass("panel_bg")
                .layout(l -> l.paddingAll(5))
                .addChild(new Label().bind(DataBindingBuilder.componentS2C(() ->
                        Component.literal(equation.get())
                ).build()));
    }

    public static UIElement liveRow(String role, String symbol, Supplier<String> value) {
        return new UIElement()
                .layout(l -> l.flexDirection(YogaFlexDirection.ROW).gapAll(6))
                .addChildren(
                        new Label().setText(role).layout(l -> l.width(82)),
                        new Label().setText(symbol).layout(l -> l.width(62)),
                        new Label().bind(DataBindingBuilder.componentS2C(() ->
                                Component.literal(value.get())
                        ).build()).layout(l -> l.flex(1))
                );
    }

    public static UIElement fixedRow(String symbol, Supplier<String> value, String reason) {
        return new UIElement()
                .layout(l -> l.flexDirection(YogaFlexDirection.ROW).gapAll(6))
                .addChildren(
                        new Label().setText("FIXED").layout(l -> l.width(82)),
                        new Label().setText(symbol).layout(l -> l.width(62)),
                        new Label().bind(DataBindingBuilder.componentS2C(() ->
                                Component.literal(value.get() + " • " + reason)
                        ).build()).layout(l -> l.flex(1))
                );
    }

    public static Button serverAction(String label, Runnable action) {
        return new Button()
                .setText(label)
                .setOnServerClick(event -> action.run());
    }

    public static Label authorityFooter() {
        return new Label().setText("SERVER AUTHORITY • UI is presentation + validated operator intent");
    }
}
