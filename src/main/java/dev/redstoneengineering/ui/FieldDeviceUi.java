package dev.redstoneengineering.ui;

import dev.redstoneengineering.ui.menu.UniversalFieldDeviceMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

/** Common server-side opener for the universal field-device engineering HMI. */
public final class FieldDeviceUi {
    private FieldDeviceUi() {}

    public static void open(ServerPlayer player, BlockPos pos) {
        var title = player.level().getBlockState(pos).getBlock().getName();
        player.openMenu(
                new SimpleMenuProvider(
                        (containerId, inventory, ignored) -> new UniversalFieldDeviceMenu(containerId, inventory, pos),
                        title
                ),
                data -> data.writeBlockPos(pos)
        );
    }
}
