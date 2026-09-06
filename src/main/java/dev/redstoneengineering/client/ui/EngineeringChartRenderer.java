package dev.redstoneengineering.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Shared, allocation-free chart primitives for RSE engineering screens.
 *
 * <p>The renderer is deliberately presentation-only: callers provide already synchronized,
 * server-authoritative samples. It never samples the world or invents timing locally.</p>
 */
public final class EngineeringChartRenderer {
    private static final int CHART_BACKGROUND = 0xFF10141A;
    private static final int GRID = 0xFF2C3642;
    private static final int AXIS = 0xFF66717B;
    private static final int LABEL = 0xFF9BA8B3;

    private EngineeringChartRenderer() {}

    /** A bounded chronological series. Missing samples use a negative value or game time. */
    public interface Series {
        int size();
        int valueAt(int slot);
        long gameTimeAt(int slot);
    }

    /** Draws the common engineering chart frame and an optional 0..15-style horizontal grid. */
    public static void drawFrame(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            int minimum,
            int maximum,
            boolean grid
    ) {
        graphics.fill(x, y, x + width, y + height, CHART_BACKGROUND);
        graphics.fill(x, y, x + width, y + 1, AXIS);
        graphics.fill(x, y + height - 1, x + width, y + height, AXIS);
        graphics.fill(x, y, x + 1, y + height, AXIS);
        graphics.fill(x + width - 1, y, x + width, y + height, AXIS);
        if (!grid || maximum <= minimum) return;

        for (int step = 1; step < 3; step++) {
            int py = y + Math.round(step * (height - 1) / 3.0f);
            graphics.fill(x + 1, py, x + width - 1, py + 1, GRID);
        }
    }

    /**
     * Plots a time-domain series using the supplied authoritative gameTime values.
     * Redstone-like sampled signals use a staircase trace so transitions remain explicit.
     */
    public static void drawWaveform(
            GuiGraphics graphics,
            Series series,
            int x,
            int y,
            int width,
            int height,
            int minimum,
            int maximum,
            int color
    ) {
        long firstTime = firstTime(series);
        long lastTime = lastTime(series);
        if (firstTime < 0 || lastTime < 0 || maximum <= minimum) return;

        int previousX = -1;
        int previousY = -1;
        for (int slot = 0; slot < series.size(); slot++) {
            int value = series.valueAt(slot);
            long gameTime = series.gameTimeAt(slot);
            if (value < minimum || gameTime < 0) {
                previousX = -1;
                previousY = -1;
                continue;
            }

            int px = mapTime(gameTime, firstTime, lastTime, x + 2, Math.max(1, width - 4));
            int bounded = Math.min(maximum, value);
            int py = mapValue(bounded, minimum, maximum, y + 2, Math.max(1, height - 4));

            if (previousX >= 0) {
                graphics.fill(Math.min(previousX, px), previousY, Math.max(previousX, px) + 1, previousY + 1, color);
                graphics.fill(px, Math.min(previousY, py), px + 1, Math.max(previousY, py) + 1, color);
            }
            graphics.fill(px - 1, py - 1, px + 2, py + 2, color);
            previousX = px;
            previousY = py;
        }
    }

    /** Shared digital-lane primitive for the Logic Analyzer phase. */
    public static void drawDigitalLane(
            GuiGraphics graphics,
            Series series,
            int x,
            int y,
            int width,
            int height,
            int threshold,
            int color
    ) {
        long firstTime = firstTime(series);
        long lastTime = lastTime(series);
        if (firstTime < 0 || lastTime < 0) return;

        int lowY = y + height - 2;
        int highY = y + 2;
        int previousX = -1;
        int previousY = -1;
        for (int slot = 0; slot < series.size(); slot++) {
            int value = series.valueAt(slot);
            long gameTime = series.gameTimeAt(slot);
            if (value < 0 || gameTime < 0) {
                previousX = -1;
                previousY = -1;
                continue;
            }
            int px = mapTime(gameTime, firstTime, lastTime, x, Math.max(1, width));
            int py = value >= threshold ? highY : lowY;
            if (previousX >= 0) {
                graphics.fill(Math.min(previousX, px), previousY, Math.max(previousX, px) + 1, previousY + 1, color);
                graphics.fill(px, Math.min(previousY, py), px + 1, Math.max(previousY, py) + 1, color);
            }
            previousX = px;
            previousY = py;
        }
    }

    /** Draws an event/cursor marker against the same authoritative time domain. */
    public static void drawTimeMarker(
            GuiGraphics graphics,
            Series series,
            long gameTime,
            int x,
            int y,
            int width,
            int height,
            int color
    ) {
        long firstTime = firstTime(series);
        long lastTime = lastTime(series);
        if (gameTime < 0 || firstTime < 0 || lastTime < 0) return;
        int px = mapTime(gameTime, firstTime, lastTime, x, Math.max(1, width));
        graphics.fill(px, y, px + 1, y + height, color);
    }

    /** Labels the actual server gameTime span used by a chart. */
    public static void drawGameTimeAxis(
            GuiGraphics graphics,
            Font font,
            Series series,
            int x,
            int y,
            int width
    ) {
        long firstTime = firstTime(series);
        long lastTime = lastTime(series);
        if (firstTime < 0 || lastTime < 0) {
            graphics.drawString(font, "gameTime: no samples", x, y, LABEL, false);
            return;
        }
        String start = Long.toString(firstTime);
        String end = Long.toString(lastTime);
        graphics.drawString(font, "gt " + start, x, y, LABEL, false);
        graphics.drawString(font, end, x + width - font.width(end), y, LABEL, false);
    }

    public static long firstTime(Series series) {
        for (int slot = 0; slot < series.size(); slot++) {
            if (series.valueAt(slot) >= 0 && series.gameTimeAt(slot) >= 0) return series.gameTimeAt(slot);
        }
        return -1L;
    }

    public static long lastTime(Series series) {
        for (int slot = series.size() - 1; slot >= 0; slot--) {
            if (series.valueAt(slot) >= 0 && series.gameTimeAt(slot) >= 0) return series.gameTimeAt(slot);
        }
        return -1L;
    }

    private static int mapTime(long gameTime, long firstTime, long lastTime, int x, int width) {
        if (lastTime <= firstTime) return x;
        double fraction = (double) (gameTime - firstTime) / (double) (lastTime - firstTime);
        fraction = Math.max(0.0, Math.min(1.0, fraction));
        return x + (int) Math.round(fraction * width);
    }

    private static int mapValue(int value, int minimum, int maximum, int y, int height) {
        double fraction = (double) (value - minimum) / (double) (maximum - minimum);
        fraction = Math.max(0.0, Math.min(1.0, fraction));
        return y + height - (int) Math.round(fraction * height);
    }
}
