package dev.redstoneengineering.ui;

import dev.redstoneengineering.ui.menu.OperationsMonitorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

/** Common server-side opener for the dedicated observer-only Operations Monitor console. */
public final class OperationsMonitorUi {
    private OperationsMonitorUi() {}

    public static void open(ServerPlayer player, BlockPos pos) {
        var title = player.level().getBlockState(pos).getBlock().getName();
        player.openMenu(
                new SimpleMenuProvider(
                        (containerId, inventory, ignored) -> new OperationsMonitorMenu(containerId, inventory, pos),
                        title
                ),
                data -> data.writeBlockPos(pos)
        );
    }
}
