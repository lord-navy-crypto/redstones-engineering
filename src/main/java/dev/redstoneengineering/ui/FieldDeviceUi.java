package dev.redstoneengineering.ui;

import dev.redstoneengineering.ui.menu.FieldDeviceMenu;
import dev.redstoneengineering.ui.menu.UniversalFieldDeviceMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

/** Common server-side openers for rich known-device UI and universal engineering fallback UI. */
public final class FieldDeviceUi {
    private FieldDeviceUi() {}

    /** Opens the established device-aware dashboard with device-specific telemetry/configuration. */
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

    /**
     * Opens the universal six-face EngineeringPortProvider inspector. This is the fallback for
     * blocks that do not yet own a richer device-specific menu projection.
     */
    public static void openUniversal(ServerPlayer player, BlockPos pos) {
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
