package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.PneumaticNetwork;
import dev.redstoneengineering.visualization.MechatronicsVisualState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Redstone-commanded inline valve. BACK is pneumatic inlet, FRONT outlet, UP is opening command. */
public class PneumaticProportionalValveBlock extends DirectionalDomainBlock {
    public PneumaticProportionalValveBlock(Properties properties) { super(properties); }

    @Override public MapCodec<PneumaticProportionalValveBlock> codec() {
        return RedstoneEngineering.PNEUMATIC_PROPORTIONAL_VALVE_CODEC.value();
    }

    @Override public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction side) {
        return side != null && side.getOpposite() == Direction.UP;
    }

    public static int opening(Level level, BlockPos pos) {
        return Math.max(0, Math.min(15, level.getSignal(pos.above(), Direction.UP)));
    }

    /** Renderer-facing immutable projection; reads command/pressure but never writes simulation state. */
    public static MechatronicsVisualState visualState(Level level, BlockPos pos) {
        return MechatronicsVisualState.valve(
                opening(level, pos),
                PneumaticNetwork.pressure(level, pos)
        );
    }

    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean moved) {
        if (level instanceof ServerLevel server) PneumaticNetwork.recompute(server, pos);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) player.displayClientMessage(Component.literal(
                "Proportional valve opening=" + opening(level, pos) + "/15 pressure=" + PneumaticNetwork.pressure(level, pos) + "/100 | UP=command BACK→FRONT"), true);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
