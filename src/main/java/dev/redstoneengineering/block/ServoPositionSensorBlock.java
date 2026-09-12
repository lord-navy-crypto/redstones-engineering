package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.metrology.MeasurementSnapshot;
import dev.redstoneengineering.metrology.MetrologyStore;
import dev.redstoneengineering.metrology.MetrologySupport;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/** Position feedback sensor with metrology plus velocity/error/trajectory diagnostics. */
public class ServoPositionSensorBlock extends PassiveDirectionalSignalBlock {
    private static final String CHANNEL = "servo_position_sensor";
    private static final int SENSOR_PROFILE = 2; // PRECISION

    public ServoPositionSensorBlock(Properties properties) { super(properties); }
    @Override public MapCodec<ServoPositionSensorBlock> codec() { return RedstoneEngineering.SERVO_POSITION_SENSOR_CODEC.value(); }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("SERVO POSITION IN", inputSide(state), EngineeringDomain.MECHATRONIC_POSITION,
                        PortKind.FEEDBACK, PortDirection.INPUT, false, "position"),
                new EngineeringPort("REDSTONE FEEDBACK OUT", outputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.SENSOR, PortDirection.OUTPUT, true, "signal")
        );
    }

    /** Mechanical source quality follows the configured RX face, not an assumed TX-opposite face. */
    public static PortQuality sourceQuality(Level level, BlockPos pos, BlockState sensorState) {
        Direction sensorInput = seriesInputSide(sensorState);
        BlockPos servoPos = pos.relative(sensorInput);
        if (!level.hasChunkAt(servoPos)) return PortQuality.STALE;
        BlockState servoState = level.getBlockState(servoPos);
        if (!(servoState.getBlock() instanceof ServoActuatorBlock)) return PortQuality.NO_SIGNAL;
        Direction servoFront = servoState.getValue(ServoActuatorBlock.FACING);
        return servoFront == sensorInput.getOpposite() ? PortQuality.VALID : PortQuality.TOPOLOGY_ERROR;
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        BlockPos servoPos = inputPos(pos, state);
        PortQuality sourceQuality = sourceQuality(level, pos, state);
        if (side == inputSide(state)) {
            int position = sourceQuality == PortQuality.VALID ? ServoActuatorBlock.position(level, servoPos) : 0;
            return Optional.of(new EngineeringPortSnapshot(
                    port.get(), position, 0.0, 15.0, sourceQuality));
        }
        MeasurementSnapshot measurement = measurement(level, pos);
        PortQuality outputQuality = sourceQuality == PortQuality.VALID
                ? MetrologySupport.portQuality(measurement) : sourceQuality;
        return Optional.of(EngineeringPortSnapshot.redstone(
                port.get(), state.getValue(OUTPUT), outputQuality));
    }

    /** Mechanical RX is not vanilla redstone; only the configured TX exposes vanilla redstone. */
    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return direction != null && direction.getOpposite() == outputSide(state);
    }

    @Override
    protected int computeOutput(Level level, BlockPos pos, BlockState state) {
        if (sourceQuality(level, pos, state) != PortQuality.VALID) return 0;
        BlockPos servoPos = inputPos(pos, state);
        int truePosition = ServoActuatorBlock.position(level, servoPos);
        if (!(level instanceof ServerLevel server)) return truePosition;
        double reading = MetrologySupport.conditionRedstone(server, pos, truePosition, SENSOR_PROFILE);
        MetrologySupport.sample(server, CHANNEL, pos, reading, truePosition, false, 1.0, 30L);
        return (int) Math.round(reading);
    }

    public static MeasurementSnapshot measurement(Level level, BlockPos pos) {
        return MetrologySupport.snapshot(level, CHANNEL, pos, 1.0, 30L);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) MetrologyStore.remove(level, CHANNEL, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                MetrologyStore.remove(level, CHANNEL, pos);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("Servo position metrology reset"), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
