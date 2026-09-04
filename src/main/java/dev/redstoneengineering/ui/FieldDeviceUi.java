package dev.redstoneengineering.ui;

import dev.redstoneengineering.ui.menu.FieldDeviceMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

/** Common server-side opener for the lightweight field-device inspector. */
public final class FieldDeviceUi {
    private FieldDeviceUi() {}

    public static void open(ServerPlayer player, BlockPos pos) {
        var title = player.level().getBlockState(pos).getBlock().getName();
        player.openMenu(
                new SimpleMenuProvider(
                        (containerId, inventory, ignored) -> new FieldDeviceMenu(containerId, inventory, pos),
                        title
                ),
                data -> data.writeBlockPos(pos)
        );
    }
}
