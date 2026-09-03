package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.blockentity.OscilloscopeBlockEntity;
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
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

public class OscilloscopeBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING =
            BlockStateProperties.HORIZONTAL_FACING;

    public OscilloscopeBlock(Properties properties) {
        super(properties);
        registerDefaultState(
                stateDefinition.any().setValue(FACING, Direction.NORTH)
        );
    }

    @Override
    public MapCodec<OscilloscopeBlock> codec() {
        return RedstoneEngineering.OSCILLOSCOPE_CODEC.value();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(
                FACING,
                context.getHorizontalDirection().getOpposite()
        );
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder
    ) {
        builder.add(FACING);
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
        return new OscilloscopeBlockEntity(pos, state);
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
            level.scheduleTick(pos, this, 2);
        }
    }

    @Override
    protected void tick(
            BlockState state,
            ServerLevel level,
            BlockPos pos,
            RandomSource random
    ) {
        InstrumentNetwork.ProbeSnapshot snapshot =
                InstrumentNetwork.scan(level, pos);

        int a = snapshot.valid(0) ? snapshot.values()[0] : -1;
        int b = snapshot.valid(1) ? snapshot.values()[1] : -1;

        if (level.getBlockEntity(pos) instanceof OscilloscopeBlockEntity scope) {
            scope.addSample(a, b);
        }

        level.scheduleTick(pos, this, 2);
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
                && level.getBlockEntity(pos)
                instanceof OscilloscopeBlockEntity scope) {

            if (player.isShiftKeyDown()) {
                scope.arm();
                player.displayClientMessage(Component.literal("Oscilloscope | trigger armed | "+scope.triggerStatus()), true);
            } else if (hitResult.getDirection() == Direction.UP) {
                scope.cycleTriggerMode();
                player.displayClientMessage(Component.literal("Oscilloscope | trigger mode → "+scope.triggerStatus()), true);
            } else if (hitResult.getDirection() == state.getValue(FACING).getClockWise()) {
                scope.cycleTriggerLevel();
                player.displayClientMessage(Component.literal("Oscilloscope | trigger level/channel → "+scope.triggerStatus()), true);
            } else if (hitResult.getDirection() == state.getValue(FACING).getCounterClockWise()) {
                scope.cycleTriggerChannel();
                player.displayClientMessage(Component.literal("Oscilloscope | trigger source → "+scope.triggerStatus()), true);
            } else if (hitResult.getDirection() == Direction.DOWN) {
                scope.moveCursorA();
                player.displayClientMessage(Component.literal("Oscilloscope | cursor A moved | Δ="+scope.cursorDeltaSamples()+" samples"), true);
            } else if (hitResult.getDirection() == state.getValue(FACING)) {
                scope.moveCursorB();
                player.displayClientMessage(Component.literal("Oscilloscope | cursor B moved | Δ="+scope.cursorDeltaSamples()+" samples"), true);
            } else {
                InstrumentNetwork.ProbeSnapshot snapshot = InstrumentNetwork.scan(level, pos);
                player.displayClientMessage(Component.literal(
                    "Scope | "+scope.triggerStatus()+" | A="+snapshot.status(0)+" ["+scope.waveform(0)+"] min/max/p2p="+scope.minimum(0)+"/"+scope.maximum(0)+"/"+scope.peakToPeak(0)+" period≈"+scope.estimatedPeriodSamples(0)+" samples"
                    +" | B="+snapshot.status(1)+" ["+scope.waveform(1)+"] min/max/p2p="+scope.minimum(1)+"/"+scope.maximum(1)+"/"+scope.peakToPeak(1)+" period≈"+scope.estimatedPeriodSamples(1)+" samples"
                    +" | cursors Δ="+scope.cursorDeltaSamples()+" samples"), true);
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
