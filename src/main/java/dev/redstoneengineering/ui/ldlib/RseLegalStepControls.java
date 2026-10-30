package dev.redstoneengineering.ui.ldlib;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import dev.vfyjxf.taffy.style.FlexWrap;
import org.appliedenergistics.yoga.YogaFlexDirection;

import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Legal discrete parameter choices for a real server-owned menu setter.
 * No direct changes to world state, client solver, or GUI framework.
 * Every button uses the same setter path as the existing numeric TextField.
 */
public final class RseLegalStepControls {
    private RseLegalStepControls() {}

    public static int[] wholeRange(int min, int max) {
        if (min > max || max - min > 100) throw new IllegalArgumentException("invalid bounded range");
        return java.util.stream.IntStream.rangeClosed(min, max).toArray();
    }

    private static void select(BooleanSupplier ready, IntSupplier current,
                               int[] legal, IntConsumer submit, int direction, boolean boundary) {
        if (!ready.getAsBoolean() || legal.length == 0) return;
        int value = current.getAsInt();
        int index = -1;
        for (int i = 0; i < legal.length; i++) if (legal[i] == value) { index = i; break; }
        if (index < 0) return; // Never interpret an uninitialized slot as the first legal value.
        int next = boundary ? (direction < 0 ? 0 : legal.length - 1) : index + direction;
        if (next < 0 || next >= legal.length || next == index) return;
        submit.accept(legal[next]);
    }

    public static UIElement choices(
            String symbol, String unitsAndEffect,
            BooleanSupplier synchronizedMenu, IntSupplier serverValue,
            int[] legal, IntConsumer serverSetter) {
        if (legal.length == 0) throw new IllegalArgumentException("empty legal domain");
        int previous = legal[0];
        for (int i = 1; i < legal.length; i++) {
            if (legal[i] <= previous) throw new IllegalArgumentException("legal domain must be ordered");
            previous = legal[i];
        }
        var row = new UIElement().layout(l -> l.flexDirection(YogaFlexDirection.ROW)
                .flexWrap(FlexWrap.WRAP).gapAll(5));
        row.addChildren(
                RseLdUiComponents.serverAction("− Legal", () -> select(synchronizedMenu, serverValue, legal, serverSetter, -1, false)),
                RseLdUiComponents.serverAction("+ Legal", () -> select(synchronizedMenu, serverValue, legal, serverSetter, 1, false)),
                RseLdUiComponents.serverAction("Minimum", () -> select(synchronizedMenu, serverValue, legal, serverSetter, -1, true)),
                RseLdUiComponents.serverAction("Maximum", () -> select(synchronizedMenu, serverValue, legal, serverSetter, 1, true))
        );
        var panel = new UIElement().addClass("panel_bg");
        panel.layout(l -> l.paddingAll(5).gapAll(4));
        panel.addChildren(
                RseLdUiComponents.liveRow("SERVER CONFIRMED", symbol, () ->
                        synchronizedMenu.getAsBoolean() ? Integer.toString(serverValue.getAsInt()) : "NOT READY"),
                RseLdUiComponents.liveRow("LEGAL VALUES", symbol, () ->
                        java.util.Arrays.toString(legal)),
                new Label().setText("CHANGES • " + unitsAndEffect),
                row
        );
        return panel;
    }
}
