package dev.redstoneengineering.ui.ldlib;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.client.Minecraft;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

/**
 * A render-only parameter-position diagram for the shared engineering workbenches.
 *
 * <p>The value must be the menu's server-confirmed engineering-unit readback,
 * not the unsent text field or an independently computed process sample.
 * Scale endpoints and optional discrete marks describe the accepted control
 * domain; a filled scale is not a waveform, physical measurement or forecast.</p>
 */
public final class ParameterScaleElement extends UIElement {
    private static final int BACKGROUND = 0xFF10141A;
    private static final int TRACK = 0xFF34404B;
    private static final int ACTIVE = 0xFF62B0FF;
    private static final int MARK = 0xFFE4ECF3;
    private static final int TEXT = 0xFFBAC7D3;

    private final String symbol;
    private final BooleanSupplier snapshotReady;
    private final DoubleSupplier confirmedValue;
    private final double minimum;
    private final double maximum;
    private final double[] legalValues;

    public ParameterScaleElement(String symbol, BooleanSupplier snapshotReady,
                                 DoubleSupplier confirmedValue, double minimum,
                                 double maximum, double... legalValues) {
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || maximum <= minimum) {
            throw new IllegalArgumentException("Parameter scale requires finite ordered bounds");
        }
        this.symbol = symbol;
        this.snapshotReady = snapshotReady;
        this.confirmedValue = confirmedValue;
        this.minimum = minimum;
        this.maximum = maximum;
        this.legalValues = legalValues.clone();
        layout(l -> l.height(54).widthPercent(100));
    }

    @Override
    public void drawBackgroundAdditional(GUIContext g) {
        int allocatedWidth = Math.round(getSizeWidth());
        int allocatedHeight = Math.round(getSizeHeight());
        if (allocatedWidth < 96 || allocatedHeight < 38) return;

        int x = Math.round(getPositionX()) + 7;
        int y = Math.round(getPositionY()) + 4;
        int w = allocatedWidth - 14;
        var graphics = g.graphics;
        graphics.fill(x, y, x + w, y + allocatedHeight - 8, BACKGROUND);
        var font = Minecraft.getInstance().font;

        String heading = "SERVER CONFIRMED POSITION • " + symbol;
        graphics.drawString(font, font.plainSubstrByWidth(heading, w - 4),
                x + 2, y + 1, TEXT, false);

        int trackX = x + 5;
        int trackW = w - 10;
        int trackY = y + 17;
        int trackH = 8;
        graphics.fill(trackX, trackY, trackX + trackW, trackY + trackH, TRACK);

        // Discrete engineering choices are visibly marked, never presented
        // as extra legal intermediate settings.
        for (double legal : legalValues) {
            if (!Double.isFinite(legal) || legal < minimum || legal > maximum) continue;
            int px = position(trackX, trackW, legal);
            graphics.fill(px, trackY - 2, px + 1, trackY + trackH + 2, MARK);
        }

        if (snapshotReady.getAsBoolean()) {
            double value = confirmedValue.getAsDouble();
            if (Double.isFinite(value) && value >= minimum && value <= maximum) {
                int px = position(trackX, trackW, value);
                graphics.fill(trackX, trackY + 2, Math.max(trackX + 1, px + 1),
                        trackY + trackH - 2, ACTIVE);
                graphics.fill(px, trackY - 3, px + 2, trackY + trackH + 3, MARK);
            } else {
                graphics.drawString(font, "INVALID / UNAVAILABLE READBACK",
                        trackX, trackY + trackH + 4, TEXT, false);
                return;
            }
        } else {
            graphics.drawString(font, "NOT READY • awaiting server", trackX,
                    trackY + trackH + 4, TEXT, false);
            return;
        }

        String left = compact(minimum);
        String right = compact(maximum);
        int labelY = trackY + trackH + 5;
        graphics.drawString(font, left, trackX, labelY, TEXT, false);
        graphics.drawString(font, right, trackX + trackW - font.width(right),
                labelY, TEXT, false);
    }

    private int position(int x, int width, double value) {
        double fraction = (value - minimum) / (maximum - minimum);
        return x + (int) Math.round(fraction * (width - 1));
    }

    private static String compact(double value) {
        return value == Math.rint(value) ? Long.toString((long) value)
                : Double.toString(value);
    }
}
