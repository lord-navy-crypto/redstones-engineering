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
import dev.redstoneengineering.physics.RedstoneObservationSupport;
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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/**
 * Two-input redstone comparator with Schmitt-style hysteresis.
 *
 * <p>PROCESS and REFERENCE are external 0..15 signals. Unlike the Signal Conditioner's fixed
 * threshold transfer, this block compares two live engineering signals and retains decision
 * state through a bounded hysteresis band to avoid chatter.</p>
 */
public final class AnalogComparatorBlock extends DirectionalSignalBlock {
    public static final IntegerProperty MODE = IntegerProperty.create("mode", 0, 1);
    public static final IntegerProperty HYSTERESIS = IntegerProperty.create("hysteresis", 0, 3);

    public static final int ABOVE = 0;
    public static final int BELOW = 1;

    private static final String RUNTIME_KEY = "analog_comparator";
    private static final int TRANSITIONS = 0;
    private static final int LAST_MARGIN = 1;
    private static final int RUNTIME_SIZE = 2;

    public AnalogComparatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(MODE, ABOVE)
                .setValue(HYSTERESIS, 1));
    }

    @Override
    public MapCodec<AnalogComparatorBlock> codec() {
        return RedstoneEngineering.ANALOG_COMPARATOR_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MODE, HYSTERESIS);
    }

    public static Direction referenceSide(BlockState state) {
        return leftOf(seriesOutputSide(state));
    }

    public static int processValue(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof AnalogComparatorBlock comparator)) return 0;
        return comparator.readBackInput(level, pos, state);
    }

    public static int referenceValue(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof AnalogComparatorBlock comparator)) return 0;
        return comparator.readInputFrom(level, pos, referenceSide(state));
    }

    public static int margin(Level level, BlockPos pos, BlockState state) {
        return processValue(level, pos, state) - referenceValue(level, pos, state);
    }

    public static int transitionCount(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, RUNTIME_KEY, pos);
        return runtime == null || runtime.length < RUNTIME_SIZE ? 0 : Math.max(0, runtime[TRANSITIONS]);
    }

    public static String modeName(int mode) {
        return mode == BELOW ? "BELOW" : "ABOVE";
    }

    public static boolean stepMode(Level level, BlockPos pos, boolean forward) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof AnalogComparatorBlock comparator)) return false;
        int next = state.getValue(MODE) == ABOVE ? BELOW : ABOVE;
        level.setBlock(pos, state.setValue(MODE, next), Block.UPDATE_CLIENTS);
        if (level instanceof ServerLevel server) server.scheduleTick(pos, comparator, 1);
        return true;
    }

    public static boolean stepHysteresis(Level level, BlockPos pos, boolean forward) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof AnalogComparatorBlock comparator)) return false;
        int current = state.getValue(HYSTERESIS);
        int next = Math.floorMod(current + (forward ? 1 : -1), 4);
        level.setBlock(pos, state.setValue(HYSTERESIS, next), Block.UPDATE_CLIENTS);
        if (level instanceof ServerLevel server) server.scheduleTick(pos, comparator, 1);
        return true;
    }

    @Override
    protected boolean isEngineeringPort(BlockState state, Direction side) {
        return side == inputSide(state) || side == referenceSide(state) || side == outputSide(state);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("PROCESS", inputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.MEASUREMENT, PortDirection.INPUT, true, "process"),
                new EngineeringPort("REFERENCE", referenceSide(state), EngineeringDomain.REDSTONE,
                        PortKind.MEASUREMENT, PortDirection.INPUT, true, "reference"),
                new EngineeringPort("DECISION OUT", outputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.CONTROL, PortDirection.OUTPUT, true, "decision")
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        if (side == outputSide(state)) {
            var process = RedstoneObservationSupport.observe(level, pos, inputSide(state));
            var reference = RedstoneObservationSupport.observe(level, pos, referenceSide(state));
            PortQuality quality = RedstoneObservationSupport.combineQuality(process.quality(), reference.quality());
            return Optional.of(EngineeringPortSnapshot.redstone(port.get(), state.getValue(OUTPUT), quality));
        }
        var observed = RedstoneObservationSupport.observe(level, pos, side);
        return Optional.of(EngineeringPortSnapshot.redstone(port.get(), observed.value(), observed.quality()));
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int process = processValue(level, pos, state);
        int reference = referenceValue(level, pos, state);
        int hysteresis = state.getValue(HYSTERESIS);
        boolean wasHigh = state.getValue(OUTPUT) > 0;

        boolean high;
        if (state.getValue(MODE) == ABOVE) {
            high = wasHigh
                    ? process >= Math.max(0, reference - hysteresis)
                    : process > Math.min(15, reference + hysteresis);
        } else {
            high = wasHigh
                    ? process <= Math.min(15, reference + hysteresis)
                    : process < Math.max(0, reference - hysteresis);
        }

        int[] runtime = RuntimeIntStore.get(level, RUNTIME_KEY, pos, RUNTIME_SIZE);
        runtime[LAST_MARGIN] = process - reference;
        if (high != wasHigh && runtime[TRANSITIONS] < Integer.MAX_VALUE) runtime[TRANSITIONS]++;

        updateOutput(level, pos, state, high ? 15 : 0);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, RUNTIME_KEY, pos);
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                stepHysteresis(level, pos, true);
                BlockState next = level.getBlockState(pos);
                player.displayClientMessage(Component.literal(
                        "Analog Comparator | mode=" + modeName(next.getValue(MODE))
                                + " | hysteresis=±" + next.getValue(HYSTERESIS)
                                + " | process=" + processValue(level, pos, next)
                                + " ref=" + referenceValue(level, pos, next)
                                + " margin=" + margin(level, pos, next)
                                + " | transitions=" + transitionCount(level, pos)), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
