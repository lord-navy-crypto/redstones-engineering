package dev.redstoneengineering.client.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Full-resolution, non-postprocessed backdrop for in-game RSE screens.
 *
 * <p>Never invokes Screen.renderBackground(), which may request the vanilla
 * background-blur post-processing path. Instead the dimmer is rendered as one
 * native-scaled GUI rectangle immediately before the panel and its text. This
 * is deliberately independent of LDLib2, shaders and texture filtering.
 * It does not claim to override an unrelated global client shader or GPU
 * scaling policy.</p>
 */
public final class RseCrispBackground {
    private static final int DIMMER = 0xD7000000;

    private RseCrispBackground() {
    }

    public static void render(GuiGraphics graphics) {
        // Minecraft GuiGraphics uses integer GUI-scaled coordinates; no
        // fractional pose scaling or blur/texture sampling is involved.
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), DIMMER);
    }
}
