package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.ui.EngineeringUiRegistration;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.ArrayList;
import java.util.List;

/** Immutable client snapshot transfer for the hand-held diagnostic tablet. */
public final class DiagnosticTabletMenu extends AbstractContainerMenu {
    private final List<String> history;

    public DiagnosticTabletMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, readHistory(data));
    }

    public DiagnosticTabletMenu(int containerId, Inventory inventory, List<String> history) {
        super(EngineeringUiRegistration.DIAGNOSTIC_TABLET.get(), containerId);
        this.history = List.copyOf(history);
    }

    public List<String> history() {
        return history;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    private static List<String> readHistory(RegistryFriendlyByteBuf data) {
        int count = Math.max(0, Math.min(8, data.readVarInt()));
        List<String> history = new ArrayList<>(count);
        for (int i = 0; i < count; i++) history.add(data.readUtf(4096));
        return history;
    }
}
