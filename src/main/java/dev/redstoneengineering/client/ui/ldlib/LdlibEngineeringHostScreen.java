package dev.redstoneengineering.client.ui.ldlib;

import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import dev.redstoneengineering.ui.menu.EngineeringDeviceMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Shared thin Minecraft host for server-backed LDLib2 engineering HMIs. */
public abstract class LdlibEngineeringHostScreen<M extends EngineeringDeviceMenu>
        extends AbstractContainerScreen<M> {

    protected LdlibEngineeringHostScreen(M menu, Inventory inventory, Component title) {
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
        // LDLib2 owns the engineering HMI canvas.
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // All labels live in the ModularUI tree.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
