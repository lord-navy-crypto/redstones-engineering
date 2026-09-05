package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.blockentity.MechatronicsVisualBlockEntity;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.PneumaticNetwork;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.ui.FieldDeviceUi;
import dev.redstoneengineering.visualization.MechatronicsVisualState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * Lumped pneumatic linear actuator.
 * BACK = pneumatic input; FRONT/FACING = 0..15 position-feedback redstone output.
 * Pressure drives a finite-rate 0..15 position state while feedback remains a
 * deliberately directional engineering port rather than an all-side signal source.
 */
public class PneumaticCylinderBlock extends DirectionalDomainBlock implements EntityBlock, EngineeringPortProvider {
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
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MechatronicsVisualBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("PNEUMATIC IN", inputSide(state), EngineeringDomain.PNEUMATIC,
                        PortKind.ACTUATOR, PortDirection.INPUT, false, "pressure"),
                new EngineeringPort("POSITION FEEDBACK", outputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.FEEDBACK, PortDirection.OUTPUT, true, "signal")
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> descriptor = engineeringPort(state, side);
        if (descriptor.isEmpty()) return Optional.empty();
        if (side == inputSide(state)) {
            int pressure = PneumaticNetwork.pressure(level, inputPos(pos, state));
            return Optional.of(new EngineeringPortSnapshot(descriptor.get(), pressure, 0.0, 100.0,
                    pressure > 0 ? PortQuality.VALID : PortQuality.NO_SIGNAL));
        }
        return Optional.of(EngineeringPortSnapshot.redstone(
                descriptor.get(), position(level, pos), PortQuality.VALID));
    }

    /** Renderer-facing immutable projection; never creates or mutates simulation state. */
    public static MechatronicsVisualState visualState(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        if (runtime == null || runtime.length < RUNTIME_SIZE) {
            return MechatronicsVisualState.cylinder(0, 0, 0);
        }
        return MechatronicsVisualState.cylinder(runtime[0], runtime[3], runtime[2]);
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
    protected void neighborChanged(
            BlockState state, Level level, BlockPos pos, net.minecraft.world.level.block.Block neighbor,
            BlockPos neighborPos, boolean moved
    ) {
        if (level instanceof ServerLevel server) server.scheduleTick(pos, this, 1);
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

        MechatronicsVisualBlockEntity.push(level, pos, visualState(level, pos));
        if (runtime[0] != oldPosition) {
            level.updateNeighborsAt(pos, this);
            level.updateNeighborsAt(outputPos(pos, state), this);
        }
        if (runtime[0] != target) level.scheduleTick(pos, this, 2);
    }

    public static int position(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length < RUNTIME_SIZE ? 0 : runtime[0];
    }

    public static int target(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length < RUNTIME_SIZE ? 0 : runtime[1];
    }

    public static int pressure(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length < RUNTIME_SIZE ? 0 : runtime[2];
    }

    public static int travel(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length < RUNTIME_SIZE ? 0 : runtime[5];
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, KEY, pos);
        super.onRemove(state, level, pos, newState, moved);
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
        return position(realLevel, pos);
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
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!player.isShiftKeyDown()) {
                FieldDeviceUi.open(serverPlayer, pos);
                return InteractionResult.CONSUME;
            }
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
