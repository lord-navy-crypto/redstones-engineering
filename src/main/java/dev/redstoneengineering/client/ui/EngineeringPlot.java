package dev.redstoneengineering.client.ui;

import net.minecraft.client.gui.GuiGraphics;

import java.util.function.IntUnaryOperator;

/**
 * Client-only plotting primitives for already synchronized engineering samples.
 *
 * This class is deliberately render-only: callers provide bounded sample values and this helper
 * converts them to pixels. It never reads the world, samples a device, solves a network, or owns
 * simulation state.
 */
public final class EngineeringPlot {
    private static final int BACKGROUND = 0xFF10141A;
    private static final int GRID_MAJOR = 0xFF34404B;
    private static final int GRID_MINOR = 0xFF252E37;
    private static final int INVALID = 0xFF6F7A84;

    private EngineeringPlot() {
    }

    public static void analogFrame(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, BACKGROUND);
        for (int division = 1; division < 4; division++) {
            int gx = x + Math.round(division * width / 4.0f);
            graphics.fill(gx, y, gx + 1, y + height, GRID_MINOR);
        }
        for (int division = 1; division < 3; division++) {
            int gy = y + Math.round(division * height / 3.0f);
            graphics.fill(x, gy, x + width, gy + 1, GRID_MINOR);
        }
        int centerY = y + height / 2;
        graphics.fill(x, centerY, x + width, centerY + 1, GRID_MAJOR);
    }

    public static void analogTrace(
            GuiGraphics graphics,
            int sampleCount,
            IntUnaryOperator sampleAt,
            int minimum,
            int maximum,
            int x,
            int y,
            int width,
            int height,
            int color
    ) {
        if (sampleCount <= 0 || maximum <= minimum || width < 3 || height < 3) return;
        int previousX = -1;
        int previousY = -1;
        int denominator = Math.max(1, sampleCount - 1);
        int span = maximum - minimum;
        for (int slot = 0; slot < sampleCount; slot++) {
            int sample = sampleAt.applyAsInt(slot);
            if (sample < minimum || sample > maximum) {
                previousX = -1;
                previousY = -1;
                continue;
            }
            // Pixel rectangles are half-open. Never draw onto the next widget.
            int px = x + Math.round(slot * (width - 1) / (float) denominator);
            int py = y + (height - 1) - Math.round((sample - minimum) * (height - 1) / (float) span);
            if (previousX >= 0) {
                graphics.fill(Math.min(previousX, px), previousY, Math.max(previousX, px) + 1, previousY + 1, color);
                graphics.fill(px, Math.min(previousY, py), px + 1, Math.max(previousY, py) + 1, color);
            }
            boundedPoint(graphics, px, py, x, y, width, height, color);
            previousX = px;
            previousY = py;
        }
    }

    public static void digitalTrace(
            GuiGraphics graphics,
            int sampleCount,
            IntUnaryOperator stateAt,
            int x,
            int y,
            int width,
            int height,
            int color
    ) {
        if (sampleCount <= 0 || width < 3 || height < 3) return;
        int previousX = -1;
        int previousY = -1;
        int denominator = Math.max(1, sampleCount - 1);
        for (int slot = 0; slot < sampleCount; slot++) {
            int state = stateAt.applyAsInt(slot);
            int px = x + Math.round(slot * (width - 1) / (float) denominator);
            if (state < 0) {
                int invalidY = y + height / 2;
                boundedPoint(graphics, px, invalidY, x, y, width, height, INVALID);
                previousX = -1;
                previousY = -1;
                continue;
            }
            int py = state > 0 ? y : y + height - 1;
            if (previousX >= 0) {
                graphics.fill(Math.min(previousX, px), previousY, Math.max(previousX, px) + 1, previousY + 1, color);
                graphics.fill(px, Math.min(previousY, py), px + 1, Math.max(previousY, py) + 1, color);
            }
            boundedPoint(graphics, px, py, x, y, width, height, color);
            previousX = px;
            previousY = py;
        }
    }

    public static void verticalMarker(
            GuiGraphics graphics,
            int slot,
            int sampleCount,
            int x,
            int y,
            int width,
            int height,
            int color
    ) {
        if (sampleCount <= 1 || width < 1 || height < 1 || slot < 0 || slot >= sampleCount) return;
        int px = x + Math.round(slot * (width - 1) / (float) (sampleCount - 1));
        graphics.fill(px, y, px + 1, y + height, color);
        graphics.fill(Math.max(x, px - 2), y, Math.min(x + width, px + 3),
                Math.min(y + height, y + 2), color);
    }

    public static void horizontalMarker(
            GuiGraphics graphics,
            int value,
            int minimum,
            int maximum,
            int x,
            int y,
            int width,
            int height,
            int color
    ) {
        if (maximum <= minimum || width < 1 || height < 1) return;
        int bounded = Math.max(minimum, Math.min(maximum, value));
        int py = y + (height - 1) - Math.round((bounded - minimum) * (height - 1) / (float) (maximum - minimum));
        for (int px = x; px < x + width; px += 4) {
            graphics.fill(px, py, Math.min(px + 2, x + width), py + 1, color);
        }
    }

    /** Clamp the 3px sample marker to the trace's own allocated rectangle. */
    private static void boundedPoint(GuiGraphics graphics, int px, int py,
                                     int x, int y, int width, int height, int color) {
        graphics.fill(Math.max(x, px - 1), Math.max(y, py - 1),
                Math.min(x + width, px + 2), Math.min(y + height, py + 2), color);
    }
}
