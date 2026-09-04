package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.blockentity.OscilloscopeBlockEntity;
import dev.redstoneengineering.instrument.InstrumentNetwork;
import dev.redstoneengineering.ui.menu.OscilloscopeMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

public class OscilloscopeBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public OscilloscopeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public MapCodec<OscilloscopeBlock> codec() {
        return RedstoneEngineering.OSCILLOSCOPE_CODEC.value();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return false;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OscilloscopeBlockEntity(pos, state);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide && !state.is(oldState.getBlock())) {
            level.scheduleTick(pos, this, OscilloscopeBlockEntity.SAMPLE_PERIOD_TICKS);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        InstrumentNetwork.ProbeSnapshot snapshot = InstrumentNetwork.scan(level, pos);
        if (level.getBlockEntity(pos) instanceof OscilloscopeBlockEntity scope) {
            scope.addSample(snapshot.valueOr(0, -1), snapshot.valueOr(1, -1));
        }
        level.scheduleTick(pos, this, OscilloscopeBlockEntity.SAMPLE_PERIOD_TICKS);
    }

    /** Bounded server-side intent used by the Engineering UI and its GameTests. */
    public static boolean applyUiAction(Level level, BlockPos pos, int action) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof OscilloscopeBlockEntity scope)) return false;
        switch (action) {
            case OscilloscopeMenu.BUTTON_ARM -> scope.arm();
            case OscilloscopeMenu.BUTTON_TRIGGER_MODE -> scope.cycleTriggerMode();
            case OscilloscopeMenu.BUTTON_TRIGGER_CHANNEL -> scope.cycleTriggerChannel();
            case OscilloscopeMenu.BUTTON_TRIGGER_LEVEL -> scope.cycleTriggerLevel();
            case OscilloscopeMenu.BUTTON_CURSOR_A -> scope.moveCursorA();
            case OscilloscopeMenu.BUTTON_CURSOR_B -> scope.moveCursorB();
            case OscilloscopeMenu.BUTTON_CLEAR -> scope.clear();
            default -> { return false; }
        }
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                if (level.getBlockEntity(pos) instanceof OscilloscopeBlockEntity scope) {
                    scope.arm();
                    player.displayClientMessage(Component.literal("Oscilloscope | trigger armed | " + scope.triggerStatus()), true);
                }
            } else {
                serverPlayer.openMenu(
                        new SimpleMenuProvider(
                                (containerId, inventory, ignored) -> new OscilloscopeMenu(containerId, inventory, pos),
                                Component.translatable("block.redstoneengineering.oscilloscope")
                        ),
                        data -> data.writeBlockPos(pos)
                );
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
