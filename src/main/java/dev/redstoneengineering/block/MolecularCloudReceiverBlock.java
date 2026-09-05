package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/** Molecular/tracer cloud sensor with a bounded filtered runtime and explicit free-space sensing aperture. */
public class MolecularCloudReceiverBlock extends PassiveDirectionalSignalBlock {
    public static final IntegerProperty SENSITIVITY = IntegerProperty.create("sensitivity", 0, 3);
    private static final int[] GAIN = {6, 9, 12, 16};
    private static final String KEY = "molecular_sensor";

    public MolecularCloudReceiverBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(SENSITIVITY, 2));
    }

    @Override
    public MapCodec<MolecularCloudReceiverBlock> codec() {
        return RedstoneEngineering.MOLECULAR_CLOUD_RECEIVER_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SENSITIVITY);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort(
                        "MOLECULAR FIELD",
                        Direction.UP,
                        EngineeringDomain.GENERIC,
                        PortKind.SENSOR,
                        PortDirection.INPUT,
                        false,
                        "concentration"
                ),
                new EngineeringPort(
                        "REDSTONE READOUT",
                        outputSide(state),
                        EngineeringDomain.REDSTONE,
                        PortKind.SENSOR,
                        PortDirection.OUTPUT,
                        true,
                        "signal"
                )
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        if (side == Direction.UP) {
            return Optional.of(new EngineeringPortSnapshot(
                    port.get(), raw(level, pos, state), 0.0, 15.0, PortQuality.VALID));
        }
        return Optional.of(EngineeringPortSnapshot.redstone(
                port.get(), state.getValue(OUTPUT), PortQuality.VALID));
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction
    ) {
        return direction != null && direction.getOpposite() == outputSide(state);
    }

    public static int raw(Level level, BlockPos pos, BlockState state) {
        var clouds = level.getEntitiesOfClass(AreaEffectCloud.class, new AABB(pos).inflate(8.0));
        double concentration = 0.0;
        for (AreaEffectCloud cloud : clouds) {
            concentration += Math.max(0.0, cloud.getRadius())
                    / (1.0 + cloud.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        }
        return Math.min(15, (int) Math.round(concentration * GAIN[state.getValue(SENSITIVITY)]));
    }

    public static int filtered(Level level, BlockPos pos) {
        return RuntimeIntStore.get(level, KEY, pos, 3)[0];
    }

    @Override
    protected int computeOutput(Level level, BlockPos pos, BlockState state) {
        return filtered(level, pos);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !state.is(oldState.getBlock())) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, KEY, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, 3); // filtered, raw target, peak
        int target = raw(level, pos, state);
        if (runtime[0] < target) runtime[0]++;
        else if (runtime[0] > target) runtime[0]--;
        runtime[1] = target;
        runtime[2] = Math.max(runtime[2], runtime[0]);
        updateOutput(level, pos, state, runtime[0]);
        level.scheduleTick(pos, this, 5);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit
    ) {
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                RuntimeIntStore.remove(level, KEY, pos);
                updateOutput(level, pos, state, 0);
                level.scheduleTick(pos, this, 1);
                player.displayClientMessage(Component.literal("Molecular sensor history reset | runtime cleared"), true);
            } else {
                int sensitivity = (state.getValue(SENSITIVITY) + 1) % 4;
                BlockState next = state.setValue(SENSITIVITY, sensitivity);
                level.setBlock(pos, next, Block.UPDATE_CLIENTS);
                level.scheduleTick(pos, this, 1);
                int[] runtime = RuntimeIntStore.get(level, KEY, pos, 3);
                player.displayClientMessage(Component.literal(
                        "Molecular receiver | UP free-space aperture | sensitivity=" + sensitivity
                                + " | filtered=" + runtime[0]
                                + " | raw=" + raw(level, pos, next)
                                + " | peak=" + runtime[2]
                                + " | FRONT REDSTONE OUT=" + outputSide(next).getName()), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
