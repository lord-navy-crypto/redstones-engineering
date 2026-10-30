package dev.redstoneengineering.client.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Pixel-exact backdrop for engineering screens on Minecraft 1.21.1.
 *
 * Screen.renderBackground can invoke the menu background-blur shader. In
 * modpacks with many HUD layers, the blurring/darkening path may obscure the
 * screen content. This method only draws an ordinary solid translucent GUI
 * rectangle: no post-processing shader, texture scaling, font transform,
 * framebuffer switching, or server-world access. Labels/widgets remain above
 * this rectangle and retain the native Minecraft font rasterization.
 */
public final class RseCrispGuiBackdrop {
    private static final int SCRIM = 0xC8000000;

    private RseCrispGuiBackdrop() {}

    public static void render(GuiGraphics graphics, int guiWidth, int guiHeight) {
        if (guiWidth <= 0 || guiHeight <= 0) return;
        graphics.fill(0, 0, guiWidth, guiHeight, SCRIM);
    }
}
