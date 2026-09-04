package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.metrology.MeasurementSnapshot;
import dev.redstoneengineering.metrology.MetrologySupport;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Position feedback sensor with metrology plus velocity/error/trajectory diagnostics. */
public class ServoPositionSensorBlock extends PassiveDirectionalSignalBlock {
    private static final String CHANNEL = "servo_position_sensor";
    private static final int SENSOR_PROFILE = 2; // PRECISION

    public ServoPositionSensorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<ServoPositionSensorBlock> codec() {
        return RedstoneEngineering.SERVO_POSITION_SENSOR_CODEC.value();
    }

    @Override
    protected int computeOutput(Level level, BlockPos pos, BlockState state) {
        BlockPos servoPos = inputPos(pos, state);
        if (!(level.getBlockState(servoPos).getBlock() instanceof ServoActuatorBlock)) return 0;
        int truePosition = RuntimeIntStore.get(level, "servo", servoPos, 13)[0];
        if (!(level instanceof ServerLevel server)) return truePosition;

        double reading = MetrologySupport.conditionRedstone(server, pos, truePosition, SENSOR_PROFILE);
        MetrologySupport.sample(server, CHANNEL, pos, reading, truePosition, false, 1.0, 30L);
        return (int) Math.round(reading);
    }

    public static MeasurementSnapshot measurement(Level level, BlockPos pos) {
        return MetrologySupport.snapshot(level, CHANNEL, pos, 1.0, 30L);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockPos servoPos = inputPos(pos, state);
            if (level.getBlockState(servoPos).getBlock() instanceof ServoActuatorBlock) {
                int[] runtime = RuntimeIntStore.get(level, "servo", servoPos, 13);
                player.displayClientMessage(Component.literal(
                        "Servo feedback | position=" + runtime[0]
                                + " velocity=" + runtime[2]
                                + " error=" + runtime[3]
                                + " target=" + runtime[1]
                                + " settle=" + runtime[11] + "t commands=" + runtime[6]
                                + " | " + MetrologySupport.compactDiagnostics(measurement(level, pos))
                ), true);
            } else {
                player.displayClientMessage(Component.literal("Servo position sensor | NO SERVO on BACK"), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
