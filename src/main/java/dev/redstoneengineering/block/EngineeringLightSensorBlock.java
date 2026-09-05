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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/** Measures local combined brightness with Alpha 1.0.15 metrology diagnostics. */
public class EngineeringLightSensorBlock extends DirectionalRedstoneSensorBlock {
    private static final int SENSOR_PROFILE = 1; // BALANCED

    public EngineeringLightSensorBlock(Properties properties) { super(properties); }

    @Override protected String metrologyChannel() { return "light_sensor"; }

    @Override
    public MapCodec<EngineeringLightSensorBlock> codec() {
        return RedstoneEngineering.ENGINEERING_LIGHT_SENSOR_CODEC.value();
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort(
                        "LIGHT APERTURE",
                        Direction.UP,
                        EngineeringDomain.OPTICAL,
                        PortKind.SENSOR,
                        PortDirection.INPUT,
                        false,
                        "light"
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
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        PortQuality quality = MetrologySupport.portQuality(sensorMeasurement(level, pos));
        if (side == Direction.UP) {
            return Optional.of(new EngineeringPortSnapshot(
                    port.get(), level.getMaxLocalRawBrightness(pos.above()), 0.0, 15.0, quality));
        }
        return Optional.of(EngineeringPortSnapshot.redstone(port.get(), state.getValue(POWER), quality));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, movedByPiston);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        double reference = level.getMaxLocalRawBrightness(pos.above());
        double reading = MetrologySupport.conditionRedstone(level, pos, reference, SENSOR_PROFILE);
        sampleMeasurement(level, pos, reading, reference, false);
        updateSensorOutput(level, pos, state, (int) Math.round(reading), 10);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            player.displayClientMessage(Component.literal(
                    "Local Light Sensor = " + state.getValue(POWER) + "/15"
                            + " | OPTICAL aperture=UP"
                            + " | " + MetrologySupport.compactDiagnostics(sensorMeasurement(level, pos))
                            + " | FRONT REDSTONE OUT=" + frontSide(state).getName()
            ), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
