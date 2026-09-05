package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.metrology.MeasurementSnapshot;
import dev.redstoneengineering.metrology.MetrologyStore;
import dev.redstoneengineering.metrology.MetrologySupport;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.PneumaticNetwork;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/** Inline pressure-drop/flow proxy instrument with 0..100 flow metrology. BACK=input, FRONT=output. */
public class PneumaticFlowMeterBlock extends DirectionalDomainBlock implements EngineeringPortProvider {
    private static final String CHANNEL = "pneumatic_flow_meter";
    private static final String RUNTIME = "pneumatic_flow";
    private static final int SENSOR_PROFILE = 2; // PRECISION
    private static final int SAMPLE_PERIOD = 10;

    public PneumaticFlowMeterBlock(Properties properties) { super(properties); }

    @Override public MapCodec<PneumaticFlowMeterBlock> codec() {
        return RedstoneEngineering.PNEUMATIC_FLOW_METER_CODEC.value();
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort(
                        "PNEUMATIC IN", inputSide(state), EngineeringDomain.PNEUMATIC,
                        PortKind.MEASUREMENT, PortDirection.INPUT, false, "pressure"
                ),
                new EngineeringPort(
                        "PNEUMATIC OUT", outputSide(state), EngineeringDomain.PNEUMATIC,
                        PortKind.MEASUREMENT, PortDirection.OUTPUT, false, "pressure"
                )
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> descriptor = engineeringPort(state, side);
        if (descriptor.isEmpty()) return Optional.empty();
        int[] runtime = RuntimeIntStore.get(level, RUNTIME, pos, 4);
        int pressure = side == inputSide(state) ? runtime[2] : runtime[3];
        return Optional.of(new EngineeringPortSnapshot(
                descriptor.get(), pressure, 0.0, 100.0,
                pressure > 0 ? PortQuality.VALID : PortQuality.NO_SIGNAL
        ));
    }

    public static int flowProxy(Level level, BlockPos pos) {
        return RuntimeIntStore.get(level, RUNTIME, pos, 4)[0];
    }

    public static int pressureDrop(Level level, BlockPos pos) {
        return RuntimeIntStore.get(level, RUNTIME, pos, 4)[1];
    }

    public static int inletPressure(Level level, BlockPos pos) {
        return RuntimeIntStore.get(level, RUNTIME, pos, 4)[2];
    }

    public static int outletPressure(Level level, BlockPos pos) {
        return RuntimeIntStore.get(level, RUNTIME, pos, 4)[3];
    }

    public static MeasurementSnapshot measurement(Level level, BlockPos pos) {
        return MetrologySupport.snapshot(level, CHANNEL, pos, 1.0, 30L);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide && !state.is(oldState.getBlock())) {
            level.scheduleTick(pos, this, 1);
            if (level instanceof ServerLevel server) PneumaticNetwork.recomputeAround(server, pos);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int[] runtime = RuntimeIntStore.get(level, RUNTIME, pos, 4);
        int referenceFlow = runtime[0];
        boolean saturated = runtime[1] * 12 > 100;
        double reading = MetrologySupport.conditionBounded(
                level, pos, referenceFlow, 0.0, 100.0, SENSOR_PROFILE
        );
        MetrologySupport.sample(level, CHANNEL, pos, reading, referenceFlow, saturated, 1.0, 30L);
        level.scheduleTick(pos, this, SAMPLE_PERIOD);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            MetrologyStore.remove(level, CHANNEL, pos);
            RuntimeIntStore.remove(level, RUNTIME, pos);
            InformationRuntime.clear(level, "pneumatic", pos);
            if (level instanceof ServerLevel server) PneumaticNetwork.recomputeAround(server, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                int[] runtime = RuntimeIntStore.get(level, RUNTIME, pos, 4);
                player.displayClientMessage(Component.literal(
                        "Pneumatic flow meter | ΔP=" + runtime[1]
                                + " | flow≈" + runtime[0]
                                + " | Pin/Pout=" + runtime[2] + "/" + runtime[3]
                                + " | " + MetrologySupport.compactDiagnostics(measurement(level, pos))
                ), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
