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

/** Molecular/tracer cloud sensor with bounded filtering and explicit free-space coverage evidence. */
public class MolecularCloudReceiverBlock extends PassiveDirectionalSignalBlock {
    public static final IntegerProperty SENSITIVITY = IntegerProperty.create("sensitivity", 0, 3);
    private static final int[] GAIN = {6, 9, 12, 16};
    private static final String KEY = "molecular_sensor";
    private static final int RUNTIME_SIZE = 4; // filtered, raw target, peak, initialized
    private static final double APERTURE_RADIUS = 8.0;

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

    public record CloudSample(int value, boolean complete) {}

    private static boolean apertureLoaded(Level level, BlockPos pos) {
        int minX = (int) Math.floor(pos.getX() - APERTURE_RADIUS);
        int maxX = (int) Math.floor(pos.getX() + APERTURE_RADIUS);
        int minZ = (int) Math.floor(pos.getZ() - APERTURE_RADIUS);
        int maxZ = (int) Math.floor(pos.getZ() + APERTURE_RADIUS);
        int y = pos.getY();
        return level.hasChunkAt(new BlockPos(minX, y, minZ))
                && level.hasChunkAt(new BlockPos(minX, y, maxZ))
                && level.hasChunkAt(new BlockPos(maxX, y, minZ))
                && level.hasChunkAt(new BlockPos(maxX, y, maxZ));
    }

    /** Live free-space sample. Complete empty coverage is a legitimate measured zero. */
    public static CloudSample sample(Level level, BlockPos pos, BlockState state) {
        if (!apertureLoaded(level, pos)) return new CloudSample(0, false);
        var clouds = level.getEntitiesOfClass(AreaEffectCloud.class, new AABB(pos).inflate(APERTURE_RADIUS));
        double concentration = 0.0;
        for (AreaEffectCloud cloud : clouds) {
            concentration += Math.max(0.0, cloud.getRadius())
                    / (1.0 + cloud.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        }
        int value = Math.min(15, (int) Math.round(concentration * GAIN[state.getValue(SENSITIVITY)]));
        return new CloudSample(value, true);
    }

    public static int raw(Level level, BlockPos pos, BlockState state) {
        return sample(level, pos, state).value();
    }

    /** Observer-only retained reading; inspection never allocates sensor runtime. */
    public static int filtered(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length < 1 ? 0 : runtime[0];
    }

    public static int peak(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length < 3 ? 0 : runtime[2];
    }

    public static boolean sampled(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime != null && runtime.length >= RUNTIME_SIZE && runtime[3] != 0;
    }

    private static PortQuality outputQuality(Level level, BlockPos pos, BlockState state) {
        if (!sampled(level, pos)) return PortQuality.STALE;
        return sample(level, pos, state).complete() ? PortQuality.VALID : PortQuality.STALE;
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        CloudSample live = sample(level, pos, state);
        if (side == Direction.UP) {
            return Optional.of(new EngineeringPortSnapshot(
                    port.get(), live.value(), 0.0, 15.0,
                    live.complete() ? PortQuality.VALID : PortQuality.STALE));
        }
        return Optional.of(EngineeringPortSnapshot.redstone(
                port.get(), state.getValue(OUTPUT), outputQuality(level, pos, state)));
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction
    ) {
        return direction != null && direction.getOpposite() == outputSide(state);
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
        CloudSample live = sample(level, pos, state);
        if (live.complete()) {
            int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
            int target = live.value();
            if (runtime[0] < target) runtime[0]++;
            else if (runtime[0] > target) runtime[0]--;
            runtime[1] = target;
            runtime[2] = Math.max(runtime[2], runtime[0]);
            runtime[3] = 1;
            updateOutput(level, pos, state, runtime[0]);
        }
        // Incomplete coverage retains the last trustworthy filtered value and marks it STALE.
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
                CloudSample live = sample(level, pos, next);
                player.displayClientMessage(Component.literal(
                        "Molecular receiver | UP free-space aperture | sensitivity=" + sensitivity
                                + " | filtered=" + filtered(level, pos)
                                + " | raw=" + live.value()
                                + " | coverage=" + (live.complete() ? "COMPLETE" : "STALE")
                                + " | peak=" + peak(level, pos)
                                + " | FRONT REDSTONE OUT=" + outputSide(next).getName()), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
