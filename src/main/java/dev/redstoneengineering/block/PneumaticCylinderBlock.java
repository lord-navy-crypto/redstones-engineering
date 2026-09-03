package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.PneumaticNetwork;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Lumped pneumatic linear actuator. Pressure drives a finite-rate 0..15 position state. */
public class PneumaticCylinderBlock extends DirectionalDomainBlock {
    private static final String KEY = "pneumatic_cylinder";

    public PneumaticCylinderBlock(Properties properties) { super(properties); }

    @Override public MapCodec<PneumaticCylinderBlock> codec() {
        return RedstoneEngineering.PNEUMATIC_CYLINDER_CODEC.value();
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (level instanceof ServerLevel server) server.scheduleTick(pos, this, 2);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, 8);
        int pressure = PneumaticNetwork.pressure(level, inputPos(pos, state));
        int target = Math.max(0, Math.min(15, (pressure * 15 + 50) / 100));
        int old = runtime[0];
        if (runtime[0] < target) runtime[0]++;
        else if (runtime[0] > target) runtime[0]--;
        runtime[1] = target;
        runtime[2] = pressure;
        runtime[3] = runtime[0] - old;
        runtime[4] = runtime[1] - runtime[0];
        runtime[5] += Math.abs(runtime[3]);
        if (runtime[3] == 0 && runtime[4] != 0) runtime[6]++;
        else if (runtime[4] == 0) runtime[6] = 0;
        runtime[7] = Math.max(runtime[7], Math.abs(runtime[3]));
        level.updateNeighborsAt(pos, this);
        level.scheduleTick(pos, this, 2);
    }

    @Override public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        if (!(level instanceof Level realLevel)) return 0;
        return RuntimeIntStore.get(realLevel, KEY, pos, 8)[0];
    }

    @Override public boolean isSignalSource(BlockState state) { return true; }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            int[] r = RuntimeIntStore.get(level, KEY, pos, 8);
            player.displayClientMessage(Component.literal(
                    "Pneumatic cylinder pos=" + r[0] + " target=" + r[1] + " pressure=" + r[2] +
                    " velocity=" + r[3] + " error=" + r[4] + " travel=" + r[5] + " stallTicks=" + r[6]), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
