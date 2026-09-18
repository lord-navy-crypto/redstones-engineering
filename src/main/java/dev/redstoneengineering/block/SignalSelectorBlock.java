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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/**
 * Two-input redstone signal selector / analog multiplexer.
 *
 * <p>A and B remain full 0..15 engineering signals. SELECT only chooses which input reaches OUT;
 * it does not quantize the selected payload. Invert-select is a compact fail-safe/configuration
 * option rather than a separate logic block.</p>
 */
public final class SignalSelectorBlock extends DirectionalSignalBlock {
    public static final BooleanProperty INVERT_SELECT = BooleanProperty.create("invert_select");

    private static final String RUNTIME_KEY = "signal_selector";
    private static final int LAST_SELECTION = 0;
    private static final int SWITCH_COUNT = 1;
    private static final int INITIALIZED = 2;
    private static final int CONTROL_HOLD_ACTIVE = 3;
    private static final int CONTROL_BAD_EPISODES = 4;
    private static final int RUNTIME_SIZE = 5;

    public SignalSelectorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(INVERT_SELECT, false));
    }

    @Override
    public MapCodec<SignalSelectorBlock> codec() {
        return RedstoneEngineering.SIGNAL_SELECTOR_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(INVERT_SELECT);
    }

    public static Direction inputBSide(BlockState state) {
        return leftOf(seriesOutputSide(state));
    }

    public static Direction selectSide(BlockState state) {
        return rightOf(seriesOutputSide(state));
    }

    private static boolean controlEvidenceUnusable(PortQuality quality) {
        return quality == PortQuality.STALE
                || quality == PortQuality.FAULT
                || quality == PortQuality.DOMAIN_MISMATCH
                || quality == PortQuality.TOPOLOGY_ERROR;
    }

    public static boolean selectedB(Level level, BlockPos pos, BlockState state) {
        var select = RedstoneObservationSupport.observe(level, pos, selectSide(state));
        int[] runtime = RuntimeIntStore.peek(level, RUNTIME_KEY, pos);
        if (controlEvidenceUnusable(select.quality())
                && runtime != null && runtime.length >= RUNTIME_SIZE && runtime[INITIALIZED] != 0) {
            return runtime[LAST_SELECTION] != 0;
        }
        boolean active = select.value() > 0;
        return state.getValue(INVERT_SELECT) ? !active : active;
    }

    public static int switchCount(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, RUNTIME_KEY, pos);
        return runtime == null || runtime.length < RUNTIME_SIZE ? 0 : Math.max(0, runtime[SWITCH_COUNT]);
    }

    public static boolean controlHoldActive(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, RUNTIME_KEY, pos);
        return runtime != null && runtime.length >= RUNTIME_SIZE && runtime[CONTROL_HOLD_ACTIVE] != 0;
    }

    public static int controlBadEpisodes(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, RUNTIME_KEY, pos);
        return runtime == null || runtime.length < RUNTIME_SIZE ? 0 : Math.max(0, runtime[CONTROL_BAD_EPISODES]);
    }

    public static PortQuality selectQuality(Level level, BlockPos pos, BlockState state) {
        return RedstoneObservationSupport.observe(level, pos, selectSide(state)).quality();
    }

    public static PortQuality selectedPayloadQuality(Level level, BlockPos pos, BlockState state) {
        Direction selectedSide = selectedB(level, pos, state) ? inputBSide(state) : seriesInputSide(state);
        return RedstoneObservationSupport.observe(level, pos, selectedSide).quality();
    }

    public static boolean toggleInvertSelect(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SignalSelectorBlock selector)) return false;
        level.setBlock(pos, state.setValue(INVERT_SELECT, !state.getValue(INVERT_SELECT)), Block.UPDATE_CLIENTS);
        if (level instanceof ServerLevel server) server.scheduleTick(pos, selector, 1);
        return true;
    }

    @Override
    protected boolean isEngineeringPort(BlockState state, Direction side) {
        return side == inputSide(state)
                || side == inputBSide(state)
                || side == selectSide(state)
                || side == outputSide(state);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("SIGNAL A", inputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.REDSTONE_ANALOG, PortDirection.INPUT, true, "signal_a"),
                new EngineeringPort("SIGNAL B", inputBSide(state), EngineeringDomain.REDSTONE,
                        PortKind.REDSTONE_ANALOG, PortDirection.INPUT, true, "signal_b"),
                new EngineeringPort("SELECT", selectSide(state), EngineeringDomain.REDSTONE,
                        PortKind.CONTROL, PortDirection.INPUT, true, "select"),
                new EngineeringPort("SELECTED OUT", outputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.REDSTONE_ANALOG, PortDirection.OUTPUT, true, "signal")
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        if (side == outputSide(state)) {
            Direction selectedSide = selectedB(level, pos, state) ? inputBSide(state) : inputSide(state);
            var selected = RedstoneObservationSupport.observe(level, pos, selectedSide);
            var select = RedstoneObservationSupport.observe(level, pos, selectSide(state));
            PortQuality outputQuality = RedstoneObservationSupport.combineQuality(
                    selected.quality(), select.quality());
            return Optional.of(EngineeringPortSnapshot.redstone(
                    port.get(), state.getValue(OUTPUT), outputQuality));
        }
        if (side == inputSide(state) || side == inputBSide(state)) {
            var observed = RedstoneObservationSupport.observe(level, pos, side);
            return Optional.of(EngineeringPortSnapshot.redstone(port.get(), observed.value(), observed.quality()));
        }
        var select = RedstoneObservationSupport.observe(level, pos, selectSide(state));
        return Optional.of(EngineeringPortSnapshot.redstone(
                port.get(), select.value(), select.quality()));
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        var select = RedstoneObservationSupport.observe(level, pos, selectSide(state));
        boolean badControl = controlEvidenceUnusable(select.quality());
        boolean b = selectedB(level, pos, state);
        int[] runtime = RuntimeIntStore.get(level, RUNTIME_KEY, pos, RUNTIME_SIZE);

        if (badControl) {
            if (runtime[CONTROL_HOLD_ACTIVE] == 0 && runtime[CONTROL_BAD_EPISODES] < Integer.MAX_VALUE) {
                runtime[CONTROL_BAD_EPISODES]++;
            }
            runtime[CONTROL_HOLD_ACTIVE] = 1;
        } else {
            runtime[CONTROL_HOLD_ACTIVE] = 0;
        }

        int selection = b ? 1 : 0;
        if (runtime[INITIALIZED] == 0) {
            runtime[LAST_SELECTION] = selection;
            runtime[INITIALIZED] = 1;
        } else if (!badControl && runtime[LAST_SELECTION] != selection) {
            runtime[LAST_SELECTION] = selection;
            if (runtime[SWITCH_COUNT] < Integer.MAX_VALUE) runtime[SWITCH_COUNT]++;
        }

        Direction selectedSide = b ? inputBSide(state) : inputSide(state);
        var selected = RedstoneObservationSupport.observe(level, pos, selectedSide);
        // Preserve the selected numerical payload, while engineeringSnapshot carries its evidence quality.
        updateOutput(level, pos, state, selected.value());
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
                toggleInvertSelect(level, pos);
                BlockState next = level.getBlockState(pos);
                player.displayClientMessage(Component.literal(
                        "Signal Selector | invertSelect=" + (next.getValue(INVERT_SELECT) ? "YES" : "NO")
                                + " | selected=" + (selectedB(level, pos, next) ? "B" : "A")
                                + " | control=" + (controlHoldActive(level, pos) ? "HOLD LAST" : "LIVE")
                                + " | badControlEpisodes=" + controlBadEpisodes(level, pos)
                                + " | switches=" + switchCount(level, pos)
                                + " | OUT=" + next.getValue(OUTPUT) + "/15"), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
