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
        if (sampleCount <= 0 || maximum <= minimum) return;
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
            int px = x + Math.round(slot * width / (float) denominator);
            int py = y + height - Math.round((sample - minimum) * height / (float) span);
            if (previousX >= 0) {
                graphics.fill(Math.min(previousX, px), previousY, Math.max(previousX, px) + 1, previousY + 1, color);
                graphics.fill(px, Math.min(previousY, py), px + 1, Math.max(previousY, py) + 1, color);
            }
            graphics.fill(px - 1, py - 1, px + 2, py + 2, color);
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
        if (sampleCount <= 0) return;
        int previousX = -1;
        int previousY = -1;
        int denominator = Math.max(1, sampleCount - 1);
        for (int slot = 0; slot < sampleCount; slot++) {
            int state = stateAt.applyAsInt(slot);
            int px = x + Math.round(slot * width / (float) denominator);
            if (state < 0) {
                int invalidY = y + height / 2;
                graphics.fill(px - 1, invalidY, px + 2, invalidY + 2, INVALID);
                previousX = -1;
                previousY = -1;
                continue;
            }
            int py = state > 0 ? y : y + height;
            if (previousX >= 0) {
                graphics.fill(Math.min(previousX, px), previousY, Math.max(previousX, px) + 1, previousY + 1, color);
                graphics.fill(px, Math.min(previousY, py), px + 1, Math.max(previousY, py) + 1, color);
            }
            graphics.fill(px - 1, py - 1, px + 2, py + 2, color);
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
        if (sampleCount <= 1) return;
        int bounded = Math.max(0, Math.min(sampleCount - 1, slot));
        int px = x + Math.round(bounded * width / (float) (sampleCount - 1));
        graphics.fill(px, y, px + 1, y + height, color);
        graphics.fill(px - 2, y, px + 3, y + 2, color);
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
        if (maximum <= minimum) return;
        int bounded = Math.max(minimum, Math.min(maximum, value));
        int py = y + height - Math.round((bounded - minimum) * height / (float) (maximum - minimum));
        for (int px = x; px < x + width; px += 4) {
            graphics.fill(px, py, Math.min(px + 2, x + width), py + 1, color);
        }
    }
}
