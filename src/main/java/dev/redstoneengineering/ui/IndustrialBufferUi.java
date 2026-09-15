package dev.redstoneengineering.ui;

import dev.redstoneengineering.block.IndustrialBufferBlock;
import dev.redstoneengineering.operations.OperationBufferLot;
import dev.redstoneengineering.operations.OperationBufferSnapshot;
import dev.redstoneengineering.ui.menu.IndustrialBufferMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

/** Server-authoritative opener and exact bounded lot-identity snapshot transport. */
public final class IndustrialBufferUi {
    public static final int MAX_VISIBLE_LOTS = 6;

    private IndustrialBufferUi() {}

    public static void open(ServerPlayer player, BlockPos pos) {
        OperationBufferSnapshot snapshot = IndustrialBufferBlock.snapshot(player.level(), pos);
        var title = player.level().getBlockState(pos).getBlock().getName();
        player.openMenu(
                new SimpleMenuProvider(
                        (containerId, inventory, ignored) -> new IndustrialBufferMenu(containerId, inventory, pos),
                        title
                ),
                buffer -> {
                    buffer.writeBlockPos(pos);
                    if (snapshot == null) {
                        buffer.writeVarInt(0);
                        buffer.writeVarInt(0);
                        buffer.writeVarInt(0);
                        buffer.writeVarInt(0);
                        return;
                    }
                    buffer.writeVarInt(snapshot.capacityUnits());
                    buffer.writeVarInt(snapshot.usedUnits());
                    buffer.writeVarInt(snapshot.lots().size());
                    int visible = Math.min(MAX_VISIBLE_LOTS, snapshot.lots().size());
                    buffer.writeVarInt(visible);
                    for (int index = 0; index < visible; index++) {
                        OperationBufferLot lot = snapshot.lots().get(index);
                        buffer.writeVarLong(lot.outputId());
                        buffer.writeVarLong(lot.jobId());
                        buffer.writeVarInt(lot.units());
                    }
                }
        );
    }
}
