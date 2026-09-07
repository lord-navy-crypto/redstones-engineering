package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.metrology.MetrologySupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/** Non-contact occupancy/count sensor with an explicit free-space aperture and FRONT redstone readout. */
public class EntityDensitySensorBlock extends DirectionalRedstoneSensorBlock {
    private static final int SENSOR_PROFILE = 1; // BALANCED
    private static final int HORIZONTAL_RADIUS = 4;

    public record DensitySample(int physicalCount, boolean complete) {
        public PortQuality quality() {
            if (!complete) return PortQuality.STALE;
            return physicalCount > 15 ? PortQuality.SATURATED : PortQuality.VALID;
        }
    }

    public EntityDensitySensorBlock(Properties properties) {
        super(properties);
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
            DensitySample sample = densitySample(level, pos);
            return Optional.of(new EngineeringPortSnapshot(
                    port.get(), Math.min(15, Math.max(0, sample.physicalCount())), 0.0, 15.0,
                    sample.quality()));
        }
        return Optional.of(EngineeringPortSnapshot.redstone(
                port.get(), state.getValue(POWER), MetrologySupport.portQuality(sensorMeasurement(level, pos))));
    }

    /**
     * Count only when every chunk touched by the free-space aperture is available.
     * A partial entity query would be a plausible-looking underestimate, so coverage
     * failure is surfaced separately instead of manufacturing a low count.
     */
    public static DensitySample densitySample(Level level, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -HORIZONTAL_RADIUS; dx <= HORIZONTAL_RADIUS; dx++) {
            for (int dz = -HORIZONTAL_RADIUS; dz <= HORIZONTAL_RADIUS; dz++) {
                cursor.set(pos.getX() + dx, pos.getY(), pos.getZ() + dz);
                if (!level.hasChunkAt(cursor)) return new DensitySample(0, false);
            }
        }
        int count = level.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(pos).inflate(4.0, 2.0, 4.0)
        ).size();
        return new DensitySample(count, true);
    }

    public static int physicalCount(Level level, BlockPos pos) {
        return densitySample(level, pos).physicalCount();
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        DensitySample sample = densitySample(level, pos);
        if (!sample.complete()) {
            // Keep the last trustworthy output and metrology sample. It will age
            // naturally to STALE instead of being replaced by a partial count.
            level.scheduleTick(pos, this, 10);
            return;
        }
        int physicalCount = sample.physicalCount();
        boolean saturated = physicalCount > 15;
        double reference = Math.min(15, physicalCount);
        double reading = MetrologySupport.conditionRedstone(level, pos, reference, SENSOR_PROFILE);
        sampleMeasurement(level, pos, reading, reference, saturated);
        updateSensorOutput(level, pos, state, (int) Math.round(reading), 10);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit
    ) {
        if (!level.isClientSide) {
            DensitySample sample = densitySample(level, pos);
            String count = sample.complete() ? Integer.toString(sample.physicalCount()) : "UNKNOWN";
            player.displayClientMessage(Component.literal(
                    "Entity Density Sensor | free-space count=" + count
                            + " | coverage=" + (sample.complete() ? "COMPLETE" : "INCOMPLETE")
                            + " | Reading=" + state.getValue(POWER) + "/15"
                            + " | " + MetrologySupport.compactDiagnostics(sensorMeasurement(level, pos))
                            + " | FRONT OUT=" + frontSide(state).getName()
            ), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
