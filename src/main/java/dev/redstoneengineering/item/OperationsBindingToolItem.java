package dev.redstoneengineering.item;

import dev.redstoneengineering.block.IndustrialBufferBlock;
import dev.redstoneengineering.block.WorkcellControllerBlock;
import dev.redstoneengineering.operations.world.OperationWorkcellStore;
import dev.redstoneengineering.operations.world.OperationWorldResourceResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Explicit operator configuration tool for Industrial Operations world bindings.
 *
 * <p>The tool remembers only deliberately clicked identities. It never discovers nearby
 * resources, schedules work, changes WIP, or fabricates completion/quality evidence.</p>
 */
public final class OperationsBindingToolItem extends Item {
    private static final String TARGET_DIMENSION = "target_dimension";
    private static final String TARGET_POSITION = "target_position";
    private static final String INPUT_BUFFER_ID = "input_buffer_id";
    private static final String INPUT_BUFFER_DIMENSION = "input_buffer_dimension";
    private static final String OUTPUT_BUFFER_ID = "output_buffer_id";
    private static final String OUTPUT_BUFFER_DIMENSION = "output_buffer_dimension";

    public OperationsBindingToolItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public Component getName(ItemStack stack) {
        CompoundTag tag = customTag(stack);
        return Component.literal("Operations Binding Tool " + selectionBadge(tag));
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag
    ) {
        CompoundTag tag = customTag(stack);
        boolean resource = hasResource(tag);
        boolean input = hasInput(tag);
        boolean output = hasOutput(tag);

        tooltipComponents.add(Component.literal("RESOURCE: " + (resource ? resourceText(tag) : "NOT SELECTED")));
        tooltipComponents.add(Component.literal("INPUT: " + (input ? "SELECTED • " + tag.getString(INPUT_BUFFER_DIMENSION) : "NOT SELECTED")));
        tooltipComponents.add(Component.literal("OUTPUT: " + (output ? "SELECTED • " + tag.getString(OUTPUT_BUFFER_DIMENSION) : "NOT SELECTED")));
        tooltipComponents.add(Component.literal("RESOURCE BIND: " + (resource ? "READY" : "WAITING FOR RESOURCE")));
        tooltipComponents.add(Component.literal("BUFFER BIND: " + (input && output ? "READY" : "WAITING FOR INPUT + OUTPUT")));
        tooltipComponents.add(Component.literal("Use buffer = INPUT • Shift+Use buffer = OUTPUT"));
        tooltipComponents.add(Component.literal("Use controller = bind resource • Shift+Use controller = bind buffers"));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return selectionCount(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int selected = selectionCount(stack);
        if (selected <= 0) return 0;
        return Math.max(1, Math.min(13, Math.round(selected * 13.0F / 3.0F)));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return switch (selectionCount(stack)) {
            case 3 -> 0x68D391;
            case 2 -> 0xF6C453;
            default -> 0x9EC8FF;
        };
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (level.isClientSide || player == null || !(level instanceof ServerLevel server)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        BlockPos clickedPos = context.getClickedPos();
        BlockState clickedState = level.getBlockState(clickedPos);
        ItemStack stack = context.getItemInHand();
        boolean shifted = player.isShiftKeyDown();

        if (clickedState.getBlock() instanceof WorkcellControllerBlock) {
            if (shifted) submitBuffers(server, player, stack, clickedPos);
            else submitResource(server, player, stack, clickedPos);
            return InteractionResult.SUCCESS;
        }

        if (clickedState.getBlock() instanceof IndustrialBufferBlock) {
            captureBuffer(level, player, stack, clickedPos, shifted);
            return InteractionResult.SUCCESS;
        }

        if (OperationWorldResourceResolver.resolve(server, clickedPos).isPresent()) {
            captureResource(level, player, stack, clickedPos);
            return InteractionResult.SUCCESS;
        }

        player.displayClientMessage(Component.literal(
                "Binding tool: clicked block exposes no Operations resource/buffer/controller identity. "
                        + statusText(stack)), true);
        return InteractionResult.SUCCESS;
    }

    private static void captureResource(Level level, Player player, ItemStack stack, BlockPos pos) {
        String dimension = level.dimension().location().toString();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString(TARGET_DIMENSION, dimension);
            tag.putLong(TARGET_POSITION, pos.asLong());
        });
        player.displayClientMessage(Component.literal(
                "Binding tool: RESOURCE selected at " + pos.toShortString() + " in " + dimension
                        + " • " + statusText(stack)), true);
    }

    private static void captureBuffer(
            Level level,
            Player player,
            ItemStack stack,
            BlockPos pos,
            boolean output
    ) {
        String dimension = level.dimension().location().toString();
        String bufferId = IndustrialBufferBlock.bufferId(pos);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (output) {
                tag.putString(OUTPUT_BUFFER_ID, bufferId);
                tag.putString(OUTPUT_BUFFER_DIMENSION, dimension);
            } else {
                tag.putString(INPUT_BUFFER_ID, bufferId);
                tag.putString(INPUT_BUFFER_DIMENSION, dimension);
            }
        });
        player.displayClientMessage(Component.literal(
                "Binding tool: " + (output ? "OUTPUT" : "INPUT") + " BUFFER selected at "
                        + pos.toShortString() + " in " + dimension + " • " + statusText(stack)), true);
    }

    private static void submitResource(
            ServerLevel level,
            Player player,
            ItemStack stack,
            BlockPos controllerPos
    ) {
        CompoundTag tag = customTag(stack);
        if (!tag.contains(TARGET_POSITION) || tag.getString(TARGET_DIMENSION).isBlank()) {
            player.displayClientMessage(Component.literal(
                    "Binding tool: select an Operations resource first. " + statusText(stack)), true);
            return;
        }
        String currentDimension = level.dimension().location().toString();
        if (!currentDimension.equals(tag.getString(TARGET_DIMENSION))) {
            player.displayClientMessage(Component.literal(
                    "Binding tool: resource and Workcell Controller must be in the same dimension. "
                            + statusText(stack)), true);
            return;
        }

        BlockPos target = BlockPos.of(tag.getLong(TARGET_POSITION));
        OperationWorkcellStore.Decision decision =
                WorkcellControllerBlock.bindResource(level, controllerPos, target);
        if (decision.changed()) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> {
                data.remove(TARGET_POSITION);
                data.remove(TARGET_DIMENSION);
            });
        }
        player.displayClientMessage(Component.literal(
                "Binding tool: " + decision.reason() + " • " + statusText(stack)), true);
    }

    private static void submitBuffers(
            ServerLevel level,
            Player player,
            ItemStack stack,
            BlockPos controllerPos
    ) {
        CompoundTag tag = customTag(stack);
        String inputId = tag.getString(INPUT_BUFFER_ID);
        String outputId = tag.getString(OUTPUT_BUFFER_ID);
        if (inputId.isBlank() || outputId.isBlank()) {
            player.displayClientMessage(Component.literal(
                    "Binding tool: select both INPUT and OUTPUT Industrial Buffers first. "
                            + statusText(stack)), true);
            return;
        }

        String currentDimension = level.dimension().location().toString();
        if (!currentDimension.equals(tag.getString(INPUT_BUFFER_DIMENSION))
                || !currentDimension.equals(tag.getString(OUTPUT_BUFFER_DIMENSION))) {
            player.displayClientMessage(Component.literal(
                    "Binding tool: both buffers and Workcell Controller must be in the same dimension. "
                            + statusText(stack)), true);
            return;
        }

        OperationWorkcellStore.BufferDecision decision = OperationWorkcellStore.bindBuffers(
                level,
                WorkcellControllerBlock.workcellId(controllerPos),
                inputId,
                outputId
        );
        if (decision.changed()) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> {
                data.remove(INPUT_BUFFER_ID);
                data.remove(INPUT_BUFFER_DIMENSION);
                data.remove(OUTPUT_BUFFER_ID);
                data.remove(OUTPUT_BUFFER_DIMENSION);
            });
        }
        player.displayClientMessage(Component.literal(
                "Binding tool: " + decision.reason() + " • " + statusText(stack)), true);
    }

    private static int selectionCount(ItemStack stack) {
        CompoundTag tag = customTag(stack);
        int selected = 0;
        if (hasResource(tag)) selected++;
        if (hasInput(tag)) selected++;
        if (hasOutput(tag)) selected++;
        return selected;
    }

    private static String selectionBadge(CompoundTag tag) {
        return "[R" + mark(hasResource(tag)) + " I" + mark(hasInput(tag)) + " O" + mark(hasOutput(tag)) + "]";
    }

    private static String statusText(ItemStack stack) {
        CompoundTag tag = customTag(stack);
        return selectionBadge(tag)
                + " resource=" + ready(hasResource(tag))
                + " buffers=" + ready(hasInput(tag) && hasOutput(tag));
    }

    private static String resourceText(CompoundTag tag) {
        BlockPos pos = BlockPos.of(tag.getLong(TARGET_POSITION));
        return pos.toShortString() + " • " + tag.getString(TARGET_DIMENSION);
    }

    private static boolean hasResource(CompoundTag tag) {
        return tag.contains(TARGET_POSITION) && !tag.getString(TARGET_DIMENSION).isBlank();
    }

    private static boolean hasInput(CompoundTag tag) {
        return !tag.getString(INPUT_BUFFER_ID).isBlank() && !tag.getString(INPUT_BUFFER_DIMENSION).isBlank();
    }

    private static boolean hasOutput(CompoundTag tag) {
        return !tag.getString(OUTPUT_BUFFER_ID).isBlank() && !tag.getString(OUTPUT_BUFFER_DIMENSION).isBlank();
    }

    private static String mark(boolean selected) { return selected ? "✓" : "·"; }
    private static String ready(boolean ready) { return ready ? "READY" : "WAIT"; }

    private static CompoundTag customTag(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData == null || customData.isEmpty() ? new CompoundTag() : customData.copyTag();
    }
}
