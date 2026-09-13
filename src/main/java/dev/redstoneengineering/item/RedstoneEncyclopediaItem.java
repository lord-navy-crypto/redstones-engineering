package dev.redstoneengineering.item;

import dev.redstoneengineering.ui.menu.RedstoneEncyclopediaMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Central in-game manual for all RSE blocks. */
public final class RedstoneEncyclopediaItem extends Item {
    public RedstoneEncyclopediaItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal("Redstone Encyclopedia");
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.literal("Right-click to open the RSE field manual").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.literal("Guide + Ports / Config for registered RSE blocks").withStyle(ChatFormatting.AQUA));
        tooltipComponents.add(Component.literal("Entries follow the live RSE block registry").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(
                    new SimpleMenuProvider(
                            (containerId, inventory, ignored) -> new RedstoneEncyclopediaMenu(containerId, inventory),
                            Component.literal("Redstone Encyclopedia")
                    ),
                    ignored -> { }
            );
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
