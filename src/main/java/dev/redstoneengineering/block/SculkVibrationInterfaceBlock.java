package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.physics.RedstoneObservationSupport;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/** Engineering interface for vanilla Sculk/calibrated-sensor event-code redstone output. */
public class SculkVibrationInterfaceBlock extends PassiveDirectionalSignalBlock {
    private static final String KEY = "sculk_interface";
    private static final int CURRENT_CODE = 0;
    private static final int EVENT_COUNT = 1;
    private static final int LAST_EVENT_CODE = 2;
    private static final int LAST_EVENT_TICK = 3;
    private static final int TRANSITION_COUNT = 4;
    private static final int RUNTIME_SIZE = 5;

    public SculkVibrationInterfaceBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<SculkVibrationInterfaceBlock> codec() {
        return RedstoneEngineering.SCULK_VIBRATION_INTERFACE_CODEC.value();
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("SCULK CODE IN", inputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.SENSOR, PortDirection.INPUT, true, "event_code"),
                new EngineeringPort("EVENT CODE OUT", outputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.MEASUREMENT, PortDirection.OUTPUT, true, "event_code")
        );
    }

    private RedstoneObservationSupport.Observation inputObservation(Level level, BlockPos pos, BlockState state) {
        return RedstoneObservationSupport.observe(level, pos, inputSide(state));
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        RedstoneObservationSupport.Observation observation = inputObservation(level, pos, state);
        int value = side == inputSide(state) ? observation.value() : state.getValue(OUTPUT);
        return Optional.of(EngineeringPortSnapshot.redstone(port.get(), value, observation.quality()));
    }

    @Override
    protected int computeOutput(Level level, BlockPos pos, BlockState state) {
        RedstoneObservationSupport.Observation observation = inputObservation(level, pos, state);
        return observation.valid() ? Math.max(0, Math.min(15, observation.value())) : 0;
    }

    /**
     * Own one authoritative sample transition. Neighbor notifications call this immediately so
     * a short event-code pulse cannot disappear before the next scheduled tick; the periodic
     * tick still provides continuous reconciliation when no neighbor notification is produced.
     */
    private void captureSample(ServerLevel level, BlockPos pos, BlockState state) {
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        int now = computeOutput(level, pos, state);
        if (now > 0 && runtime[CURRENT_CODE] == 0) {
            runtime[EVENT_COUNT]++;
            runtime[LAST_EVENT_CODE] = now;
            runtime[LAST_EVENT_TICK] = (int) Math.min(Integer.MAX_VALUE, level.getGameTime());
        }
        if (now != runtime[CURRENT_CODE]) runtime[TRANSITION_COUNT]++;
        runtime[CURRENT_CODE] = now;
        updateOutput(level, pos, state, now);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel serverLevel) {
            captureSample(serverLevel, pos, state);
            serverLevel.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block neighborBlock,
            BlockPos neighborPos,
            boolean movedByPiston
    ) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level instanceof ServerLevel serverLevel) {
            captureSample(serverLevel, pos, state);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        captureSample(level, pos, state);
        level.scheduleTick(pos, this, 1);
    }

    /** Observer-only telemetry access; UI inspection must not create event history. */
    private static int runtimeValue(Level level, BlockPos pos, int index) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length <= index ? 0 : runtime[index];
    }

    public int eventCount(Level level, BlockPos pos) {
        return runtimeValue(level, pos, EVENT_COUNT);
    }

    public int lastEventCode(Level level, BlockPos pos) {
        return runtimeValue(level, pos, LAST_EVENT_CODE);
    }

    public int transitionCount(Level level, BlockPos pos) {
        return runtimeValue(level, pos, TRANSITION_COUNT);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, KEY, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                RuntimeIntStore.remove(level, KEY, pos);
                player.displayClientMessage(Component.literal("Sculk event diagnostics reset"), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
