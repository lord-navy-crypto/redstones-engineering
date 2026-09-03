package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.blockentity.LogicAnalyzerBlockEntity;
import dev.redstoneengineering.instrument.InstrumentNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
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
        return defaultBlockState().setValue(
                FACING,
                context.getHorizontalDirection().getOpposite()
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, THRESHOLD);
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            @Nullable Direction direction
    ) {
        return false;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LogicAnalyzerBlockEntity(pos, state);
    }

    @Override
    protected void onPlace(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState oldState,
            boolean movedByPiston
    ) {
        if (!level.isClientSide && !state.is(oldState.getBlock())) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void tick(
            BlockState state,
            ServerLevel level,
            BlockPos pos,
            RandomSource random
    ) {
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

        level.scheduleTick(pos, this, 1);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof LogicAnalyzerBlockEntity analyzer) {

            if (player.isShiftKeyDown()) {
                analyzer.arm();
                player.displayClientMessage(
                        Component.literal("Logic Analyzer | trigger armed | " + analyzer.triggerStatus()),
                        true
                );
            } else if (hitResult.getDirection() == Direction.UP) {
                int nextThreshold = state.getValue(THRESHOLD) >= 15
                        ? 1
                        : state.getValue(THRESHOLD) + 1;
                level.setBlock(pos, state.setValue(THRESHOLD, nextThreshold), Block.UPDATE_CLIENTS);
                player.displayClientMessage(
                        Component.literal("Logic Analyzer | threshold → " + nextThreshold),
                        true
                );
            } else if (hitResult.getDirection() == state.getValue(FACING).getClockWise()) {
                analyzer.cycleTriggerChannel();
                player.displayClientMessage(
                        Component.literal("Logic Analyzer | trigger source → " + analyzer.triggerStatus()),
                        true
                );
            } else if (hitResult.getDirection() == state.getValue(FACING).getCounterClockWise()) {
                analyzer.cycleTriggerEdge();
                player.displayClientMessage(
                        Component.literal("Logic Analyzer | trigger edge → " + analyzer.triggerStatus()),
                        true
                );
            } else if (hitResult.getDirection() == Direction.DOWN) {
                analyzer.moveCursorA();
                player.displayClientMessage(
                        Component.literal("Logic Analyzer | cursor A moved | Δ=" + analyzer.cursorDeltaSamples() + " samples"),
                        true
                );
            } else if (hitResult.getDirection() == state.getValue(FACING)) {
                analyzer.moveCursorB();
                player.displayClientMessage(
                        Component.literal("Logic Analyzer | cursor B moved | Δ=" + analyzer.cursorDeltaSamples() + " samples"),
                        true
                );
            } else {
                InstrumentNetwork.ProbeSnapshot snapshot = InstrumentNetwork.scan(level, pos);
                StringBuilder text = new StringBuilder(
                        "Logic | threshold=" + state.getValue(THRESHOLD)
                                + " | " + analyzer.triggerStatus()
                                + " | cursors Δ=" + analyzer.cursorDeltaSamples() + " samples"
                                + " | " + snapshot.networkStatus()
                );
                for (int channel = 0; channel < 4; channel++) {
                    text.append(" | ")
                            .append(SignalProbeBlock.channelName(channel))
                            .append("=")
                            .append(snapshot.status(channel))
                            .append(":")
                            .append(analyzer.waveform(channel))
                            .append(" ↑")
                            .append(analyzer.rising(channel))
                            .append(" ↓")
                            .append(analyzer.falling(channel))
                            .append(" duty=")
                            .append(analyzer.dutyPercent(channel))
                            .append("%");
                }
                player.displayClientMessage(Component.literal(text.toString()), true);
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
