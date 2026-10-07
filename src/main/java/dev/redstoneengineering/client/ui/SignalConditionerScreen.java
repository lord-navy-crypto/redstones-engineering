package dev.redstoneengineering.client.ui;

import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import dev.redstoneengineering.ui.menu.SignalConditionerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Thin Minecraft host for the LDLib2 Signal Conditioner HMI.
 *
 * <p>All engineering layout, formula presentation, controls and synchronized
 * values are now owned by SignalConditionerLdUi. The menu remains the
 * authoritative server mutation boundary.</p>
 */
public final class SignalConditionerScreen extends AbstractContainerScreen<SignalConditionerMenu> {
    public SignalConditionerScreen(SignalConditionerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        if (menu instanceof IModularUIHolderMenu holder && holder.getModularUI() != null) {
            this.imageWidth = Math.max(1, (int) holder.getModularUI().getWidth());
            this.imageHeight = Math.max(1, (int) holder.getModularUI().getHeight());
        }
        super.init();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // LDLib2 renders the complete engineering HMI.
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Labels are part of the ModularUI tree.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
