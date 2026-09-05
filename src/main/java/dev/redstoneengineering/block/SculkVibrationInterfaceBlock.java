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

/** Engineering interface for vanilla Sculk/calibrated-sensor event-code redstone output. */
public class SculkVibrationInterfaceBlock extends PassiveDirectionalSignalBlock {
    private static final String KEY = "sculk_interface";

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

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        int value = side == inputSide(state) ? computeOutput(level, pos, state) : state.getValue(OUTPUT);
        return Optional.of(EngineeringPortSnapshot.redstone(
                port.get(), value, value > 0 ? PortQuality.VALID : PortQuality.NO_SIGNAL));
    }

    @Override
    protected int computeOutput(Level level, BlockPos pos, BlockState state) {
        return Math.max(0, Math.min(15, level.getSignal(inputPos(pos, state), inputSide(state))));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel serverLevel) serverLevel.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, 5);
        int now = computeOutput(level, pos, state);
        if (now > 0 && runtime[0] == 0) {
            runtime[1]++;
            runtime[2] = now;
            runtime[3] = (int) Math.min(Integer.MAX_VALUE, level.getGameTime());
        }
        if (now != runtime[0]) runtime[4]++;
        runtime[0] = now;
        updateOutput(level, pos, state, now);
        level.scheduleTick(pos, this, 1);
    }

    public int eventCount(Level level, BlockPos pos) {
        return RuntimeIntStore.get(level, KEY, pos, 5)[1];
    }

    public int lastEventCode(Level level, BlockPos pos) {
        return RuntimeIntStore.get(level, KEY, pos, 5)[2];
    }

    public int transitionCount(Level level, BlockPos pos) {
        return RuntimeIntStore.get(level, KEY, pos, 5)[4];
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
