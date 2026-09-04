package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.metrology.MeasurementSnapshot;
import dev.redstoneengineering.metrology.MetrologyStore;
import dev.redstoneengineering.metrology.MetrologySupport;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Inline pressure-drop/flow proxy instrument with 0..100 flow metrology. BACK=input, FRONT=output. */
public class PneumaticFlowMeterBlock extends DirectionalDomainBlock {
    private static final String CHANNEL = "pneumatic_flow_meter";
    private static final int SENSOR_PROFILE = 2; // PRECISION
    private static final int SAMPLE_PERIOD = 10;

    public PneumaticFlowMeterBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<PneumaticFlowMeterBlock> codec() {
        return RedstoneEngineering.PNEUMATIC_FLOW_METER_CODEC.value();
    }

    public static int flowProxy(Level level, BlockPos pos) {
        return RuntimeIntStore.get(level, "pneumatic_flow", pos, 4)[0];
    }

    public static MeasurementSnapshot measurement(Level level, BlockPos pos) {
        return MetrologySupport.snapshot(level, CHANNEL, pos, 1.0, 30L);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide && !state.is(oldState.getBlock())) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int[] runtime = RuntimeIntStore.get(level, "pneumatic_flow", pos, 4);
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
        if (!state.is(newState.getBlock())) MetrologyStore.remove(level, CHANNEL, pos);
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            int[] runtime = RuntimeIntStore.get(level, "pneumatic_flow", pos, 4);
            player.displayClientMessage(Component.literal(
                    "Pneumatic flow meter | ΔP=" + runtime[1]
                            + " | flow≈" + runtime[0]
                            + " | Pin/Pout=" + runtime[2] + "/" + runtime[3]
                            + " | " + MetrologySupport.compactDiagnostics(measurement(level, pos))
            ), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
