package dev.redstoneengineering.item;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.diagnostics.RseDiagnosticSeverity;
import dev.redstoneengineering.diagnostics.RseDiagnostics;
import dev.redstoneengineering.diagnostics.topology.EngineeringTopologyView;
import dev.redstoneengineering.diagnostics.topology.TopologyFaceSnapshot;
import dev.redstoneengineering.diagnostics.topology.TopologyVisualizationSnapshot;
import dev.redstoneengineering.ui.menu.DiagnosticTabletMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Hand-held observer-only diagnostics tablet.
 *
 * <p>Right-click a block to retain a bounded topology/evidence snapshot and review it immediately.
 * Right-click air to reopen retained history. The tablet never drives a network, changes a block,
 * schedules ticks, or runs a second solver; it only consumes existing BlockState, vanilla redstone
 * observation and the formal EngineeringPort/Topology projection.</p>
 */
public final class DiagnosticTabletItem extends Item {
    public static final int MAX_HISTORY = 8;
    private static final String COUNT = "rse_tablet_count";
    private static final String SNAPSHOT_PREFIX = "rse_tablet_snapshot_";

    public DiagnosticTabletItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal("Engineering Diagnostic Tablet");
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            BlockPos pos = context.getClickedPos();
            String snapshot = capture(level, pos, context.getClickedFace());
            ItemStack tablet = context.getItemInHand();
            pushSnapshot(tablet, snapshot);
            String blockName = level.getBlockState(pos).getBlock().getName().getString();
            serverPlayer.displayClientMessage(Component.literal("Tablet snapshot captured: " + blockName), true);
            RseDiagnostics.record(RseDiagnosticSeverity.INFO, "DiagnosticTablet", "Captured observer snapshot at " + pos.toShortString(), null);
            openTablet(serverPlayer, tablet);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            openTablet(serverPlayer, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static void openTablet(ServerPlayer serverPlayer, ItemStack stack) {
        List<String> history = history(stack);
        serverPlayer.openMenu(
                new SimpleMenuProvider(
                        (containerId, inventory, ignored) -> new DiagnosticTabletMenu(containerId, inventory, history),
                        Component.literal("Engineering Diagnostic Tablet")
                ),
                buffer -> {
                    buffer.writeVarInt(history.size());
                    for (String entry : history) buffer.writeUtf(entry, 4096);
                }
        );
    }

    public static List<String> history(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) return List.of();
        CompoundTag tag = customData.copyTag();
        int count = Math.max(0, Math.min(MAX_HISTORY, tag.getInt(COUNT)));
        List<String> history = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String value = tag.getString(SNAPSHOT_PREFIX + i);
            if (!value.isBlank()) history.add(value);
        }
        return List.copyOf(history);
    }

    private static void pushSnapshot(ItemStack stack, String snapshot) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            int count = Math.max(0, Math.min(MAX_HISTORY, tag.getInt(COUNT)));
            int newCount = Math.min(MAX_HISTORY, count + 1);
            for (int i = newCount - 1; i >= 1; i--) {
                String previous = tag.getString(SNAPSHOT_PREFIX + (i - 1));
                if (!previous.isBlank()) tag.putString(SNAPSHOT_PREFIX + i, previous);
            }
            tag.putString(SNAPSHOT_PREFIX, snapshot);
            tag.putInt(COUNT, newCount);
        });
    }

    private static String capture(Level level, BlockPos pos, Direction clickedFace) {
        BlockState state = level.getBlockState(pos);
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        TopologyVisualizationSnapshot topology = EngineeringTopologyView.inspect(level, pos, state);
        StringBuilder out = new StringBuilder(1536);
        out.append(state.getBlock().getName().getString()).append('\n');
        out.append("ID: ").append(id).append('\n');
        out.append("POS: ").append(pos.getX()).append(", ").append(pos.getY()).append(", ").append(pos.getZ()).append('\n');
        out.append("TARGET FACE: ").append(clickedFace.getName().toUpperCase(Locale.ROOT)).append('\n');
        out.append("CONTEXT: dimension=").append(level.dimension().location())
                .append(" • tick=").append(level.getGameTime()).append('\n');
        out.append("SOURCE: ").append(RedstoneEngineering.MOD_ID.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace()) ? "RSE" : "VANILLA / OTHER").append('\n');
        out.append("REDSTONE IN: best-neighbor=").append(level.getBestNeighborSignal(pos)).append("/15 • powered=").append(level.hasNeighborSignal(pos)).append('\n');
        appendState(out, state);
        out.append("TOPOLOGY: ").append(topology.summary()).append('\n');
        out.append("STATUS: ").append(topology.issueCount() == 0 ? "NOMINAL TOPOLOGY" : "CHECK TOPOLOGY • issues=" + topology.issueCount()).append('\n');
        if (topology.faces().isEmpty()) {
            out.append("PORTS: no EngineeringPort contract; BlockState/redstone evidence only\n");
        } else {
            for (TopologyFaceSnapshot face : topology.faces()) {
                if (!face.hasPort()) continue;
                out.append(face.compact());
                EngineeringPortSnapshot observation = face.observation();
                if (observation != null) {
                    out.append(" value=")
                            .append(String.format(Locale.ROOT, "%.2f", observation.value()))
                            .append("/")
                            .append(String.format(Locale.ROOT, "%.2f", observation.maximum()))
                            .append(" q=").append(observation.quality());
                }
                if (!face.detail().isBlank()) out.append(" • ").append(face.detail());
                out.append('\n');
            }
        }
        out.append("MODE: observer-only; no network recompute or device-state mutation");
        return out.substring(0, Math.min(4000, out.length()));
    }

    private static void appendState(StringBuilder out, BlockState state) {
        if (state.getValues().isEmpty()) {
            out.append("STATE: no BlockState properties\n");
            return;
        }
        out.append("STATE: ");
        boolean first = true;
        List<Property<?>> properties = state.getProperties().stream()
                .sorted(Comparator.comparing(Property::getName))
                .toList();
        for (Property<?> property : properties) {
            if (!first) out.append(" • ");
            first = false;
            out.append(property.getName()).append('=').append(state.getValue(property));
        }
        out.append('\n');
    }
}
