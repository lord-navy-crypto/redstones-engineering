package dev.redstoneengineering.ui;

import dev.redstoneengineering.ui.menu.WorkcellControllerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

/** Common server-side opener for the Workcell Controller console. */
public final class WorkcellControllerUi {
    private WorkcellControllerUi() {}

    public static void open(ServerPlayer player, BlockPos pos) {
        var title = player.level().getBlockState(pos).getBlock().getName();
        player.openMenu(
                new SimpleMenuProvider(
                        (containerId, inventory, ignored) -> new WorkcellControllerMenu(containerId, inventory, pos),
                        title
                ),
                data -> data.writeBlockPos(pos)
        );
    }
}
