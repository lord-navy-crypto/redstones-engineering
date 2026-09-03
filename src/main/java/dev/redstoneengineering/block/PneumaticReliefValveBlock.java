package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.PneumaticNetwork;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/** Safety relief valve. Clamps local/downstream network pressure above a configurable setpoint. */
public class PneumaticReliefValveBlock extends DirectionalDomainBlock {
    public static final IntegerProperty SETPOINT = IntegerProperty.create("setpoint", 1, 4);

    public PneumaticReliefValveBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(SETPOINT, 3));
    }

    @Override public MapCodec<PneumaticReliefValveBlock> codec() {
        return RedstoneEngineering.PNEUMATIC_RELIEF_VALVE_CODEC.value();
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SETPOINT);
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (level instanceof ServerLevel server) PneumaticNetwork.recompute(server, pos);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            int next = state.getValue(SETPOINT) % 4 + 1;
            BlockState newState = state.setValue(SETPOINT, next);
            level.setBlock(pos, newState, Block.UPDATE_CLIENTS);
            if (level instanceof ServerLevel server) PneumaticNetwork.recompute(server, pos);
            int[] diag = RuntimeIntStore.get(level, "pneumatic_relief", pos, 3);
            player.displayClientMessage(Component.literal(
                    "Relief valve setpoint=" + (next * 25) + "/100 ventEvents=" + diag[0] +
                    " lastExcess=" + diag[1] + " totalVentedProxy=" + diag[2]), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
