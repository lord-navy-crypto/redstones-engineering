package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.PneumaticNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Ambient-air compressor. Redstone strength commands outlet pressure rather than acting as a binary switch. */
public class AirCompressorBlock extends Block {
    public AirCompressorBlock(Properties properties) { super(properties); }

    @Override public MapCodec<AirCompressorBlock> codec() {
        return RedstoneEngineering.AIR_COMPRESSOR_CODEC.value();
    }

    public static int commandedPressure(Level level, BlockPos pos) {
        return Math.round(Math.max(0, Math.min(15, level.getBestNeighborSignal(pos))) * 100f / 15f);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (level instanceof ServerLevel server) PneumaticNetwork.recompute(server, pos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean moved) {
        if (level instanceof ServerLevel server) PneumaticNetwork.recompute(server, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            InformationRuntime.clear(level, "pneumatic", pos);
            PneumaticNetwork.recomputeAround(server, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            player.displayClientMessage(Component.literal(
                    "Air compressor command=" + level.getBestNeighborSignal(pos) + "/15 pressure="
                            + commandedPressure(level, pos) + "/100 | intake=ambient air"
            ), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
