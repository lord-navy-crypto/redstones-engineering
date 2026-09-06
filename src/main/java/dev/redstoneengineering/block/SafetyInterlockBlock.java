package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.EngineeringSystemsModule;
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

/**
 * Three-channel permissive/interlock controller.
 * BACK=A, LEFT=B, RIGHT=C, FRONT=PERMIT. All three channels must be non-zero.
 */
public class SafetyInterlockBlock extends PassiveDirectionalSignalBlock {
    private static final String KEY = "safety_interlock";
    // [failedMask, blockedTicks, permitTicks, transitions, previousPermit]
    private static final int RUNTIME_SIZE = 5;

    public SafetyInterlockBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<SafetyInterlockBlock> codec() {
        return EngineeringSystemsModule.SAFETY_INTERLOCK_CODEC.value();
    }

    @Override
    protected boolean isEngineeringPort(BlockState state, Direction side) {
        Direction front = outputSide(state);
        return side == inputSide(state) || side == leftOf(front) || side == rightOf(front) || side == front;
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        Direction front = outputSide(state);
        return List.of(
                new EngineeringPort("PERMISSIVE A", inputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.SAFETY, PortDirection.INPUT, true, "permissive_a"),
                new EngineeringPort("PERMISSIVE B", leftOf(front), EngineeringDomain.REDSTONE,
                        PortKind.SAFETY, PortDirection.INPUT, true, "permissive_b"),
                new EngineeringPort("PERMISSIVE C", rightOf(front), EngineeringDomain.REDSTONE,
                        PortKind.SAFETY, PortDirection.INPUT, true, "permissive_c"),
                new EngineeringPort("PERMIT OUT", front, EngineeringDomain.REDSTONE,
                        PortKind.SAFETY, PortDirection.OUTPUT, true, "permit")
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        Direction front = outputSide(state);
        int value = side == front ? state.getValue(OUTPUT) : readInputFrom(level, pos, side);
        PortQuality quality = value > 0 ? PortQuality.VALID : PortQuality.NO_SIGNAL;
        return Optional.of(EngineeringPortSnapshot.redstone(port.get(), value, quality));
    }

    @Override
    protected int computeOutput(Level level, BlockPos pos, BlockState state) {
        Direction front = outputSide(state);
        int a = readBackInput(level, pos, state);
        int b = readInputFrom(level, pos, leftOf(front));
        int c = readInputFrom(level, pos, rightOf(front));
        int mask = (a <= 0 ? 1 : 0) | (b <= 0 ? 2 : 0) | (c <= 0 ? 4 : 0);
        int permit = mask == 0 ? 1 : 0;

        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        runtime[0] = mask;
        if (permit != runtime[4]) runtime[3]++;
        runtime[4] = permit;
        if (permit != 0) runtime[2]++;
        else runtime[1]++;
        return permit != 0 ? 15 : 0;
    }

    public static int failedMask(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length == 0 ? 7 : runtime[0];
    }

    public static String compactDiagnostics(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        if (runtime == null || runtime.length < RUNTIME_SIZE) return "INTERLOCK not evaluated";
        if (runtime[0] == 0) return "INTERLOCK PERMIT | all permissives valid | transitions=" + runtime[3];
        StringBuilder missing = new StringBuilder();
        if ((runtime[0] & 1) != 0) missing.append("A");
        if ((runtime[0] & 2) != 0) missing.append(missing.isEmpty() ? "B" : ",B");
        if ((runtime[0] & 4) != 0) missing.append(missing.isEmpty() ? "C" : ",C");
        return "INTERLOCK BLOCKED | missing=" + missing + " | blockedTicks=" + runtime[1];
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server) server.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        updateOutput(level, pos, state, outputValue(level, pos, state));
        level.scheduleTick(pos, this, 2);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, KEY, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                RuntimeIntStore.remove(level, KEY, pos);
                player.displayClientMessage(Component.literal("Interlock diagnostics reset"), true);
            } else {
                player.displayClientMessage(Component.literal(compactDiagnostics(level, pos)), true);
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
