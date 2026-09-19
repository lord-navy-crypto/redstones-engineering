package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.metrology.MetrologyStore;
import dev.redstoneengineering.metrology.MetrologySupport;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.physics.SensorModel;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/**
 * Non-contact occupancy sensor with selectable aperture and acquisition profile.
 *
 * Coverage must be complete before a count is trusted. The output remains a bounded 0..15
 * occupancy signal while aperture radius and measurement dynamics are explicit operator choices.
 */
public class EntityDensitySensorBlock extends DirectionalRedstoneSensorBlock {
    public static final IntegerProperty PROFILE = IntegerProperty.create("profile", 0, 3);
    public static final IntegerProperty APERTURE_MODE = IntegerProperty.create("aperture_mode", 0, 2);

    private static final String RUNTIME_KEY = "entity_density_sensor_profile";
    private static final int PENDING_READING_100 = 0;
    private static final int PENDING_REFERENCE_100 = 1;
    private static final int INITIALIZED = 2;
    private static final int RUNTIME_SIZE = 3;

    public record DensitySample(int physicalCount, int radius, boolean complete) {
        public PortQuality quality() {
            if (!complete) return PortQuality.STALE;
            return physicalCount > 15 ? PortQuality.SATURATED : PortQuality.VALID;
        }
    }

    public EntityDensitySensorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(PROFILE, 1)
                .setValue(APERTURE_MODE, 1));
    }

    @Override
    protected String metrologyChannel() {
        return "entity_density";
    }

    @Override
    public MapCodec<EntityDensitySensorBlock> codec() {
        return RedstoneEngineering.ENTITY_DENSITY_SENSOR_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PROFILE, APERTURE_MODE);
    }

    public static int radiusForMode(int mode) {
        return switch (Math.max(0, Math.min(2, mode))) {
            case 0 -> 2;
            case 1 -> 4;
            default -> 6;
        };
    }

    public static int configuredRadius(BlockState state) {
        return radiusForMode(state.getValue(APERTURE_MODE));
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort(
                        "OCCUPANCY FIELD",
                        Direction.UP,
                        EngineeringDomain.GENERIC,
                        PortKind.SENSOR,
                        PortDirection.INPUT,
                        false,
                        "entities"
                ),
                new EngineeringPort(
                        "SENSOR OUT",
                        frontSide(state),
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
            DensitySample sample = densitySample(level, pos, state);
            return Optional.of(new EngineeringPortSnapshot(
                    port.get(),
                    Math.min(15, Math.max(0, sample.physicalCount())),
                    0.0,
                    15.0,
                    sample.quality()));
        }
        return Optional.of(EngineeringPortSnapshot.redstone(
                port.get(), state.getValue(POWER), MetrologySupport.portQuality(sensorMeasurement(level, pos))));
    }

    /**
     * Count only when every chunk touched by the configured free-space aperture is available.
     * A partial query would create a plausible-looking underestimate, so incomplete coverage
     * remains explicit STALE evidence.
     */
    public static DensitySample densitySample(Level level, BlockPos pos, BlockState state) {
        int radius = configuredRadius(state);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                cursor.set(pos.getX() + dx, pos.getY(), pos.getZ() + dz);
                if (!level.hasChunkAt(cursor)) return new DensitySample(0, radius, false);
            }
        }

        int count = level.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(pos).inflate(radius, 2.0, radius)
        ).size();
        return new DensitySample(count, radius, true);
    }

    public static int physicalCount(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof EntityDensitySensorBlock
                ? densitySample(level, pos, state).physicalCount()
                : 0;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int profile = state.getValue(PROFILE);
        DensitySample sample = densitySample(level, pos, state);
        if (!sample.complete()) {
            // Retain the last trustworthy output while aperture coverage is incomplete.
            level.scheduleTick(pos, this, SensorModel.samplePeriod(profile));
            return;
        }

        int physicalCount = sample.physicalCount();
        boolean saturated = physicalCount > 15;
        double reference = Math.min(15, physicalCount);
        double conditioned = MetrologySupport.conditionRedstone(level, pos, reference, profile);
        int[] runtime = RuntimeIntStore.get(level, RUNTIME_KEY, pos, RUNTIME_SIZE);

        double emittedReading = conditioned;
        double emittedReference = reference;
        if (SensorModel.latencySamples(profile) > 0 && runtime[INITIALIZED] != 0) {
            emittedReading = runtime[PENDING_READING_100] / 100.0;
            emittedReference = runtime[PENDING_REFERENCE_100] / 100.0;
        }

        runtime[PENDING_READING_100] = (int) Math.round(conditioned * 100.0);
        runtime[PENDING_REFERENCE_100] = (int) Math.round(reference * 100.0);
        runtime[INITIALIZED] = 1;

        sampleMeasurement(level, pos, emittedReading, emittedReference, saturated);
        updateSensorOutput(
                level,
                pos,
                state,
                (int) Math.round(emittedReading),
                SensorModel.samplePeriod(profile)
        );
    }

    public static String profileName(int profile) { return SensorModel.profileName(profile); }
    public static int profileSamplePeriod(int profile) { return SensorModel.samplePeriod(profile); }
    public static int profileNoiseAmplitude(int profile) { return SensorModel.noiseAmplitude(profile); }
    public static int profileLatencySamples(int profile) { return SensorModel.latencySamples(profile); }

    public static boolean adjustProfile(Level level, BlockPos pos, int delta) {
        if (!(level instanceof ServerLevel server)) return false;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof EntityDensitySensorBlock sensor)) return false;
        int next = Math.floorMod(state.getValue(PROFILE) + delta, 4);
        BlockState updated = state.setValue(PROFILE, next);
        level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
        invalidateAcquisition(level, pos, sensor);
        server.scheduleTick(pos, sensor, 1);
        return true;
    }

    public static boolean adjustAperture(Level level, BlockPos pos, int delta) {
        if (!(level instanceof ServerLevel server)) return false;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof EntityDensitySensorBlock sensor)) return false;
        int next = Math.floorMod(state.getValue(APERTURE_MODE) + delta, 3);
        BlockState updated = state.setValue(APERTURE_MODE, next);
        level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
        invalidateAcquisition(level, pos, sensor);
        server.scheduleTick(pos, sensor, 1);
        return true;
    }

    private static void invalidateAcquisition(Level level, BlockPos pos, EntityDensitySensorBlock sensor) {
        RuntimeIntStore.remove(level, RUNTIME_KEY, pos);
        MetrologyStore.remove(level, sensor.metrologyChannel(), pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, RUNTIME_KEY, pos);
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                DensitySample sample = densitySample(level, pos, state);
                int profile = state.getValue(PROFILE);
                String count = sample.complete() ? Integer.toString(sample.physicalCount()) : "UNKNOWN";
                player.displayClientMessage(Component.literal(
                        "Entity Density Sensor | count=" + count
                                + " | radius=" + sample.radius() + " blocks"
                                + " | profile=" + SensorModel.profileName(profile)
                                + " sample=" + SensorModel.samplePeriod(profile) + "t"
                                + " noise=±" + SensorModel.noiseAmplitude(profile) + "/100"
                                + " latency=" + SensorModel.latencySamples(profile) + " sample"
                                + " | coverage=" + (sample.complete() ? "COMPLETE" : "INCOMPLETE")
                                + " | Reading=" + state.getValue(POWER) + "/15"
                                + " | " + MetrologySupport.compactDiagnostics(sensorMeasurement(level, pos))
                                + " | FRONT OUT=" + frontSide(state).getName()
                ), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
