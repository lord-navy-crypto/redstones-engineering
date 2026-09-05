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
            int physical = physicalCount(level, pos);
            return Optional.of(new EngineeringPortSnapshot(
                    port.get(), Math.min(15, physical), 0.0, 15.0,
                    physical > 15 ? PortQuality.SATURATED : PortQuality.VALID));
        }
        return Optional.of(EngineeringPortSnapshot.redstone(
                port.get(), state.getValue(POWER), MetrologySupport.portQuality(sensorMeasurement(level, pos))));
    }

    public static int physicalCount(Level level, BlockPos pos) {
        return level.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(pos).inflate(4.0, 2.0, 4.0)
        ).size();
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int physicalCount = physicalCount(level, pos);
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
            player.displayClientMessage(Component.literal(
                    "Entity Density Sensor | free-space count=" + physicalCount(level, pos)
                            + " | Reading=" + state.getValue(POWER) + "/15"
                            + " | " + MetrologySupport.compactDiagnostics(sensorMeasurement(level, pos))
                            + " | FRONT OUT=" + frontSide(state).getName()
            ), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
