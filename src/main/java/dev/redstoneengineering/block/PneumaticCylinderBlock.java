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

import javax.annotation.Nullable;

/**
 * Lumped pneumatic linear actuator.
 * BACK = pneumatic input; FRONT/FACING = 0..15 position-feedback redstone output.
 * Pressure drives a finite-rate 0..15 position state while feedback remains a
 * deliberately directional engineering port rather than an all-side signal source.
 */
public class PneumaticCylinderBlock extends DirectionalDomainBlock {
    private static final String KEY = "pneumatic_cylinder";
    private static final int RUNTIME_SIZE = 11;

    public PneumaticCylinderBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<PneumaticCylinderBlock> codec() {
        return RedstoneEngineering.PNEUMATIC_CYLINDER_CODEC.value();
    }

    @Override
    protected void onPlace(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState oldState,
            boolean moved
    ) {
        super.onPlace(state, level, pos, oldState, moved);
        if (level instanceof ServerLevel server) server.scheduleTick(pos, this, 2);
    }

    @Override
    protected void tick(
            BlockState state,
            ServerLevel level,
            BlockPos pos,
            RandomSource random
    ) {
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        int pressure = PneumaticNetwork.pressure(level, inputPos(pos, state));
        int target = Math.max(0, Math.min(15, (pressure * 15 + 50) / 100));
        int oldPosition = runtime[0];
        int oldVelocity = runtime[3];

        if (runtime[0] < target) runtime[0]++;
        else if (runtime[0] > target) runtime[0]--;

        runtime[1] = target;
        runtime[2] = pressure;
        runtime[3] = runtime[0] - oldPosition;
        runtime[4] = runtime[1] - runtime[0];
        runtime[5] += Math.abs(runtime[3]);

        if (runtime[3] == 0 && runtime[4] != 0) runtime[6]++;
        else if (runtime[4] == 0) runtime[6] = 0;

        runtime[7] = Math.max(runtime[7], Math.abs(runtime[3]));
        runtime[8] = Math.max(runtime[8], pressure); // peak pressure
        if (oldVelocity != 0 && runtime[3] != 0 && Integer.signum(oldVelocity) != Integer.signum(runtime[3])) {
            runtime[9]++; // motion reversals
        }
        runtime[10]++; // actuator samples

        if (runtime[0] != oldPosition) {
            level.updateNeighborsAt(pos, this);
            level.updateNeighborsAt(outputPos(pos, state), this);
        }
        level.scheduleTick(pos, this, 2);
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            @Nullable Direction direction
    ) {
        return direction != null
                && direction.getOpposite() == outputSide(state);
    }

    @Override
    public int getSignal(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            Direction side
    ) {
        if (!(level instanceof Level realLevel)) return 0;
        if (side != outputSide(state).getOpposite()) return 0;
        return RuntimeIntStore.get(realLevel, KEY, pos, RUNTIME_SIZE)[0];
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {
        if (!level.isClientSide) {
            int[] r = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
            player.displayClientMessage(Component.literal(
                    "Pneumatic cylinder"
                            + " | pressure=" + r[2] + "/100"
                            + " peak=" + r[8]
                            + " | pos=" + r[0] + "/15"
                            + " target=" + r[1]
                            + " velocity=" + r[3]
                            + " error=" + r[4]
                            + " travel=" + r[5]
                            + " stallTicks=" + r[6]
                            + " reversals=" + r[9]
                            + " samples=" + r[10]
                            + " | pneumatic IN=" + inputSide(state).getName()
                            + " feedback OUT=" + outputSide(state).getName() + ":" + r[0]
            ), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
