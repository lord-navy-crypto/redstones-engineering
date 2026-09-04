package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.blockentity.LogicAnalyzerBlockEntity;
import dev.redstoneengineering.instrument.InstrumentNetwork;
import dev.redstoneengineering.ui.menu.LogicAnalyzerMenu;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

public class LogicAnalyzerBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty THRESHOLD = IntegerProperty.create("threshold", 1, 15);

    public LogicAnalyzerBlock(Properties properties) {
        super(properties);
        registerDefaultState(
                stateDefinition.any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(THRESHOLD, 8)
        );
    }

    @Override
    public MapCodec<LogicAnalyzerBlock> codec() {
        return RedstoneEngineering.LOGIC_ANALYZER_CODEC.value();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, THRESHOLD);
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return false;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LogicAnalyzerBlockEntity(pos, state);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide && !state.is(oldState.getBlock())) {
            level.scheduleTick(pos, this, LogicAnalyzerBlockEntity.SAMPLE_PERIOD_TICKS);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        InstrumentNetwork.ProbeSnapshot snapshot = InstrumentNetwork.scan(level, pos);
        int threshold = state.getValue(THRESHOLD);
        int mask = 0;
        int validMask = 0;

        for (int channel = 0; channel < 4; channel++) {
            if (!snapshot.valid(channel)) continue;
            validMask |= 1 << channel;
            if (snapshot.values()[channel] >= threshold) mask |= 1 << channel;
        }

        if (level.getBlockEntity(pos) instanceof LogicAnalyzerBlockEntity analyzer) {
            analyzer.addSample(mask, validMask);
        }

        level.scheduleTick(pos, this, LogicAnalyzerBlockEntity.SAMPLE_PERIOD_TICKS);
    }

    /** Bounded server-side intent used by the Engineering UI and its GameTests. */
    public static boolean applyUiAction(Level level, BlockPos pos, int action) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof LogicAnalyzerBlockEntity analyzer)) return false;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof LogicAnalyzerBlock block)) return false;

        switch (action) {
            case LogicAnalyzerMenu.BUTTON_ARM -> analyzer.arm();
            case LogicAnalyzerMenu.BUTTON_THRESHOLD_DECREASE -> {
                int next = state.getValue(THRESHOLD) <= 1 ? 15 : state.getValue(THRESHOLD) - 1;
                level.setBlock(pos, state.setValue(THRESHOLD, next), Block.UPDATE_CLIENTS);
            }
            case LogicAnalyzerMenu.BUTTON_THRESHOLD_INCREASE -> {
                int next = state.getValue(THRESHOLD) >= 15 ? 1 : state.getValue(THRESHOLD) + 1;
                level.setBlock(pos, state.setValue(THRESHOLD, next), Block.UPDATE_CLIENTS);
            }
            case LogicAnalyzerMenu.BUTTON_TRIGGER_CHANNEL -> analyzer.cycleTriggerChannel();
            case LogicAnalyzerMenu.BUTTON_TRIGGER_EDGE -> analyzer.cycleTriggerEdge();
            case LogicAnalyzerMenu.BUTTON_CURSOR_A -> analyzer.moveCursorA();
            case LogicAnalyzerMenu.BUTTON_CURSOR_B -> analyzer.moveCursorB();
            case LogicAnalyzerMenu.BUTTON_CLEAR -> analyzer.clear();
            default -> { return false; }
        }
        if (action == LogicAnalyzerMenu.BUTTON_THRESHOLD_DECREASE || action == LogicAnalyzerMenu.BUTTON_THRESHOLD_INCREASE) {
            level.scheduleTick(pos, block, 1);
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
                if (level.getBlockEntity(pos) instanceof LogicAnalyzerBlockEntity analyzer) {
                    analyzer.arm();
                    player.displayClientMessage(Component.literal("Logic Analyzer | trigger armed | " + analyzer.triggerStatus()), true);
                }
            } else {
                serverPlayer.openMenu(
                        new SimpleMenuProvider(
                                (containerId, inventory, ignored) -> new LogicAnalyzerMenu(containerId, inventory, pos),
                                Component.translatable("block.redstoneengineering.logic_analyzer")
                        ),
                        data -> data.writeBlockPos(pos)
                );
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
