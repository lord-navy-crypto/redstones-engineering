package dev.redstoneengineering.ui;

import dev.redstoneengineering.block.EdgeDetectorBlock;
import dev.redstoneengineering.block.PrecisionFilterBlock;
import dev.redstoneengineering.block.PulseShaperBlock;
import dev.redstoneengineering.block.QuartzClockDividerBlock;
import dev.redstoneengineering.block.QuartzOscillatorBlock;
import dev.redstoneengineering.block.QuartzStabilityMonitorBlock;
import dev.redstoneengineering.block.RangeSensorBlock;
import dev.redstoneengineering.ui.menu.FieldDeviceMenu;
import dev.redstoneengineering.ui.menu.QuartzTimingMenu;
import dev.redstoneengineering.ui.menu.RangeSensorMenu;
import dev.redstoneengineering.ui.menu.SignalProcessorMenu;
import dev.redstoneengineering.ui.menu.UniversalFieldDeviceMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

/** Common server-side openers for rich known-device UI and universal engineering fallback UI. */
public final class FieldDeviceUi {
    private FieldDeviceUi() {}

    /** Opens the richest registered device-aware dashboard for the selected block. */
    public static void open(ServerPlayer player, BlockPos pos) {
        var state = player.level().getBlockState(pos);
        var title = state.getBlock().getName();
        if (state.getBlock() instanceof RangeSensorBlock) {
            player.openMenu(
                    new SimpleMenuProvider(
                            (containerId, inventory, ignored) -> new RangeSensorMenu(containerId, inventory, pos),
                            title
                    ),
                    data -> data.writeBlockPos(pos)
            );
            return;
        }
        if (state.getBlock() instanceof PrecisionFilterBlock
                || state.getBlock() instanceof EdgeDetectorBlock
                || state.getBlock() instanceof PulseShaperBlock) {
            player.openMenu(
                    new SimpleMenuProvider(
                            (containerId, inventory, ignored) -> new SignalProcessorMenu(containerId, inventory, pos),
                            title
                    ),
                    data -> data.writeBlockPos(pos)
            );
            return;
        }
        if (state.getBlock() instanceof QuartzOscillatorBlock
                || state.getBlock() instanceof QuartzClockDividerBlock
                || state.getBlock() instanceof QuartzStabilityMonitorBlock) {
            player.openMenu(
                    new SimpleMenuProvider(
                            (containerId, inventory, ignored) -> new QuartzTimingMenu(containerId, inventory, pos),
                            title
                    ),
                    data -> data.writeBlockPos(pos)
            );
            return;
        }
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
