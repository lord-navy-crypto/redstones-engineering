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
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

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
        return Component.literal("Operations Binding Tool");
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

        player.displayClientMessage(Component.literal("Binding tool: clicked block exposes no Operations resource/buffer/controller identity."), true);
        return InteractionResult.SUCCESS;
    }

    private static void captureResource(Level level, Player player, ItemStack stack, BlockPos pos) {
        String dimension = level.dimension().location().toString();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString(TARGET_DIMENSION, dimension);
            tag.putLong(TARGET_POSITION, pos.asLong());
        });
        player.displayClientMessage(Component.literal(
                "Binding tool: RESOURCE selected at " + pos.toShortString() + " in " + dimension), true);
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
                        + pos.toShortString() + " in " + dimension), true);
    }

    private static void submitResource(
            ServerLevel level,
            Player player,
            ItemStack stack,
            BlockPos controllerPos
    ) {
        CompoundTag tag = customTag(stack);
        if (!tag.contains(TARGET_POSITION) || tag.getString(TARGET_DIMENSION).isBlank()) {
            player.displayClientMessage(Component.literal("Binding tool: select an Operations resource first."), true);
            return;
        }
        String currentDimension = level.dimension().location().toString();
        if (!currentDimension.equals(tag.getString(TARGET_DIMENSION))) {
            player.displayClientMessage(Component.literal("Binding tool: resource and Workcell Controller must be in the same dimension."), true);
            return;
        }

        BlockPos target = BlockPos.of(tag.getLong(TARGET_POSITION));
        OperationWorkcellStore.Decision decision =
                WorkcellControllerBlock.bindResource(level, controllerPos, target);
        player.displayClientMessage(Component.literal("Binding tool: " + decision.reason()), true);
        if (decision.changed()) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> {
                data.remove(TARGET_POSITION);
                data.remove(TARGET_DIMENSION);
            });
        }
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
            player.displayClientMessage(Component.literal("Binding tool: select both INPUT and OUTPUT Industrial Buffers first."), true);
            return;
        }

        String currentDimension = level.dimension().location().toString();
        if (!currentDimension.equals(tag.getString(INPUT_BUFFER_DIMENSION))
                || !currentDimension.equals(tag.getString(OUTPUT_BUFFER_DIMENSION))) {
            player.displayClientMessage(Component.literal("Binding tool: both buffers and Workcell Controller must be in the same dimension."), true);
            return;
        }

        OperationWorkcellStore.BufferDecision decision = OperationWorkcellStore.bindBuffers(
                level,
                WorkcellControllerBlock.workcellId(controllerPos),
                inputId,
                outputId
        );
        player.displayClientMessage(Component.literal("Binding tool: " + decision.reason()), true);
        if (decision.changed()) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> {
                data.remove(INPUT_BUFFER_ID);
                data.remove(INPUT_BUFFER_DIMENSION);
                data.remove(OUTPUT_BUFFER_ID);
                data.remove(OUTPUT_BUFFER_DIMENSION);
            });
        }
    }

    private static CompoundTag customTag(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData == null || customData.isEmpty() ? new CompoundTag() : customData.copyTag();
    }
}
